# Jenkins Shared Library

Thư viện chung Jenkins cung cấp các hàm tiện ích cho CI/CD pipeline với khả năng tự động hóa hoàn toàn.

## 🚀 Quick Start

### Jenkinsfile đơn giản nhất (1 dòng):
```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'your-agent' }
  stages {
    stage('CI/CD') {
      steps {
        script {
          cicdPipeline()  // Thực hiện toàn bộ CI/CD tự động
        }
      }
    }
  }
}
```

## Cài đặt

1. Thêm shared library vào Jenkins:
   - Vào **Manage Jenkins** > **Configure System**
   - Tìm phần **Global Pipeline Libraries**
   - Thêm library với tên `jenkins-shared`
   - Cấu hình Git repository URL

2. Hoặc cấu hình trong Jenkinsfile:
```groovy
@Library('jenkins-shared@main') _
```

## 🎯 Tính năng chính

- **Hoàn toàn tự động**: Tất cả hàm có thể gọi không cần tham số
- **Dual ConfigMap**: Hỗ trợ ConfigMap `general` (chung) và branch-specific
- **Smart registry**: Tự động chọn registry theo branch pattern
- **Branch sanitization**: Tự động xử lý branch names cho Kubernetes
- **Override linh hoạt**: Có thể override bất kỳ tham số nào khi cần

## 📚 Documentation

### Detailed Documentation
- **[k8sGetConfig](./docs/k8sGetConfig.md)** - ConfigMap management với dual ConfigMap support
- **[getProjectVars](./docs/getProjectVars.md)** - Auto-detect project variables từ Git
- **[cicdPipeline](./docs/cicdPipeline.md)** - One-line CI/CD pipeline function
- **[Docker Functions](./docs/docker-functions.md)** - Build và push Docker images
- **[Kubernetes Functions](./docs/k8s-functions.md)** - Deployment và Kubernetes operations
- **[Troubleshooting](./docs/troubleshooting.md)** - Debug guide và common issues

## Các hàm có sẵn

### cicdPipeline ⭐
**Function chính - thực hiện toàn bộ CI/CD trong 1 lần gọi**

```groovy
// Siêu đơn giản - chỉ 1 dòng!
cicdPipeline()

// Với customization
cicdPipeline(
    getConfigStage: true,     // Lấy ConfigMap
    buildStage: true,         // Build & push Docker
    deployStage: true         // Deploy to K8s
)
```

### dockerBuild
Xây dựng Docker image.

**Tham số (tất cả tùy chọn):**
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag cho image (mặc định: tự động từ commit hash)
- `dockerfile`: Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context`: Build context (mặc định: '.')
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

**Ví dụ:**
```groovy
// Hoàn toàn tự động
dockerBuild()

// Chỉ định dockerfile khác
dockerBuild(dockerfile: 'docker/Dockerfile')

// Custom image và tag
dockerBuild(
    image: '172.16.3.0/mtw/my-app',
    tag: 'v1.0.0'
)
```

### dockerPush
Đẩy Docker image lên registry.

**Tham số (tất cả tùy chọn):**
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag của image (mặc định: tự động từ commit hash)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

**Ví dụ:**
```groovy
// Hoàn toàn tự động
dockerPush()

// Custom image và tag
dockerPush(
    image: '172.16.3.0/mtw/my-app',
    tag: 'v1.0.0'
)
```

### dockerBuildPush
Xây dựng và đẩy Docker image trong một bước.

**Tham số (tất cả tùy chọn):**
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag cho image (mặc định: tự động từ commit hash)
- `dockerfile`: Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context`: Build context (mặc định: '.')
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

**Ví dụ:**
```groovy
// Hoàn toàn tự động
dockerBuildPush()

// Chỉ định dockerfile khác
dockerBuildPush(dockerfile: 'docker/Dockerfile')

// Custom image và tag
dockerBuildPush(
    image: '172.16.3.0/mtw/my-app',
    tag: 'v1.0.0'
)
```

