/**
 * multiEnvPipeline - Smart pipeline that auto-configures based on branch
 *
 * Automatically handles:
 * - Agent selection based on branch pattern
 * - Environment-specific configurations
 * - Branch protection and permissions
 * - Registry and deployment targets
 *
 * @param config Map of optional parameters:
 *   - customAgentMapping: Override default agent mapping
 *   - skipStages: List of stages to skip
 *   - additionalStages: Closure with additional stages
 *
 * @return void
 */
def call(Map config = [:]) {
  // Get branch name
  def branchName = env.BRANCH_NAME ?: env.GIT_BRANCH?.replaceAll('^origin/', '') ?: 'unknown'
  def lowerBranch = branchName.toLowerCase()

  // Default agent mapping
  def defaultAgentMapping = [
    'prod': ['main', 'master', 'prod', 'production'],
    'staging': ['staging', 'stage'],
    'beta': ['dev', 'develop', 'beta', 'feature', 'hotfix', 'bugfix']
  ]

  // Allow custom agent mapping
  def agentMapping = config.customAgentMapping ?: defaultAgentMapping

  // Determine agent
  def selectedAgent = 'beta' // fallback
  agentMapping.each { agent, patterns ->
    patterns.each { pattern ->
      if (lowerBranch.contains(pattern)) {
        selectedAgent = agent
        return true // break out of loops
      }
    }
  }

  echo """
[INFO] multiEnvPipeline: Environment Detection
  Branch: ${branchName}
  Selected Agent: ${selectedAgent}
  Environment: ${getEnvironmentName(branchName)}
"""

  // Return pipeline configuration
  return [
    agent: selectedAgent,
    environment: getEnvironmentName(branchName),
    branchName: branchName,
    runSetup: !config.skipStages?.contains('setup'),
    runBuild: !config.skipStages?.contains('build'),
    runDeploy: !config.skipStages?.contains('deploy'),
    additionalStages: config.additionalStages
  ]
}

/**
 * Complete pipeline wrapper with multi-environment support
 */
def pipeline(Map config = [:]) {
  def envConfig = call(config)

  pipeline {
    agent { label envConfig.agent }

    stages {
      stage('Setup') {
        when { expression { envConfig.runSetup } }
        steps {
          script {
            echo "[INFO] Running on agent: ${envConfig.agent} for environment: ${envConfig.environment}"
            pipelineSetup(config.pipelineSetupConfig ?: [:])
          }
        }
      }

      stage('Get Config') {
        when { expression { envConfig.runBuild } }
        steps {
          script {
            k8sGetConfig(config.k8sGetConfigConfig ?: [:])
          }
        }
      }

      stage('Build & Push') {
        when { expression { envConfig.runBuild } }
        steps {
          script {
            dockerBuildPush(config.dockerBuildPushConfig ?: [:])
          }
        }
      }

      stage('Deploy') {
        when { expression { envConfig.runDeploy } }
        steps {
          script {
            k8sSetImage(config.k8sSetImageConfig ?: [:])
          }
        }
      }

      // Allow additional stages
      script {
        if (envConfig.additionalStages) {
          envConfig.additionalStages.call()
        }
      }
    }

    post {
      success {
        script {
          telegramNotify(config.telegramNotifyConfig ?: [:])
        }
      }

      failure {
        script {
          telegramNotify(config.telegramNotifyConfig ?: [:])
        }
      }

      always {
        script {
          echo "[INFO] Pipeline completed on agent: ${envConfig.agent}"
        }
      }
    }
  }
}

/**
 * Get environment name from branch
 */
def getEnvironmentName(branchName) {
  def lowerBranch = branchName.toLowerCase()

  if (lowerBranch.contains('prod') || lowerBranch.contains('main') || lowerBranch.contains('master')) {
    return 'PRODUCTION'
  } else if (lowerBranch.contains('staging') || lowerBranch.contains('stage')) {
    return 'STAGING'
  } else {
    return 'BETA'
  }
}

/**
 * Validate environment and agent compatibility
 */
def validateEnvironment() {
  def branchName = env.BRANCH_NAME ?: env.GIT_BRANCH?.replaceAll('^origin/', ')
  def currentAgent = env.NODE_NAME ?: 'unknown'
  def expectedAgent = getAgentByBranch()

  if (currentAgent != expectedAgent) {
    error """
[ERROR] Agent Mismatch Detected!
  Branch: ${branchName}
  Current Agent: ${currentAgent}
  Expected Agent: ${expectedAgent}

Please ensure this branch runs on the correct agent.
"""
  }

  echo "[SUCCESS] Agent validation passed: ${currentAgent} is correct for branch ${branchName}"
}