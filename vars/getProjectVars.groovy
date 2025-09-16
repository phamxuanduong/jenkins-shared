def call(Map config = [:]) {
  // Extract repository info from GIT_URL
  def gitUrl = env.GIT_URL ?: ''
  def repoName = ''

  if (gitUrl) {
    // Extract repo name from git URL (supports both SSH and HTTPS)
    def urlPattern = /.*[\/:]([^\/]+)\/([^\/]+?)(?:\.git)?$/
    def matcher = gitUrl =~ urlPattern
    if (matcher) {
      repoName = matcher[0][2]
    }
  }

  // Calculate branch name and sanitize for K8s naming
  def branchName = config.repoBranch ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'main'
  def sanitizedBranch = branchName.replaceAll('/', '-').replaceAll('[^a-zA-Z0-9-]', '-').toLowerCase()

  // Calculate final repo name for consistent use
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
      // Default to beta for unknown branches
      registry = env.REGISTRY_BETA ?: '172.16.3.0/mtw'
    }
  }

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

  // Log all variables for debugging
  echo """
[INFO] Project Variables:
  REPO_NAME:     ${vars.REPO_NAME}
  REPO_BRANCH:   ${vars.REPO_BRANCH}
  SANITIZED_BRANCH: ${sanitizedBranch}
  NAMESPACE:     ${vars.NAMESPACE}
  DEPLOYMENT:    ${vars.DEPLOYMENT}
  APP_NAME:      ${vars.APP_NAME}
  REGISTRY:      ${vars.REGISTRY} (auto-selected based on branch)
  COMMIT_HASH:   ${vars.COMMIT_HASH}
"""

  return vars
}