// vars/k8sGetConfig.groovy
def call(Map args = [:]) {
  if (!args.namespace) error 'k8sGetConfig: thiếu "namespace"'
  if (!args.configmap) error 'k8sGetConfig: thiếu "configmap"'
  if (!(args.items instanceof Map) || args.items.isEmpty()) {
    error 'k8sGetConfig: thiếu "items" (Map: key-trong-ConfigMap -> đường-dẫn-file-đích)'
  }

  String ns = args.namespace
  String cm = args.configmap
  boolean strict = (args.strict == true)   // nếu true: báo lỗi khi thiếu key

  args.items.each { String key, String destPath ->
    if (!destPath) error "k8sGetConfig: path rỗng cho key '${key}'"
    sh """#!/bin/bash -eu
      dir=\$(dirname '${destPath}')
      [ "\$dir" = "." ] || mkdir -p "\$dir"
      kubectl get configmap '${cm}' -n '${ns}' \\
        ${ strict ? "--allow-missing-template-keys=false" : "" } \\
        -o go-template='{{index .data "${key}"}}' > '${destPath}'
    """
  }
}
