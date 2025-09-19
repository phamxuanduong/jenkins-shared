/**
 * swarmSetImage - Update Docker Swarm service with new container image
 *
 * @param args Map of optional parameters:
 *   - service: Service name (default: "{repoName}_{sanitizedBranch}" from getProjectVars)
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - context: Docker context name (default: 'docker-swarm')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if docker service update command fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  // Check deployment permissions (only if permission check is enabled)
  if (vars.CAN_DEPLOY == false) {
    echo "[BLOCKED] swarmSetImage: Deployment blocked due to insufficient permissions"
    echo "[INFO] swarmSetImage: User '${vars.GIT_USER}' cannot deploy to protected branch '${vars.REPO_BRANCH}'"
    return
  }

  String service = args.service ?: "${vars.REPO_NAME}_${vars.SANITIZED_BRANCH}"
  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String context = args.context ?: 'docker-swarm'

  // Input validation to prevent command injection
  validateDockerImageName(image)
  validateDockerTag(tag)

  sh(
    label: "swarmSetImage: ${service} -> ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      # Use properly quoted variables to prevent injection
      SERVICE=\$(printf '%q' "${service}")
      IMAGE=\$(printf '%q' "${image}")
      TAG=\$(printf '%q' "${tag}")
      CONTEXT=\$(printf '%q' "${context}")

      echo "[INFO] swarmSetImage: Updating Docker Swarm service '\${SERVICE}'"
      echo "[INFO] swarmSetImage: Setting image to '\${IMAGE}:\${TAG}'"
      echo "[INFO] swarmSetImage: Using Docker context '\${CONTEXT}'"

      # Check if Docker context exists
      if ! docker context ls --format '{{.Name}}' | grep -q "^\${CONTEXT}\$"; then
        echo "[ERROR] swarmSetImage: Docker context '\${CONTEXT}' not found"
        echo "[INFO] swarmSetImage: Available contexts:"
        docker context ls
        exit 1
      fi

      # Check if service exists
      if ! docker --context "\${CONTEXT}" service ls --format '{{.Name}}' | grep -q "^\${SERVICE}\$"; then
        echo "[ERROR] swarmSetImage: Service '\${SERVICE}' not found in Docker Swarm"
        echo "[INFO] swarmSetImage: Available services:"
        docker --context "\${CONTEXT}" service ls
        exit 1
      fi

      # Update service with new image (with registry auth for private registries)
      docker --context "\${CONTEXT}" service update --with-registry-auth --image "\${IMAGE}:\${TAG}" "\${SERVICE}"

      echo "[SUCCESS] swarmSetImage: Updated service '\${SERVICE}' with image '\${IMAGE}:\${TAG}'"

      # Show service status
      echo "[INFO] swarmSetImage: Service status:"
      docker --context "\${CONTEXT}" service ps "\${SERVICE}" --no-trunc
    """
  )
}