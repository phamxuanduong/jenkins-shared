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

  sh(
    label: "dockerBuild: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] dockerBuild: Building Docker image ${image}:${tag}"
      echo "[INFO] dockerBuild: Build context: ${context}"
      echo "[INFO] dockerBuild: Using dockerfile: ${dockerfile}"

      docker build -t ${image}:${tag} -f ${dockerfile} ${context}

      echo "[SUCCESS] dockerBuild: Built ${image}:${tag}"
    """
  )
}