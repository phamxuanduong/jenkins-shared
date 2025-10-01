#!/usr/bin/env groovy

/**
 * sharedUtils - Common utility functions for Jenkins shared library
 *
 * This file contains reusable helper functions used across multiple pipeline functions.
 * All functions here should be generic and stateless where possible.
 */

/**
 * Get cached project vars from pipelineSetup or call getProjectVars()
 *
 * @return Map of project variables
 */
def getProjectVarsOptimized() {
  if (env.PIPELINE_SETUP_COMPLETE == 'true' && env.PROJECT_VARS_JSON) {
    try {
      return readJSON(text: env.PROJECT_VARS_JSON)
    } catch (Exception e) {
      echo "[WARN] sharedUtils: Could not parse cached project vars, falling back to getProjectVars()"
    }
  }
  return getProjectVars()
}

/**
 * Get project variables from Git and environment
 * Integrated from getProjectVars.groovy
 */
def getProjectVars(Map config = [:]) {
  // Fail-safe: Check if deployment is already blocked
  if (env.DEPLOYMENT_BLOCKED == 'true') {
    echo "[FATAL] sharedUtils: Deployment already blocked - preventing infinite loop"
    currentBuild.result = 'FAILURE'
    throw new Exception("DEPLOYMENT_ALREADY_BLOCKED: Multiple calls detected")
  }

  // Extract repository info from GIT_URL
  def gitUrl = env.GIT_URL ?: ''
  def repoName = ''

  if (gitUrl) {
    def urlPattern = /.*[\/:]([^\/]+)\/([^\/]+?)(?:\.git)?$/
    def matcher = gitUrl =~ urlPattern
    if (matcher) {
      repoName = matcher[0][2]
    }
  }

  // Calculate branch name and sanitize for K8s naming
  def branchName = config.repoBranch ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'main'
  def sanitizedBranch = branchName.replaceAll('/', '-').replaceAll('[^a-zA-Z0-9-]', '-').toLowerCase()
  def finalRepoName = config.repoName ?: repoName ?: 'unknown-repo'

  // Determine registry based on branch name
  def registry = config.registry
  if (!registry) {
    def lowerBranch = branchName.toLowerCase()
    if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
      registry = env.REGISTRY_BETA ?: '172.16.3.0/mtw'
    } else if (lowerBranch.contains('staging')) {
      registry = env.REGISTRY_STAGING ?: '172.16.3.0/mtw'
    } else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
               lowerBranch.contains('prod') || lowerBranch.contains('production')) {
      registry = env.REGISTRY_PROD ?: '172.16.3.0/mtw'
    } else {
      registry = env.REGISTRY_BETA ?: '172.16.3.0/mtw'
    }
  }

  // Get commit message for notifications
  def commitMessage = getCommitMessage()

  // Set defaults and allow overrides
  def vars = [
    REPO_NAME: finalRepoName,
    REPO_BRANCH: branchName,
    SANITIZED_BRANCH: sanitizedBranch,
    NAMESPACE: config.namespace ?: finalRepoName,
    DEPLOYMENT: config.deployment ?: "${finalRepoName}-${sanitizedBranch}",
    APP_NAME: config.appName ?: "${finalRepoName}-${sanitizedBranch}",
    REGISTRY: registry,
    COMMIT_HASH: config.commitHash ?: env.GIT_COMMIT?.take(7) ?: 'latest',
    COMMIT_MESSAGE: commitMessage
  ]

  // Auto-enable permission check if has GitHub token
  def hasGitHubToken = env.GITHUB_TOKEN || env.GITHUB_APP_INSTALLATION_TOKEN
  def shouldCheckPermissions = config.enablePermissionCheck || (hasGitHubToken && config.enablePermissionCheck != false)

  def permissionCheck = [canDeploy: true, reason: 'SKIPPED']
  if (shouldCheckPermissions) {
    echo "[INFO] sharedUtils: GitHub permission validation enabled"
    try {
      permissionCheck = validateDeployPermissions([
        repoName: finalRepoName,
        branchName: branchName
      ])

      vars.PERMISSION_CHECK = permissionCheck
      vars.CAN_DEPLOY = permissionCheck.canDeploy
      vars.PERMISSION_REASON = permissionCheck.reason
      vars.GIT_USER = permissionCheck.username ?: 'unknown'

    } catch (Exception e) {
      echo "[WARN] sharedUtils: Permission check failed: ${e.getMessage()}"
      permissionCheck = [canDeploy: true, reason: 'PERMISSION_CHECK_FAILED']
      vars.PERMISSION_CHECK = permissionCheck
      vars.CAN_DEPLOY = true
      vars.PERMISSION_REASON = 'PERMISSION_CHECK_FAILED'
      vars.GIT_USER = 'unknown'
    }
  } else {
    echo "[INFO] sharedUtils: GitHub permission validation disabled"
    vars.PERMISSION_CHECK = permissionCheck
    vars.CAN_DEPLOY = true
    vars.PERMISSION_REASON = 'SKIPPED'
    vars.GIT_USER = 'unknown'
  }

  echo """
[INFO] sharedUtils: Project Variables:
  REPO_NAME:        ${vars.REPO_NAME}
  REPO_BRANCH:      ${vars.REPO_BRANCH}
  SANITIZED_BRANCH: ${vars.SANITIZED_BRANCH}
  NAMESPACE:        ${vars.NAMESPACE}
  DEPLOYMENT:       ${vars.DEPLOYMENT}
  APP_NAME:         ${vars.APP_NAME}
  REGISTRY:         ${vars.REGISTRY}
  COMMIT_HASH:      ${vars.COMMIT_HASH}
  GIT_USER:         ${vars.GIT_USER}
  CAN_DEPLOY:       ${vars.CAN_DEPLOY}
  PERMISSION_REASON: ${vars.PERMISSION_REASON}
"""

  // If deployment is blocked, notify and stop
  if (vars.CAN_DEPLOY == false || vars.CAN_DEPLOY == 'false') {
    def blockMessage = """
🚫 *Deployment Blocked*

📦 *Repository:* `${permissionCheck.repository ?: "${vars.REPO_NAME}"}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
👤 *User:* `${vars.GIT_USER}`

❌ *Reason:* ${getBlockedReasonMessage(permissionCheck)}

🔒 This branch requires specific permissions to deploy.
Please contact a repository administrator.

🔗 *Build:* [#${env.BUILD_NUMBER}](${env.BUILD_URL})
"""

    try {
      telegramNotify([
        message: blockMessage,
        failOnError: false,
        vars: vars
      ])
    } catch (Exception e) {
      echo "[WARN] sharedUtils: Failed to send blocked deployment notification: ${e.getMessage()}"
    }

    env.DEPLOYMENT_BLOCKED = 'true'
    currentBuild.result = 'FAILURE'
    error("DEPLOYMENT_BLOCKED: ${permissionCheck.reason}")
  }

  return vars
}

