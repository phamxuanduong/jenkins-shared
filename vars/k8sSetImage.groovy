def call(Map args = [:]) {
  if (!args.deployment) error 'k8sSetImage: thiếu "deployment"'
  if (!args.image) error 'k8sSetImage: thiếu "image"'
  if (!args.tag) error 'k8sSetImage: thiếu "tag"'
  if (!args.namespace) error 'k8sSetImage: thiếu "namespace"'

  String deployment = args.deployment
  String image = args.image
  String tag = args.tag
  String namespace = args.namespace
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