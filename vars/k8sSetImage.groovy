def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String deployment = args.deployment ?: vars.DEPLOYMENT
  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String namespace = args.namespace ?: vars.NAMESPACE
  String container = args.container ?: '*'

  sh(
    label: "k8sSetImage: ${deployment} -> ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] Updating deployment '${deployment}' in namespace '${namespace}'"
      echo "[INFO] Setting image to '${image}:${tag}' for container '${container}'"

      kubectl set image deployment/${deployment} ${container}=${image}:${tag} -n ${namespace}

      echo "[OK] Updated deployment '${deployment}' with image '${image}:${tag}'"
    """
  )
}