### k8sSetImage
Cập nhật image cho Kubernetes deployment.

**Tham số (tất cả tùy chọn):**
- `deployment`: Tên deployment (mặc định: tự động từ `getProjectVars()`)
- `image`: Tên image mới (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag của image (mặc định: tự động từ commit hash)
- `namespace`: Kubernetes namespace (mặc định: tự động từ repo name)
- `container`: Tên container cụ thể (mặc định: '*' - tất cả containers)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

**Ví dụ:**
```groovy
// Hoàn toàn tự động
k8sSetImage()

// Custom deployment name
k8sSetImage(deployment: 'my-custom-deployment')

// Đầy đủ custom
k8sSetImage(
    deployment: 'hyra-one-base-api-beta-api',
    image: '172.16.3.0/mtw/hyra-one-base-api-beta-api',
    tag: 'v1.0.0',
    namespace: 'hyra-one-base-api'
)
```

### k8sGetConfig
Lấy dữ liệu từ Kubernetes ConfigMap và lưu vào file. Tự động lấy từ 2 ConfigMaps: `general` (chung) và branch-specific.

**Tham số (tất cả tùy chọn):**
- `namespace`: Kubernetes namespace (mặc định: tự động từ `getProjectVars().NAMESPACE`)
- `configmap`: Tên ConfigMap theo branch (mặc định: tự động từ `getProjectVars().SANITIZED_BRANCH`)
- `generalConfigmap`: Tên ConfigMap chung (mặc định: 'general')
- `items`: Map các key và đường dẫn file đích (mặc định: lấy tất cả keys từ cả 2 ConfigMaps)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

**Mặc định tự động:**
- `namespace` = REPO_NAME (từ Git URL)
- `generalConfigmap` = 'general' (chứa files dùng chung cho tất cả branches)
- `configmap` = SANITIZED_BRANCH (chứa files riêng cho branch này)
- Lấy tất cả data từ cả 2 ConfigMaps
- Files từ branch ConfigMap sẽ override files từ general ConfigMap (nếu cùng tên)
- Tự động bỏ qua nếu ConfigMap/key không tồn tại

**Workflow:**
1. Lấy tất cả files từ ConfigMap `general` (files dùng chung)
2. Lấy tất cả files từ ConfigMap theo branch (files riêng cho branch)
3. Files từ branch sẽ ghi đè lên files chung nếu trùng tên

**Implementation Details:**
- **Key extraction**: Sử dụng `kubectl -o yaml` + `awk` để parse ConfigMap keys
- **Special characters**: Hỗ trợ keys có dấu chấm (`.env`) và ký tự đặc biệt
- **Data fetching**: Dùng `go-template` với `index` function cho keys có ký tự đặc biệt
- **Error handling**: Graceful skip nếu ConfigMap/key không tồn tại
- **Multi-line support**: Hỗ trợ content nhiều dòng từ ConfigMap

**Troubleshooting:**
- Nếu `.env` báo empty: Check ConfigMap có data với `kubectl describe configmap`
- Nếu keys không được detect: Check YAML format với `kubectl get cm -o yaml`
- Nếu special keys fail: Function dùng `go-template index` để handle tất cả key types

**Ví dụ:**
```groovy
// Hoàn toàn tự động - lấy từ 'general' và branch hiện tại
k8sGetConfig()

// Custom general ConfigMap name
k8sGetConfig(generalConfigmap: 'shared')

// Chỉ định specific items
k8sGetConfig(
    items: [
        'Dockerfile': 'Dockerfile',
        '.env': '.env',
        'config.yaml': 'config/app.yaml'
    ]
)

// Full custom
k8sGetConfig(
    namespace: 'my-namespace',
    generalConfigmap: 'shared-config',
    configmap: 'beta-config',
    items: [
        'Dockerfile': 'build/Dockerfile',
        '.env': 'deploy/.env'
    ]
)
```

**Ví dụ ConfigMap setup:**
```yaml
# ConfigMap general (shared)
apiVersion: v1
kind: ConfigMap
metadata:
  name: general
  namespace: my-app
data:
  .env: |
    ENV=production
    DATABASE_CONNECTION=mysql
  docker-compose.yml: |
    version: '3'
    services: ...

# ConfigMap beta-api (branch-specific)
apiVersion: v1
kind: ConfigMap
metadata:
  name: beta-api
  namespace: my-app
data:
  Dockerfile: |
    FROM node:18
    COPY . /app
  .env: |
    ENV=beta
    DEBUG=true
    # Override .env từ general
```

### getProjectVars
Lấy các biến dự án tự động từ Git và environment, có thể override.

**Tham số (tất cả tùy chọn):**
- `repoName`: Tên repository (mặc định: tự động từ GIT_URL)
- `repoBranch`: Tên branch (mặc định: tự động từ GIT_BRANCH)
- `namespace`: Kubernetes namespace (mặc định: repoName)
- `deployment`: Tên deployment (mặc định: "{repoName}-{repoBranch}")
- `appName`: Tên ứng dụng (mặc định: repoName)
- `registry`: Docker registry (mặc định: env.DOCKER_REGISTRY hoặc '172.16.3.0/mtw')
- `commitHash`: Git commit hash (mặc định: env.GIT_COMMIT?.take(7))

**Mặc định tự động:**
- NAMESPACE = REPO_NAME (ví dụ: `hyra-one-base-api`)
- DEPLOYMENT = REPO_NAME-SANITIZED_BRANCH (ví dụ: `hyra-one-base-api-beta-api`)
- APP_NAME = REPO_NAME-SANITIZED_BRANCH (ví dụ: `hyra-one-base-api-beta-api`)
- REGISTRY tự động chọn theo branch:
  - Branch chứa `dev`, `beta` → `env.REGISTRY_BETA`
  - Branch chứa `staging` → `env.REGISTRY_STAGING`
  - Branch chứa `main`, `master`, `prod`, `production` → `env.REGISTRY_PROD`
  - Các branch khác → `env.REGISTRY_BETA` (mặc định)

**Trả về:** Map chứa các biến: REPO_NAME, REPO_BRANCH, SANITIZED_BRANCH, NAMESPACE, DEPLOYMENT, APP_NAME, REGISTRY, COMMIT_HASH

**Ví dụ:**
```groovy
script {
    def vars = getProjectVars()

    // Tự động từ Git:
    // NAMESPACE = hyra-one-base-api (repo name)
    // DEPLOYMENT = hyra-one-base-api-main (repo-branch)
    // REGISTRY tự động chọn:
    //   - main branch → env.REGISTRY_PROD
    //   - develop branch → env.REGISTRY_BETA
    //   - staging branch → env.REGISTRY_STAGING

    dockerBuildPush(
        image: "${vars.REGISTRY}/${vars.APP_NAME}",
        tag: vars.COMMIT_HASH
    )

    k8sSetImage(
        deployment: vars.DEPLOYMENT,
        image: "${vars.REGISTRY}/${vars.APP_NAME}",
        tag: vars.COMMIT_HASH,
        namespace: vars.NAMESPACE
    )
}
```

### cicdPipeline
Thực hiện toàn bộ quy trình CI/CD trong một hàm duy nhất.

**Tham số (tất cả tùy chọn):**
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)
- `getConfigStage`: Bật/tắt stage lấy ConfigMap (mặc định: true)
- `buildStage`: Bật/tắt stage build & push (mặc định: true)
- `deployStage`: Bật/tắt stage deploy (mặc định: true)
- `getConfigStageName`: Tên stage lấy config (mặc định: 'K8s get Configmap')
- `buildStageName`: Tên stage build (mặc định: 'Build & Push')
- `deployStageName`: Tên stage deploy (mặc định: 'Deploy')

