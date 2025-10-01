#!/usr/bin/env groovy

/**
 * ci - Complete CI/CD pipeline in a single call
 *
 * Orchestrates the entire build and deploy process with sensible defaults.
 * Projects only need to call ci() - all configuration is managed in jenkins-shared.
 *
 * @param config Map of optional parameters:
 *   - agent: Jenkins agent label (default: auto-detect from branch)
 *   - skipConfigMap: Skip k8sGetConfigMap (default: false)
 *   - skipBuild: Skip Docker build (default: false)
 *   - skipPush: Skip Docker push (default: false)
 *   - skipDeploy: Skip k8sSetImage (default: false)
 *   - skipNotification: Skip Telegram (default: false)
 *   - beforeBuild: Closure to run before build (default: null)
 *   - afterDeploy: Closure to run after deploy (default: null)
 *
 * @example
 *   // Simplest usage in project Jenkinsfile
 *   @Library('jenkins-shared@main') _
 *   ci()
 *
 * @example
 *   // With pre-build tests
 *   @Library('jenkins-shared@main') _
 *   ci([
 *     beforeBuild: {
 *       sh 'npm install'
 *       sh 'npm test'
 *     }
 *   ])
 */
def call(Map config = [:]) {
  def agentLabel = config.agent ?: autoDetectAgent()

  pipeline {
    agent {
      label agentLabel
    }

    stages {
      stage('Setup') {
        steps {
          script {
            pipelineSetup(config)
          }
        }
      }

      stage('Get ConfigMap') {
        when {
          expression { config.skipConfigMap != true }
        }
        steps {
          script {
            k8sGetConfigMap()
          }
        }
      }

      stage('Pre-Build') {
        when {
          expression { config.beforeBuild != null }
        }
        steps {
          script {
            config.beforeBuild()
          }
        }
      }

      stage('Build Image') {
        when {
          expression { config.skipBuild != true }
        }
        steps {
          script {
            dockerBuildImage()
          }
        }
      }

      stage('Push Image') {
        when {
          expression { config.skipPush != true && config.skipBuild != true }
        }
        steps {
          script {
            dockerPushImage()
          }
        }
      }

      stage('Deploy') {
        when {
          expression { config.skipDeploy != true }
        }
        steps {
          script {
            k8sSetImage()
          }
        }
      }

      stage('Post-Deploy') {
        when {
          expression { config.afterDeploy != null }
        }
        steps {
          script {
            config.afterDeploy()
          }
        }
      }
    }

    post {
      success {
        script {
          if (config.skipNotification != true) {
            telegramNotify()
          }
        }
      }
      failure {
        script {
          if (config.skipNotification != true) {
            telegramNotify()
          }
        }
      }
    }
  }
}

/**
 * Auto-detect agent based on branch name
 */
def autoDetectAgent() {
  def branchName = env.BRANCH_NAME ?: ''
  def envAgent = ['beta', 'staging', 'prod'].find { env ->
    branchName.equalsIgnoreCase(env) || branchName.toLowerCase().startsWith(env + '/')
  }
  return envAgent ?: ''
}
