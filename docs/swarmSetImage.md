# swarmSetImage

Cập nhật Docker Swarm service với image mới. Function tự động detect service name theo pattern `reponame_repobranch`.

## Tham số

**Tất cả tham số đều tùy chọn:**
- `service`: Tên service (mặc định: `{REPO_NAME}_{SANITIZED_BRANCH}` từ getProjectVars)
- `image`: Docker image name (mặc định: auto từ getProjectVars)
- `tag`: Image tag (mặc định: commit hash)
- `context`: Docker context name (mặc định: 'docker-swarm')
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

## Service Naming Convention

Function sử dụng naming pattern của Docker Swarm:
```
Service Name = {REPO_NAME}_{SANITIZED_BRANCH}
```

### Examples:
```bash
# Repository: my-app, Branch: beta/api
Service Name: my-app_beta-api

# Repository: user-service, Branch: main
Service Name: user-service_main

# Repository: payment-gateway, Branch: prod/worker-data
Service Name: payment-gateway_prod-worker-data
```

## Required Setup

### Docker Context Configuration
```bash
# Tạo Docker context cho Swarm cluster
docker context create docker-swarm \
  --docker "host=tcp://swarm-manager:2376,ca=ca.pem,cert=cert.pem,key=key.pem"

# Hoặc sử dụng SSH
docker context create docker-swarm \
  --docker "host=ssh://user@swarm-manager"

# Verify context
docker context ls
docker --context docker-swarm node ls
```

### Registry Authentication

**Private Registry Setup:**
Function tự động sử dụng `--with-registry-auth` flag để Swarm nodes có thể pull từ private registries.

```bash
# Đảm bảo Docker daemon trên manager node đã login
docker login 192.168.1.10

# Hoặc login với credentials
echo "password" | docker login 192.168.1.10 -u username --password-stdin

# Verify registry access
docker pull 192.168.1.10/your-image:tag
```

**Jenkins Setup:**
```groovy
// Login to registry trong Jenkins pipeline
withCredentials([usernamePassword(
    credentialsId: 'docker-registry-creds',
    usernameVariable: 'DOCKER_USER',
    passwordVariable: 'DOCKER_PASS'
)]) {
    sh """
    echo \$DOCKER_PASS | docker --context docker-swarm login 192.168.1.10 -u \$DOCKER_USER --password-stdin
    """

    // Sau đó deploy
    swarmSetImage()
}

## Ví dụ sử dụng

### Basic usage (auto-detect)
```groovy
script {
    try {
        // Build và push image trước
        dockerBuildPush()

        // Update Swarm service tự động (với registry auth)
        swarmSetImage()

    } catch (Exception e) {
        echo "Deployment failed: ${e.getMessage()}"
        throw e
    }
}
```

### With registry authentication
```groovy
script {
    withCredentials([usernamePassword(
        credentialsId: 'docker-registry-creds',
        usernameVariable: 'DOCKER_USER',
        passwordVariable: 'DOCKER_PASS'
    )]) {
        // Login to private registry
        sh """
        echo \$DOCKER_PASS | docker --context docker-swarm login 192.168.1.10 -u \$DOCKER_USER --password-stdin
        """

        // Build, push và deploy
        dockerBuildPush()
        swarmSetImage()
    }
}
```

### Custom service name
```groovy
swarmSetImage(
    service: "my-custom-service",
    image: "my-registry/my-app",
    tag: "v1.2.3"
)
```

### Different Docker context
```groovy
swarmSetImage(
    context: "production-swarm"
)
```

### Complete CI/CD pipeline
```groovy
script {
    // Get project variables
    def vars = getProjectVars()

    // Build and push Docker image
    dockerBuildPush(vars: vars)

    // Update Swarm service
    swarmSetImage(vars: vars)
}
```

## Pipeline integration

### Basic pipeline
```groovy
@Library('jenkins-shared@main') _

