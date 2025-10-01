#!/usr/bin/env groovy

/**
 * k8sGetIngress - Get ingress information from Kubernetes
 *
 * @param args Map of optional parameters:
 *   - ingress: Ingress name (default: from deployment name)
 *   - deployment: Deployment name (default: from getProjectVars)
 *   - namespace: Kubernetes namespace (default: from getProjectVars)
 *   - outputFormat: Output format - 'domains', 'hosts', 'yaml', 'json' (default: 'domains')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return Mixed: Based on outputFormat
 *   - 'domains': String - Comma-separated list of domains or null
 *   - 'hosts': List - Array of host strings
 *   - 'yaml': String - Full YAML output
 *   - 'json': Map - Parsed JSON object
 *
 * @example
 *   // Get domains as comma-separated string
 *   def domains = k8sGetIngress()
 *
 *   // Get hosts as array
 *   def hosts = k8sGetIngress(outputFormat: 'hosts')
 *
 *   // Get full ingress YAML
 *   def yaml = k8sGetIngress(outputFormat: 'yaml')
 *
 *   // Get specific ingress
 *   def domains = k8sGetIngress(ingress: 'my-app', namespace: 'production')
 */
def call(Map args = [:]) {
  // Get project vars if not provided - use cached data if available
  def vars = args.vars ?: sharedUtils.getProjectVarsOptimized()

  String ingressName = args.ingress ?: args.deployment ?: vars.DEPLOYMENT
  String namespace = args.namespace ?: vars.NAMESPACE
  String outputFormat = args.outputFormat ?: 'domains'

  if (!ingressName || !namespace) {
    echo "[WARN] k8sGetIngress: Missing ingress name or namespace"
    return null
  }

  try {
    // Check if ingress exists
    def checkExists = sh(
      script: """
        kubectl get ingress ${ingressName} -n ${namespace} >/dev/null 2>&1 && echo 'exists' || echo 'not_found'
      """,
      returnStdout: true
    ).trim()

    if (checkExists != 'exists') {
      echo "[INFO] k8sGetIngress: Ingress '${ingressName}' not found in namespace '${namespace}'"
      return null
    }

    // Get ingress data based on format
    switch (outputFormat) {
      case 'domains':
        return getIngressDomains(ingressName, namespace)

      case 'hosts':
        return getIngressHosts(ingressName, namespace)

      case 'yaml':
        return getIngressYaml(ingressName, namespace)

      case 'json':
        return getIngressJson(ingressName, namespace)

      default:
        echo "[WARN] k8sGetIngress: Unknown output format '${outputFormat}', using 'domains'"
        return getIngressDomains(ingressName, namespace)
    }

  } catch (Exception e) {
    echo "[ERROR] k8sGetIngress: Failed to get ingress: ${e.getMessage()}"
    return null
  }
}

/**
 * Get ingress domains as comma-separated string
 */
def getIngressDomains(String ingressName, String namespace) {
  try {
    def result = sh(
      script: """
        kubectl get ingress ${ingressName} -n ${namespace} \
          -o jsonpath='{.spec.rules[*].host}' 2>/dev/null || echo ''
      """,
      returnStdout: true
    ).trim()

    if (result) {
      // Multiple domains separated by space, join with comma
      def domains = result.split(/\s+/).join(', ')
      echo "[INFO] k8sGetIngress: Found domain(s) '${domains}' from ingress '${ingressName}'"
      return domains
    }
  } catch (Exception e) {
    echo "[WARN] k8sGetIngress: Error getting domains: ${e.getMessage()}"
  }

  return null
}

/**
 * Get ingress hosts as array
 */
def getIngressHosts(String ingressName, String namespace) {
  try {
    def result = sh(
      script: """
        kubectl get ingress ${ingressName} -n ${namespace} \
          -o jsonpath='{.spec.rules[*].host}' 2>/dev/null || echo ''
      """,
      returnStdout: true
    ).trim()

    if (result) {
      def hosts = result.split(/\s+/).toList()
      echo "[INFO] k8sGetIngress: Found ${hosts.size()} host(s) from ingress '${ingressName}'"
      return hosts
    }
  } catch (Exception e) {
    echo "[WARN] k8sGetIngress: Error getting hosts: ${e.getMessage()}"
  }

  return []
}

/**
 * Get full ingress YAML
 */
def getIngressYaml(String ingressName, String namespace) {
  try {
    def yaml = sh(
      script: """
        kubectl get ingress ${ingressName} -n ${namespace} -o yaml 2>/dev/null || echo ''
      """,
      returnStdout: true
    ).trim()

    if (yaml) {
      echo "[INFO] k8sGetIngress: Retrieved YAML for ingress '${ingressName}'"
      return yaml
    }
  } catch (Exception e) {
    echo "[WARN] k8sGetIngress: Error getting YAML: ${e.getMessage()}"
  }

  return null
}

/**
 * Get ingress as JSON/Map
 */
def getIngressJson(String ingressName, String namespace) {
  try {
    def json = sh(
      script: """
        kubectl get ingress ${ingressName} -n ${namespace} -o json 2>/dev/null || echo '{}'
      """,
      returnStdout: true
    ).trim()

    if (json && json != '{}') {
      def jsonObject = readJSON text: json
      echo "[INFO] k8sGetIngress: Retrieved JSON for ingress '${ingressName}'"
      return jsonObject
    }
  } catch (Exception e) {
    echo "[WARN] k8sGetIngress: Error getting JSON: ${e.getMessage()}"
  }

  return null
}
