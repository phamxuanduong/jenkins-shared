def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH

  sh(
    label: "dockerPush: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] Pushing Docker image ${image}:${tag}"

      docker push ${image}:${tag}

      echo "[OK] Pushed ${image}:${tag}"
    """
  )
}