/**
 * Get commit message from latest commit
 */
def getCommitMessage(Integer maxLength = 100) {
  try {
    def commitMsg = sh(
      script: "git log -1 --pretty=format:'%s' 2>/dev/null || echo 'No commit message'",
      returnStdout: true
    ).trim()

    if (commitMsg.length() > maxLength) {
      commitMsg = commitMsg.substring(0, maxLength - 3) + "..."
    }

    return commitMsg
  } catch (Exception e) {
    echo "[WARN] sharedUtils: Could not get commit message: ${e.getMessage()}"
    return "No commit message available"
  }
}

/**
 * Get git user from latest commit
 */
def getUserFromGit() {
  try {
    def gitUser = sh(
      script: "git log -1 --pretty=format:'%an' 2>/dev/null || echo 'unknown'",
      returnStdout: true
    ).trim()

    if (gitUser.contains('@')) {
      gitUser = gitUser.split('@')[0]
    }

    return gitUser
  } catch (Exception e) {
    echo "[WARN] sharedUtils: Could not determine git user: ${e.getMessage()}"
    return 'unknown'
  }
}

/**
 * Get repository owner from GIT_URL
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

/**
 * Get repository name from GIT_URL
 */
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

/**
 * Escape markdown special characters for Telegram
 */
def escapeMarkdown(String text) {
  return text.replaceAll(/[_*\[\]()~`>#+=|{}.!-]/) { "\\$it" }
}

/**
 * Get blocked reason message for display
 */
def getBlockedReasonMessage(permissionCheck) {
  switch (permissionCheck.reason) {
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

// ============================================================================
// GitHub API Functions (integrated from githubApi.groovy)
// ============================================================================

/**
 * Check user permissions via GitHub API
 */
def checkUserPermissions(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def username = params.username ?: getUserFromGit()
  def token = env.GITHUB_TOKEN

  if (!token) {
    echo "[WARN] sharedUtils: GITHUB_TOKEN not found, skipping permission check"
    return [hasAdminAccess: true, reason: 'NO_TOKEN']
  }

  try {
    echo "[INFO] sharedUtils: Checking permissions for user '${username}' on ${repoOwner}/${repoName}"

    def apiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/collaborators/${username}/permission"
    def response = sh(
      script: """
      set +x
      curl -s -H "Authorization: token ${token}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "${apiUrl}"
      """,
      returnStdout: true
    ).trim()

    def jsonResponse = readJSON text: response

    if (jsonResponse.permission) {
      def permission = jsonResponse.permission
      def hasAdmin = permission == 'admin'

      echo "[INFO] sharedUtils: User '${username}' has '${permission}' permission"

      return [
        hasAdminAccess: hasAdmin,
        permission: permission,
        username: username,
        reason: hasAdmin ? 'ADMIN_ACCESS' : 'INSUFFICIENT_PERMISSION'
      ]
    } else {
      echo "[WARN] sharedUtils: Could not determine user permissions"
      return [hasAdminAccess: true, reason: 'API_ERROR']
    }

  } catch (Exception e) {
    echo "[ERROR] sharedUtils: Failed to check permissions: ${e.getMessage()}"
    return [hasAdminAccess: true, reason: 'API_EXCEPTION']
  }
}

/**
 * Get branch protection rules from GitHub Repository Variables
 */
def getBranchProtectionRules(Map params) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def token = env.GITHUB_TOKEN

  if (!token) {
    echo "[WARN] sharedUtils: GITHUB_TOKEN not found, skipping branch protection check"
    return [isProtected: false, reason: 'NO_TOKEN']
  }

  try {
    echo "[INFO] sharedUtils: Fetching branch protection variables for ${repoOwner}/${repoName}"

    def adminApiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/actions/variables/BRANCH_PROTECT_ADMIN"
    def maintainApiUrl = "https://api.github.com/repos/${repoOwner}/${repoName}/actions/variables/BRANCH_PROTECT_MAINTAIN"

    def adminResponse = sh(
      script: """
      set +x
      curl -s -H "Authorization: token ${token}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "${adminApiUrl}"
      """,
      returnStdout: true
    ).trim()

    def maintainResponse = sh(
      script: """
      set +x
      curl -s -H "Authorization: token ${token}" \\
           -H "Accept: application/vnd.github.v3+json" \\
           "${maintainApiUrl}"
      """,
      returnStdout: true
    ).trim()

    def adminJson = readJSON text: adminResponse
    def maintainJson = readJSON text: maintainResponse

    def adminBranches = []
    def maintainBranches = []

    if (adminJson.name == 'BRANCH_PROTECT_ADMIN' && adminJson.value) {
      adminBranches = adminJson.value.split(',').collect { it.trim() }
      echo "[INFO] sharedUtils: Found BRANCH_PROTECT_ADMIN: ${adminJson.value}"
    }

    if (maintainJson.name == 'BRANCH_PROTECT_MAINTAIN' && maintainJson.value) {
      maintainBranches = maintainJson.value.split(',').collect { it.trim() }
      echo "[INFO] sharedUtils: Found BRANCH_PROTECT_MAINTAIN: ${maintainJson.value}"
    }

    def isAdminProtected = adminBranches.contains(branchName)
    def isMaintainProtected = maintainBranches.contains(branchName)

    if (isAdminProtected) {
      echo "[INFO] sharedUtils: Branch '${branchName}' requires ADMIN permission"
      return [
        isProtected: true,
        branchName: branchName,
        protectionLevel: 'ADMIN',
        adminBranches: adminJson.value ?: '',
        maintainBranches: maintainJson.value ?: '',
        reason: 'ADMIN_REQUIRED'
      ]
    } else if (isMaintainProtected) {
      echo "[INFO] sharedUtils: Branch '${branchName}' requires MAINTAIN or ADMIN permission"
      return [
        isProtected: true,
        branchName: branchName,
        protectionLevel: 'MAINTAIN',
        adminBranches: adminJson.value ?: '',
        maintainBranches: maintainJson.value ?: '',
        reason: 'MAINTAIN_REQUIRED'
      ]
    } else {
      def hasNoVars = (!adminJson.name && !maintainJson.name) ||
                      (adminJson.message?.contains('Not Found') && maintainJson.message?.contains('Not Found'))

      if (hasNoVars) {
        echo "[INFO] sharedUtils: No branch protection variables found"
        return [
          isProtected: false,
          branchName: branchName,
          reason: 'NO_PROTECTION_VARS'
        ]
      } else {
        echo "[INFO] sharedUtils: Branch '${branchName}' is not protected"
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
    echo "[ERROR] sharedUtils: Failed to fetch repository variables: ${e.getMessage()}"
    return [
      isProtected: false,
      branchName: branchName,
      reason: 'API_EXCEPTION'
    ]
  }
}

/**
 * Validate deployment permissions
 */
def validateDeployPermissions(Map params = [:]) {
  def repoOwner = params.repoOwner ?: getRepoOwner()
  def repoName = params.repoName ?: getRepoName()
  def branchName = params.branchName ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME
  def username = params.username ?: getUserFromGit()

  echo "[INFO] sharedUtils: Validating deployment permissions..."
  echo "[INFO] sharedUtils: Repository: ${repoOwner}/${repoName}"
  echo "[INFO] sharedUtils: Branch: ${branchName}"
  echo "[INFO] sharedUtils: User: ${username}"

  def branchProtection = getBranchProtectionRules([
    repoOwner: repoOwner,
    repoName: repoName,
    branchName: branchName
  ])

  if (!branchProtection.isProtected) {
    def reason = branchProtection.reason == 'NO_PROTECTION_VARS' ? 'NO_PROTECTION_VARS' : 'BRANCH_NOT_PROTECTED'
    echo "[INFO] sharedUtils: Branch not protected, allowing deployment"
    return [
      canDeploy: true,
      reason: reason,
      branchProtection: branchProtection,
      username: username,
      branchName: branchName,
      repository: "${repoOwner}/${repoName}"
    ]
  }

  def userPermissions = checkUserPermissions([
    repoOwner: repoOwner,
    repoName: repoName,
    username: username
  ])

  def canDeploy = false
  def deployReason = ''

  if (branchProtection.protectionLevel == 'ADMIN') {
    canDeploy = userPermissions.permission == 'admin'
    deployReason = canDeploy ? 'ADMIN_ACCESS_GRANTED' : 'ADMIN_REQUIRED_BUT_NOT_ADMIN'
  } else if (branchProtection.protectionLevel == 'MAINTAIN') {
    canDeploy = userPermissions.permission in ['admin', 'maintain']
    deployReason = canDeploy ? 'MAINTAIN_OR_ADMIN_ACCESS_GRANTED' : 'MAINTAIN_OR_ADMIN_REQUIRED'
  }

  if (canDeploy) {
    echo "[SUCCESS] sharedUtils: User '${username}' has '${userPermissions.permission}' permission, allowing deployment"
  } else {
    echo "[BLOCKED] sharedUtils: User '${username}' has '${userPermissions.permission}' permission but ${branchProtection.protectionLevel} required"
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

// This is required for vars/ scripts
return this
