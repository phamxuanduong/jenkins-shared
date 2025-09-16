def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  // Auto-select credentials based on environment if not explicitly provided
  String botToken = args.botToken ?: getEnvironmentBotToken(vars.REPO_BRANCH)
  String chatId = args.chatId ?: getEnvironmentChatId(vars.REPO_BRANCH)
  String threadId = args.threadId ?: getEnvironmentThreadId(vars.REPO_BRANCH)

  // Message configuration
  String message = args.message ?: buildDefaultMessage(vars)
  String parseMode = args.parseMode ?: 'Markdown'
  boolean disableNotification = args.disableNotification ?: false

  // Validate required parameters
  if (!botToken) {
    echo "[WARN] TELEGRAM_BOT_TOKEN not found, skipping notification"
    return
  }
  if (!chatId) {
    echo "[WARN] TELEGRAM_CHAT_ID not found, skipping notification"
    return
  }

  echo "[INFO] Sending Telegram notification..."
  echo "[INFO] Environment: ${getEnvironmentName(vars.REPO_BRANCH)}"
  echo "[INFO] Chat ID: ${chatId}"
  if (threadId) {
    echo "[INFO] Thread ID: ${threadId}"
  }

  try {
    // Build API URL
    def apiUrl = "https://api.telegram.org/bot${botToken}/sendMessage"

    // Build request body
    def requestBody = [
      chat_id: chatId,
      text: message,
      parse_mode: parseMode,
      disable_notification: disableNotification
    ]

    // Add thread ID if provided
    if (threadId) {
      requestBody.message_thread_id = threadId
    }

    // Convert to JSON
    def jsonBody = groovy.json.JsonOutput.toJson(requestBody)

    // Send HTTP request
    def response = sh(
      script: """
      curl -s -X POST \\
        -H "Content-Type: application/json" \\
        -d '${jsonBody}' \\
        "${apiUrl}"
      """,
      returnStdout: true
    ).trim()

    // Parse response
    def jsonResponse = readJSON text: response

    if (jsonResponse.ok) {
      echo "[SUCCESS] Telegram notification sent successfully"
      echo "[INFO] Message ID: ${jsonResponse.result.message_id}"
    } else {
      echo "[ERROR] Telegram notification failed: ${jsonResponse.description}"
      error "Telegram notification failed: ${jsonResponse.description}"
    }

  } catch (Exception e) {
    echo "[ERROR] Failed to send Telegram notification: ${e.getMessage()}"
    if (args.failOnError != false) {
      error "Telegram notification failed: ${e.getMessage()}"
    }
  }
}

def buildDefaultMessage(vars) {
  def status = currentBuild.currentResult ?: 'UNKNOWN'
  def duration = currentBuild.durationString ?: 'Unknown'
  def buildUrl = env.BUILD_URL ?: 'N/A'

  // Status emoji
  def statusEmoji = [
    'SUCCESS': '✅',
    'FAILURE': '❌',
    'UNSTABLE': '⚠️',
    'ABORTED': '🛑',
    'NOT_BUILT': '⭕'
  ][status] ?: '❓'

  // Build message
  def message = """
${statusEmoji} *Build ${status}*

📦 *Project:* `${vars.REPO_NAME}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
🏷️ *Tag:* `${vars.COMMIT_HASH}`
🚀 *Registry:* `${vars.REGISTRY}`

⏱️ *Duration:* ${duration}
🔗 *Build:* [#${env.BUILD_NUMBER}](${buildUrl})

*Deployment:* `${vars.DEPLOYMENT}`
*Namespace:* `${vars.NAMESPACE}`
"""

  return message.trim()
}

def getEnvironmentBotToken(branchName) {
  def lowerBranch = branchName.toLowerCase()

  if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
    return env.TELEGRAM_BOT_TOKEN_BETA ?: env.TELEGRAM_BOT_TOKEN
  } else if (lowerBranch.contains('staging')) {
    return env.TELEGRAM_BOT_TOKEN_STAGING ?: env.TELEGRAM_BOT_TOKEN
  } else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
             lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    return env.TELEGRAM_BOT_TOKEN_PROD ?: env.TELEGRAM_BOT_TOKEN
  } else {
    return env.TELEGRAM_BOT_TOKEN_BETA ?: env.TELEGRAM_BOT_TOKEN // fallback
  }
}

def getEnvironmentChatId(branchName) {
  def lowerBranch = branchName.toLowerCase()

  if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
    return env.TELEGRAM_CHAT_ID_BETA ?: env.TELEGRAM_CHAT_ID
  } else if (lowerBranch.contains('staging')) {
    return env.TELEGRAM_CHAT_ID_STAGING ?: env.TELEGRAM_CHAT_ID
  } else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
             lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    return env.TELEGRAM_CHAT_ID_PROD ?: env.TELEGRAM_CHAT_ID
  } else {
    return env.TELEGRAM_CHAT_ID_BETA ?: env.TELEGRAM_CHAT_ID // fallback
  }
}

def getEnvironmentThreadId(branchName) {
  def lowerBranch = branchName.toLowerCase()

  if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
    return env.TELEGRAM_THREAD_ID_BETA ?: env.TELEGRAM_THREAD_ID
  } else if (lowerBranch.contains('staging')) {
    return env.TELEGRAM_THREAD_ID_STAGING ?: env.TELEGRAM_THREAD_ID
  } else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
             lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    return env.TELEGRAM_THREAD_ID_PROD ?: env.TELEGRAM_THREAD_ID
  } else {
    return env.TELEGRAM_THREAD_ID_BETA ?: env.TELEGRAM_THREAD_ID // fallback
  }
}

def getEnvironmentName(branchName) {
  def lowerBranch = branchName.toLowerCase()

  if (lowerBranch.contains('dev') || lowerBranch.contains('beta')) {
    return 'BETA'
  } else if (lowerBranch.contains('staging')) {
    return 'STAGING'
  } else if (lowerBranch.contains('main') || lowerBranch.contains('master') ||
             lowerBranch.contains('prod') || lowerBranch.contains('production')) {
    return 'PRODUCTION'
  } else {
    return 'BETA' // fallback
  }
}