# Jenkins Shared Library

Modern Jenkins shared library cho CI/CD automation với kiến trúc modular và zero-configuration.

## 🚀 Quick Start

### Cách đơn giản nhất - 2 dòng code:

```groovy
@Library('jenkins-shared@main') _
ci()
```

Chỉ vậy thôi! Pipeline sẽ tự động:
- ✅ Setup project variables từ Git
- ✅ Kiểm tra GitHub permissions (nếu có)
- ✅ Lấy ConfigMaps từ Kubernetes
- ✅ Build và push Docker image
- ✅ Deploy lên Kubernetes
- ✅ Gửi thông báo Telegram

## 📚 Cài đặt

### Cách 1: Global Pipeline Library (Khuyến nghị)

1. Vào **Manage Jenkins** > **Configure System**
2. Tìm **Global Pipeline Libraries**
3. Thêm library:
   - Name: `jenkins-shared`
   - Default version: `main`
   - Retrieval method: Modern SCM
   - Source Code Management: Git
   - Project Repository: `https://github.com/phamxuanduong/jenkins-shared.git`

### Cách 2: Project-level (Trong Jenkinsfile)

```groovy
@Library('jenkins-shared@main') _
```

## 🎯 Core Functions

### `ci()` - Complete CI/CD Pipeline

Hàm wrapper hoàn chỉnh orchestrate toàn bộ pipeline.

```groovy
// Đơn giản nhất
@Library('jenkins-shared@main') _
ci()

// Với custom steps
@Library('jenkins-shared@main') _
ci([
  beforeBuild: {
    sh 'npm install'
    sh 'npm test'
  },
  afterDeploy: {
    sh 'kubectl rollout status deployment/my-app'
  }
])

// Skip các bước không cần
@Library('jenkins-shared@main') _
ci([
  skipConfigMap: true,  // Không cần ConfigMap
  skipDeploy: true      // Chỉ build, không deploy
])
```

**Tham số:**
- `agent`: Jenkins agent label (mặc định: auto-detect từ branch)
- `skipConfigMap`: Skip k8sGetConfigMap (mặc định: false)
- `skipBuild`: Skip Docker build (mặc định: false)
- `skipPush`: Skip Docker push (mặc định: false)
- `skipDeploy`: Skip k8sSetImage (mặc định: false)
- `skipNotification`: Skip Telegram (mặc định: false)
- `beforeBuild`: Closure chạy trước build
- `afterDeploy`: Closure chạy sau deploy

### `pipelineSetup()` - Project Initialization

Setup project variables và kiểm tra permissions.

```groovy
def vars = pipelineSetup()

echo "Deploying ${vars.APP_NAME} to ${vars.NAMESPACE}"
```

**Auto-detected variables:**
- `REPO_NAME`: Tên repository từ Git URL
- `REPO_BRANCH`: Tên branch hiện tại
- `SANITIZED_BRANCH`: Branch name sanitized cho K8s
- `NAMESPACE`: Kubernetes namespace (= REPO_NAME)
- `DEPLOYMENT`: Deployment name (= REPO_NAME-SANITIZED_BRANCH)
- `APP_NAME`: Application name (= REPO_NAME-SANITIZED_BRANCH)
- `REGISTRY`: Docker registry (auto-select theo branch)
- `COMMIT_HASH`: Git commit hash (7 ký tự đầu)
- `GIT_USER`: Git user từ commit
- `CAN_DEPLOY`: Permission check result
- `COMMIT_MESSAGE`: Commit message

### `dockerBuildImage()` - Build Docker Image

```groovy
// Auto-detect tất cả
dockerBuildImage()

// Custom dockerfile
dockerBuildImage(dockerfile: 'docker/Dockerfile.prod')

// Full custom
dockerBuildImage(
  image: 'my-registry/my-app',
  tag: 'v1.0.0',
  dockerfile: 'Dockerfile',
  context: '.'
)
```

### `dockerPushImage()` - Push Docker Image

```groovy
// Auto-detect
dockerPushImage()

// Custom
dockerPushImage(
  image: 'my-registry/my-app',
  tag: 'v1.0.0'
)
```

### `k8sGetConfigMap()` - Fetch ConfigMaps & Secrets

Lấy config từ Kubernetes ConfigMaps và Secrets với logic ưu tiên.

