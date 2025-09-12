def call(Map args = [:]) {
  if (!args.namespace) error 'k8sGetConfig: thiếu "namespace"'
  if (!args.configmap) error 'k8sGetConfig: thiếu "configmap"'
  if (!(args.items instanceof Map) || args.items.isEmpty()) {
    error 'k8sGetConfig: thiếu "items" (Map: key-trong-ConfigMap -> đường-dẫn-file-đích)'
  }

  String ns = args.namespace
  String cm = args.configmap
  Map items = args.items   // ví dụ: ['Dockerfile':'build/images/base/Dockerfile', '.env':'deploy/dev/.env']

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

        # go-template: index hỗ trợ key có dấu chấm; strict để thiếu key -> lỗi
        if ! kubectl get configmap '${cm}' -n '${ns}' \\
             --allow-missing-template-keys=false \\
             -o go-template='{{index .data "${key}"}}' > "\$tmp"; then
          echo "[ERROR] Missing key '${key}' in ConfigMap '${cm}'" >&2
          exit 1
        fi

        # Guard: Go template có thể in literal "<no value>" khi key thiếu; hoặc rỗng
        if [ ! -s "\$tmp" ] || grep -qxF '<no value>' "\$tmp"; then
          echo "[ERROR] Key '${key}' thiếu hoặc rỗng (got '<no value>'/empty)" >&2
          exit 1
        fi

        bytes=\$(wc -c < "\$tmp" | tr -d ' ')
        sha=\$(sha256sum "\$tmp" | awk '{print \$1}')
        mv -f "\$tmp" '${destPath}'
        echo "[OK] Wrote '${destPath}' (\${bytes} bytes, sha256=\$sha)"
      """
    )
  }
}
