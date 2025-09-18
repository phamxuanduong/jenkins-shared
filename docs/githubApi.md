# githubApi

GitHub API integration for branch protection and user permission validation in Jenkins CI/CD pipelines.

## Overview

This function provides comprehensive GitHub repository permission checking to prevent unauthorized deployments to protected branches. It integrates seamlessly with existing pipeline functions.

## Features

- **Branch Protection Detection**: Automatically detects GitHub branch protection rules
- **User Permission Validation**: Checks if the committer has admin access to the repository
- **Automatic Pipeline Blocking**: Stops deployment when insufficient permissions are detected
- **Telegram Notifications**: Sends alerts when deployments are blocked
- **Graceful Fallback**: Allows deployment if GitHub API is unavailable

## Required Environment Variables

```bash
# GitHub API access (required for permission checks)
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Telegram notification settings (from telegramNotify)
TELEGRAM_BOT_TOKEN_BETA=123456789:ABCDEFghijklmnopqrstuvwxyz123456789
TELEGRAM_CHAT_ID_BETA=-1001234567890
# ... other environment-specific credentials
```

## Setup GitHub Token

### 1. Create Personal Access Token
1. Go to GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scopes:
   - `repo` - Full control of private repositories
   - `read:org` - Read org and team membership (if using organization repos)

### 2. Configure in Jenkins
```bash
# Add to Jenkins Global Environment Variables
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## How It Works

### Automatic Integration

The permission system is automatically enabled in all deployment functions:

1. **`getProjectVars()`** - Performs permission validation during setup
2. **`dockerBuildPush()`** - Blocked if insufficient permissions
3. **`k8sSetImage()`** - Blocked if insufficient permissions
4. **`swarmSetImage()`** - Blocked if insufficient permissions

### Permission Logic

```
1. Extract repository owner and name from GIT_URL
2. Get current branch name from Jenkins environment
3. Check if branch has protection rules via GitHub API
4. If branch is protected:
   - Get user permissions for the repository
   - Check if user has 'admin' role
   - Block deployment if user is not admin
5. If branch is not protected:
   - Allow deployment
```

## API Functions

### validateDeployPermissions

Main function that performs comprehensive permission validation:

```groovy
def result = githubApi('validateDeployPermissions', [
  repoOwner: 'myorg',
  repoName: 'myrepo',
  branchName: 'main',
  username: 'john-doe'
])

// Result structure:
[
  canDeploy: true/false,
  reason: 'ADMIN_ACCESS_GRANTED' | 'INSUFFICIENT_PERMISSIONS' | 'BRANCH_NOT_PROTECTED',
  branchProtection: [isProtected: true/false, ...],
  userPermissions: [hasAdminAccess: true/false, permission: 'admin', ...],
  username: 'john-doe',
  branchName: 'main',
  repository: 'myorg/myrepo'
]
```

### checkPermissions

Check user's repository permissions:

```groovy
def result = githubApi('checkPermissions', [
  repoOwner: 'myorg',
  repoName: 'myrepo',
  username: 'john-doe'
])

// Result:
[
  hasAdminAccess: true/false,
  permission: 'admin' | 'write' | 'read' | 'none',
  username: 'john-doe',
  reason: 'ADMIN_ACCESS' | 'INSUFFICIENT_PERMISSION'
]
```

### getBranchProtection

Check branch protection status:

```groovy
def result = githubApi('getBranchProtection', [
  repoOwner: 'myorg',
  repoName: 'myrepo',
  branchName: 'main'
])

// Result:
[
  isProtected: true/false,
  branchName: 'main',
  protectionRules: {...}, // GitHub API response
  reason: 'BRANCH_PROTECTED' | 'NOT_PROTECTED'
]
```

## Pipeline Integration

### Automatic Usage (Recommended)

The permission system works automatically with existing pipelines:

```groovy
@Library('jenkins-shared@main') _

