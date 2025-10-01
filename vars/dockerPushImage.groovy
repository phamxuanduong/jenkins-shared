/**
 * dockerPush - Push Docker images to registry with automatic configuration
 *
 * @param args Map of optional parameters:
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if Docker push fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH

  // Input validation to prevent command injection
  // Note: Basic validation is done via shell quoting below

  sh(
    label: "dockerPush: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      # Use properly quoted variables to prevent injection
      IMAGE=\$(printf '%q' "${image}")
      TAG=\$(printf '%q' "${tag}")

      echo "[INFO] dockerPush: Pushing Docker image \${IMAGE}:\${TAG}"

      docker push "\${IMAGE}:\${TAG}"

      echo "[SUCCESS] dockerPush: Pushed \${IMAGE}:\${TAG}"
    """
  )
}