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

  // Check deployment permissions
  if (!vars.CAN_DEPLOY) {
    echo "[BLOCKED] swarmSetImage: Deployment blocked due to insufficient permissions"
    echo "[INFO] swarmSetImage: User '${vars.GIT_USER}' cannot deploy to protected branch '${vars.REPO_BRANCH}'"
    return
  }

  String service = args.service ?: "${vars.REPO_NAME}_${vars.SANITIZED_BRANCH}"
  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String context = args.context ?: 'docker-swarm'

  sh(
    label: "swarmSetImage: ${service} -> ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] swarmSetImage: Updating Docker Swarm service '${service}'"
      echo "[INFO] swarmSetImage: Setting image to '${image}:${tag}'"
      echo "[INFO] swarmSetImage: Using Docker context '${context}'"

      # Check if Docker context exists
      if ! docker context ls --format '{{.Name}}' | grep -q '^${context}\$'; then
        echo "[ERROR] swarmSetImage: Docker context '${context}' not found"
        echo "[INFO] swarmSetImage: Available contexts:"
        docker context ls
        exit 1
      fi

      # Check if service exists
      if ! docker --context ${context} service ls --format '{{.Name}}' | grep -q '^${service}\$'; then
        echo "[ERROR] swarmSetImage: Service '${service}' not found in Docker Swarm"
        echo "[INFO] swarmSetImage: Available services:"
        docker --context ${context} service ls
        exit 1
      fi

      # Update service with new image (with registry auth for private registries)
      docker --context ${context} service update --with-registry-auth --image ${image}:${tag} ${service}

      echo "[SUCCESS] swarmSetImage: Updated service '${service}' with image '${image}:${tag}'"

      # Show service status
      echo "[INFO] swarmSetImage: Service status:"
      docker --context ${context} service ps ${service} --no-trunc
    """
  )
}