pipeline {
  agent { label 'beta' }

  stages {
    stage('Setup') {
      steps {
        script {
          // Permission check happens automatically here
          VARS = getProjectVars()
          // Pipeline will stop here if user lacks permissions
        }
      }
    }

    stage('Build & Push') {
      steps {
        script {
          // Will be skipped if permissions failed
          dockerBuildPush()
        }
      }
    }

    stage('Deploy to K8s') {
      steps {
        script {
          // Will be skipped if permissions failed
          k8sSetImage()
        }
      }
    }
  }

  post {
    always {
      script {
        // Notification includes permission status
        telegramNotify()
      }
    }
  }
}
```

### Manual Control

Skip permission checks if needed:

```groovy
script {
  // Skip permission validation
  VARS = getProjectVars(skipPermissionCheck: true)

  // Or check permissions manually
  def permissionCheck = githubApi('validateDeployPermissions')
  if (permissionCheck.canDeploy) {
    dockerBuildPush(vars: VARS)
    k8sSetImage(vars: VARS)
  } else {
    echo "Deployment blocked: ${permissionCheck.reason}"
  }
}
```

## Permission Scenarios

### Scenario 1: Protected Branch + Admin User
```
Branch: main (protected)
User: john-doe (admin)
Result: ✅ Deployment allowed
```

### Scenario 2: Protected Branch + Non-Admin User
```
Branch: main (protected)
User: jane-dev (write access)
Result: 🚫 Deployment blocked
Message sent to Telegram
```

### Scenario 3: Non-Protected Branch
```
Branch: feature/new-api (not protected)
User: any-user
Result: ✅ Deployment allowed
```

### Scenario 4: No GitHub Token
```
GITHUB_TOKEN: not set
Result: ✅ Deployment allowed (graceful fallback)
```

## Telegram Notifications

When deployment is blocked, automatic Telegram notification is sent:

```
🚫 Deployment Blocked

📦 Repository: myorg/myrepo
🌿 Branch: main
👤 User: jane-dev

❌ Reason: User does not have admin access to protected branch

🔒 This branch is protected and requires admin permissions to deploy.
Please contact a repository administrator or use a pull request workflow.

🔗 Build: #123
```

## Branch Protection Patterns

Common branch names that trigger protection checks:

**Production branches:**
- `main`, `master`
- `prod`, `production`
- `release`, `release/*`

**Staging branches:**
- `staging`, `staging/*`
- `stage`, `stage/*`

**Development branches:**
- `dev`, `develop`
- `beta`, `beta/*`
- `feature/*`, `hotfix/*`

## Troubleshooting

### GitHub API Issues

**Error:** `Bad credentials`
```groovy
// Check token is valid
sh 'curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/user'
```

**Error:** `Not Found`
```groovy
// Check repository access
sh 'curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/repos/owner/repo'
```

### Permission Check Bypass

Temporarily disable for debugging:
```groovy
VARS = getProjectVars(skipPermissionCheck: true)
```

### Debug Permission Status

```groovy
script {
  def vars = getProjectVars()
  echo "CAN_DEPLOY: ${vars.CAN_DEPLOY}"
  echo "PERMISSION_REASON: ${vars.PERMISSION_REASON}"
  echo "GIT_USER: ${vars.GIT_USER}"
  echo "REPO_BRANCH: ${vars.REPO_BRANCH}"
}
```

### Common Issues

**Issue:** Pipeline always blocked despite admin access
**Solution:** Verify GitHub token has correct repository scope

**Issue:** Wrong user detected in permission check
**Solution:** User is extracted from git commit author, ensure git is configured correctly

**Issue:** Branch protection not detected
**Solution:** Check branch name extraction and GitHub branch protection settings

## Security Best Practices

### Token Security
- Store GitHub token as Jenkins secret
- Use minimum required token scopes
- Rotate tokens regularly
- Monitor token usage

### Access Control
- Enable branch protection on critical branches
- Require admin approval for protected branch changes
- Use pull request workflows for non-admin users
- Regular audit of repository permissions

### Monitoring
- Monitor Telegram alerts for blocked deployments
- Review Jenkins logs for permission check failures
- Track unauthorized deployment attempts

## Examples

### Custom Permission Check
```groovy
script {
  def check = githubApi('validateDeployPermissions', [
    repoOwner: 'myorg',
    repoName: 'myrepo',
    branchName: env.BRANCH_NAME,
    username: 'specific-user'
  ])

  if (!check.canDeploy) {
    telegramNotify([
      message: "🚫 Custom check failed: ${check.reason}",
      failOnError: false
    ])
    error "Deployment blocked by custom permission check"
  }
}
```

### Multiple Repository Check
```groovy
script {
  def repos = ['frontend', 'backend', 'database']
  def blocked = []

  repos.each { repo ->
    def check = githubApi('validateDeployPermissions', [
      repoName: repo
    ])
    if (!check.canDeploy) {
      blocked << repo
    }
  }

  if (blocked.size() > 0) {
    error "Deployment blocked for repositories: ${blocked.join(', ')}"
  }
}
```

### Conditional Branch Protection
```groovy
script {
  def vars = getProjectVars()

  // Only check permissions for production branches
  if (vars.REPO_BRANCH in ['main', 'master', 'production']) {
    def check = githubApi('validateDeployPermissions')
    if (!check.canDeploy) {
      error "Production deployment blocked: ${check.reason}"
    }
  }

  // Proceed with deployment
  dockerBuildPush(vars: vars)
  k8sSetImage(vars: vars)
}
```