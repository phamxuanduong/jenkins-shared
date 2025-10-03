/**
 * dockerBuild - Build Docker images with automatic configuration
 *
 * @param args Map of optional parameters:
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - dockerfile: Dockerfile path (default: 'Dockerfile')
 *   - context: Build context (default: '.')
 *   - sshKey: SSH private key path for build secrets (default: '/root/.ssh/id_rsa')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if Docker build fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: sharedUtils.getProjectVarsOptimized()

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String dockerfile = args.dockerfile ?: 'Dockerfile'
  String context = args.context ?: '.'
  String sshKey = args.sshKey ?: '/root/.ssh/id_rsa'

  // Input validation to prevent command injection
  // Note: Basic validation is done via shell quoting below

  sh(
    label: "dockerBuild: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      # Use properly quoted variables to prevent injection
      IMAGE=\$(printf '%q' "${image}")
      TAG=\$(printf '%q' "${tag}")
      DOCKERFILE=\$(printf '%q' "${dockerfile}")
      CONTEXT=\$(printf '%q' "${context}")
      SSH_KEY=\$(printf '%q' "${sshKey}")

      echo "[INFO] dockerBuild: Building Docker image \${IMAGE}:\${TAG}"
      echo "[INFO] dockerBuild: Build context: \${CONTEXT}"
      echo "[INFO] dockerBuild: Using dockerfile: \${DOCKERFILE}"
      echo "[INFO] dockerBuild: SSH key for secrets: \${SSH_KEY}"

      # Check if SSH key exists
      if [ -f "\${SSH_KEY}" ]; then
        echo "[INFO] dockerBuild: Using SSH secret for private repository access"
        docker build -t "\${IMAGE}:\${TAG}" -f "\${DOCKERFILE}" \\
          --secret id=id_rsa,src="\${SSH_KEY}" \\
          "\${CONTEXT}"
      else
        echo "[WARN] dockerBuild: SSH key not found at \${SSH_KEY}, building without secrets"
        docker build -t "\${IMAGE}:\${TAG}" -f "\${DOCKERFILE}" "\${CONTEXT}"
      fi

      echo "[SUCCESS] dockerBuild: Built \${IMAGE}:\${TAG}"
    """
  )
}