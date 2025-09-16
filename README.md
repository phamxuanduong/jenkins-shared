# Jenkins Shared Library

Thư viện chung Jenkins cung cấp các hàm tiện ích cho CI/CD pipeline.

## Cài đặt

1. Thêm shared library vào Jenkins:
   - Vào **Manage Jenkins** > **Configure System**
   - Tìm phần **Global Pipeline Libraries**
   - Thêm library với tên `jenkins-shared`
   - Cấu hình Git repository URL

2. Hoặc cấu hình trong Jenkinsfile:
```groovy
@Library('jenkins-shared') _
```

## Các hàm có sẵn

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
- DEPLOYMENT = REPO_NAME-REPO_BRANCH (ví dụ: `hyra-one-base-api-main`)
- APP_NAME = REPO_NAME
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

### deployPipeline
Pipeline hoàn chỉnh với cấu hình tập trung (legacy).

**Tham số:**
- `appName` (bắt buộc): Tên ứng dụng
- `registry` (bắt buộc): Docker registry URL
- `namespace` (bắt buộc): Kubernetes namespace
- `dockerfile` (tùy chọn): Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context` (tùy chọn): Build context (mặc định: '.')
- `deploymentName` (tùy chọn): Tên deployment (mặc định: '{appName}-deployment')
- `commitHash` (tùy chọn): Git commit hash (mặc định: env.GIT_COMMIT?.take(7))
- `buildStage` (tùy chọn): Bật/tắt build stage (mặc định: true)
- `deployStage` (tùy chọn): Bật/tắt deploy stage (mặc định: true)

**Ví dụ:**
```groovy
@Library('jenkins-shared') _

deployPipeline([
    appName: 'hyra-one-base-api',
    registry: '172.16.3.0/mtw',
    namespace: 'hyra-one-base-api',
    deploymentName: 'hyra-one-base-api-beta-api'
])
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

### Cách 2: Sử dụng deployPipeline (Legacy)
```groovy
@Library('jenkins-shared') _

deployPipeline([
    appName: 'hyra-one-base-api',
    registry: '172.16.3.0/mtw',
    namespace: 'hyra-one-base-api',
    deploymentName: 'hyra-one-base-api-beta-api'
])
```

### Cách 3: Sử dụng getProjectVars (Linh hoạt)
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

### Cách 4: Hoàn toàn tự động (Đơn giản nhất)
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

## Setup Jenkins Environment Variables

Cần thiết lập các biến môi trường trong Jenkins:

```bash
# Jenkins Global Environment Variables
REGISTRY_BETA=registry-beta.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry-prod.company.com
```

## Workflow tiêu chuẩn

1. **Build**: Xây dựng Docker image với commit hash làm tag
2. **Push**: Đẩy image lên registry phù hợp với branch
3. **Deploy**: Cập nhật Kubernetes deployment với image mới

**Registry Selection Logic:**
- `develop`, `dev-*`, `beta`, `beta-*` → `REGISTRY_BETA`
- `staging`, `staging-*` → `REGISTRY_STAGING`
- `main`, `master`, `prod`, `production` → `REGISTRY_PROD`
- Các branch khác → `REGISTRY_BETA` (fallback)

Tất cả các hàm đều có error handling và logging chi tiết để dễ debug.