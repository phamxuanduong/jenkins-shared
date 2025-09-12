// vars/k8sGetConfig.groovy
def call(Map args = [:]) {
  if (!args.namespace) error 'k8sGetConfig: thiếu "namespace"'
  if (!args.configmap) error 'k8sGetConfig: thiếu "configmap"'
  if (!(args.items instanceof Map) || args.items.isEmpty()) {
    error 'k8sGetConfig: thiếu "items" (Map: key-trong-ConfigMap -> đường-dẫn-file-đích)'
  }

  String ns = args.namespace
  String cm = args.configmap
  Map items = args.items

  items.each { String key, String destPath ->
    if (!destPath) error "k8sGetConfig: path rỗng cho key '${key}'"

    sh(
      label: "k8sGetConfig: ${cm}/${key} → ${destPath}",
      script: """#!/bin/bash
        set -Eeuo pipefail

        echo "[INFO] Fetching key='${key}' from ConfigMap='${cm}' (ns='${ns}')"
        dir=\$(dirname '${destPath}')
        [ "\$dir" = "." ] || mkdir -p "\$dir"

        tmp=\$(mktemp)
        # Thiếu key -> kubectl trả lỗi (strict)
        kubectl get configmap '${cm}' -n '${ns}' \\
          --allow-missing-template-keys=false \\
          -o go-template='{{index .data "${key}"}}' > "\$tmp"

        if [ ! -s "\$tmp" ]; then
          echo "[ERROR] Key '${key}' tồn tại nhưng nội dung rỗng"
          exit 1
        fi

        bytes=\$(wc -c < "\$tmp" | tr -d ' ')
        mv -f "\$tmp" '${destPath}'
        echo "[OK] Wrote '${destPath}' (\${bytes} bytes)"
      """
    )
  }
}
