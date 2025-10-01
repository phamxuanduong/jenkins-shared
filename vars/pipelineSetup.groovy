#!/usr/bin/env groovy

/**
 * pipelineSetup - Complete pipeline initialization with permission validation
 *
 * This function handles:
 * - Project variables detection from Git
 * - GitHub permission validation (user & branch)
 * - Environment variables setup for subsequent stages
 * - Automatic Telegram notifications for blocked deployments
 * - Pipeline termination if permissions are insufficient
 *
 * @param config Map of optional parameters:
 *   - All parameters from sharedUtils.getProjectVars() are supported
 *   - enablePermissionCheck: Override permission checking (default: auto-detect)
 *   - customMessage: Custom blocked deployment message
 *   - failOnBlock: Whether to fail pipeline on permission block (default: true)
 *
 * @return Map containing all project variables and permission status
 */
def call(Map config = [:]) {
  echo "[INFO] pipelineSetup: Starting pipeline initialization..."

  try {
    // Get project variables with integrated permission checking
    def projectVars = sharedUtils.getProjectVars(config)

    // Set environment variables for subsequent stages to use
    env.PIPELINE_SETUP_COMPLETE = 'true'
    env.REPO_NAME = projectVars.REPO_NAME
    env.REPO_BRANCH = projectVars.REPO_BRANCH
    env.SANITIZED_BRANCH = projectVars.SANITIZED_BRANCH
    env.NAMESPACE = projectVars.NAMESPACE
    env.DEPLOYMENT = projectVars.DEPLOYMENT
    env.APP_NAME = projectVars.APP_NAME
    env.REGISTRY = projectVars.REGISTRY
    env.COMMIT_HASH = projectVars.COMMIT_HASH
    env.GIT_USER = projectVars.GIT_USER
    env.CAN_DEPLOY = projectVars.CAN_DEPLOY.toString()
    env.PERMISSION_REASON = projectVars.PERMISSION_REASON

    // Store serialized project vars for functions that need the full object
    env.PROJECT_VARS_JSON = groovy.json.JsonOutput.toJson(projectVars)

    // Get domain from ingress (if exists)
    def domain = k8sGetIngress(
      deployment: projectVars.DEPLOYMENT,
      namespace: projectVars.NAMESPACE
    )
    def domainLine = domain ? "🌐 Domain: ${domain}\n" : ""

    echo """
[SUCCESS] pipelineSetup: Pipeline initialization completed successfully!

📦 Project: ${projectVars.REPO_NAME}
🌿 Branch: ${projectVars.REPO_BRANCH}
👤 User: ${projectVars.GIT_USER}
✅ Permission: ${projectVars.CAN_DEPLOY ? 'GRANTED' : 'DENIED'} (${projectVars.PERMISSION_REASON})
🏷️ Tag: ${projectVars.COMMIT_HASH}
📦 Registry: ${projectVars.REGISTRY}
🚀 Deployment: ${projectVars.DEPLOYMENT}
🏛️ Namespace: ${projectVars.NAMESPACE}
${domainLine}"""

    // If we reach here, permissions are valid and pipeline can continue
    return projectVars

  } catch (Exception e) {
    // Handle any setup failures
    echo "[ERROR] pipelineSetup: Pipeline initialization failed: ${e.getMessage()}"

    // Send failure notification
    def errorMessage = """❌ *Pipeline Setup Failed*

📦 *Project:* `${env.JOB_NAME ?: 'Unknown'}`
🌿 *Branch:* `${env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'Unknown'}`
👤 *User:* `${sharedUtils.getUserFromGit()}`

⚠️ *Error:* ${e.getMessage()}

🔗 *Build:* [#${env.BUILD_NUMBER}](${env.BUILD_URL})"""

    try {
      telegramNotify([
        message: errorMessage,
        failOnError: false
      ])
    } catch (Exception notifyError) {
      echo "[WARN] pipelineSetup: Failed to send error notification: ${notifyError.getMessage()}"
    }

    // Re-throw the exception to fail the pipeline
    throw e
  }
}
