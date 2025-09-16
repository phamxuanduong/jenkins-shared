# telegramNotify

Gửi thông báo tới Telegram về status của Jenkins build với format đẹp và thông tin chi tiết.

## Tham số

**Tất cả tham số đều tùy chọn:**
- `botToken`: Telegram bot token (mặc định: `env.TELEGRAM_BOT_TOKEN`)
- `chatId`: Telegram chat ID (mặc định: `env.TELEGRAM_CHAT_ID`)
- `threadId`: Telegram thread ID (mặc định: `env.TELEGRAM_THREAD_ID`)
- `message`: Custom message (mặc định: auto-generated từ build info)
- `parseMode`: Parse mode (mặc định: 'Markdown')
- `disableNotification`: Silent notification (mặc định: false)
- `failOnError`: Fail build nếu notification lỗi (mặc định: true)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

## Required Environment Variables

Setup trong Jenkins Global Environment Variables hoặc Credentials:

```bash
# Environment-specific credentials (required cho từng môi trường)

# BETA Environment (dev/beta branches)
TELEGRAM_BOT_TOKEN_BETA=123456789:ABCDEFghijklmnopqrstuvwxyz123456789
TELEGRAM_CHAT_ID_BETA=-1001234567890
TELEGRAM_THREAD_ID_BETA=123

# STAGING Environment (staging branches)
TELEGRAM_BOT_TOKEN_STAGING=987654321:ZYXWVUtsrqponmlkjihgfedcba987654321
TELEGRAM_CHAT_ID_STAGING=-1001234567891
TELEGRAM_THREAD_ID_STAGING=456

# PRODUCTION Environment (main/prod branches)
TELEGRAM_BOT_TOKEN_PROD=555666777:ABCDEFghijklmnopqrstuvwxyz555666777
TELEGRAM_CHAT_ID_PROD=-1001234567892
TELEGRAM_THREAD_ID_PROD=789

# Fallback credentials (optional - dùng khi không có environment-specific)
TELEGRAM_BOT_TOKEN=123456789:ABCDEFghijklmnopqrstuvwxyz123456789
TELEGRAM_CHAT_ID=-1001234567890
TELEGRAM_THREAD_ID=123
```

## Setup Telegram Bot

