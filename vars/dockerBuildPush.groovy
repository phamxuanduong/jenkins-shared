def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String image = args.image ?: "${vars.REGISTRY}/${vars.APP_NAME}"
  String tag = args.tag ?: vars.COMMIT_HASH
  String dockerfile = args.dockerfile ?: 'Dockerfile'
  String context = args.context ?: '.'

  sh(
    label: "dockerBuildPush: ${image}:${tag}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] Building and pushing Docker image ${image}:${tag}"
      echo "[INFO] Context: ${context}"
      echo "[INFO] Dockerfile: ${dockerfile}"

      # Build image
      echo "[INFO] Building image..."
      docker build -t ${image}:${tag} -f ${dockerfile} ${context}
      echo "[OK] Built ${image}:${tag}"

      # Push image
      echo "[INFO] Pushing image..."
      docker push ${image}:${tag}
      echo "[OK] Pushed ${image}:${tag}"

      echo "[SUCCESS] Build and push completed for ${image}:${tag}"
    """
  )
}