pipeline {
    agent { label 'swarm-deployer' }

    stages {
        stage('Build & Push') {
            steps {
                script {
                    dockerBuildPush()
                }
            }
        }

        stage('Deploy to Swarm') {
            steps {
                script {
                    swarmSetImage()
                }
            }
        }
    }

    post {
        success {
            script {
                telegramNotify(
                    message: "✅ *Swarm Deployment Success*\\n\\nService updated with new image!"
                )
            }
        }
    }
}
```

### Environment-specific deployment
```groovy
pipeline {
    agent { label 'swarm-deployer' }

    stages {
        stage('Deploy') {
            steps {
                script {
                    def vars = getProjectVars()

                    // Choose context based on branch
                    def swarmContext = 'docker-swarm'
                    if (vars.REPO_BRANCH == 'main') {
                        swarmContext = 'production-swarm'
                    } else if (vars.REPO_BRANCH.contains('staging')) {
                        swarmContext = 'staging-swarm'
                    }

                    swarmSetImage(
                        context: swarmContext,
                        vars: vars
                    )
                }
            }
        }
    }
}
```

## Service Status Monitoring

Function tự động hiển thị service status sau khi update:

```bash
# Example output:
[INFO] swarmSetImage: Service status:
ID             NAME           IMAGE                          NODE      DESIRED STATE   CURRENT STATE
abc123def456   my-app_beta.1  registry.com/my-app:abc123d   worker1   Running         Running 2 minutes ago
```

## Error Handling

### Service not found
```groovy
script {
    try {
        swarmSetImage()
    } catch (Exception e) {
        if (e.getMessage().contains('Service not found')) {
            echo "Service doesn't exist, creating new service..."

            // Create new service
            sh """
            docker --context docker-swarm service create \\
              --name \${SERVICE_NAME} \\
              --replicas 2 \\
              --publish 8080:8080 \\
              \${IMAGE}:\${TAG}
            """
        } else {
            throw e
        }
    }
}
```

### Context not available
```groovy
script {
    try {
        swarmSetImage()
    } catch (Exception e) {
        if (e.getMessage().contains('context not found')) {
            echo "Docker context not configured, using default"

            swarmSetImage(context: 'default')
        } else {
            throw e
        }
    }
}
```

## Advanced Usage

### Rolling update with custom options
```groovy
script {
    def vars = getProjectVars()
    def serviceName = "${vars.REPO_NAME}_${vars.SANITIZED_BRANCH}"
    def imageTag = "${vars.REGISTRY}/${vars.APP_NAME}:${vars.COMMIT_HASH}"

    // Custom update with additional options
    sh """
    docker --context docker-swarm service update \\
      --with-registry-auth \\
      --image ${imageTag} \\
      --update-parallelism 1 \\
      --update-delay 10s \\
      --update-failure-action rollback \\
      --rollback-parallelism 1 \\
      ${serviceName}
    """
}
```

### Health check validation
```groovy
script {
    swarmSetImage()

    // Wait for service to be healthy
    timeout(time: 5, unit: 'MINUTES') {
        script {
            def vars = getProjectVars()
            def serviceName = "${vars.REPO_NAME}_${vars.REPO_BRANCH}"

            waitUntil {
                def result = sh(
                    script: """
                    docker --context docker-swarm service ps ${serviceName} \\
                      --filter 'desired-state=running' \\
                      --format '{{.CurrentState}}' | head -1
                    """,
                    returnStdout: true
                ).trim()

                return result.contains('Running')
            }
        }
    }

    echo "Service is healthy and running!"
}
```

## Troubleshooting

### Common Issues

#### Docker context not found
**Error:** `Docker context 'docker-swarm' not found`

**Solution:**
```bash
# List available contexts
docker context ls

# Create missing context
docker context create docker-swarm --docker "host=tcp://swarm-manager:2376"
```

#### Service not found
**Error:** `Service 'my-app_beta' not found in Docker Swarm`

**Solution:**
```bash
# List all services
docker --context docker-swarm service ls

# Check service naming pattern
# Ensure service follows: {REPO_NAME}_{REPO_BRANCH} format
```

#### Permission denied
**Error:** `permission denied while trying to connect`

**Solution:**
```bash
# Check Docker daemon access
docker --context docker-swarm node ls

# Verify certificates and keys
ls -la ~/.docker/contexts/docker-swarm/
```

#### Registry authentication failed
**Error:** `failed to resolve reference` hoặc `pull access denied`

**Solution:**
```bash
# Login to registry on manager node
docker --context docker-swarm login 192.168.1.10

# Test image pull
docker --context docker-swarm pull 192.168.1.10/your-image:tag

# Ensure --with-registry-auth is used (function does this automatically)
docker --context docker-swarm service update --with-registry-auth --image image:tag service-name
```

### Debug Commands

```groovy
script {
    // Debug service information
    sh """
    echo "=== Docker Contexts ==="
    docker context ls

    echo "=== Swarm Services ==="
    docker --context docker-swarm service ls

    echo "=== Service Details ==="
    docker --context docker-swarm service inspect ${SERVICE_NAME}
    """
}
```

## Best Practices

### Service Naming
- Sử dụng consistent naming pattern: `{repo}_{branch}`
- Avoid special characters ngoài underscore
- Keep service names short và descriptive

### Image Management
- Always tag images với commit hash cho traceability
- Use registry để store images before deployment
- Implement proper image cleanup policies

### Rolling Updates
- Set appropriate update delays để avoid service disruption
- Configure rollback policies cho production services
- Monitor service health sau mỗi update

### Security
- Store Docker context credentials securely
- Use TLS encryption cho Swarm communication
- Implement proper RBAC cho service updates

## Integration với CI/CD

### Complete workflow
```groovy
pipeline {
    stages {
        stage('Build') {
            steps { script { dockerBuildPush() } }
        }

        stage('Deploy to Dev Swarm') {
            when { branch 'develop' }
            steps {
                script {
                    swarmSetImage(context: 'dev-swarm')
                }
            }
        }

        stage('Deploy to Prod Swarm') {
            when { branch 'main' }
            steps {
                script {
                    swarmSetImage(context: 'prod-swarm')
                }
            }
        }
    }
}
```