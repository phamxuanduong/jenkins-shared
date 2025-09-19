/**
 * githubApi - GitHub API utility functions for permissions and branch protection
 *
 * @param action String: The action to perform ('checkPermissions', 'getBranchProtection', 'validateAgentAssignment', 'validateDeployPermissions')
 * @param params Map: Parameters specific to each action
 * @return Mixed: Result based on action
 */
def call(String action, Map params = [:]) {
  switch (action) {
    case 'checkPermissions':
      return checkUserPermissions(params)
    case 'getBranchProtection':
      return getBranchProtectionRules(params)
    case 'validateAgentAssignment':
      return validateAgentAssignment(params)
    case 'validateDeployPermissions':
      return validateDeployPermissions(params)
    default:
      error "githubApi: Unknown action '${action}'"
  }
}

/**
 * Check if user has admin permissions via GitHub API
 */
def checkUserPermissions(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def username = params.username ?: getUserFromCommit()
  def token = params.token ?: env.GITHUB_TOKEN ?: env.GITHUB_APP_INSTALLATION_TOKEN

  if (!token) {
    echo "[WARN] githubApi: GITHUB_TOKEN not found, skipping permission check"
    return [hasAdminAccess: true, reason: 'NO_TOKEN'] // Allow if no token configured
  }

  try {
    echo "[INFO] githubApi: Checking permissions for user '${username}' on ${repoOwner}/${repoName}"

    // Get user's repository permissions using secure credential handling
    def apiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/collaborators/${username}/permission"
    def response = ''

    withCredentials([string(credentialsId: 'github-token', variable: 'SECURE_TOKEN')]) {
      response = sh(
        script: """
        set +x
        curl -s -H "Authorization: token \$SECURE_TOKEN" \\
             -H "Accept: application/vnd.github.v3+json" \\
             "${apiUrl}"
        """,
        returnStdout: true
      ).trim()
    }

    def jsonResponse = readJSON text: response

    if (jsonResponse.permission) {
      def permission = jsonResponse.permission
      def hasAdmin = permission == 'admin'

      echo "[INFO] githubApi: User '${username}' has '${permission}' permission"

      return [
        hasAdminAccess: hasAdmin,
        permission: permission,
        username: username,
        reason: hasAdmin ? 'ADMIN_ACCESS' : 'INSUFFICIENT_PERMISSION'
      ]
    } else {
      echo "[WARN] githubApi: Could not determine user permissions: ${response}"
      return [hasAdminAccess: true, reason: 'API_ERROR'] // Allow if API fails
    }

  } catch (Exception e) {
    echo "[ERROR] githubApi: Failed to check permissions: ${e.getMessage()}"
    return [hasAdminAccess: true, reason: 'API_EXCEPTION'] // Allow if exception occurs
  }
}

/**
 * Check branch protection using GitHub Repository Variables with admin/maintain levels
 */
