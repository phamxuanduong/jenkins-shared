# cicdPipeline

Thực hiện toàn bộ quy trình CI/CD trong một hàm duy nhất. Đây là high-level function tích hợp tất cả các bước: lấy ConfigMap, build Docker image, và deploy.

## Tham số

**Tất cả tham số đều tùy chọn:**
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)
- `getConfigStage`: Bật/tắt stage lấy ConfigMap (mặc định: true)
- `buildStage`: Bật/tắt stage build & push (mặc định: true)
- `deployStage`: Bật/tắt stage deploy (mặc định: true)
- `getConfigStageName`: Tên stage lấy config (mặc định: 'K8s get Configmap')
- `buildStageName`: Tên stage build (mặc định: 'Build & Push')
- `deployStageName`: Tên stage deploy (mặc định: 'Deploy')

## Quy trình tự động

### 1. K8s get Configmap (Optional)
- Lấy Dockerfile và .env từ ConfigMap `general` và branch-specific
- Sử dụng function `k8sGetConfig()`
- Files từ branch ConfigMap override files từ general

### 2. Build & Push
- Build Docker image với Dockerfile từ ConfigMap (hoặc repo)
- Tag image với commit hash
- Push lên registry được auto-select theo branch
- Sử dụng function `dockerBuildPush()`

### 3. Deploy
- Cập nhật Kubernetes deployment với image mới
- Sử dụng deployment name và namespace được auto-detect
- Sử dụng function `k8sSetImage()`

## Implementation Details

### Stage Management
```groovy
if (getConfigStage) {
    stage(getConfigStageName) {
        k8sGetConfig(vars: vars)
    }
}

if (buildStage) {
    stage(buildStageName) {
        dockerBuildPush(vars: vars)
    }
}

if (deployStage) {
    stage(deployStageName) {
        k8sSetImage(vars: vars)
    }
}
```

### Error Handling
- Nếu một stage fail, các stage sau sẽ bị skip
- Mỗi stage có independent error handling
- Logs chi tiết cho debugging

### Variable Passing
- Gọi `getProjectVars()` một lần và pass cho tất cả stages
- Ensures consistency across all operations
- Có thể override bằng `vars` parameter

## Ví dụ sử dụng

### Hoàn toàn tự động
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

### Chỉ build và deploy (skip ConfigMap)
```groovy
cicdPipeline(getConfigStage: false)
```

### Custom stage names
```groovy
cicdPipeline(
    getConfigStageName: 'Fetch Configuration',
    buildStageName: 'Docker Build & Push',
    deployStageName: 'Kubernetes Deploy'
)
```

### Với custom vars
```groovy
script {
    def customVars = getProjectVars(
        registry: 'custom-registry.com',
        namespace: 'production'
    )

    cicdPipeline(vars: customVars)
}
```

### Conditional stages
```groovy
// Skip deploy for feature branches
script {
    def vars = getProjectVars()
    def shouldDeploy = vars.REPO_BRANCH.contains('main') || vars.REPO_BRANCH.contains('beta')

    cicdPipeline(
        deployStage: shouldDeploy,
        buildStageName: shouldDeploy ? 'Build & Push' : 'Build Only'
    )
}
```

## Equivalent Manual Implementation

Function `cicdPipeline()` tương đương với:

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

## Use Cases

### Simple Projects
- Microservices với standard workflow
- APIs với Docker deployment
- Web applications với static configuration

### Complex Projects
```groovy
// Multi-environment deployment
script {
    def environments = ['staging', 'production']

    environments.each { env ->
        cicdPipeline(
            vars: getProjectVars(namespace: env),
            deployStageName: "Deploy to ${env}"
        )
    }
}
```

### Testing Integration
```groovy
pipeline {
  agent { label 'test' }

  stages {
    stage('Tests') {
      steps {
        script {
          sh 'npm test'
          sh 'npm run lint'
        }
      }
    }

    stage('CI/CD') {
      when {
        anyOf {
          branch 'main'
          branch 'beta/*'
        }
      }
      steps {
        script {
          cicdPipeline()
        }
      }
    }
  }
}
```

## Best Practices

### Agent Selection
```groovy
pipeline {
  agent {
    label 'docker-enabled'  // Ensure Docker is available
  }
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

### Error Handling
```groovy
pipeline {
  stages {
    stage('CI/CD') {
      steps {
        script {
          try {
            cicdPipeline()
          } catch (Exception e) {
            echo "CI/CD failed: ${e.getMessage()}"
            currentBuild.result = 'FAILURE'
            throw e
          }
        }
      }
    }
  }

  post {
    failure {
      emailext subject: "Build Failed: ${env.JOB_NAME}",
               body: "Build failed for ${env.BUILD_URL}"
    }
  }
}
```

### Parallel Deployments
```groovy
// NOT recommended with cicdPipeline - use manual stages instead
parallel {
  "Deploy API": {
    cicdPipeline(vars: getProjectVars(appName: 'api'))
  },
  "Deploy Worker": {
    cicdPipeline(vars: getProjectVars(appName: 'worker'))
  }
}
```

### Branch-specific Behavior
```groovy
script {
    def vars = getProjectVars()

    if (vars.REPO_BRANCH.startsWith('feature/')) {
        // Feature branches: build only, no deploy
        cicdPipeline(deployStage: false)
    } else if (vars.REPO_BRANCH.contains('beta')) {
        // Beta branches: full CI/CD to beta environment
        cicdPipeline()
    } else if (vars.REPO_BRANCH == 'main') {
        // Main branch: deploy to production
        cicdPipeline(vars: getProjectVars(registry: env.REGISTRY_PROD))
    }
}
```

## Troubleshooting

### Stage skipped unexpectedly
**Nguyên nhân**: Previous stage failed
**Giải pháp**: Check logs của stage trước đó

### ConfigMap stage fails
**Nguyên nhân**: ConfigMap không tồn tại hoặc permission issues
**Giải pháp**:
```groovy
// Skip ConfigMap stage nếu không cần
cicdPipeline(getConfigStage: false)

// Hoặc check ConfigMap exists
sh 'kubectl get configmap general -n your-namespace'
```

### Docker build fails
**Nguyên nhân**: Docker không available trên agent
**Giải pháp**: Ensure agent có Docker installed và running

### Deploy stage fails
**Nguyên nhân**: Deployment không tồn tại hoặc kubectl permission
**Giải pháp**: Check Kubernetes access và deployment existence

### Function not found
**Nguyên nhân**: Library chưa được load hoặc version cũ
**Giải pháp**:
```groovy
@Library('jenkins-shared@main') _  // Ensure latest version
```