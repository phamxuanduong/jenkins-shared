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

**Tham số:**
- `image` (bắt buộc): Tên image
- `tag` (bắt buộc): Tag cho image
- `dockerfile` (tùy chọn): Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context` (tùy chọn): Build context (mặc định: '.')

**Ví dụ:**
```groovy
dockerBuild(
    image: '172.16.3.0/mtw/my-app',
    tag: env.GIT_COMMIT?.take(7),
    dockerfile: 'docker/Dockerfile',
    context: '.'
)
```

### dockerPush
Đẩy Docker image lên registry.

**Tham số:**
- `image` (bắt buộc): Tên image
- `tag` (bắt buộc): Tag của image

**Ví dụ:**
```groovy
dockerPush(
    image: '172.16.3.0/mtw/my-app',
    tag: env.GIT_COMMIT?.take(7)
)
```

### dockerBuildPush
Xây dựng và đẩy Docker image trong một bước.

**Tham số:**
- `image` (bắt buộc): Tên image
- `tag` (bắt buộc): Tag cho image
- `dockerfile` (tùy chọn): Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context` (tùy chọn): Build context (mặc định: '.')

**Ví dụ:**
```groovy
dockerBuildPush(
    image: '172.16.3.0/mtw/my-app',
    tag: env.GIT_COMMIT?.take(7),
    dockerfile: 'docker/Dockerfile',
    context: '.'
)
```

### k8sSetImage
Cập nhật image cho Kubernetes deployment.

**Tham số:**
- `deployment` (bắt buộc): Tên deployment
- `image` (bắt buộc): Tên image mới
- `tag` (bắt buộc): Tag của image
- `namespace` (bắt buộc): Kubernetes namespace
- `container` (tùy chọn): Tên container cụ thể (mặc định: '*' - tất cả containers)

**Ví dụ:**
```groovy
k8sSetImage(
    deployment: 'hyra-one-base-api-beta-api',
    image: '172.16.3.0/mtw/hyra-one-base-api-beta-api',
    tag: env.GIT_COMMIT?.take(7),
    namespace: 'hyra-one-base-api'
)
```

### k8sGetConfig
Lấy dữ liệu từ Kubernetes ConfigMap và lưu vào file.

**Tham số:**
- `namespace` (bắt buộc): Kubernetes namespace
- `configmap` (bắt buộc): Tên ConfigMap
- `items` (bắt buộc): Map các key và đường dẫn file đích

**Ví dụ:**
```groovy
k8sGetConfig(
    namespace: 'my-namespace',
    configmap: 'app-config',
    items: [
        'Dockerfile': 'build/images/base/Dockerfile',
        '.env': 'deploy/dev/.env'
    ]
)
```

## Jenkinsfile hoàn chỉnh

```groovy
@Library('jenkins-shared') _

pipeline {
    agent any

    environment {
        REGISTRY = '172.16.3.0/mtw'
        APP_NAME = 'hyra-one-base-api'
        COMMIT_HASH = "${env.GIT_COMMIT?.take(7)}"
    }

    stages {
        stage('Build') {
            steps {
                dockerBuild(
                    image: "${REGISTRY}/${APP_NAME}",
                    tag: "${COMMIT_HASH}"
                )
            }
        }

        stage('Push') {
            steps {
                dockerPush(
                    image: "${REGISTRY}/${APP_NAME}",
                    tag: "${COMMIT_HASH}"
                )
            }
        }

        stage('Deploy') {
            steps {
                k8sSetImage(
                    deployment: "${APP_NAME}-beta-api",
                    image: "${REGISTRY}/${APP_NAME}-beta-api",
                    tag: "${COMMIT_HASH}",
                    namespace: "${APP_NAME}"
                )
            }
        }
    }
}
```

## Workflow tiêu chuẩn

1. **Build**: Xây dựng Docker image với commit hash làm tag
2. **Push**: Đẩy image lên private registry
3. **Deploy**: Cập nhật Kubernetes deployment với image mới

Tất cả các hàm đều có error handling và logging chi tiết để dễ debug.