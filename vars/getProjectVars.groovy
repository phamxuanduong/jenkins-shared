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

  // Set defaults and allow overrides
  def vars = [
    REPO_NAME: config.repoName ?: repoName ?: 'unknown-repo',
    REPO_BRANCH: config.repoBranch ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: env.BRANCH_NAME ?: 'main',
    NAMESPACE: config.namespace ?: repoName ?: 'default',
    DEPLOYMENT: config.deployment ?: "${repoName}-deployment",
    APP_NAME: config.appName ?: repoName ?: 'app',
    REGISTRY: config.registry ?: '172.16.3.0/mtw',
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