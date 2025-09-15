def call(Map args = [:]) {
  if (!args.image) error 'dockerBuild: thiếu "image"'
  if (!args.tag) error 'dockerBuild: thiếu "tag"'

  String image = args.image
  String tag = args.tag
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