def getBranchProtectionRules(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def token = params.token ?: env.GITHUB_TOKEN ?: env.GITHUB_APP_INSTALLATION_TOKEN

  if (!token) {
    echo "[WARN] githubApi: GITHUB_TOKEN not found, skipping branch protection check"
    return [isProtected: false, reason: 'NO_TOKEN']
  }

  try {
    echo "[INFO] githubApi: Fetching branch protection variables from GitHub Repository Variables for ${repoOwner}/${repoName}"

    // Fetch BRANCH_PROTECT_ADMIN and BRANCH_PROTECT_MAINTAIN variables using secure credentials
    def adminApiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/actions/variables/BRANCH_PROTECT_ADMIN"
    def maintainApiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/actions/variables/BRANCH_PROTECT_MAINTAIN"
    def adminResponse = ''
    def maintainResponse = ''

    withCredentials([string(credentialsId: 'github-token', variable: 'SECURE_TOKEN')]) {
      adminResponse = sh(
        script: """
        set +x
        curl -s -H "Authorization: token \$SECURE_TOKEN" \\
             -H "Accept: application/vnd.github.v3+json" \\
             "${adminApiUrl}"
        """,
        returnStdout: true
      ).trim()

      maintainResponse = sh(
        script: """
        set +x
        curl -s -H "Authorization: token \$SECURE_TOKEN" \\
             -H "Accept: application/vnd.github.v3+json" \\
             "${maintainApiUrl}"
        """,
        returnStdout: true
      ).trim()
    }

    def adminJson = readJSON text: adminResponse
    def maintainJson = readJSON text: maintainResponse

    def adminBranches = []
    def maintainBranches = []

    // Parse admin protected branches
    if (adminJson.name == 'BRANCH_PROTECT_ADMIN' && adminJson.value) {
      adminBranches = adminJson.value.split(',').collect { it.trim() }
      echo "[INFO] githubApi: Found BRANCH_PROTECT_ADMIN: ${adminJson.value}"
    }

    // Parse maintain protected branches
    if (maintainJson.name == 'BRANCH_PROTECT_MAINTAIN' && maintainJson.value) {
      maintainBranches = maintainJson.value.split(',').collect { it.trim() }
      echo "[INFO] githubApi: Found BRANCH_PROTECT_MAINTAIN: ${maintainJson.value}"
    }

    // Check if branch is in either list
    def isAdminProtected = adminBranches.contains(branchName)
    def isMaintainProtected = maintainBranches.contains(branchName)

    if (isAdminProtected) {
      echo "[INFO] githubApi: Branch '${branchName}' requires ADMIN permission"
      return [
        isProtected: true,
        branchName: branchName,
        protectionLevel: 'ADMIN',
        adminBranches: adminJson.value ?: '',
        maintainBranches: maintainJson.value ?: '',
        reason: 'ADMIN_REQUIRED'
      ]
    } else if (isMaintainProtected) {
      echo "[INFO] githubApi: Branch '${branchName}' requires MAINTAIN or ADMIN permission"
      return [
        isProtected: true,
        branchName: branchName,
        protectionLevel: 'MAINTAIN',
        adminBranches: adminJson.value ?: '',
        maintainBranches: maintainJson.value ?: '',
        reason: 'MAINTAIN_REQUIRED'
      ]
    } else {
      // Check if no protection variables exist at all
      def hasNoVars = (!adminJson.name && !maintainJson.name) ||
                      (adminJson.message?.contains('Not Found') && maintainJson.message?.contains('Not Found'))

      if (hasNoVars) {
        echo "[INFO] githubApi: No branch protection variables found in repository - skipping protection check"
        return [
          isProtected: false,
          branchName: branchName,
          reason: 'NO_PROTECTION_VARS'
        ]
      } else {
        echo "[INFO] githubApi: Branch '${branchName}' is not in any protected branches list"
        return [
          isProtected: false,
          branchName: branchName,
          adminBranches: adminJson.value ?: '',
          maintainBranches: maintainJson.value ?: '',
          reason: 'NOT_PROTECTED'
        ]
      }
    }

  } catch (Exception e) {
    echo "[ERROR] githubApi: Failed to fetch repository variables: ${e.getMessage()}"
    return [
      isProtected: false,
      branchName: branchName,
      reason: 'API_EXCEPTION'
    ]
  }
}


/**
 * Validate agent assignment based on branch patterns
 */
def validateAgentAssignment(Map params = [:]) {
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def currentAgent = env.NODE_NAME ?: 'unknown'

  echo "[INFO] githubApi: Validating agent assignment for branch '${branchName}' on agent '${currentAgent}'"

  // Define branch patterns and their allowed agent patterns
  def branchAgentRules = [
    [pattern: /.*beta.*/, allowedAgents: ['beta'], description: 'Beta branches'],
    [pattern: /.*staging.*/, allowedAgents: ['prod', 'prod-.*'], description: 'Staging branches'],
    [pattern: /.*prod.*/, allowedAgents: ['prod', 'prod-.*'], description: 'Production branches'],
    [pattern: /^main$/, allowedAgents: ['prod', 'prod-.*'], description: 'Main branch'],
    [pattern: /^master$/, allowedAgents: ['prod', 'prod-.*'], description: 'Master branch'],
    [pattern: /.*production.*/, allowedAgents: ['prod', 'prod-.*'], description: 'Production branches']
  ]

  // Check if branch matches any pattern
  def matchedRule = branchAgentRules.find { rule ->
    branchName.toLowerCase() ==~ rule.pattern
  }

  if (matchedRule) {
    def allowedAgents = matchedRule.allowedAgents
    def isCorrectAgent = allowedAgents.any { agentPattern ->
      currentAgent ==~ agentPattern
    }

    if (isCorrectAgent) {
      echo "[SUCCESS] githubApi: ${matchedRule.description} '${branchName}' correctly running on agent '${currentAgent}'"
      return [
        isValidAgent: true,
        branchName: branchName,
        currentAgent: currentAgent,
        allowedAgents: allowedAgents,
        reason: 'CORRECT_AGENT'
      ]
    } else {
      def allowedAgentsStr = allowedAgents.join(' or ')
      echo "[BLOCKED] githubApi: ${matchedRule.description} '${branchName}' must run on agent matching '${allowedAgentsStr}' but running on '${currentAgent}'"
      return [
        isValidAgent: false,
        branchName: branchName,
        currentAgent: currentAgent,
        allowedAgents: allowedAgents,
        description: matchedRule.description,
        reason: 'WRONG_AGENT'
      ]
    }
  } else {
    echo "[INFO] githubApi: Branch '${branchName}' has no specific agent requirements"
    return [
      isValidAgent: true,
      branchName: branchName,
      currentAgent: currentAgent,
      reason: 'NO_AGENT_REQUIREMENT'
    ]
  }
}

