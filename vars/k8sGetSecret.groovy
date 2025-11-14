/**
 * k8sGetSecret - Retrieve sensitive configuration from Kubernetes Secrets
 *
 * Lấy sensitive files từ Secret (.env, application.properties, database.yml, v.v.)
 * Secret name CỐ ĐỊNH theo base environment (beta, staging, prod) - KHÔNG có suffix
 *
 * Secret naming:
 * - Branch "beta" → Secret "beta"
 * - Branch "beta/api" → Secret "beta" (KHÔNG phải "beta-api")
 * - Branch "beta/worker" → Secret "beta" (KHÔNG phải "beta-worker")
 * - Branch "prod" → Secret "prod"
 * - Branch "prod/api" → Secret "prod" (KHÔNG phải "prod-api")
 *
 * Secret sẽ OVERRIDE files từ ConfigMap nếu trùng tên
 *
 * @param args Map of optional parameters:
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - secret: Secret name (default: base environment - beta/staging/prod)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void - Files are written to workspace
 *
 * @example
 *   // Auto-fetch (khuyến nghị)
 *   k8sGetSecret()
 *
 * @example
 *   // Custom Secret name
 *   k8sGetSecret(
 *     secret: 'my-secret'
 *   )
 */
def call(Map args = [:]) {
  // Get project vars if not provided - check for cached data first
  def vars = args.vars ?: sharedUtils.getProjectVarsOptimized()

  String ns = args.namespace ?: vars.NAMESPACE

  // Parse branch to detect base environment
  def branchParts = parseBranchName(vars.REPO_BRANCH ?: env.BRANCH_NAME ?: '')
  String baseEnv = branchParts.base

  // Secret name defaults to base environment (beta, prod, staging) - NO suffix
  String secretName = args.secret ?: baseEnv

  echo """
[INFO] k8sGetSecret: Configuration Strategy:
  - Original Branch: '${vars.REPO_BRANCH}'
  - Base Environment: '${baseEnv}'
  - Secret Name: '${secretName}' (base environment only, no suffix)
"""

  // Fetch ALL files from Secret
  echo "[INFO] k8sGetSecret: Fetching ALL files from Secret '${secretName}'"
  fetchAllKeysFromSecret(ns, secretName)

  echo "[SUCCESS] k8sGetSecret: Secret fetch completed!"
}

/**
 * Parse branch name to extract base environment
 *
 * Examples:
 * - "beta" → [base: "beta", suffix: null]
 * - "beta/api" → [base: "beta", suffix: "api"]
 * - "prod/worker" → [base: "prod", suffix: "worker"]
 */
def parseBranchName(String branchName) {
  if (!branchName) {
    return [base: '', suffix: null]
  }

  // Remove origin/ prefix if exists
  branchName = branchName.replaceAll('^origin/', '')

  // Check if branch has a slash (indicating suffix)
  if (branchName.contains('/')) {
    def parts = branchName.split('/', 2)
    return [base: parts[0].toLowerCase(), suffix: parts[1].toLowerCase()]
  } else {
    return [base: branchName.toLowerCase(), suffix: null]
  }
}

/**
 * Fetch all keys from a Kubernetes Secret
 *
 * @param ns Namespace
 * @param secretName Secret name (e.g., "beta", "prod", "staging")
 */
def fetchAllKeysFromSecret(String ns, String secretName) {
  // Get all data keys from Secret using kubectl and awk
  def keysList = sh(
    script: """
    if kubectl get secret '${secretName}' -n '${ns}' >/dev/null 2>&1; then
      kubectl get secret '${secretName}' -n '${ns}' -o yaml | awk '/^data:/ {flag=1; next} /^[a-zA-Z]/ && flag {flag=0} flag && /^  [^ ]/ {gsub(/^  /, ""); gsub(/:.*/, ""); print}' 2>/dev/null || true
    fi
    """,
    returnStdout: true
  ).trim()

  if (keysList) {
    keysList.split('\n').each { String keyName ->
      if (keyName && keyName.trim()) {
        def trimmedKey = keyName.trim()
        fetchSecretKey(ns, secretName, trimmedKey, trimmedKey)
      }
    }
  } else {
    echo "[WARN] k8sGetSecret: Secret '${secretName}' not found or empty, skipping..."
  }
}

/**
 * Fetch a specific key from Secret (with base64 decode)
 *
 * @param ns Namespace
 * @param secretName Secret name
 * @param key Key name in Secret
 * @param destPath Destination file path
 */
def fetchSecretKey(String ns, String secretName, String key, String destPath) {
  if (!destPath) {
    echo "[WARN] k8sGetSecret: Empty destination path for key '${key}', skipping..."
    return
  }

  sh(
    label: "k8sGetSecret: ${secretName}/${key} → ${destPath}",
    script: """#!/bin/bash
      set -Eeuo pipefail

      echo "[INFO] k8sGetSecret: Fetching key='${key}' from Secret='${secretName}' (ns='${ns}')"
      dir=\$(dirname '${destPath}')
      [ "\$dir" = "." ] || mkdir -p "\$dir"

      tmp=\$(mktemp)

      # Check if key exists in secret first - use go-template for keys with special chars
      if ! kubectl get secret '${secretName}' -n '${ns}' \\
           -o go-template='{{index .data "'${key}'"}}' 2>/dev/null | base64 -d > "\$tmp"; then
        echo "[WARN] k8sGetSecret: Secret '${secretName}' not found or key '${key}' missing, skipping..." >&2
        rm -f "\$tmp"
        exit 0
      fi

      # Check if the key actually has data
      if [ ! -s "\$tmp" ]; then
        echo "[WARN] k8sGetSecret: Key '${key}' is empty in Secret '${secretName}', skipping..." >&2
        rm -f "\$tmp"
        exit 0
      fi

      bytes=\$(wc -c < "\$tmp" | tr -d ' ')
      sha=\$(sha256sum "\$tmp" | awk '{print \$1}')
      mv -f "\$tmp" '${destPath}'
      echo "[SUCCESS] k8sGetSecret: Wrote '${destPath}' (\${bytes} bytes, sha256=\$sha) from Secret '${secretName}'"
    """
  )
}
