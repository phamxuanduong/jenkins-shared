# Troubleshooting Guide

Hướng dẫn xử lý các vấn đề thường gặp khi sử dụng Jenkins Shared Library.

## Common Issues

### 1. ConfigMap Issues

#### `.env` báo empty mặc dù ConfigMap có data
**Triệu chứng:**
```
[WARN] Key '.env' is empty in ConfigMap 'general', skipping...
```

**Nguyên nhân:** Keys có dấu chấm không được handle đúng cách

**Giải pháp đã fix:** Function sử dụng `go-template index` thay vì `jsonpath`

**Debug steps:**
```bash
# Check ConfigMap data
kubectl describe configmap general -n your-namespace
kubectl get configmap general -n your-namespace -o yaml

# Test manual extraction
kubectl get configmap general -n your-namespace -o go-template='{{index .data ".env"}}'
```

#### ConfigMap keys không được detect
**Triệu chứng:** Không có keys nào được lấy từ ConfigMap

**Debug steps:**
```bash
# Check ConfigMap structure
kubectl get configmap your-cm -n your-namespace -o yaml

# Test key extraction command
kubectl get configmap your-cm -n your-namespace -o yaml | \
awk '/^data:/ {flag=1; next} /^[a-zA-Z]/ && flag {flag=0} flag && /^  [^ ]/ {gsub(/^  /, ""); gsub(/:.*/, ""); print}'
```

### 2. Git và Project Variables

#### Repository name không detect được
**Triệu chứng:**
```
[INFO] Project Variables:
  REPO_NAME:     unknown-repo
```

**Nguyên nhân:** GIT_URL format không standard hoặc không có

**Giải pháp:**
```groovy
// Override manually
getProjectVars(repoName: 'my-app')

// Hoặc check environment
echo "GIT_URL: ${env.GIT_URL}"
```

#### Registry không đúng
**Triệu chứng:** Docker push đến registry sai

**Debug:**
```groovy
// Check environment variables
echo "REGISTRY_BETA: ${env.REGISTRY_BETA}"
echo "REGISTRY_STAGING: ${env.REGISTRY_STAGING}"
echo "REGISTRY_PROD: ${env.REGISTRY_PROD}"

// Check branch detection
def vars = getProjectVars()
echo "Branch: ${vars.REPO_BRANCH}"
echo "Registry: ${vars.REGISTRY}"
```

### 3. Docker Issues

#### Docker command not found
**Triệu chứng:**
```
docker: command not found
```

**Nguyên nhân:** Jenkins agent không có Docker

**Giải pháp:**
```bash
# Install Docker on agent
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Add jenkins user to docker group
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins

# Test Docker access
docker --version
docker ps
```

#### Registry authentication failed
**Triệu chứng:**
```
Error response from daemon: unauthorized
```

**Giải pháp:**
```groovy
// Method 1: Direct login
sh 'docker login registry.company.com -u $DOCKER_USER -p $DOCKER_PASS'

// Method 2: Using Jenkins credentials
withCredentials([usernamePassword(credentialsId: 'docker-registry',
                                  usernameVariable: 'USER',
                                  passwordVariable: 'PASS')]) {
    sh 'docker login registry.company.com -u $USER -p $PASS'
    dockerBuildPush()
}

// Method 3: Docker config.json
// Place in Jenkins agent ~/.docker/config.json
```

### 4. Kubernetes Issues

#### Deployment not found
**Triệu chứng:**
```
Error from server (NotFound): deployments.apps "my-app-beta-api" not found
```

**Debug:**
```bash
# List all deployments
kubectl get deployments -n your-namespace

# Check deployment naming pattern
kubectl get deployment -n your-namespace -l app=your-app

# Check expected vs actual names
echo "Expected: my-app-beta-api"
echo "Actual deployments:"
kubectl get deployments -n your-namespace --no-headers | awk '{print $1}'
```

#### Permission denied (RBAC)
**Triệu chứng:**
```
Error from server (Forbidden): deployments.apps is forbidden
```

**Debug:**
```bash
# Check current permissions
kubectl auth can-i update deployments -n your-namespace
kubectl auth can-i get configmaps -n your-namespace

# Check service account
kubectl get serviceaccount -n your-namespace
kubectl describe serviceaccount jenkins -n your-namespace
```

**Fix RBAC:**
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: your-namespace
  name: jenkins-deployer