/**
 * Perform comprehensive permission check
 */
def validateDeployPermissions(Map params = [:]) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def username = params.username ?: getUserFromCommit()

  echo "[INFO] githubApi: Validating deployment permissions..."
  echo "[INFO] githubApi: Repository: ${repoOwner}/${repoName}"
  echo "[INFO] githubApi: Branch: ${branchName}"
  echo "[INFO] githubApi: User: ${username}"

  // First, validate agent assignment
  def agentValidation = validateAgentAssignment([
    branchName: branchName
  ])

  if (!agentValidation.isValidAgent) {
    echo "[BLOCKED] githubApi: Agent validation failed for branch '${branchName}'"
    return [
      canDeploy: false,
      reason: 'WRONG_AGENT',
      agentValidation: agentValidation,
      username: username,
      branchName: branchName,
      repository: "${repoOwner}/${repoName}"
    ]
  }

  // Check branch protection
  def branchProtection = getBranchProtectionRules([
    repoOwner: repoOwner,
    repoName: repoName,
    branchName: branchName
  ])

  // If branch is not protected, allow deployment
  if (!branchProtection.isProtected) {
    def reason = branchProtection.reason == 'NO_PROTECTION_VARS' ? 'NO_PROTECTION_VARS' : 'BRANCH_NOT_PROTECTED'
    echo "[INFO] githubApi: ${reason == 'NO_PROTECTION_VARS' ? 'No protection variables found' : 'Branch not protected'}, allowing deployment"
    return [
      canDeploy: true,
      reason: reason,
      branchProtection: branchProtection,
      username: username,
      branchName: branchName,
      repository: "${repoOwner}/${repoName}"
    ]
  }

  // If branch is protected, check user permissions
  def userPermissions = checkUserPermissions([
    repoOwner: repoOwner,
    repoName: repoName,
    username: username
  ])

  // Determine if user can deploy based on protection level and user role
  def canDeploy = false
  def deployReason = ''

  if (branchProtection.protectionLevel == 'ADMIN') {
    // Admin-only branches: only admin role can deploy
    canDeploy = userPermissions.permission == 'admin'
    deployReason = canDeploy ? 'ADMIN_ACCESS_GRANTED' : 'ADMIN_REQUIRED_BUT_NOT_ADMIN'
  } else if (branchProtection.protectionLevel == 'MAINTAIN') {
    // Maintain branches: both admin and maintain roles can deploy
    canDeploy = userPermissions.permission in ['admin', 'maintain']
    deployReason = canDeploy ? 'MAINTAIN_OR_ADMIN_ACCESS_GRANTED' : 'MAINTAIN_OR_ADMIN_REQUIRED'
  }

  if (canDeploy) {
    echo "[SUCCESS] githubApi: User '${username}' has '${userPermissions.permission}' permission, allowing deployment to ${branchProtection.protectionLevel}-protected branch '${branchName}'"
  } else {
    echo "[BLOCKED] githubApi: User '${username}' has '${userPermissions.permission}' permission but ${branchProtection.protectionLevel} permission required for branch '${branchName}'"
  }

  return [
    canDeploy: canDeploy,
    reason: deployReason,
    branchProtection: branchProtection,
    userPermissions: userPermissions,
    username: username,
    branchName: branchName,
    repository: "${repoOwner}/${repoName}"
  ]
}

/**
 * Utility functions (kept here for Jenkins shared library compatibility)
 */
def getRepoOwner() {
  def gitUrl = env.GIT_URL ?: ''
  if (gitUrl) {
    def urlPattern = /.*[\/:]([^\/]+)\/([^\/]+?)(?:\.git)?$/
    def matcher = gitUrl =~ urlPattern
    if (matcher) {
      return matcher[0][1]
    }
  }
  return 'unknown'
}

def getRepoName() {
  def gitUrl = env.GIT_URL ?: ''
  if (gitUrl) {
    def urlPattern = /.*[\/:]([^\/]+)\/([^\/]+?)(?:\.git)?$/
    def matcher = gitUrl =~ urlPattern
    if (matcher) {
      return matcher[0][2]
    }
  }
  return 'unknown'
}

def getUserFromCommit() {
  try {
    // Try to get username from git commit
    def gitUser = sh(
      script: "git log -1 --pretty=format:'%an' 2>/dev/null || echo 'unknown'",
      returnStdout: true
    ).trim()

    // If git user is an email, extract username part
    if (gitUser.contains('@')) {
      gitUser = gitUser.split('@')[0]
    }

    return gitUser
  } catch (Exception e) {
    echo "[WARN] githubApi: Could not determine git user: ${e.getMessage()}"
    return 'unknown'
  }
}