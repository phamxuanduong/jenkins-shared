# Docker Functions

Tập hợp các functions để build và push Docker images với khả năng tự động hóa hoàn toàn.

## dockerBuild

Xây dựng Docker image.

### Tham số (tất cả tùy chọn)
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag cho image (mặc định: tự động từ commit hash)
- `dockerfile`: Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context`: Build context (mặc định: '.')
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

### Ví dụ
```groovy
// Hoàn toàn tự động
dockerBuild()

// Custom dockerfile
dockerBuild(dockerfile: 'docker/Dockerfile.prod')

// Custom image và tag
dockerBuild(
    image: 'my-registry.com/my-app',
    tag: 'v1.0.0'
)
```

## dockerPush

Đẩy Docker image lên registry.

### Tham số (tất cả tùy chọn)
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag của image (mặc định: tự động từ commit hash)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

### Ví dụ
```groovy
// Hoàn toàn tự động
dockerPush()

// Custom image và tag
dockerPush(
    image: 'my-registry.com/my-app',
    tag: 'latest'
)
```

## dockerBuildPush

Xây dựng và đẩy Docker image trong một bước. Đây là function được recommend nhất.

### Tham số (tất cả tùy chọn)
- `image`: Tên image (mặc định: tự động từ `getProjectVars()`)
- `tag`: Tag cho image (mặc định: tự động từ commit hash)
- `dockerfile`: Đường dẫn Dockerfile (mặc định: 'Dockerfile')
- `context`: Build context (mặc định: '.')
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

### Ví dụ
```groovy
// Hoàn toàn tự động
dockerBuildPush()

// Custom dockerfile
dockerBuildPush(dockerfile: 'docker/Dockerfile.beta')

// Full custom
dockerBuildPush(
    image: 'registry.company.com/my-app',
    tag: 'v2.1.0',
    dockerfile: 'build/Dockerfile',
    context: './app'
)
```

## Implementation Details

### Automatic Image Naming
```groovy
// Format: {REGISTRY}/{APP_NAME}
def vars = getProjectVars()
def image = "${vars.REGISTRY}/${vars.APP_NAME}"

