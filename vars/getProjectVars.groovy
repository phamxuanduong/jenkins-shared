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

  // Calculate branch name
  def branchName = config.repoBranch ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'main'

  // Calculate final repo name for consistent use
  def finalRepoName = config.repoName ?: repoName ?: 'unknown-repo'

  // Set defaults and allow overrides
  def vars = [
    REPO_NAME: finalRepoName,
    REPO_BRANCH: branchName,
    NAMESPACE: config.namespace ?: finalRepoName,
    DEPLOYMENT: config.deployment ?: "${finalRepoName}-${branchName}",
    APP_NAME: config.appName ?: finalRepoName,
    REGISTRY: config.registry ?: env.DOCKER_REGISTRY ?: '172.16.3.0/mtw',
    COMMIT_HASH: config.commitHash ?: env.GIT_COMMIT?.take(7) ?: 'latest'
  ]

  // Log all variables for debugging
  echo """
[INFO] Project Variables:
  REPO_NAME:    ${vars.REPO_NAME}
  REPO_BRANCH:  ${vars.REPO_BRANCH}
  NAMESPACE:    ${vars.NAMESPACE}
  DEPLOYMENT:   ${vars.DEPLOYMENT}
  APP_NAME:     ${vars.APP_NAME}
  REGISTRY:     ${vars.REGISTRY}
  COMMIT_HASH:  ${vars.COMMIT_HASH}
"""

  return vars
}