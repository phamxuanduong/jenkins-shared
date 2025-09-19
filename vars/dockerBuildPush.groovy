/**
 * dockerBuildPush - Build and push Docker images in one operation
 *
 * @param args Map of optional parameters:
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - dockerfile: Dockerfile path (default: 'Dockerfile')
 *   - context: Build context (default: '.')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if Docker build or push fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided (pass vars to avoid duplicate calls)
  def vars = args.vars ?: getProjectVars()

  // Check deployment permissions (only if permission check is enabled)
  if (vars.CAN_DEPLOY == false) {
    echo "[BLOCKED] dockerBuildPush: Deployment blocked - ${vars.PERMISSION_REASON}"
    echo "[INFO] dockerBuildPush: Skipping Docker build and push"
    return
  }

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String dockerfile = args.dockerfile ?: 'Dockerfile'
  String context = args.context ?: '.'

  // Input validation to prevent command injection
  validateDockerImageName(image)
  validateDockerTag(tag)
  validateFilePath(dockerfile)
  validateContextPath(context)

  sh(
    label: "dockerBuildPush: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      # Use properly quoted variables to prevent injection
      IMAGE=\$(printf '%q' "${image}")
      TAG=\$(printf '%q' "${tag}")
      DOCKERFILE=\$(printf '%q' "${dockerfile}")
      CONTEXT=\$(printf '%q' "${context}")

      echo "[INFO] dockerBuildPush: Building and pushing Docker image \${IMAGE}:\${TAG}"
      echo "[INFO] dockerBuildPush: Build context: \${CONTEXT}"
      echo "[INFO] dockerBuildPush: Using dockerfile: \${DOCKERFILE}"

      # Build image
      echo "[INFO] dockerBuildPush: Building image..."
      docker build -t "\${IMAGE}:\${TAG}" -f "\${DOCKERFILE}" "\${CONTEXT}"
      echo "[SUCCESS] dockerBuildPush: Built \${IMAGE}:\${TAG}"

      # Push image
      echo "[INFO] dockerBuildPush: Pushing image..."
      docker push "\${IMAGE}:\${TAG}"
      echo "[SUCCESS] dockerBuildPush: Pushed \${IMAGE}:\${TAG}"

      echo "[SUCCESS] dockerBuildPush: Build and push completed for \${IMAGE}:\${TAG}"
    """
  )
}