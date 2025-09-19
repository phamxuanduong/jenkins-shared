/**
 * k8sGetConfig - Retrieve configuration from Kubernetes ConfigMaps
 *
 * @param args Map of optional parameters:
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - configmap: Branch-specific ConfigMap name (default: sanitized branch name)
 *   - generalConfigmap: General ConfigMap name (default: 'general')
 *   - items: Map of key-value pairs to extract (default: all keys from both ConfigMaps)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void - Files are written to workspace
 */
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
    echo "[INFO] k8sGetConfig: Processing ConfigMap: '${cm}'"

    // Get all keys from this ConfigMap if items not specified
    if (!items) {
      // Get only data keys using kubectl and awk to parse yaml output
      def keysList = sh(
        script: """
        if kubectl get configmap '${cm}' -n '${ns}' >/dev/null 2>&1; then
          kubectl get configmap '${cm}' -n '${ns}' -o yaml | awk '/^data:/ {flag=1; next} /^[a-zA-Z]/ && flag {flag=0} flag && /^  [^ ]/ {gsub(/^  /, ""); gsub(/:.*/, ""); print}' 2>/dev/null || true
        fi
        """,
        returnStdout: true
      ).trim()

      if (keysList) {
        keysList.split('\n').each { String keyName ->
          if (keyName && keyName.trim()) {
            fetchConfigKey(ns, cm, keyName.trim(), keyName.trim())
          }
        }
      } else {
        echo "[WARN] k8sGetConfig: ConfigMap '${cm}' not found or empty, skipping..."
      }
    } else {
      // Use specified items
      items.each { String keyName, String destPath ->
        fetchConfigKey(ns, cm, keyName, destPath)
      }
    }
  }
}

def fetchConfigKey(String ns, String cm, String key, String destPath) {
  if (!destPath) {
    echo "[WARN] k8sGetConfig: Empty destination path for key '${key}', skipping..."
    return
  }

  sh(
    label: "k8sGetConfig: ${cm}/${key} → ${destPath}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] k8sGetConfig: Fetching key='${key}' from ConfigMap='${cm}' (ns='${ns}')"
      dir=\$(dirname '${destPath}')
      [ "\$dir" = "." ] || mkdir -p "\$dir"

      tmp=\$(mktemp)

      # Check if key exists in configmap first - use go-template for keys with special chars
      if ! kubectl get configmap '${cm}' -n '${ns}' \\
           -o go-template='{{index .data "'${key}'"}}' > "\$tmp" 2>/dev/null; then
        echo "[WARN] k8sGetConfig: ConfigMap '${cm}' not found or key '${key}' missing, skipping..." >&2
        rm -f "\$tmp"
        exit 0
      fi

      # Check if the key actually has data
      if [ ! -s "\$tmp" ]; then
        echo "[WARN] k8sGetConfig: Key '${key}' is empty in ConfigMap '${cm}', skipping..." >&2
        rm -f "\$tmp"
        exit 0
      fi

      bytes=\$(wc -c < "\$tmp" | tr -d ' ')
      sha=\$(sha256sum "\$tmp" | awk '{print \$1}')
      mv -f "\$tmp" '${destPath}'
      echo "[SUCCESS] k8sGetConfig: Wrote '${destPath}' (\${bytes} bytes, sha256=\$sha) from ConfigMap '${cm}'"
    """
  )
}
