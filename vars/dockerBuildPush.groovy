def call(Map args = [:]) {
  if (!args.image) error 'dockerBuildPush: thiếu "image"'
  if (!args.tag) error 'dockerBuildPush: thiếu "tag"'

  String image = args.image
  String tag = args.tag
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