**Logic mới (backward compatible):**
- **Step 1**: Lấy TẤT CẢ files từ ConfigMaps (Dockerfile, .env, nginx.conf, etc.)
- **Step 2**: Lấy TẤT CẢ files từ Secret (nếu có) → **OVERRIDE** files từ ConfigMap

**Ưu tiên:**
- ✅ Secret có ưu tiên cao hơn → files từ Secret sẽ override files từ ConfigMap
- ✅ Fallback: Nếu Secret không có → dùng files từ ConfigMap (dự án cũ)

**Naming:**
- Secret name **GIỐNG** ConfigMap name (beta, beta-api, prod-worker, etc.)
- Secret data keys: `.env`, `application.properties`, `database.yml`, etc.

```groovy
// Auto-fetch (khuyến nghị)
k8sGetConfigMap()

// Custom Secret name
k8sGetConfigMap(
  secret: 'my-secret'
)

// Skip Secret (chỉ dùng ConfigMap - cho dự án cũ)
k8sGetConfigMap(
  skipSecret: true
)
```

**Ví dụ cấu trúc:**

**Dự án cũ - Chỉ dùng ConfigMap:**
```yaml
# ConfigMap 'beta' chứa TẤT CẢ files
apiVersion: v1
kind: ConfigMap
metadata:
  name: beta
  namespace: my-app
data:
  .env: |
    NODE_ENV=beta
    DATABASE_URL=postgres://beta-db
  Dockerfile: |
    FROM node:18
    COPY . /app
    CMD ["npm", "start"]
  application.properties: |
    server.port=8080
```

**Dự án mới - ConfigMap + Secret:**
```yaml
# ConfigMap 'beta' chứa Dockerfile, nginx.conf (files không nhạy cảm)
apiVersion: v1
kind: ConfigMap
metadata:
  name: beta
  namespace: my-app
data:
  Dockerfile: |
    FROM node:18
    COPY . /app
    CMD ["npm", "start"]
  nginx.conf: |
    server {
      listen 80;
    }

---
# Secret 'beta' (TÊN GIỐNG ConfigMap) chứa sensitive files
apiVersion: v1
kind: Secret
metadata:
  name: beta  # ← TÊN GIỐNG ConfigMap
  namespace: my-app
type: Opaque
data:
  .env: Tk9ERV9FTlY9YmV0YQpEQVRBQkFTRV9VUkw9cG9zdGdyZXM6Ly9iZXRhLWRiCkpXVF9TRUNSRVQ9c3VwZXItc2VjcmV0LWtleQ==
  application.properties: c2VydmVyLnBvcnQ9ODA4MApkYi51c2VybmFtZT1hZG1pbgpkYi5wYXNzd29yZD1zZWNyZXQ=
  # Base64 encoded:
  # .env: NODE_ENV=beta, DATABASE_URL=..., JWT_SECRET=...
  # application.properties: server.port=8080, db.username=admin, db.password=secret
```

**Ví dụ với branch suffix:**
```yaml
# Branch: beta/api → ConfigMap 'beta-api', Secret 'beta-api'
# Branch: beta/worker → ConfigMap 'beta-worker', Secret 'beta-worker'

# ConfigMap 'beta-api'
apiVersion: v1
kind: ConfigMap
metadata:
  name: beta-api
data:
  Dockerfile: |
    FROM node:18
    CMD ["npm", "run", "start:api"]

---
# Secret 'beta-api' (TÊN GIỐNG ConfigMap)
apiVersion: v1
kind: Secret
metadata:
  name: beta-api
data:
  .env: <base64-encoded-env-for-api>
```

**Migration path từ cũ sang mới:**
1. ✅ Giữ nguyên ConfigMap (dự án vẫn chạy bình thường)
2. ✅ Tạo Secret cùng tên với ConfigMap
3. ✅ Pipeline tự động ưu tiên Secret (override ConfigMap)
4. ✅ Optional: Xóa sensitive files khỏi ConfigMap

### `k8sSetImage()` - Deploy to Kubernetes

```groovy
// Auto-detect deployment
k8sSetImage()

// Custom deployment
k8sSetImage(
  deployment: 'my-app-prod',
  image: 'registry/my-app',
  tag: 'v1.0.0',
  namespace: 'production'
)
```

### `k8sGetIngress()` - Get Ingress Info

