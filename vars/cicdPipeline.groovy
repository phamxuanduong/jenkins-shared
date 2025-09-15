def call(Map args = [:]) {
  // Get project vars if not provided
  def vars = args.vars ?: getProjectVars()

  // Allow overriding individual stages
  boolean getConfigStage = args.getConfigStage != false  // default true
  boolean buildStage = args.buildStage != false          // default true
  boolean deployStage = args.deployStage != false        // default true

  // Custom stage names
  String getConfigStageName = args.getConfigStageName ?: 'K8s get Configmap'
  String buildStageName = args.buildStageName ?: 'Build & Push'
  String deployStageName = args.deployStageName ?: 'Deploy'

  echo """
[INFO] CICD Pipeline Starting:
  Stages enabled: getConfig=${getConfigStage}, build=${buildStage}, deploy=${deployStage}
  Using project vars: ${vars.REPO_NAME}-${vars.SANITIZED_BRANCH}
"""

  if (getConfigStage) {
    stage(getConfigStageName) {
      echo "[INFO] Fetching configuration from ConfigMap..."
      k8sGetConfig(vars: vars)
    }
  }

  if (buildStage) {
    stage(buildStageName) {
      echo "[INFO] Building and pushing Docker image..."
      dockerBuildPush(vars: vars)
    }
  }

  if (deployStage) {
    stage(deployStageName) {
      echo "[INFO] Deploying to Kubernetes..."
      k8sSetImage(vars: vars)
    }
  }

  echo "[SUCCESS] CICD Pipeline completed successfully!"
}