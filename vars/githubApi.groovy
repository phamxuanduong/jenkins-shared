/**
 * githubApi - GitHub API utility functions for permissions and branch protection
 *
 * @param action String: The action to perform ('checkPermissions', 'getBranchProtection', 'getUser')
 * @param params Map: Parameters specific to each action
 * @return Mixed: Result based on action
 */
def call(String action, Map params = [:]) {
  switch (action) {
    case 'checkPermissions':
      return checkUserPermissions(params)
    case 'getBranchProtection':
      return getBranchProtectionRules(params)
    case 'getUser':
      return getUserInfo(params)
    case 'validateDeployPermissions':
      return validateDeployPermissions(params)
    default:
      error "githubApi: Unknown action '${action}'"
  }
}

/**
 * Check if user has admin permissions on repository
 */
def checkUserPermissions(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def username = params.username ?: getUserFromCommit()
  def token = params.token ?: env.GITHUB_TOKEN

  if (!token) {
    echo "[WARN] githubApi: GITHUB_TOKEN not found, skipping permission check"
    return [hasAdminAccess: true, reason: 'NO_TOKEN'] // Allow if no token configured
  }

  try {
    echo "[INFO] githubApi: Checking permissions for user '${username}' on ${repoOwner}/${repoName}"

    // Get user's repository permissions
    def response = sh(
      script: """
      curl -s -H "Authorization: token ${token}" \\
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
 * Get branch protection rules for repository
 */
def getBranchProtectionRules(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def token = params.token ?: env.GITHUB_TOKEN

  if (!token) {
    echo "[WARN] githubApi: GITHUB_TOKEN not found, skipping branch protection check"
    return [isProtected: false, reason: 'NO_TOKEN']
  }

  try {
    echo "[INFO] githubApi: Checking branch protection for '${branchName}' on ${repoOwner}/${repoName}"

    // Check if specific branch has protection rules
    def response = sh(
      script: """
      curl -s -H "Authorization: token ${token}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "https://api.github.com/repos/${repoOwner}/${repoName}/branches/${branchName}/protection"
      """,
      returnStdout: true
    ).trim()

    def jsonResponse = readJSON text: response

    if (jsonResponse.url) {
      // Branch is protected
      echo "[INFO] githubApi: Branch '${branchName}' is protected"

      return [
        isProtected: true,
        branchName: branchName,
        protectionRules: jsonResponse,
        reason: 'BRANCH_PROTECTED'
      ]
    } else if (jsonResponse.message && jsonResponse.message.contains('Branch not protected')) {
      // Branch is not protected
      echo "[INFO] githubApi: Branch '${branchName}' is not protected"
      return [isProtected: false, reason: 'NOT_PROTECTED']
    } else {
      echo "[WARN] githubApi: Unknown response for branch protection: ${response}"
      return [isProtected: false, reason: 'API_ERROR']
    }

  } catch (Exception e) {
    echo "[ERROR] githubApi: Failed to check branch protection: ${e.getMessage()}"
    return [isProtected: false, reason: 'API_EXCEPTION']
  }
}

/**
 * Get user information from commit
 */
def getUserInfo(Map params) {
  def username = params.username ?: getUserFromCommit()
  def token = params.token ?: env.GITHUB_TOKEN

  if (!token) {
    return [username: username, reason: 'NO_TOKEN']
  }

  try {
    def response = sh(
      script: """
      curl -s -H "Authorization: token ${token}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "https://api.github.com/users/${username}"
      """,
      returnStdout: true
    ).trim()

    def jsonResponse = readJSON text: response
    return [
      username: username,
      userInfo: jsonResponse,
      reason: 'SUCCESS'
    ]

  } catch (Exception e) {
    echo "[ERROR] githubApi: Failed to get user info: ${e.getMessage()}"
    return [username: username, reason: 'API_EXCEPTION']
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
      branchProtection: branchProtection
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