```groovy
// Get domains as comma-separated string
def domains = k8sGetIngress()
echo "Domains: ${domains}"  // api.example.com, www.example.com

// Get hosts as array
def hosts = k8sGetIngress(outputFormat: 'hosts')

// Get full YAML
def yaml = k8sGetIngress(outputFormat: 'yaml')

// Get as JSON object
def ingress = k8sGetIngress(outputFormat: 'json')
```

### `telegramNotify()` - Telegram Notifications

Tự động gửi thông báo với environment-specific routing.

```groovy
// Auto-generate message từ build info
telegramNotify()

// Custom message
telegramNotify(
  message: "✅ *Deploy Success!*\n\nApp: `my-app`"
)

// Silent notification
telegramNotify(disableNotification: true)
```

**Environment Routing:**
- Branch `beta`, `dev` → `TELEGRAM_BOT_TOKEN_BETA` + `TELEGRAM_CHAT_ID_BETA`
- Branch `staging` → `TELEGRAM_BOT_TOKEN_STAGING` + `TELEGRAM_CHAT_ID_STAGING`
- Branch `main`, `prod` → `TELEGRAM_BOT_TOKEN_PROD` + `TELEGRAM_CHAT_ID_PROD`

## 🔧 Shared Utilities

### `sharedUtils` - Common Helper Functions

Internal utility functions (không cần gọi trực tiếp trong projects):

- `getProjectVars()`: Get all project variables
- `getProjectVarsOptimized()`: Get cached vars from pipelineSetup
- `getUserFromGit()`: Get git user from commit
- `getCommitMessage()`: Get commit message
- `getRepoOwner()`: Extract repo owner from Git URL
- `getRepoName()`: Extract repo name from Git URL
- `escapeMarkdown()`: Escape Telegram markdown
- GitHub API functions: `checkUserPermissions()`, `getBranchProtectionRules()`, `validateDeployPermissions()`

## 📋 Complete Examples

### Example 1: Simple Web App

```groovy
@Library('jenkins-shared@main') _
ci()
```

### Example 2: With Tests

```groovy
@Library('jenkins-shared@main') _
ci([
  beforeBuild: {
    sh 'npm install'
    sh 'npm test'
    sh 'npm run lint'
  }
])
```

### Example 3: Build Only (No Deploy)

```groovy
@Library('jenkins-shared@main') _
ci([
  skipDeploy: true,
  afterDeploy: {
    echo "Image built and pushed: ${env.APP_NAME}:${env.COMMIT_HASH}"
  }
])
```

### Example 4: Custom Agent

```groovy
@Library('jenkins-shared@main') _
ci([
  agent: 'docker',
  skipConfigMap: true
])
```

### Example 5: Manual Pipeline (Full Control)

```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent {
    label "${['beta','staging','prod'].find { p -> env.BRANCH_NAME?.equalsIgnoreCase(p) } ?: ''}"
  }

  stages {
    stage('Setup') {
      steps {
        script {
          def vars = pipelineSetup()
          echo "Deploying ${vars.APP_NAME}"
        }
      }
    }

    stage('Get Config') {
      steps { script { k8sGetConfigMap() } }
    }

    stage('Build') {
      steps { script { dockerBuildImage() } }
    }

    stage('Push') {
      steps { script { dockerPushImage() } }
    }

    stage('Deploy') {
      steps { script { k8sSetImage() } }
    }
  }

  post {
    success { script { telegramNotify() } }
    failure { script { telegramNotify() } }
  }
}
```

## 🔒 Security & Permissions

### GitHub Branch Protection

Tự động kiểm tra permissions khi có `GITHUB_TOKEN`:

**Setup trong Jenkins:**
```bash
# Credentials
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**Setup trong GitHub Repository Variables:**
```
BRANCH_PROTECT_ADMIN=prod,main
BRANCH_PROTECT_MAINTAIN=staging
```

**Scenarios:**
- ✅ Protected branch + Admin user → Deploy allowed
- 🚫 Protected branch + Non-admin user → Deploy blocked + Telegram alert
- ✅ Non-protected branch → Deploy allowed
- ✅ No GitHub token → Deploy allowed (fallback)

### Environment Variables

**Required cho Telegram:**
```bash
# Beta/Dev environment
TELEGRAM_BOT_TOKEN_BETA=123456:ABC...
TELEGRAM_CHAT_ID_BETA=-1001234567890
TELEGRAM_THREAD_ID_BETA=123  # Optional

