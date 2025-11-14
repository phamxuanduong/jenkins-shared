/**
 * k8sGetConfigMap - Retrieve configuration from Kubernetes ConfigMaps
 *
 * Lấy config files từ ConfigMaps (Dockerfile, nginx.conf, v.v.)
 * Để lấy sensitive files (.env, application.properties), dùng k8sGetSecret()
 *
 * ConfigMap structure:
 * - Case 1: Branch không có suffix (e.g., "beta", "prod")
 *   → Lấy ALL files từ ConfigMap "general" + "beta"/"prod"
 * - Case 2: Branch có suffix (e.g., "beta/api", "beta/worker")
 *   → Lấy files từ ConfigMap "general" + "beta"/"prod" + "beta-api"/"beta-worker"
 *
 * @param args Map of optional parameters:
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - configmap: Branch-specific ConfigMap name (default: sanitized branch name)
 *   - generalConfigmap: General ConfigMap name (default: 'general')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void - Files are written to workspace
 *
 * @example
 *   // Auto-fetch
 *   k8sGetConfigMap()
 *
 * @example
 *   // Custom ConfigMap names
 *   k8sGetConfigMap(
 *     generalConfigmap: 'shared',
 *     configmap: 'prod-custom'
 *   )
 */
def call(Map args = [:]) {
  // Get project vars if not provided - check for cached data first
  def vars = args.vars ?: sharedUtils.getProjectVarsOptimized()

  String ns = args.namespace ?: vars.NAMESPACE
  String branchCm = args.configmap ?: vars.SANITIZED_BRANCH
  String generalCm = args.generalConfigmap ?: 'general'

  // Parse branch to detect base environment and suffix
  def branchParts = parseBranchName(vars.REPO_BRANCH ?: env.BRANCH_NAME ?: '')
  String baseEnv = branchParts.base
  String suffix = branchParts.suffix

  echo """
[INFO] k8sGetConfig: Configuration Strategy:
  - Original Branch: '${vars.REPO_BRANCH}'
  - Base Environment: '${baseEnv}'
  - Suffix: '${suffix ?: 'none'}'
  - Sanitized Branch: '${branchCm}'
  - Fetching from ConfigMaps: general, ${baseEnv}${suffix ? ', ' + branchCm : ''}
"""

  // Fetch from ConfigMaps (all files)
  echo "[INFO] k8sGetConfig: Fetching ALL files from ConfigMaps"
  List<String> configmaps = determineConfigMaps(baseEnv, suffix, branchCm, generalCm)

  configmaps.each { String cm ->
    echo "[INFO] k8sGetConfig: Processing ConfigMap '${cm}' (namespace: '${ns}')"
    fetchAllKeysFromConfigMap(ns, cm)
  }

  echo "[SUCCESS] k8sGetConfig: Configuration fetch completed!"
}

/**
 * Parse branch name to extract base environment and suffix
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
 * Determine which ConfigMaps to fetch from (keep old logic for backward compatibility)
 */
def determineConfigMaps(String baseEnv, String suffix, String branchCm, String generalCm) {
  List<String> configmaps = [generalCm]

  if (!suffix) {
    // Case 1: No suffix (e.g., "beta", "prod")
    // → Fetch from base ConfigMap only
    echo "[INFO] k8sGetConfig: Case 1 - No suffix, using ConfigMap '${baseEnv}'"
    configmaps.add(baseEnv)
  } else {
    // Case 2: Has suffix (e.g., "beta/api", "prod/worker")
    // → Fetch from base ConfigMap (shared) AND suffixed ConfigMap (specific)
    echo "[INFO] k8sGetConfig: Case 2 - Suffix '${suffix}' detected"
    echo "[INFO] k8sGetConfig: - Using base ConfigMap '${baseEnv}' for shared files"
    echo "[INFO] k8sGetConfig: - Using suffixed ConfigMap '${branchCm}' for specific files"
    configmaps.add(baseEnv)
    configmaps.add(branchCm)
  }

  return configmaps.unique()
}

/**
 * Fetch all keys from a ConfigMap
 *
 * @param ns Namespace
 * @param cm ConfigMap name
 */
def fetchAllKeysFromConfigMap(String ns, String cm) {
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
        def trimmedKey = keyName.trim()
        fetchConfigKey(ns, cm, trimmedKey, trimmedKey)
      }
    }
  } else {
    echo "[WARN] k8sGetConfig: ConfigMap '${cm}' not found or empty, skipping..."
  }
}

/**
 * Fetch a specific key from ConfigMap
 */
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
