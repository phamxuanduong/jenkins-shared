#!/usr/bin/env groovy

/**
 * telegramNotify - Send Telegram notifications with auto environment routing
 *
 * @param args Map of optional parameters:
 *   - botToken: Telegram bot token (default: auto from environment)
 *   - chatId: Telegram chat ID (default: auto from environment)
 *   - threadId: Telegram thread ID (default: auto from environment)
 *   - message: Custom message (default: auto-generated from build info)
 *   - parseMode: Parse mode (default: 'Markdown')
 *   - disableNotification: Silent notification (default: false)
 *   - failOnError: Fail build on notification error (default: true)
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 */
def call(Map args = [:]) {
  // Check for deployment blocked to prevent infinite loops in post actions
  if (env.DEPLOYMENT_BLOCKED == 'true' && !args.vars) {
    echo "[WARN] telegramNotify: Deployment already blocked, skipping notification to prevent infinite loop"
    return
  }

  // Get project vars if not provided - use cached data if available
  def vars = args.vars ?: sharedUtils.getProjectVarsOptimized()

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
    echo "[WARN] telegramNotify: TELEGRAM_BOT_TOKEN not found, skipping notification"
    return
  }
  if (!chatId) {
    echo "[WARN] telegramNotify: TELEGRAM_CHAT_ID not found, skipping notification"
    return
  }

  echo "[INFO] telegramNotify: Sending Telegram notification..."
  echo "[INFO] telegramNotify: Environment: ${getEnvironmentName(vars.REPO_BRANCH)}"
  echo "[INFO] telegramNotify: Chat ID: ${chatId}"
  if (threadId) {
    echo "[INFO] telegramNotify: Thread ID: ${threadId}"
  }

  try {
    // Build request body
    def requestBody = [
      chat_id: chatId,
      text: message,
      disable_notification: disableNotification
    ]

    // Add parse_mode
    requestBody.parse_mode = parseMode

    // Add thread ID if provided
    if (threadId) {
      requestBody.message_thread_id = threadId
    }

    // Convert to JSON
    def jsonBody = groovy.json.JsonOutput.toJson(requestBody)

    // Send HTTP request - simple and straightforward, no retry needed
    def apiUrl = "https://api.telegram.org/bot${botToken}/sendMessage"

    def response = sh(
      script: """
      set +x
      # Use printf to properly escape JSON for shell
      JSON_BODY=\$(printf '%s' '${jsonBody.replace("'", "'\\''")}')

      # Send request and capture only HTTP status code
      HTTP_CODE=\$(curl -s -w '%{http_code}' -o /tmp/telegram_response_\$\$.txt \\
        -X POST \\
        -H "Content-Type: application/json" \\
        -d "\$JSON_BODY" \\
        "${apiUrl}")

      # Output format: HTTP_CODE|first_100_chars_of_response
      echo "\${HTTP_CODE}|\$(head -c 100 /tmp/telegram_response_\$\$.txt)"
      rm -f /tmp/telegram_response_\$\$.txt
      """,
      returnStdout: true
    ).trim()

    // Parse simple response format: "200|{\"ok\":true,..."
    def parts = response.split('\\|', 2)
    def httpCode = parts[0]
    def responsePreview = parts.length > 1 ? parts[1] : ''

    // Check success by HTTP code and "ok":true in response
    if (httpCode == '200' && responsePreview.contains('"ok":true')) {
      echo "[SUCCESS] telegramNotify: Telegram notification sent successfully (HTTP ${httpCode})"

      // Try to extract message_id from preview
      def messageIdMatch = (responsePreview =~ /"message_id":(\d+)/)
      if (messageIdMatch) {
        echo "[INFO] telegramNotify: Message ID: ${messageIdMatch[0][1]}"
      }
    } else {
      // Extract error description from preview
      def errorMsg = "HTTP ${httpCode}"
      def descMatch = (responsePreview =~ /"description":"([^"]+)"/)
      if (descMatch) {
        errorMsg += " - ${descMatch[0][1]}"
      } else if (responsePreview) {
        errorMsg += " - ${responsePreview}"
      }

      echo "[ERROR] telegramNotify: Telegram notification failed: ${errorMsg}"
      if (args.failOnError != false) {
        error "telegramNotify: Telegram notification failed: ${errorMsg}"
      }
    }

  } catch (Exception e) {
    echo "[ERROR] telegramNotify: Failed to send Telegram notification: ${e.getMessage()}"
    if (args.failOnError != false) {
      error "telegramNotify: Telegram notification failed: ${e.getMessage()}"
    }
  }
}

/**
 * Build default notification message
 */
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

  // Get commit message from vars (cached from getProjectVars)
  def commitMessage = vars.COMMIT_MESSAGE ?: 'No commit message'

  // Escape markdown special characters for Telegram
  def escapedCommitMsg = sharedUtils.escapeMarkdown(commitMessage)

  // Get domain from ingress (if exists)
  def domain = k8sGetIngress(
    deployment: vars.DEPLOYMENT,
    namespace: vars.NAMESPACE
  )
  def domainLine = domain ? "\n🌐 *Domain:* ${domain}" : ""

  // Build message with Markdown formatting
  def message = """
${statusEmoji} *Build ${status}*

📦 *Project:* `${vars.REPO_NAME}`
🌿 *Branch:* `${vars.REPO_BRANCH}`
👤 *User:* `${vars.GIT_USER ?: 'unknown'}`
🏷️ *Tag:* `${vars.COMMIT_HASH}`${domainLine}

💬 *Commit:* `${escapedCommitMsg}`

⏱️ *Duration:* ${duration}
🔗 *Build:* [#${env.BUILD_NUMBER}](${buildUrl})
"""

  return message.trim()
}

/**
 * Get environment-specific bot token
 */
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
    return env.TELEGRAM_BOT_TOKEN_BETA ?: env.TELEGRAM_BOT_TOKEN
  }
}

/**
 * Get environment-specific chat ID
 */
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
    return env.TELEGRAM_CHAT_ID_BETA ?: env.TELEGRAM_CHAT_ID
  }
}

/**
 * Get environment-specific thread ID
 */
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
    return env.TELEGRAM_THREAD_ID_BETA ?: env.TELEGRAM_THREAD_ID
  }
}

/**
 * Get environment name from branch
 */
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
    return 'BETA'
  }
}
