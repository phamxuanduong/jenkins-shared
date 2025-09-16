# Kubernetes Functions

Tập hợp các functions để tương tác với Kubernetes cluster, bao gồm deployment updates và ConfigMap management.

## k8sSetImage

Cập nhật image cho Kubernetes deployment.

### Tham số (tất cả tùy chọn)
- `deployment`: Tên deployment (mặc định: tự động từ `getProjectVars()`)
- `image`: Tên image mới (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag của image (mặc định: tự động từ commit hash)
- `namespace`: Kubernetes namespace (mặc định: tự động từ repo name)
- `container`: Tên container cụ thể (mặc định: '*' - tất cả containers)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

### Implementation Details
- Sử dụng `kubectl set image` command
- Supports rolling updates
- Automatic validation của deployment status
- Error handling cho missing deployments

### Ví dụ
```groovy
// Hoàn toàn tự động
k8sSetImage()

// Custom deployment name
k8sSetImage(deployment: 'my-custom-deployment')

// Specific container
k8sSetImage(container: 'app-container')

// Full custom
k8sSetImage(
    deployment: 'api-deployment',
    image: 'registry.com/my-api',
    tag: 'v1.2.3',
    namespace: 'production',
    container: 'api'
)
```

### Command Generated
```bash
kubectl set image deployment/my-app-beta-api *=registry-beta.com/my-app:abc123d -n my-app
```

## k8sGetConfig

Lấy dữ liệu từ Kubernetes ConfigMap và lưu vào file. Chi tiết đầy đủ xem [k8sGetConfig.md](./k8sGetConfig.md).

### Quick Reference
```groovy
// Auto-fetch từ general + branch ConfigMaps
k8sGetConfig()

// Custom ConfigMaps
k8sGetConfig(
    generalConfigmap: 'shared',
    configmap: 'prod-api'
)

// Specific files only
k8sGetConfig(
    items: [
        'Dockerfile': 'Dockerfile',
        '.env': '.env'
    ]
)
```

## Advanced Kubernetes Operations

### Deployment Status Check
```groovy
script {
    def vars = getProjectVars()

    // Update deployment
    k8sSetImage(vars: vars)

    // Wait for rollout
    sh """
    kubectl rollout status deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE} --timeout=300s
    """

    // Check pod status
    sh """
    kubectl get pods -n ${vars.NAMESPACE} -l app=${vars.APP_NAME}
    """
}
```

### Rollback on Failure
```groovy
script {
    def vars = getProjectVars()

    try {
        k8sSetImage(vars: vars)

        // Wait and verify deployment
        sh """
        kubectl rollout status deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE} --timeout=300s
        """

        // Health check
        sh """
        kubectl get deployment ${vars.DEPLOYMENT} -n ${vars.NAMESPACE} -o jsonpath='{.status.readyReplicas}'
        """

    } catch (Exception e) {
        echo "Deployment failed, rolling back..."

        sh """
        kubectl rollout undo deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE}
        """

        throw e
    }
}
```

### Multi-Environment Deployment
```groovy
script {
    def environments = [
        [namespace: 'staging', registry: env.REGISTRY_STAGING],
        [namespace: 'production', registry: env.REGISTRY_PROD]
    ]

    environments.each { env ->
        def vars = getProjectVars(
            namespace: env.namespace,
            registry: env.registry
        )

        stage("Deploy to ${env.namespace}") {
            k8sSetImage(vars: vars)

            // Verify deployment
            sh """
            kubectl rollout status deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE}
            """
        }
    }
}
```

### Deployment with Resources
```groovy
script {
    def vars = getProjectVars()

    // Update image
    k8sSetImage(vars: vars)

    // Update resource limits (if needed)
    sh """
    kubectl patch deployment ${vars.DEPLOYMENT} -n ${vars.NAMESPACE} -p '{
        "spec": {
            "template": {
                "spec": {
                    "containers": [{
                        "name": "app",
                        "resources": {
                            "requests": {"memory": "256Mi", "cpu": "250m"},
                            "limits": {"memory": "512Mi", "cpu": "500m"}
                        }
                    }]
                }
            }
        }
    }'
    """
}
```

## Kubernetes Manifests

### Deployment Template
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app-beta-api
  namespace: my-app
  labels:
    app: my-app
    environment: beta
    component: api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-app
      component: api
  template:
    metadata:
      labels:
        app: my-app
        component: api
    spec:
      containers:
      - name: app
        image: registry-beta.com/my-app:placeholder
        ports:
        - containerPort: 3000
        env:
        - name: NODE_ENV
          value: "production"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 3000
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 3000
          initialDelaySeconds: 5
          periodSeconds: 5
```

### Service Template
```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-app-beta-api
  namespace: my-app
spec:
  selector:
    app: my-app
    component: api
  ports:
  - protocol: TCP
    port: 80
    targetPort: 3000
  type: ClusterIP
```

## Best Practices

### Namespace Organization
```
# Environment-based namespaces
my-app-dev
my-app-staging
my-app-production

# Or service-based namespaces
my-app          # Main application
my-app-workers  # Background workers
my-app-cache    # Redis/caching layer
```

### Deployment Naming
```
# Pattern: {app-name}-{environment}-{component}
my-app-beta-api
my-app-beta-worker
my-app-prod-api
my-app-prod-worker
```

### ConfigMap Organization
```
# general: Shared configs
general:
  .env: base environment variables
  nginx.conf: shared nginx config

# Environment-specific: Override configs
beta-api:
  .env: beta-specific environment
  Dockerfile: beta-specific Dockerfile

prod-api:
  .env: production environment
  Dockerfile: production Dockerfile
```

### RBAC Setup
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: my-app
  name: jenkins-deployer
rules:
- apiGroups: ["apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "patch", "update"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-deployer
  namespace: my-app
subjects:
- kind: ServiceAccount
  name: jenkins
  namespace: default
roleRef:
  kind: Role
  name: jenkins-deployer
  apiGroup: rbac.authorization.k8s.io
```

## Troubleshooting

### Deployment not found
**Nguyên nhân**: Deployment chưa được tạo hoặc tên sai
**Giải pháp**:
```bash
# List deployments
kubectl get deployments -n your-namespace

# Check deployment name pattern
kubectl get deployment -n your-namespace -l app=your-app
```

### Image pull errors
**Nguyên nhân**: Registry authentication hoặc image không tồn tại
**Giải pháp**:
```bash
# Check image exists
docker pull your-registry.com/your-app:tag

# Check image pull secrets
kubectl get secrets -n your-namespace
kubectl describe pod pod-name -n your-namespace
```

### ConfigMap not found
**Nguyên nhân**: ConfigMap chưa được tạo
**Giải pháp**:
```bash
# List ConfigMaps
kubectl get configmaps -n your-namespace

# Create ConfigMap from files
kubectl create configmap general --from-file=.env --from-file=Dockerfile -n your-namespace
```

### Permission denied
**Nguyên nhân**: RBAC không đủ quyền
**Giải pháp**:
```bash
# Check current permissions
kubectl auth can-i update deployments -n your-namespace

# Check service account
kubectl get serviceaccount jenkins -o yaml
```

### Rollout stuck
**Nguyên nhân**: New pods không healthy
**Giải pháp**:
```bash
# Check rollout status
kubectl rollout status deployment/your-deployment -n your-namespace

# Check pod logs
kubectl logs -l app=your-app -n your-namespace

# Check pod events
kubectl describe pods -l app=your-app -n your-namespace
```

## Monitoring và Logging

### Deployment Monitoring
```groovy
script {
    def vars = getProjectVars()

    // Deploy
    k8sSetImage(vars: vars)

    // Monitor rollout
    timeout(time: 5, unit: 'MINUTES') {
        sh """
        kubectl rollout status deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE}
        """
    }

    // Health check
    sh """
    kubectl get deployment ${vars.DEPLOYMENT} -n ${vars.NAMESPACE} -o json | \
    jq '.status | {replicas, readyReplicas, updatedReplicas}'
    """
}
```

### Log Collection
```groovy
script {
    def vars = getProjectVars()

    // Deployment logs
    sh """
    kubectl logs deployment/${vars.DEPLOYMENT} -n ${vars.NAMESPACE} --tail=100
    """

    // Events
    sh """
    kubectl get events -n ${vars.NAMESPACE} --sort-by=.metadata.creationTimestamp
    """
}
```

### Metrics Collection
```groovy
script {
    def vars = getProjectVars()

    // Resource usage
    sh """
    kubectl top pods -n ${vars.NAMESPACE} -l app=${vars.APP_NAME}
    """

    // Pod status
    sh """
    kubectl get pods -n ${vars.NAMESPACE} -l app=${vars.APP_NAME} -o wide
    """
}
```