/**
 * cicdPipeline - Complete CI/CD pipeline in one function call
 *
 * @param args Map of optional parameters:
 *   - getConfigStage: Enable config retrieval stage (default: true)
 *   - buildStage: Enable build and push stage (default: true)
 *   - deployStage: Enable deployment stage (default: true)
 *   - getConfigStageName: Custom stage name for config (default: 'K8s get Configmap')
 *   - buildStageName: Custom stage name for build (default: 'Build & Push')
 *   - deployStageName: Custom stage name for deploy (default: 'Deploy')
 *   - vars: Project variables (default: auto-call getProjectVars)
 *
 * @return void
 */
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
[INFO] cicdPipeline: CICD Pipeline Starting:
  Stages enabled: getConfig=${getConfigStage}, build=${buildStage}, deploy=${deployStage}
  Using project vars: ${vars.REPO_NAME}-${vars.SANITIZED_BRANCH}
"""

  if (getConfigStage) {
    stage(getConfigStageName) {
      echo "[INFO] cicdPipeline: Fetching configuration from ConfigMap..."
      k8sGetConfig(vars: vars)
    }
  }

  if (buildStage) {
    stage(buildStageName) {
      echo "[INFO] cicdPipeline: Building and pushing Docker image..."
      dockerBuildPush(vars: vars)
    }
  }

  if (deployStage) {
    stage(deployStageName) {
      echo "[INFO] cicdPipeline: Deploying to Kubernetes..."
      k8sSetImage(vars: vars)
    }
  }

  echo "[SUCCESS] cicdPipeline: CICD Pipeline completed successfully!"
}