### 1. Tạo Bot
1. Chat với [@BotFather](https://t.me/botfather) trên Telegram
2. Send `/newbot`
3. Đặt tên và username cho bot
4. Copy Bot Token

### 2. Lấy Chat ID

**Cho group chat:**
```bash
# Add bot vào group, sau đó gọi API
curl "https://api.telegram.org/bot<BOT_TOKEN>/getUpdates"

# Tìm chat object, copy "id" field (số âm cho group)
# Ví dụ: -1001234567890
```

**Cho private chat:**
```bash
# Send message cho bot, sau đó gọi API
curl "https://api.telegram.org/bot<BOT_TOKEN>/getUpdates"

# Tìm chat object, copy "id" field (số dương cho private)
# Ví dụ: 1234567890
```

### 3. Thread ID (Optional)
- Cho groups có topics enabled
- Right-click trên topic message → Copy link
- Thread ID là số cuối trong link: `https://t.me/c/1234567890/123/456` → `456`

## Default Message Format

Function tự động tạo message với format sau:

```
✅ Build SUCCESS

📦 Project: my-app
🌿 Branch: beta/api
🏷️ Tag: abc123d

⏱️ Duration: 2 min 30 sec
🔗 Build: #42

Deployment: my-app-beta-api
Namespace: my-app
```

## Ví dụ sử dụng

### Basic usage (auto message)
```groovy
script {
    try {
        // Your CI/CD steps
        k8sGetConfig()
        dockerBuildPush()
        k8sSetImage()

        // Send success notification - auto-detects environment from branch
        telegramNotify()
    } catch (Exception e) {
        // Send failure notification - auto-detects environment from branch
        telegramNotify(
            message: "❌ *Build Failed*\n\nError: `${e.getMessage()}`"
        )
        throw e
    }
}
```

### Custom message
```groovy
telegramNotify(
    message: """
🚀 *Deployment Complete*

Application: `my-app`
Environment: `production`
Version: `v1.2.3`

✅ All services healthy
    """
)
```

### Environment Routing Examples
```groovy
// Auto-routing based on branch name
// beta/api branch → TELEGRAM_CHAT_ID_BETA
// staging branch → TELEGRAM_CHAT_ID_STAGING
// main branch → TELEGRAM_CHAT_ID_PROD
telegramNotify()

// Override for specific chat
telegramNotify(chatId: env.TELEGRAM_SPECIAL_CHAT)

// Silent notification (keeps auto-routing)
telegramNotify(disableNotification: true)

// Don't fail build on notification error
telegramNotify(failOnError: false)
```

### Pipeline integration
```groovy
pipeline {
    agent { label 'beta' }

    stages {
        stage('Deploy') {
            steps {
                script {
                    k8sGetConfig()
                    dockerBuildPush()
                    k8sSetImage()
                }
            }
        }
    }

    post {
        success {
            script {
                // Auto-routes to correct environment chat
                telegramNotify()
            }
        }
        failure {
            script {
                // Auto-routes to correct environment chat
                telegramNotify(
                    message: "❌ *Build Failed*\n\nProject: `${env.JOB_NAME}`\nBuild: [#${env.BUILD_NUMBER}](${env.BUILD_URL})"
                )
            }
        }
    }
}
```

## Environment Routing Logic

Function tự động chọn **token, chatId, threadId** dựa trên branch name:

```groovy
// Beta Environment - Uses TELEGRAM_*_BETA credentials
dev, develop, beta, beta/api, beta-worker → BETA credentials

// Staging Environment - Uses TELEGRAM_*_STAGING credentials
staging, staging-fix → STAGING credentials

// Production Environment - Uses TELEGRAM_*_PROD credentials
main, master, prod, production → PRODUCTION credentials

// Fallback - Uses TELEGRAM_*_BETA credentials
feature/api, hotfix/bug → BETA credentials (fallback)
```

### Branch Pattern Examples
```groovy
// These branches will use BETA credentials:
beta/api → TELEGRAM_BOT_TOKEN_BETA, TELEGRAM_CHAT_ID_BETA, TELEGRAM_THREAD_ID_BETA
beta/worker → TELEGRAM_BOT_TOKEN_BETA, TELEGRAM_CHAT_ID_BETA, TELEGRAM_THREAD_ID_BETA
dev-feature → TELEGRAM_BOT_TOKEN_BETA, TELEGRAM_CHAT_ID_BETA, TELEGRAM_THREAD_ID_BETA

// These branches will use STAGING credentials:
staging → TELEGRAM_BOT_TOKEN_STAGING, TELEGRAM_CHAT_ID_STAGING, TELEGRAM_THREAD_ID_STAGING
staging-hotfix → TELEGRAM_BOT_TOKEN_STAGING, TELEGRAM_CHAT_ID_STAGING, TELEGRAM_THREAD_ID_STAGING

// These branches will use PROD credentials:
main → TELEGRAM_BOT_TOKEN_PROD, TELEGRAM_CHAT_ID_PROD, TELEGRAM_THREAD_ID_PROD
master → TELEGRAM_BOT_TOKEN_PROD, TELEGRAM_CHAT_ID_PROD, TELEGRAM_THREAD_ID_PROD

// Custom override if needed:
telegramNotify(
    botToken: env.TELEGRAM_CUSTOM_TOKEN,
    chatId: env.TELEGRAM_CUSTOM_CHAT,
    threadId: env.TELEGRAM_CUSTOM_THREAD
)
```

## Advanced Usage

### Conditional notifications
```groovy
script {
    def vars = getProjectVars()

    // Only notify for main branches (auto-routes to correct chat)
    if (vars.REPO_BRANCH in ['main', 'beta', 'staging']) {
        telegramNotify()
    }

    // Different messages per environment (keeps auto-routing)
    def message = ""
    if (vars.REPO_BRANCH == 'main') {
        message = "🚀 *Production Deployment*\n\nVersion: `${vars.COMMIT_HASH}` deployed successfully!"
    } else {
        message = "✅ *${vars.REPO_BRANCH} Deployment*\n\nBuild completed successfully!"
    }

    telegramNotify(message: message)
}
```

### Multiple notifications with auto-routing
```groovy
script {
    // Standard notification (auto-routes based on branch)
    telegramNotify()

    // Additional custom notification
    telegramNotify(
        chatId: env.TELEGRAM_OPS_CHAT,
        message: "📈 Monitoring: New deployment ready"
    )
}
```

### Rich formatting
```groovy
telegramNotify(
    message: """
🎉 *Release v2.1.0 Deployed*

🔗 *Links:*
• [Application](https://app.company.com)
• [Monitoring](https://monitoring.company.com)
• [Logs](https://logs.company.com)

📊 *Metrics:*
• Build time: 3m 45s
• Image size: 245MB
• Tests: 127 passed

👥 *Team:* @dev-team
    """,
    parseMode: 'Markdown'
)
```

## Error Handling

### Graceful degradation
```groovy
script {
    try {
        // Main CI/CD workflow
        k8sGetConfig()
        dockerBuildPush()
        k8sSetImage()

        // Try to notify success
        try {
            telegramNotify()
        } catch (Exception e) {
            echo "Telegram notification failed, but deployment succeeded: ${e.getMessage()}"
        }

    } catch (Exception e) {
        // Always try to notify failures
        try {
            telegramNotify(
                message: "❌ *Deploy Failed*\n\nError: `${e.getMessage()}`",
                failOnError: false
            )
        } catch (Exception notifyError) {
            echo "Both deployment and notification failed"
        }
        throw e
    }
}
```

### Retry logic
```groovy
script {
    retry(3) {
        telegramNotify()
    }
}
```

## Security Best Practices

### Jenkins Credentials
```groovy
// Store bot token as Jenkins secret
withCredentials([string(credentialsId: 'telegram-bot-token', variable: 'BOT_TOKEN')]) {
    telegramNotify(botToken: env.BOT_TOKEN)
}

// Or use global environment variables (recommended)
// Set in Jenkins > Manage Jenkins > Configure System > Global Properties
```

### Token Protection
- Never commit bot tokens to code
- Use Jenkins credentials store
- Rotate tokens regularly
- Limit bot permissions

## Troubleshooting

### Bot token issues
**Error:** `Unauthorized`
**Solution:** Check bot token is correct and active

### Chat ID issues
**Error:** `Bad Request: chat not found`
**Solution:**
- Ensure bot is added to group/chat
- Check chat ID format (negative for groups, positive for private)
- Bot must have permission to send messages

### Network issues
**Error:** `Connection timeout`
**Solution:**
- Check Jenkins agent has internet access
- Verify Telegram API is not blocked
- Add retry logic

### Message formatting
**Error:** `Bad Request: can't parse entities`
**Solution:**
- Check Markdown syntax is correct
- Escape special characters: `_`, `*`, `[`, `]`, `(`, `)`
- Use `parseMode: null` for plain text

### Common fixes
```groovy
// Debug what's being sent
telegramNotify(message: "Test message", failOnError: false)

// Check environment variables
echo "BETA - Token: ${env.TELEGRAM_BOT_TOKEN_BETA ? 'Yes' : 'No'}, Chat: ${env.TELEGRAM_CHAT_ID_BETA ? 'Yes' : 'No'}, Thread: ${env.TELEGRAM_THREAD_ID_BETA ? 'Yes' : 'No'}"
echo "STAGING - Token: ${env.TELEGRAM_BOT_TOKEN_STAGING ? 'Yes' : 'No'}, Chat: ${env.TELEGRAM_CHAT_ID_STAGING ? 'Yes' : 'No'}, Thread: ${env.TELEGRAM_THREAD_ID_STAGING ? 'Yes' : 'No'}"
echo "PROD - Token: ${env.TELEGRAM_BOT_TOKEN_PROD ? 'Yes' : 'No'}, Chat: ${env.TELEGRAM_CHAT_ID_PROD ? 'Yes' : 'No'}, Thread: ${env.TELEGRAM_THREAD_ID_PROD ? 'Yes' : 'No'}"
echo "Fallback - Token: ${env.TELEGRAM_BOT_TOKEN ? 'Yes' : 'No'}, Chat: ${env.TELEGRAM_CHAT_ID ? 'Yes' : 'No'}, Thread: ${env.TELEGRAM_THREAD_ID ? 'Yes' : 'No'}"

// Test API manually with specific environment
sh 'curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN_BETA}/getMe"'
sh 'curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN_STAGING}/getMe"'
sh 'curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN_PROD}/getMe"'
```

## Message Templates

### Success template
```groovy
def successMessage = """
✅ *Deployment Successful*

📦 *Project:* `${vars.REPO_NAME}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
🚀 *Environment:* `${vars.NAMESPACE}`
⏱️ *Duration:* ${currentBuild.durationString}

🔗 [View Build](${env.BUILD_URL})
"""
```

### Failure template
```groovy
def failureMessage = """
❌ *Deployment Failed*

📦 *Project:* `${vars.REPO_NAME}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
💥 *Stage:* ${env.STAGE_NAME}
⏱️ *Duration:* ${currentBuild.durationString}

🔗 [View Logs](${env.BUILD_URL}console)
🛠️ [Retry Build](${env.BUILD_URL}rebuild)
"""
```

### Warning template
```groovy
def warningMessage = """
⚠️ *Build Unstable*

📦 *Project:* `${vars.REPO_NAME}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
📊 *Tests:* Some failures detected
⏱️ *Duration:* ${currentBuild.durationString}

🔍 [Check Results](${env.BUILD_URL}testReport)
"""
```