# Staging environment
TELEGRAM_BOT_TOKEN_STAGING=123456:ABC...
TELEGRAM_CHAT_ID_STAGING=-1001234567890

# Production environment
TELEGRAM_BOT_TOKEN_PROD=123456:ABC...
TELEGRAM_CHAT_ID_PROD=-1001234567890
```

**Optional cho Registry:**
```bash
REGISTRY_BETA=registry-beta.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry-prod.company.com
```

## 🏗️ Architecture

```
vars/
├── ci.groovy                   # Main wrapper - single entry point
├── sharedUtils.groovy          # Shared utilities & GitHub API
├── pipelineSetup.groovy        # Project initialization
├── dockerBuildImage.groovy     # Build Docker image
├── dockerPushImage.groovy      # Push Docker image
├── k8sGetConfigMap.groovy      # Fetch Kubernetes ConfigMaps
├── k8sGetIngress.groovy        # Get Kubernetes Ingress info
├── k8sSetImage.groovy          # Deploy to Kubernetes
├── swarmSetImage.groovy        # Deploy to Docker Swarm
└── telegramNotify.groovy       # Telegram notifications
```

**Design Principles:**
- ✅ **Zero Configuration**: Works out of the box
- ✅ **Single Source of Truth**: All logic centralized
- ✅ **Modular**: Each function has single responsibility
- ✅ **Extensible**: Easy to add custom steps
- ✅ **Maintainable**: Changes in library, not in projects

## 🎓 Best Practices

### 1. Use `ci()` for Standard Workflows

```groovy
@Library('jenkins-shared@main') _
ci()
```

### 2. Add Custom Steps with Hooks

```groovy
@Library('jenkins-shared@main') _
ci([
  beforeBuild: { sh 'npm test' },
  afterDeploy: { sh 'kubectl rollout status deployment/app' }
])
```

### 3. Skip Unnecessary Steps

```groovy
@Library('jenkins-shared@main') _
ci([skipConfigMap: true])  // No ConfigMap needed
```

### 4. Use Manual Pipeline for Complex Workflows

```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'beta' }
  stages {
    stage('Setup') {
      steps { script { pipelineSetup() } }
    }
    // ... your custom stages
  }
}
```

## 🆘 Troubleshooting

### Pipeline Fails at Setup

**Check:**
1. Git repository accessible
2. Branch name correct
3. Jenkins has kubectl access

### Docker Build Fails

**Check:**
1. Dockerfile exists in workspace
2. Docker daemon accessible from agent
3. Registry credentials configured

### Kubernetes Deploy Fails

**Check:**
1. kubectl configured correctly
2. Namespace exists
3. Deployment exists
4. Image pull secrets configured

### Telegram Not Working

**Check:**
1. `TELEGRAM_BOT_TOKEN_*` environment variables set
2. `TELEGRAM_CHAT_ID_*` environment variables set
3. Bot added to chat/channel
4. Bot has permission to send messages

### Permission Check Failing

**Check:**
1. `GITHUB_TOKEN` has correct permissions
2. Repository variables configured correctly
3. User has required permissions on GitHub

## 📝 Migration Guide

### From Old Pipeline to `ci()`

**Before:**
```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'beta' }
  stages {
    stage('Setup') { steps { script { pipelineSetup() } } }
    stage('Build & Deploy') {
      steps {
        script {
          k8sGetConfigMap()
          dockerBuildImage()
          dockerPushImage()
          k8sSetImage()
        }
      }
    }
  }
  post {
    success { script { telegramNotify() } }
    failure { script { telegramNotify() } }
  }
}
```

**After:**
```groovy
@Library('jenkins-shared@main') _
ci()
```

**Benefit:**
- From 30 lines → 2 lines
- No changes needed when library updates
- Consistent across all projects

## 📖 Further Reading

- [Troubleshooting Guide](./docs/troubleshooting.md)
- [Kubernetes Functions](./docs/k8s-functions.md)
- [Docker Functions](./docs/docker-functions.md)
- [Telegram Notifications](./docs/telegramNotify.md)

## 🤝 Contributing

1. Make changes in your branch
2. Test with `@Library('jenkins-shared@your-branch') _`
3. Create PR to `main`
4. All projects automatically get updates

## 📜 License

Internal use only - Metaway Holdings
