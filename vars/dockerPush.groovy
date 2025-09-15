def call(Map args = [:]) {
  if (!args.image) error 'dockerPush: thiếu "image"'
  if (!args.tag) error 'dockerPush: thiếu "tag"'

  String image = args.image
  String tag = args.tag

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