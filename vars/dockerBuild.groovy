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

      echo "[INFO] Building Docker image ${image}:${tag}"
      echo "[INFO] Context: ${context}"
      echo "[INFO] Dockerfile: ${dockerfile}"

      docker build -t ${image}:${tag} -f ${dockerfile} ${context}

      echo "[OK] Built ${image}:${tag}"
    """
  )
}