# getProjectVars

Lấy các biến dự án tự động từ Git và environment, có thể override. Đây là hàm core để tự động detect thông tin project.

## Tham số

**Tất cả tham số đều tùy chọn:**
- `repoName`: Tên repository (mặc định: tự động từ GIT_URL)
- `repoBranch`: Tên branch (mặc định: tự động từ GIT_BRANCH)
- `namespace`: Kubernetes namespace (mặc định: repoName)
- `deployment`: Tên deployment (mặc định: "{repoName}-{sanitizedBranch}")
- `appName`: Tên ứng dụng (mặc định: repoName)
- `registry`: Docker registry (mặc định: auto-select based on branch)
- `commitHash`: Git commit hash (mặc định: env.GIT_COMMIT?.take(7))

## Mặc định tự động

- `REPO_NAME`: Extract từ GIT_URL (ví dụ: `my-app`)
- `REPO_BRANCH`: Từ GIT_BRANCH, loại bỏ `origin/` prefix
- `SANITIZED_BRANCH`: Branch name được sanitize cho Kubernetes (`beta/api` → `beta-api`)
- `NAMESPACE`: = REPO_NAME
- `DEPLOYMENT`: = `{REPO_NAME}-{SANITIZED_BRANCH}`
- `APP_NAME`: = REPO_NAME
- `REGISTRY`: Tự động chọn theo branch pattern
- `COMMIT_HASH`: 7 ký tự đầu của Git commit

## Registry Selection Logic

Function tự động chọn Docker registry based on branch name patterns:

```groovy
def lowerBranch = branchName.toLowerCase()
if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
    registry = env.REGISTRY_BETA
} else if (lowerBranch.contains('staging')) {
    registry = env.REGISTRY_STAGING
} else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
           lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    registry = env.REGISTRY_PROD
} else {
    registry = env.REGISTRY_BETA // fallback
}
```

**Examples:**
- `develop`, `dev-feature`, `beta`, `beta-api` → `REGISTRY_BETA`
- `staging`, `staging-fix` → `REGISTRY_STAGING`
- `main`, `master`, `prod`, `production` → `REGISTRY_PROD`
- `feature/api`, `hotfix/bug` → `REGISTRY_BETA` (fallback)

## Branch Sanitization

Chuyển đổi branch names thành Kubernetes-compliant names:

### Rules
- Thay thế `/` thành `-` (`feature/api` → `feature-api`)
- Thay thế ký tự không hợp lệ thành `-`
- Convert thành lowercase
- Regex: `replaceAll('/', '-').replaceAll('[^a-zA-Z0-9-]', '-').toLowerCase()`

### Examples
```
beta/api → beta-api
feature/user-auth → feature-user-auth
hotfix/db_connection → hotfix-db-connection
develop → develop
main → main
```

## Implementation Details

### Git URL Parsing
Regex pattern hỗ trợ cả SSH và HTTPS Git URLs:
```groovy
def urlPattern = /.*[\/:]([^\/]+)\/([^\/]+?)(?:\.git)?$/
// Matches:
// https://github.com/user/repo.git → repo
// git@github.com:user/repo.git → repo
// https://gitlab.com/group/subgroup/repo → repo
```

### Branch Detection Priority
1. `config.repoBranch` (parameter override)
2. `env.GIT_BRANCH?.replaceAll('^origin/', '')` (remove origin/ prefix)
3. `env.BRANCH_NAME` (fallback)
4. `'main'` (default)

### Environment Integration
Function tích hợp với Jenkins environment variables:
- `GIT_URL`: Repository URL
- `GIT_BRANCH`: Current branch (có thể có origin/ prefix)
- `GIT_COMMIT`: Full commit hash
- `BRANCH_NAME`: Alternative branch variable
- `REGISTRY_*`: Registry URLs for different environments

## Required Environment Variables

Setup trong Jenkins Global Environment Variables:

```bash
# Registry URLs
REGISTRY_BETA=registry-beta.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry-prod.company.com

# Automatically available in Jenkins
GIT_URL=https://github.com/user/repo.git
GIT_BRANCH=origin/main
GIT_COMMIT=abc123def456789...
BRANCH_NAME=main
```

## Return Value

Function trả về Map với các keys:

```groovy
[
    REPO_NAME: 'my-app',
    REPO_BRANCH: 'beta/api',
    SANITIZED_BRANCH: 'beta-api',
    NAMESPACE: 'my-app',
    DEPLOYMENT: 'my-app-beta-api',
    APP_NAME: 'my-app',
    REGISTRY: 'registry-beta.company.com',
    COMMIT_HASH: 'abc123d'
]
```

## Ví dụ sử dụng

### Basic usage
```groovy
script {
    def vars = getProjectVars()
    echo "Deploying ${vars.APP_NAME}:${vars.COMMIT_HASH} to ${vars.NAMESPACE}"
}
```

### With overrides
```groovy
script {
    def vars = getProjectVars(
        namespace: 'custom-namespace',
        deployment: 'special-deployment',
        registry: 'custom-registry.com'
    )
}
```

### In other functions
```groovy
// Other functions automatically call getProjectVars()
dockerBuildPush() // Uses auto-detected vars
k8sSetImage()     // Uses auto-detected vars
k8sGetConfig()    // Uses auto-detected vars
```

## Debug Output

Function tự động log tất cả variables để debug:

```
[INFO] Project Variables:
  REPO_NAME:     my-app
  REPO_BRANCH:   beta/api
  SANITIZED_BRANCH: beta-api
  NAMESPACE:     my-app
  DEPLOYMENT:    my-app-beta-api
  APP_NAME:      my-app
  REGISTRY:      registry-beta.company.com (auto-selected based on branch)
  COMMIT_HASH:   abc123d
```

## Troubleshooting

### Repository name không detect được
**Nguyên nhân**: GIT_URL format không standard
**Giải pháp**: Override manually
```groovy
getProjectVars(repoName: 'my-app')
```

### Registry không đúng
**Nguyên nhân**: Branch pattern không match hoặc env vars chưa set
**Giải pháp**:
```groovy
// Check environment variables
echo "REGISTRY_BETA: ${env.REGISTRY_BETA}"

// Override if needed
getProjectVars(registry: 'my-registry.com')
```

### Branch sanitization issues
**Nguyên nhân**: Special characters in branch name
**Giải pháp**: Function tự động handle, check SANITIZED_BRANCH trong logs

### Commit hash empty
**Nguyên nhân**: GIT_COMMIT không available
**Giải pháp**:
```groovy
getProjectVars(commitHash: 'latest')
```

## Best Practices

### Environment Setup
```groovy
// Jenkins Global Environment Variables
REGISTRY_BETA=registry-dev.company.com
REGISTRY_STAGING=registry-staging.company.com
REGISTRY_PROD=registry.company.com

// Registry authentication should be configured at Jenkins level
```

### Branch Naming
- Use consistent patterns: `feature/`, `hotfix/`, `beta/`, etc.
- Avoid special characters except `-`, `/`, `_`
- Keep names descriptive but concise

### Override Strategy
- Use defaults for 90% of cases
- Override only when necessary
- Document any custom configurations

### Integration with Other Functions
```groovy
// Recommended: Let other functions auto-call getProjectVars()
k8sGetConfig()    // Auto-detects namespace, configmap
dockerBuildPush() // Auto-detects image, tag, registry
k8sSetImage()     // Auto-detects deployment, namespace

// Alternative: Explicit vars for complex scenarios
script {
    def vars = getProjectVars(namespace: 'shared-services')
    k8sGetConfig(vars: vars)
    dockerBuildPush(vars: vars)
}
```