rules:
- apiGroups: ["apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "patch", "update"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]
```

### 5. Jenkins Pipeline Issues

#### Variable naming conflicts
**Triệu chứng:**
```
groovy.lang.MissingPropertyException: No such property: key
```

**Nguyên nhân:** Variable names conflict với Jenkins built-ins

**Giải pháp đã fix:** Function sử dụng `keyName` thay vì `key`

#### Library not found
**Triệu chứng:**
```
No such DSL method 'cicdPipeline' found
```

**Giải pháp:**
```groovy
// Ensure library is loaded with correct version
@Library('jenkins-shared@main') _

// Check library configuration in Jenkins
// Manage Jenkins > Configure System > Global Pipeline Libraries
```

#### Function parameters not working
**Triệu chứng:** Function không hoạt động như expected

**Debug:**
```groovy
// Add debug logging
script {
    def vars = getProjectVars()
    vars.each { key, value ->
        echo "${key}: ${value}"
    }

    // Test individual functions
    k8sGetConfig(vars: vars)
    dockerBuildPush(vars: vars)
    k8sSetImage(vars: vars)
}
```

## Debug Commands

### Environment Check
```groovy
script {
    echo "=== Environment Variables ==="
    echo "GIT_URL: ${env.GIT_URL}"
    echo "GIT_BRANCH: ${env.GIT_BRANCH}"
    echo "GIT_COMMIT: ${env.GIT_COMMIT}"
    echo "WORKSPACE: ${env.WORKSPACE}"

    echo "=== Registry Variables ==="
    echo "REGISTRY_BETA: ${env.REGISTRY_BETA}"
    echo "REGISTRY_STAGING: ${env.REGISTRY_STAGING}"
    echo "REGISTRY_PROD: ${env.REGISTRY_PROD}"

    echo "=== Project Variables ==="
    def vars = getProjectVars()
    vars.each { key, value ->
        echo "${key}: ${value}"
    }
}
```

### Docker Check
```groovy
script {
    echo "=== Docker Check ==="
    sh 'docker --version'
    sh 'docker info'
    sh 'docker images | head -5'

    echo "=== Registry Login Test ==="
    sh 'docker login registry.company.com --username test --password-stdin <<< "test"'
}
```

### Kubernetes Check
```groovy
script {
    def vars = getProjectVars()

    echo "=== Kubernetes Check ==="
    sh 'kubectl version --client'
    sh 'kubectl config current-context'

    echo "=== Namespace Check ==="
    sh "kubectl get namespace ${vars.NAMESPACE}"

    echo "=== Resources Check ==="
    sh "kubectl get deployments -n ${vars.NAMESPACE}"
    sh "kubectl get configmaps -n ${vars.NAMESPACE}"

    echo "=== Permissions Check ==="
    sh "kubectl auth can-i update deployments -n ${vars.NAMESPACE}"
    sh "kubectl auth can-i get configmaps -n ${vars.NAMESPACE}"
}
```

## Performance Issues

### Slow ConfigMap operations
**Nguyên nhân:** Large ConfigMaps hoặc network latency

**Giải pháp:**
```groovy
// Specific items only
k8sGetConfig(
    items: [
        'Dockerfile': 'Dockerfile',
        '.env': '.env'
    ]
)

// Skip ConfigMap stage if not needed
cicdPipeline(getConfigStage: false)
```

### Slow Docker builds
**Nguyên nhân:** Layer caching issues

**Giải pháp:**
```dockerfile
# Optimize Dockerfile layer order
FROM node:18-alpine
WORKDIR /app

# Copy package files first (cached layer)
COPY package*.json ./
RUN npm ci --only=production

# Copy source code last (changes frequently)
COPY . .
CMD ["npm", "start"]
```

### Pipeline timeout
**Nguyên nhân:** Operations taking too long

**Giải pháp:**
```groovy
pipeline {
    options {
        timeout(time: 30, unit: 'MINUTES')
    }
    stages {
        stage('CI/CD') {
            steps {
                script {
                    timeout(time: 20, unit: 'MINUTES') {
                        cicdPipeline()
                    }
                }
            }
        }
    }
}
```

## Best Practices for Debugging

### Structured Logging
```groovy
script {
    try {
        echo "=== Starting CI/CD Pipeline ==="
        echo "Timestamp: ${new Date()}"
        echo "Build: ${env.BUILD_NUMBER}"
        echo "Branch: ${env.GIT_BRANCH}"

        cicdPipeline()

        echo "=== Pipeline Completed Successfully ==="
    } catch (Exception e) {
        echo "=== Pipeline Failed ==="
        echo "Error: ${e.getMessage()}"
        echo "Stack trace: ${e.getStackTrace()}"
        throw e
    }
}
```

### Conditional Debugging
```groovy
script {
    def debugMode = env.DEBUG_MODE == 'true'

    if (debugMode) {
        echo "=== Debug Mode Enabled ==="
        // Detailed logging
        def vars = getProjectVars()
        vars.each { key, value ->
            echo "DEBUG: ${key} = ${value}"
        }
    }

    cicdPipeline()
}
```

### Environment-specific Configuration
```groovy
script {
    def vars = getProjectVars()

    // Different behavior per environment
    if (vars.REPO_BRANCH.contains('dev')) {
        echo "Development mode - enabling debug"
        cicdPipeline(
            getConfigStage: true,
            buildStage: true,
            deployStage: false  // No deploy for dev branches
        )
    } else if (vars.REPO_BRANCH.contains('beta')) {
        echo "Beta mode - full pipeline"
        cicdPipeline()
    } else if (vars.REPO_BRANCH == 'main') {
        echo "Production mode - with monitoring"
        cicdPipeline()

        // Additional monitoring for production
        sh "kubectl get pods -n ${vars.NAMESPACE} -l app=${vars.APP_NAME}"
    }
}
```

## Support và Contact

### Jenkins Logs
- Console Output: Check step-by-step execution
- Blue Ocean: Visual pipeline view
- Pipeline Steps: Detailed timing and status

### Kubernetes Logs
```bash
# Pod logs
kubectl logs -f deployment/your-deployment -n your-namespace

# Events
kubectl get events -n your-namespace --sort-by=.metadata.creationTimestamp

# Resource status
kubectl describe deployment your-deployment -n your-namespace
```

### Common Solutions Summary

| Issue | Quick Fix |
|-------|-----------|
| `.env` empty | Check ConfigMap data exists |
| Docker not found | Install Docker on agent |
| Deployment not found | Check deployment exists in namespace |
| Permission denied | Fix RBAC rules |
| Registry auth failed | Configure Docker login |
| Library not found | Check @Library declaration |
| Variable conflicts | Use latest library version |
| Build timeout | Optimize Dockerfile layers |