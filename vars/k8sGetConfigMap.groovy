/**
 * k8sGetConfig - Retrieve configuration from Kubernetes ConfigMaps
 *
 * Logic:
 * - Case 1: Branch without suffix (e.g., "beta", "prod")
 *   → Fetch ALL files (.env, Dockerfile, etc.) from base ConfigMap (beta, prod)
 * - Case 2: Branch with suffix (e.g., "beta/api", "beta/worker")
 *   → Fetch .env from base ConfigMap (beta, prod)
 *   → Fetch Dockerfile from suffixed ConfigMap (beta-api, beta-worker)
 *
 * @param args Map of optional parameters:
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - configmap: Branch-specific ConfigMap name (default: sanitized branch name)
 *   - generalConfigmap: General ConfigMap name (default: 'general')
 *   - items: Map of key-value pairs to extract (default: auto-detect based on branch)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void - Files are written to workspace
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
[INFO] k8sGetConfig: Branch Analysis:
  - Original Branch: '${vars.REPO_BRANCH}'
  - Base Environment: '${baseEnv}'
  - Suffix: '${suffix ?: 'none'}'
  - Sanitized Branch: '${branchCm}'
"""

  // Determine which ConfigMaps to use and what files to fetch
  List<Map> configMapStrategy = determineConfigMapStrategy(baseEnv, suffix, branchCm, generalCm)

  configMapStrategy.each { Map strategy ->
    String cm = strategy.configmap
    List<String> files = strategy.files

    echo """
[INFO] k8sGetConfig: Processing ConfigMap '${cm}':
  - Namespace: '${ns}'
  - Files to fetch: ${files ?: 'ALL'}
"""

    if (files == null || files.isEmpty()) {
      // Fetch ALL keys from this ConfigMap
      fetchAllKeysFromConfigMap(ns, cm)
    } else {
      // Fetch specific files only
      files.each { String fileName ->
        fetchConfigKey(ns, cm, fileName, fileName)
      }
    }
  }
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
 * Determine ConfigMap fetching strategy based on branch structure
 */
def determineConfigMapStrategy(String baseEnv, String suffix, String branchCm, String generalCm) {
  List<Map> strategy = []

  // Always fetch from general ConfigMap first (if it exists)
  strategy.add([
    configmap: generalCm,
    files: null  // Fetch all files
  ])

  if (!suffix) {
    // Case 1: No suffix (e.g., "beta", "prod")
    // → Fetch ALL files from base ConfigMap
    echo "[INFO] k8sGetConfig: Case 1 - No suffix detected, fetching ALL files from '${baseEnv}'"
    strategy.add([
      configmap: baseEnv,
      files: null  // Fetch all files including .env and Dockerfile
    ])
  } else {
    // Case 2: Has suffix (e.g., "beta/api", "prod/worker")
    // → Fetch .env from base ConfigMap (beta, prod)
    // → Fetch Dockerfile from suffixed ConfigMap (beta-api, prod-worker)
    echo "[INFO] k8sGetConfig: Case 2 - Suffix '${suffix}' detected"
    echo "[INFO] k8sGetConfig: - Fetching .env from base ConfigMap '${baseEnv}'"
    echo "[INFO] k8sGetConfig: - Fetching Dockerfile from suffixed ConfigMap '${branchCm}'"

    strategy.add([
      configmap: baseEnv,
      files: ['.env']  // Fetch only .env from base
    ])

    strategy.add([
      configmap: branchCm,
      files: ['Dockerfile']  // Fetch only Dockerfile from suffixed
    ])
  }

  return strategy
}

/**
 * Fetch all keys from a ConfigMap
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
        fetchConfigKey(ns, cm, keyName.trim(), keyName.trim())
      }
    }
  } else {
    echo "[WARN] k8sGetConfig: ConfigMap '${cm}' not found or empty, skipping..."
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

