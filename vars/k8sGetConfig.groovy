def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String ns = args.namespace ?: vars.NAMESPACE
  String cm = args.configmap ?: vars.SANITIZED_BRANCH

  // Default items if not specified
  Map items = args.items ?: [
    'Dockerfile': 'Dockerfile',
    '.env': '.env'
  ]

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

        # Check if key exists in configmap first
        if ! kubectl get configmap '${cm}' -n '${ns}' \\
             -o jsonpath="{.data.${key}}" > "\$tmp" 2>/dev/null; then
          echo "[WARN] ConfigMap '${cm}' not found or key '${key}' missing, skipping..." >&2
          rm -f "\$tmp"
          exit 0
        fi

        # Check if the key actually has data
        if [ ! -s "\$tmp" ]; then
          echo "[WARN] Key '${key}' is empty in ConfigMap '${cm}', skipping..." >&2
          rm -f "\$tmp"
          exit 0
        fi

        bytes=\$(wc -c < "\$tmp" | tr -d ' ')
        sha=\$(sha256sum "\$tmp" | awk '{print \$1}')
        mv -f "\$tmp" '${destPath}'
        echo "[OK] Wrote '${destPath}' (\${bytes} bytes, sha256=\$sha)"
      """
    )
  }
}