**Quy trình tự động:**
1. **K8s get Configmap**: Lấy Dockerfile và .env từ ConfigMap theo branch
2. **Build & Push**: Build Docker image và push lên registry
3. **Deploy**: Cập nhật Kubernetes deployment với image mới

**Ví dụ:**
```groovy
// Hoàn toàn tự động - thực hiện toàn bộ CI/CD
cicdPipeline()

// Chỉ build và deploy (bỏ qua lấy config)
cicdPipeline(getConfigStage: false)

// Custom stage names
cicdPipeline(
    buildStageName: 'Docker Build & Push',
    deployStageName: 'Kubernetes Deploy'
)
```


## Jenkinsfile hoàn chỉnh

### Cách 1: Sử dụng cicdPipeline (Siêu đơn giản)
```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'beta' }

  stages {
    stage('CI/CD') {
      steps {
        script {
          cicdPipeline()
        }
      }
    }
  }
}
```


### Cách 2: Sử dụng getProjectVars (Linh hoạt)
```groovy
@Library('jenkins-shared@main') _

def VARS

pipeline {
  agent { label 'beta' }

  stages {
    stage('Setup') {
      steps {
        script {
          VARS = getProjectVars()
        }
      }
    }

    stage('K8s get Configmap') {
      steps {
        script {
          k8sGetConfig(vars: VARS)
        }
      }
    }

    stage('Build & Push') {
      steps {
        script {
          dockerBuildPush(vars: VARS)
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          k8sSetImage(vars: VARS)
        }
      }
    }
  }
}
```

