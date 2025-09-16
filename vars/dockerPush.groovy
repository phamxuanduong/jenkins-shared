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

  sh(
    label: "dockerPush: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] dockerPush: Pushing Docker image ${image}:${tag}"

      docker push ${image}:${tag}

      echo "[SUCCESS] dockerPush: Pushed ${image}:${tag}"
    """
  )
}