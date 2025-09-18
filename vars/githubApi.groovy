/**
 * githubApi - GitHub API utility functions for permissions and branch protection
 *
 * @param action String: The action to perform ('checkPermissions', 'getBranchProtection', 'validateDeployPermissions')
 * @param params Map: Parameters specific to each action
 * @return Mixed: Result based on action
 */
def call(String action, Map params = [:]) {
  switch (action) {
    case 'checkPermissions':
      return checkUserPermissions(params)
    case 'getBranchProtection':
      return getBranchProtectionRules(params)
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

    // Get user's repository permissions (hide token in logs)
    def response = sh(
      script: """
      curl -s -H "Authorization: token \${GITHUB_TOKEN}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "https://api.github.com/repos/${repoOwner}/${repoName}/collaborators/${username}/permission"
      """,
      returnStdout: true
    ).trim()

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
 * Check branch protection using environment variables (for GitHub Free tier)
 */
def getBranchProtectionRules(Map params) {
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def protectedBranches = env.PROTECTED_BRANCHES ?: 'main,master,production,prod'

  echo "[INFO] githubApi: Checking branch protection for '${branchName}' using environment config"
  echo "[INFO] githubApi: Protected branches: ${protectedBranches}"

  // Parse protected branches list
  def protectedList = protectedBranches.split(',').collect { it.trim() }
  def isProtected = protectedList.contains(branchName)

  if (isProtected) {
    echo "[INFO] githubApi: Branch '${branchName}' is in protected branches list"
    return [
      isProtected: true,
      branchName: branchName,
      reason: 'ENV_CONFIG_PROTECTED'
    ]
  } else {
    echo "[INFO] githubApi: Branch '${branchName}' is not in protected branches list"
    return [
      isProtected: false,
      branchName: branchName,
      reason: 'ENV_CONFIG_NOT_PROTECTED'
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

  // Check branch protection
  def branchProtection = getBranchProtectionRules([
    repoOwner: repoOwner,
    repoName: repoName,
    branchName: branchName
  ])

  // If branch is not protected, allow deployment
  if (!branchProtection.isProtected) {
    echo "[INFO] githubApi: Branch '${branchName}' is not protected, allowing deployment"
    return [
      canDeploy: true,
      reason: 'BRANCH_NOT_PROTECTED',
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

  def canDeploy = userPermissions.hasAdminAccess

  if (canDeploy) {
    echo "[SUCCESS] githubApi: User '${username}' has admin access, allowing deployment to protected branch '${branchName}'"
  } else {
    echo "[BLOCKED] githubApi: User '${username}' does not have admin access for protected branch '${branchName}'"
  }

  return [
    canDeploy: canDeploy,
    reason: canDeploy ? 'ADMIN_ACCESS_GRANTED' : 'INSUFFICIENT_PERMISSIONS',
    branchProtection: branchProtection,
    userPermissions: userPermissions,
    username: username,
    branchName: branchName,
    repository: "${repoOwner}/${repoName}"
  ]
}

/**
 * Utility functions
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