### Cách 3: Hoàn toàn tự động (Đơn giản nhất)
```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'beta' }

  stages {
    stage('K8s get Configmap') {
      steps {
        script {
          k8sGetConfig()
        }
      }
    }

    stage('Build & Push') {
      steps {
        script {
          dockerBuildPush()
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          k8sSetImage()
        }
      }
    }
  }
}
```

## 🔧 Setup Jenkins Environment Variables

Cần thiết lập các biến môi trường trong Jenkins:

```bash
# Jenkins Global Environment Variables
REGISTRY_BETA=registry-beta.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry-prod.company.com
```

## 🚀 Workflow tiêu chuẩn

### Automatic CI/CD Flow
1. **Setup**: Auto-detect project info từ Git (repo, branch, commit)
2. **ConfigMap**: Lấy configs từ `general` + branch-specific ConfigMaps
3. **Build**: Build Docker image với auto-generated name và commit tag
4. **Push**: Push lên registry được auto-select theo branch pattern
5. **Deploy**: Update K8s deployment với image mới

### Registry Selection Logic
- `develop`, `dev-*`, `beta`, `beta-*` → `REGISTRY_BETA`
- `staging`, `staging-*` → `REGISTRY_STAGING`
- `main`, `master`, `prod`, `production` → `REGISTRY_PROD`
- Các branch khác → `REGISTRY_BETA` (fallback)

### Branch Sanitization
- `beta/api` → `beta-api` (K8s compatible)
- `feature/user-auth` → `feature-user-auth`
- Tự động handle special characters

## 🔍 Key Features

- **Zero Configuration**: Tất cả hàm hoạt động mà không cần parameters
- **Smart Defaults**: Auto-detect mọi thứ từ Git và environment
- **Dual ConfigMap**: Support shared + branch-specific configurations
- **Error Resilient**: Graceful handling của missing resources
- **Debug Friendly**: Chi tiết logs và troubleshooting guides
- **Kubernetes Native**: Tuân thủ K8s naming conventions

## 🆘 Need Help?

1. **Check logs**: Tất cả functions có detailed logging
2. **Read docs**: Chi tiết documentation cho mỗi function
3. **Debug guide**: [Troubleshooting](./docs/troubleshooting.md) cho common issues
4. **Test individual**: Có thể test từng function riêng lẻ

**Note về `.env` issue đã fix**:
- Function hiện sử dụng `go-template index` để handle keys có dấu chấm
- ConfigMap parsing dùng YAML + awk thay vì complex regex
- Debug output không interfere với key extraction