// Examples:
// registry-beta.com/my-app
// registry-prod.com/api-service
```

### Automatic Tagging
- Uses Git commit hash (first 7 characters)
- Format: `abc123d`
- Fallback: `'latest'` if no Git commit available

### Registry Selection
- Automatically selects registry based on branch:
  - `beta/*` branches → `REGISTRY_BETA`
  - `staging/*` branches → `REGISTRY_STAGING`
  - `main/master` branches → `REGISTRY_PROD`

### Error Handling
- Validates Docker availability
- Checks build context exists
- Handles authentication errors
- Provides detailed error messages

### Build Context
- Default: Current directory (`.`)
- Supports relative and absolute paths
- Automatically handles `.dockerignore`

## Advanced Usage

### Multi-stage builds
```groovy
// Build different stages
dockerBuild(
    dockerfile: 'Dockerfile',
    tag: 'builder-stage'
)

dockerBuild(
    dockerfile: 'Dockerfile',
    tag: 'runtime-stage'
)
```

### Platform-specific builds
```groovy
// Build for specific platform
dockerBuild(
    buildArgs: '--platform linux/amd64'
)
```

### Build with args
```groovy
// Docker build args (if your Dockerfile supports)
script {
    def vars = getProjectVars()

    sh """
    docker build \\
        --build-arg NODE_ENV=production \\
        --build-arg VERSION=${vars.COMMIT_HASH} \\
        -t ${vars.REGISTRY}/${vars.APP_NAME}:${vars.COMMIT_HASH} \\
        -f Dockerfile .
    """

    dockerPush(vars: vars)
}
```

### Conditional building
```groovy
script {
    def vars = getProjectVars()

    if (vars.REPO_BRANCH.contains('beta')) {
        dockerBuildPush(dockerfile: 'Dockerfile.beta')
    } else if (vars.REPO_BRANCH == 'main') {
        dockerBuildPush(dockerfile: 'Dockerfile.prod')
    } else {
        dockerBuild() // Build only, don't push
    }
}
```

## Best Practices

### Dockerfile Organization
```
project/
├── Dockerfile              # Default/production
├── Dockerfile.beta         # Beta environment
├── Dockerfile.dev          # Development
└── docker/
    ├── Dockerfile.worker   # Worker service
    └── Dockerfile.api      # API service
```

### Registry Authentication
```groovy
// Ensure Docker login in Jenkins agent setup
sh 'docker login registry.company.com -u $DOCKER_USER -p $DOCKER_PASS'

// Or use Jenkins credentials
withCredentials([usernamePassword(credentialsId: 'docker-registry',
                                  usernameVariable: 'USER',
                                  passwordVariable: 'PASS')]) {
    sh 'docker login registry.company.com -u $USER -p $PASS'
    dockerBuildPush()
}
```

### Image Tagging Strategy
```groovy
script {
    def vars = getProjectVars()

    // Tag with commit hash
    dockerBuildPush(tag: vars.COMMIT_HASH)

    // Also tag as latest for main branch
    if (vars.REPO_BRANCH == 'main') {
        dockerBuild(tag: 'latest')
        dockerPush(tag: 'latest')
    }
}
```

### Build Optimization
```dockerfile
# Dockerfile optimization
FROM node:18-alpine as builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

FROM node:18-alpine as runtime
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY . .
EXPOSE 3000
CMD ["npm", "start"]
```

## Troubleshooting

### Docker command not found
**Nguyên nhân**: Docker không installed trên Jenkins agent
**Giải pháp**:
```bash
# Install Docker on agent
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Add jenkins user to docker group
sudo usermod -aG docker jenkins
```

### Build context issues
**Nguyên nhân**: Files không tồn tại trong build context
**Giải pháp**:
```groovy
// Check build context
sh 'ls -la .'
sh 'cat Dockerfile'

// Use different context
dockerBuild(context: './app')
```

### Registry authentication failed
**Nguyên nhân**: Docker chưa login hoặc credentials sai
**Giải pháp**:
```groovy
// Test registry access
sh 'docker login registry.company.com'

// Check credentials in Jenkins
withCredentials([...]) {
    dockerBuildPush()
}
```

### Image too large
**Nguyên nhân**: Dockerfile không optimized
**Giải pháp**:
```dockerfile
# Use multi-stage builds
# Use .dockerignore
# Use alpine base images
# Clean up packages after install
```

### Build fails intermittently
**Nguyên nhân**: Network issues hoặc base image unavailable
**Giải pháp**:
```groovy
// Add retry logic
retry(3) {
    dockerBuild()
}

// Use local base images
```

## Environment Variables

### Required
```bash
# Automatically available in Jenkins
GIT_COMMIT=abc123def456...

# Required for registry selection
REGISTRY_BETA=registry-beta.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry-prod.company.com
```

### Optional
```bash
# Docker daemon configuration
DOCKER_HOST=unix:///var/run/docker.sock
DOCKER_BUILDKIT=1

# Registry authentication
DOCKER_REGISTRY_USER=username
DOCKER_REGISTRY_PASS=password
```

## Performance Tips

### Layer Caching
```dockerfile
# Order layers by change frequency
FROM node:18-alpine
WORKDIR /app

# Dependencies change less frequently
COPY package*.json ./
RUN npm ci --only=production

# Source code changes more frequently
COPY . .
CMD ["npm", "start"]
```

### Parallel Builds
```groovy
// Build multiple services in parallel
parallel {
    "API": {
        dockerBuildPush(
            dockerfile: 'api/Dockerfile',
            context: './api'
        )
    },
    "Worker": {
        dockerBuildPush(
            dockerfile: 'worker/Dockerfile',
            context: './worker'
        )
    }
}
```

### Build Cache
```groovy
// Use BuildKit for better caching
withEnv(['DOCKER_BUILDKIT=1']) {
    dockerBuild()
}
```