def call(Map config = [:]) {
  // Validate required config
  if (!config.appName) error 'deployPipeline: thiếu "appName"'
  if (!config.registry) error 'deployPipeline: thiếu "registry"'
  if (!config.namespace) error 'deployPipeline: thiếu "namespace"'

  // Set defaults
  def cfg = [
    appName: config.appName,
    registry: config.registry,
    namespace: config.namespace,
    dockerfile: config.dockerfile ?: 'Dockerfile',
    context: config.context ?: '.',
    deploymentName: config.deploymentName ?: "${config.appName}-deployment",
    commitHash: config.commitHash ?: env.GIT_COMMIT?.take(7),
    buildStage: config.buildStage != false,  // default true
    deployStage: config.deployStage != false  // default true
  ]

  // Calculate full image name
  cfg.fullImage = "${cfg.registry}/${cfg.appName}"

  pipeline {
    agent any

    environment {
      APP_NAME = "${cfg.appName}"
      REGISTRY = "${cfg.registry}"
      NAMESPACE = "${cfg.namespace}"
      COMMIT_HASH = "${cfg.commitHash}"
      FULL_IMAGE = "${cfg.fullImage}"
      DEPLOYMENT_NAME = "${cfg.deploymentName}"
    }

    stages {
      stage('Build & Push') {
        when { expression { return cfg.buildStage } }
        steps {
          script {
            dockerBuildPush(
              image: cfg.fullImage,
              tag: cfg.commitHash,
              dockerfile: cfg.dockerfile,
              context: cfg.context
            )
          }
        }
      }

      stage('Deploy') {
        when { expression { return cfg.deployStage } }
        steps {
          script {
            k8sSetImage(
              deployment: cfg.deploymentName,
              image: cfg.fullImage,
              tag: cfg.commitHash,
              namespace: cfg.namespace
            )
          }
        }
      }
    }

    post {
      success {
        echo "[SUCCESS] Pipeline completed for ${cfg.appName}:${cfg.commitHash}"
      }
      failure {
        echo "[FAILURE] Pipeline failed for ${cfg.appName}:${cfg.commitHash}"
      }
    }
  }
}