/**
 * utilityHelpers - Common utility functions for Jenkins shared library
 *
 * Centralized helper functions to avoid code duplication across multiple files
 */

/**
 * Parse repository owner and name from Git URL
 * @param gitUrl Git repository URL (SSH or HTTPS)
 * @return Map with 'owner' and 'name' keys
 */
def parseGitUrl(String gitUrl) {
  if (!gitUrl) {
    return [owner: 'unknown', name: 'unknown']
  }

  // Extract repo owner and name from git URL (supports both SSH and HTTPS)
  def urlPattern = /.*[\\/:]([^\\/]+)\\/([^\\/]+?)(?:\\.git)?$/
  def matcher = gitUrl =~ urlPattern
  if (matcher) {
    return [
      owner: matcher[0][1],
      name: matcher[0][2]
    ]
  }

  return [owner: 'unknown', name: 'unknown']
}

/**
 * Get repository owner from environment Git URL
 */
def getRepoOwner() {
  def gitUrl = env.GIT_URL ?: ''
  return parseGitUrl(gitUrl).owner
}

/**
 * Get repository name from environment Git URL
 */
def getRepoName() {
  def gitUrl = env.GIT_URL ?: ''
  return parseGitUrl(gitUrl).name
}

/**
 * Determine environment name based on branch name patterns
 * @param branchName The branch name to analyze
 * @return String environment name ('dev', 'staging', 'prod', 'unknown')
 */
def getEnvironmentFromBranch(String branchName) {
  if (!branchName) {
    return 'unknown'
  }

  def lowerBranch = branchName.toLowerCase()

  // Development environments
  if (lowerBranch.contains('dev') || lowerBranch.contains('beta') || lowerBranch.contains('feature')) {
    return 'dev'
  }

  // Staging environment
  if (lowerBranch.contains('staging') || lowerBranch.contains('stage')) {
    return 'staging'
  }

  // Production environments
  if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
      lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    return 'prod'
  }

  // Default to dev for unknown patterns
  return 'dev'
}

/**
 * Get registry URL based on branch/environment
 * @param branchName The branch name
 * @return String registry URL
 */
def getRegistryForBranch(String branchName) {
  def environment = getEnvironmentFromBranch(branchName)

  switch (environment) {
    case 'dev':
      return env.REGISTRY_BETA ?: '172.16.3.0/mtw'
    case 'staging':
      return env.REGISTRY_STAGING ?: '172.16.3.0/mtw'
    case 'prod':
      return env.REGISTRY_PROD ?: '172.16.3.0/mtw'
    default:
      return env.REGISTRY_BETA ?: '172.16.3.0/mtw'
  }
}

/**
 * Sanitize branch name for Kubernetes resource naming
 * @param branchName Raw branch name
 * @return String sanitized name following K8s naming conventions
 */
def sanitizeBranchName(String branchName) {
  if (!branchName) {
    return 'unknown'
  }

  return branchName
    .replaceAll('^origin/', '')  // Remove origin/ prefix
    .replaceAll('/', '-')        // Replace slashes with hyphens
    .replaceAll('[^a-zA-Z0-9-]', '-')  // Replace invalid chars with hyphens
    .toLowerCase()               // Convert to lowercase for K8s compatibility
}

/**
 * Get user from git commit (for GitHub API calls)
 */
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
    echo "[WARN] utilityHelpers: Could not determine git user: ${e.getMessage()}"
    return 'unknown'
  }
}

/**
 * Format error message with consistent styling
 * @param component Component name (e.g., 'dockerBuild', 'k8sSetImage')
 * @param action Action being performed (e.g., 'Building image', 'Updating deployment')
 * @param details Additional error details
 * @return String formatted error message
 */
def formatErrorMessage(String component, String action, String details) {
  return "[ERROR] ${component}: ${action} failed - ${details}"
}

/**
 * Format success message with consistent styling
 * @param component Component name
 * @param action Action completed
 * @param details Additional success details
 * @return String formatted success message
 */
def formatSuccessMessage(String component, String action, String details) {
  return "[SUCCESS] ${component}: ${action} completed - ${details}"
}

/**
 * Format info message with consistent styling
 * @param component Component name
 * @param message Info message
 * @return String formatted info message
 */
def formatInfoMessage(String component, String message) {
  return "[INFO] ${component}: ${message}"
}

// Make functions available at global scope for Jenkins shared library
this.parseGitUrl = this.&parseGitUrl
this.getRepoOwner = this.&getRepoOwner
this.getRepoName = this.&getRepoName
this.getEnvironmentFromBranch = this.&getEnvironmentFromBranch
this.getRegistryForBranch = this.&getRegistryForBranch
this.sanitizeBranchName = this.&sanitizeBranchName
this.getUserFromCommit = this.&getUserFromCommit
this.formatErrorMessage = this.&formatErrorMessage
this.formatSuccessMessage = this.&formatSuccessMessage
this.formatInfoMessage = this.&formatInfoMessage

// Main call method (required for Jenkins shared library)
def call(Map args = [:]) {
  echo "[INFO] utilityHelpers: Common utility functions loaded"
}