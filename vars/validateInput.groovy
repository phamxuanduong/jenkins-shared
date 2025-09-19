/**
 * validateInput - Input validation utility functions to prevent injection attacks
 *
 * @param value String: The value to validate
 * @return void
 * @throws Exception if validation fails
 */

/**
 * Validate Docker image name format
 */
def validateDockerImageName(String imageName) {
  if (!imageName) {
    throw new Exception("Docker image name cannot be empty")
  }

  // Allow alphanumeric, dots, hyphens, underscores, slashes, and colons for registry URLs
  if (!imageName.matches(/^[a-zA-Z0-9\.\-_\/\:]+$/)) {
    throw new Exception("Invalid Docker image name: ${imageName}. Only alphanumeric, dots, hyphens, underscores, slashes, and colons are allowed")
  }

  // Prevent dangerous characters that could be used for injection
  def dangerousChars = ['$(', '`', ';', '|', '&&', '||', '>', '<', '&']
  for (char in dangerousChars) {
    if (imageName.contains(char)) {
      throw new Exception("Docker image name contains dangerous character: ${char}")
    }
  }
}

/**
 * Validate Docker tag format
 */
def validateDockerTag(String tag) {
  if (!tag) {
    throw new Exception("Docker tag cannot be empty")
  }

  // Allow alphanumeric, dots, hyphens, underscores
  if (!tag.matches(/^[a-zA-Z0-9\.\-_]+$/)) {
    throw new Exception("Invalid Docker tag: ${tag}. Only alphanumeric, dots, hyphens, and underscores are allowed")
  }

  if (tag.length() > 128) {
    throw new Exception("Docker tag too long: ${tag.length()} characters. Maximum is 128")
  }
}

/**
 * Validate file path (for Dockerfile, context, etc.)
 */
def validateFilePath(String filePath) {
  if (!filePath) {
    throw new Exception("File path cannot be empty")
  }

  // Prevent directory traversal and dangerous characters
  if (filePath.contains('..') || filePath.contains('~')) {
    throw new Exception("File path contains dangerous directory traversal: ${filePath}")
  }

  // Allow only safe characters for file paths
  if (!filePath.matches(/^[a-zA-Z0-9\.\-_\/]+$/)) {
    throw new Exception("Invalid file path: ${filePath}. Only alphanumeric, dots, hyphens, underscores, and slashes are allowed")
  }
}

/**
 * Validate context path for Docker build
 */
def validateContextPath(String contextPath) {
  validateFilePath(contextPath)

  // Additional checks for context path
  if (contextPath.startsWith('/')) {
    throw new Exception("Context path should be relative, not absolute: ${contextPath}")
  }
}

/**
 * Validate Kubernetes namespace name
 */
def validateK8sNamespace(String namespace) {
  if (!namespace) {
    throw new Exception("Kubernetes namespace cannot be empty")
  }

  // K8s namespace naming rules
  if (!namespace.matches(/^[a-z0-9\-]+$/)) {
    throw new Exception("Invalid Kubernetes namespace: ${namespace}. Only lowercase alphanumeric and hyphens are allowed")
  }

  if (namespace.startsWith('-') || namespace.endsWith('-')) {
    throw new Exception("Kubernetes namespace cannot start or end with hyphen: ${namespace}")
  }

  if (namespace.length() > 63) {
    throw new Exception("Kubernetes namespace too long: ${namespace.length()} characters. Maximum is 63")
  }
}

/**
 * Validate Kubernetes deployment name
 */
def validateK8sDeploymentName(String deploymentName) {
  if (!deploymentName) {
    throw new Exception("Kubernetes deployment name cannot be empty")
  }

  // K8s resource naming rules
  if (!deploymentName.matches(/^[a-z0-9\-]+$/)) {
    throw new Exception("Invalid Kubernetes deployment name: ${deploymentName}. Only lowercase alphanumeric and hyphens are allowed")
  }

  if (deploymentName.startsWith('-') || deploymentName.endsWith('-')) {
    throw new Exception("Kubernetes deployment name cannot start or end with hyphen: ${deploymentName}")
  }

  if (deploymentName.length() > 253) {
    throw new Exception("Kubernetes deployment name too long: ${deploymentName.length()} characters. Maximum is 253")
  }
}

/**
 * Validate container name
 */
def validateContainerName(String containerName) {
  if (!containerName || containerName == '*') {
    return // '*' is allowed for all containers
  }

  if (!containerName.matches(/^[a-z0-9\-]+$/)) {
    throw new Exception("Invalid container name: ${containerName}. Only lowercase alphanumeric and hyphens are allowed")
  }
}

// Make functions available at global scope
this.validateDockerImageName = this.&validateDockerImageName
this.validateDockerTag = this.&validateDockerTag
this.validateFilePath = this.&validateFilePath
this.validateContextPath = this.&validateContextPath
this.validateK8sNamespace = this.&validateK8sNamespace
this.validateK8sDeploymentName = this.&validateK8sDeploymentName
this.validateContainerName = this.&validateContainerName

// Main call method (not used but required for Jenkins shared library)
def call(Map args = [:]) {
  echo "[INFO] validateInput: Utility functions loaded"
}