/**
 * getProjectVars - Auto-detect project variables from Git and environment
 *
 * @param config Map of optional parameters:
 *   - repoName: Repository name (default: auto from GIT_URL)
 *   - repoBranch: Branch name (default: auto from GIT_BRANCH)
 *   - namespace: Kubernetes namespace (default: repoName)
 *   - deployment: Deployment name (default: "{repoName}-{sanitizedBranch}")
 *   - appName: Application name (default: "{repoName}-{sanitizedBranch}")
 *   - registry: Docker registry (default: auto-select based on branch)
 *   - commitHash: Git commit hash (default: env.GIT_COMMIT?.take(7))
 *   - enablePermissionCheck: Enable GitHub permission validation (default: auto-detect from GITHUB_TOKEN)
 *
 * @return Map containing project variables and permission status
 */
def call(Map config = [:]) {
  // Fail-safe: Check if deployment is already blocked to prevent infinite loops
  if (env.DEPLOYMENT_BLOCKED == 'true') {
    echo "[FATAL] getProjectVars: Deployment already blocked - preventing infinite loop"
    currentBuild.result = 'FAILURE'
    throw new Exception("DEPLOYMENT_ALREADY_BLOCKED: Multiple calls detected")
  }
  // Use utility functions to extract repository info
  def repoName = getRepoName()
  def branchName = config.repoBranch ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'main'
  def sanitizedBranch = sanitizeBranchName(branchName)
  def finalRepoName = config.repoName ?: repoName ?: 'unknown-repo'

  // Determine registry based on branch name using utility function
  def registry = config.registry ?: getRegistryForBranch(branchName)

  // Set defaults and allow overrides
  def vars = [
    REPO_NAME: finalRepoName,
    REPO_BRANCH: branchName,
    SANITIZED_BRANCH: sanitizedBranch,
    NAMESPACE: config.namespace ?: finalRepoName,
    DEPLOYMENT: config.deployment ?: "${finalRepoName}-${sanitizedBranch}",
    APP_NAME: config.appName ?: "${finalRepoName}-${sanitizedBranch}",
    REGISTRY: registry,
    COMMIT_HASH: config.commitHash ?: env.GIT_COMMIT?.take(7) ?: 'latest'
  ]

  // Auto-enable permission check if explicitly enabled or has GitHub token
  def hasGitHubToken = env.GITHUB_TOKEN || env.GITHUB_APP_INSTALLATION_TOKEN
  def shouldCheckPermissions = config.enablePermissionCheck || (hasGitHubToken && config.enablePermissionCheck != false)

  def permissionCheck = [canDeploy: true, reason: 'SKIPPED']
  if (shouldCheckPermissions) {
    echo "[INFO] getProjectVars: GitHub permission validation enabled - checking for repository PROTECTED_BRANCHES variable"
    try {
      permissionCheck = githubApi('validateDeployPermissions', [
        repoName: finalRepoName,
        branchName: branchName
      ])

      // Add permission info to vars
      vars.PERMISSION_CHECK = permissionCheck
      vars.CAN_DEPLOY = permissionCheck.canDeploy
      vars.PERMISSION_REASON = permissionCheck.reason
      vars.GIT_USER = permissionCheck.username ?: 'unknown'

    } catch (Exception e) {
      echo "[WARN] getProjectVars: Permission check failed: ${e.getMessage()}"
      // Default to allow deployment if permission check fails
      permissionCheck = [canDeploy: true, reason: 'PERMISSION_CHECK_FAILED']
      vars.PERMISSION_CHECK = permissionCheck
      vars.CAN_DEPLOY = true
      vars.PERMISSION_REASON = 'PERMISSION_CHECK_FAILED'
      vars.GIT_USER = 'unknown'
    }
  } else {
    echo "[INFO] getProjectVars: GitHub permission validation disabled (no GitHub token or explicitly disabled)"
    vars.PERMISSION_CHECK = permissionCheck
    vars.CAN_DEPLOY = true
    vars.PERMISSION_REASON = 'SKIPPED'
    vars.GIT_USER = 'unknown'
  }

  // Log all variables for debugging
  echo """
[INFO] getProjectVars: Project Variables:
  REPO_NAME:        ${vars.REPO_NAME}
  REPO_BRANCH:      ${vars.REPO_BRANCH}
  SANITIZED_BRANCH: ${vars.SANITIZED_BRANCH}
  NAMESPACE:        ${vars.NAMESPACE}
  DEPLOYMENT:       ${vars.DEPLOYMENT}
  APP_NAME:         ${vars.APP_NAME}
  REGISTRY:         ${vars.REGISTRY} (auto-selected based on branch)
  COMMIT_HASH:      ${vars.COMMIT_HASH}
  GIT_USER:         ${vars.GIT_USER}
  CAN_DEPLOY:       ${vars.CAN_DEPLOY}
  PERMISSION_REASON: ${vars.PERMISSION_REASON}
"""

  // If deployment is blocked, notify and stop the pipeline
  if (!vars.CAN_DEPLOY) {
    def blockMessage = ""
    def errorMessage = ""

    if (permissionCheck.reason == 'WRONG_AGENT') {
      def agentInfo = permissionCheck.agentValidation
      blockMessage = """
🚫 *Agent Assignment Error*

📦 *Repository:* `${permissionCheck.repository ?: "${vars.REPO_NAME}"}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
🤖 *Current Agent:* `${agentInfo?.currentAgent}`
✅ *Required Agent:* `${agentInfo?.requiredAgent}`

❌ *Reason:* ${getBlockedReasonMessage(permissionCheck)}

🔧 Please run this pipeline on the correct Jenkins agent for this branch type.

🔗 *Build:* [#${env.BUILD_NUMBER}](${env.BUILD_URL})
"""
      errorMessage = "Agent validation failed: ${getBlockedReasonMessage(permissionCheck)}"
    } else {
      blockMessage = """
🚫 *Deployment Blocked*

📦 *Repository:* `${permissionCheck.repository ?: "${vars.REPO_NAME}"}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
👤 *User:* `${vars.GIT_USER}`

❌ *Reason:* ${getBlockedReasonMessage(permissionCheck)}

🔒 This branch requires specific permissions to deploy.
Please contact a repository administrator or use the correct agent.

🔗 *Build:* [#${env.BUILD_NUMBER}](${env.BUILD_URL})
"""
      errorMessage = "Deployment blocked: ${permissionCheck.reason} - ${getBlockedReasonMessage(permissionCheck)}"
    }

    // Send Telegram notification about blocked deployment
    try {
      telegramNotify([
        message: blockMessage,
        failOnError: false
      ])
    } catch (Exception e) {
      echo "[WARN] getProjectVars: Failed to send blocked deployment notification: ${e.getMessage()}"
    }

    echo "[ERROR] getProjectVars: ${errorMessage}"
    echo "[FATAL] getProjectVars: Terminating pipeline due to blocked deployment"

    // Set environment flag to prevent infinite loops
    env.DEPLOYMENT_BLOCKED = 'true'

    // Use currentBuild.result to mark build as failed and throw exception
    currentBuild.result = 'FAILURE'

    // Force immediate termination
    throw new hudson.AbortException("DEPLOYMENT_BLOCKED: ${errorMessage}")
  }

  return vars
}

/**
 * Generate human-readable message for blocked deployment reason
 */
def getBlockedReasonMessage(permissionCheck) {
  switch (permissionCheck.reason) {
    case 'WRONG_AGENT':
      def agentInfo = permissionCheck.agentValidation
      return "Branch '${agentInfo?.branchName}' (${agentInfo?.description}) must run on agent '${agentInfo?.requiredAgent}' but currently running on '${agentInfo?.currentAgent}'"
    case 'ADMIN_REQUIRED_BUT_NOT_ADMIN':
      return "Branch requires ADMIN permission but user has '${permissionCheck.userPermissions?.permission ?: 'unknown'}' permission"
    case 'MAINTAIN_OR_ADMIN_REQUIRED':
      return "Branch requires MAINTAIN or ADMIN permission but user has '${permissionCheck.userPermissions?.permission ?: 'unknown'}' permission"
    case 'INSUFFICIENT_PERMISSIONS':
      return "User does not have sufficient permissions for protected branch"
    case 'API_ERROR':
      return "GitHub API error during permission check"
    case 'API_EXCEPTION':
      return "GitHub API exception during permission check"
    default:
      return "Permission issue: ${permissionCheck.reason}"
  }
}