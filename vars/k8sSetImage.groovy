/**
 * k8sSetImage - Update Kubernetes deployment with new container image
 *
 * @param args Map of optional parameters:
 *   - deployment: Deployment name (default: from getProjectVars)
 *   - image: Docker image name (default: auto from getProjectVars)
 *   - tag: Image tag (default: commit hash)
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - container: Container name pattern (default: '*' for all containers)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 * @throws Exception if kubectl command fails
 */
def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  // Check deployment permissions
  if (!vars.CAN_DEPLOY) {
    echo "[BLOCKED] k8sSetImage: Deployment blocked due to insufficient permissions"
    echo "[INFO] k8sSetImage: User '${vars.GIT_USER}' cannot deploy to protected branch '${vars.REPO_BRANCH}'"
    return
  }

  String deployment = args.deployment ?: vars.DEPLOYMENT
  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String namespace = args.namespace ?: vars.NAMESPACE
  String container = args.container ?: '*'

  sh(
    label: "k8sSetImage: ${deployment} -> ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] k8sSetImage: Updating deployment '${deployment}' in namespace '${namespace}'"
      echo "[INFO] k8sSetImage: Setting image to '${image}:${tag}' for container '${container}'"

      kubectl set image deployment/${deployment} ${container}=${image}:${tag} -n ${namespace}

      echo "[SUCCESS] k8sSetImage: Updated deployment '${deployment}' with image '${image}:${tag}'"
    """
  )
}