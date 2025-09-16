def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  String ns = args.namespace ?: vars.NAMESPACE
  String branchCm = args.configmap ?: vars.SANITIZED_BRANCH
  String generalCm = args.generalConfigmap ?: 'general'

  // ConfigMaps to fetch from (general first, then branch-specific)
  List<String> configmaps = [generalCm, branchCm].unique()

  // Default items if not specified - fetch all data from both ConfigMaps
  Map items = args.items

  echo """
[INFO] k8sGetConfig: Fetching from multiple ConfigMaps in namespace '${ns}':
  - General ConfigMap: '${generalCm}' (shared files)
  - Branch ConfigMap: '${branchCm}' (branch-specific files)
  - Branch-specific files will override general files if same key exists
"""

  configmaps.each { String cm ->
    echo "[INFO] Processing ConfigMap: '${cm}'"

    // Get all keys from this ConfigMap if items not specified
    if (!items) {
      def allKeys = sh(
        script: """kubectl get configmap '${cm}' -n '${ns}' -o jsonpath='{.data}' 2>/dev/null || echo '{}'""",
        returnStdout: true
      ).trim()

      if (allKeys && allKeys != '{}') {
        // Parse JSON to get all keys and create items map
        def keysJson = readJSON text: allKeys
        keysJson.each { key, value ->
          fetchConfigKey(ns, cm, key, key)
        }
      } else {
        echo "[WARN] ConfigMap '${cm}' not found or empty, skipping..."
      }
    } else {
      // Use specified items
      items.each { String key, String destPath ->
        fetchConfigKey(ns, cm, key, destPath)
      }
    }
  }
}

def fetchConfigKey(String ns, String cm, String key, String destPath) {
  if (!destPath) {
    echo "[WARN] Empty destination path for key '${key}', skipping..."
    return
  }

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
      echo "[OK] Wrote '${destPath}' (\${bytes} bytes, sha256=\$sha) from ConfigMap '${cm}'"
    """
  )
}
