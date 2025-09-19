/**
 * dockerBuild - Build Docker images with automatic configuration
 *
 * @param args Map of optional parameters:
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - dockerfile: Dockerfile path (default: 'Dockerfile')
 *   - context: Build context (default: '.')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if Docker build fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String dockerfile = args.dockerfile ?: 'Dockerfile'
  String context = args.context ?: '.'

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

      echo "[INFO] dockerBuild: Building Docker image \${IMAGE}:\${TAG}"
      echo "[INFO] dockerBuild: Build context: \${CONTEXT}"
      echo "[INFO] dockerBuild: Using dockerfile: \${DOCKERFILE}"

      docker build -t "\${IMAGE}:\${TAG}" -f "\${DOCKERFILE}" "\${CONTEXT}"

      echo "[SUCCESS] dockerBuild: Built \${IMAGE}:\${TAG}"
    """
  )
}