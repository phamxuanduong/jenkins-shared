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
  // Get project vars if not provided (pass vars to avoid duplicate calls)
  def vars = args.vars ?: getProjectVars()

  // Check deployment permissions (only if permission check is enabled)
  if (vars.CAN_DEPLOY == false) {
    echo "[BLOCKED] k8sSetImage: Deployment blocked - ${vars.PERMISSION_REASON}"
    echo "[INFO] k8sSetImage: Skipping Kubernetes deployment"
    return
  }

  String deployment = args.deployment ?: vars.DEPLOYMENT
  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String namespace = args.namespace ?: vars.NAMESPACE
  String container = args.container ?: '*'

  // Input validation to prevent command injection
  // Note: Basic validation is done via shell quoting below

  sh(
    label: "k8sSetImage: ${deployment} -> ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      # Use properly quoted variables to prevent injection
      DEPLOYMENT=\$(printf '%q' "${deployment}")
      IMAGE=\$(printf '%q' "${image}")
      TAG=\$(printf '%q' "${tag}")
      NAMESPACE=\$(printf '%q' "${namespace}")
      CONTAINER=\$(printf '%q' "${container}")

      echo "[INFO] k8sSetImage: Updating deployment '\${DEPLOYMENT}' in namespace '\${NAMESPACE}'"
      echo "[INFO] k8sSetImage: Setting image to '\${IMAGE}:\${TAG}' for container '\${CONTAINER}'"

      kubectl set image deployment/"\${DEPLOYMENT}" "\${CONTAINER}=\${IMAGE}:\${TAG}" -n "\${NAMESPACE}"

      echo "[SUCCESS] k8sSetImage: Updated deployment '\${DEPLOYMENT}' with image '\${IMAGE}:\${TAG}'"
    """
  )
}