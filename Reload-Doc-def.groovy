#!/usr/bin/groovy

def agent_label = "Agent-label"

String curl_command = "curl --location 'Curl-URL'"

properties([
  parameters(
    [choice(choices: ['dev', 'qa', 'uat', 'prd'], description: 'please select environment', name: 'env_name', defaultValue: 'prd'),
      string(name: "Request_Number", defaultValue: 'RITM000000', description: "ServiceNow_Request_Number")
    ])
])
node('master') {
  stage('Initialization') {
    if (params.env_name == "dev") {
      agent_label = "DEV-Agent-Label"
      project = "GCP-Project-Name"
      clustereast = "GCP-cluster-Name"
      regioneast = "us-east1"
      proxyeast = "Proxy_IP:8443"
      clusterwest = "GCP-cluster-Name"
      regionwest = "us-west1"
      proxywest = "Proxy_IP:8443"
    } else
    if (params.env_name == "qa") {
      agent_label = "QA-Agent-Label"
      project = "GCP-Project-Name"
      clustereast = "GCP-cluster-Name"
      regioneast = "us-east1"
      proxyeast = "Proxy_IP:8443"
      clusterwest = "GCP-cluster-Name"
      regionwest = "us-west1"
      proxywest = "Proxy_IP
    } else
    if (params.env_name == "uat") {
      agent_label = "UAT-Agent-Label"
      project = "GCP-Project-Name"
      clustereast = "GCP-cluster-Name"
      regioneast = "us-east1"
      proxyeast = "Proxy_IP:8443"
      clusterwest = "GCP-cluster-Name"
      regionwest = "us-west1"
      proxywest = "Proxy_IP
    } else
    if (params.env_name == "prd") {
      agent_label = "PRD-Agent-Label"
      project = "GCP-Project-Name"
      clustereast = "GCP-cluster-Name"
      regioneast = "us-east1"
      proxyeast = "Proxy_IP:8443"
      clusterwest = "GCP-cluster-Name"
      regionwest = "us-west1"
      proxywest = "Proxy_IP
    }
  }
}
pipeline {
  options {
    buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')
    disableConcurrentBuilds()
  }
  agent {
    label "${agent_label}"
  }
  environment {
    email_recipient = 'Email_address'
  }
  stages {
    stage('Reload Document Definition in Firestore') {
        steps {
          script {
            currentBuild.displayName = "[$params.env_name] $params.Request_Number"
            sh ""
            "
            gcloud container clusters get - credentials $clustereast--region = $regioneast--project = $project;
            export HTTPS_PROXY = $proxyeast;
            export alpinepod = `kubectl get pods -n alpine --selector=app=alpine-tools --no-headers=true | cut -d ' ' -f 1 | head -n 1`;
            kubectl exec\ $ {
              alpinepod
            } - n alpine - c alpine - tools - container--$curl_command ""
            "}
          }
        }
        stage('Rollout workload_artifact') {
          steps {
            script {
              sh ""
              "
              gcloud container clusters get - credentials $clustereast--region = $regioneast--project = $project;
              export HTTPS_PROXY = $proxyeast;
              kubectl rollout restart - n namespace deployment workload_artifact ""
              "}
            }
          }
          stage('Rollout workload_artifact') {
            steps {
              script {
                sh ""
                "
                gcloud container clusters get - credentials $clustereast--region = $regioneast--project = $project;
                export HTTPS_PROXY = $proxyeast;
                kubectl rollout restart - n namespace deployment workload_artifact ""
                "}
              }
            }
            stage('Rollout workload_artifact') {
              steps {
                script {
                  sh ""
                  "
                  gcloud container clusters get - credentials $clusterwest--region = $regionwest--project = $project;
                  export HTTPS_PROXY = $proxywest;
                  kubectl rollout restart - n namespace deployment workload_artifact ""
                  "
                }
              }
            }
            stage('Rollout workload_artifact') {
              steps {
                script {
                  sh ""
                  "
                  gcloud container clusters get - credentials $clusterwest--region = $regionwest--project = $project;
                  export HTTPS_PROXY = $proxywest;
                  kubectl rollout restart - n namespace deployment workload_artifact ""
                  "
                }
              }
            }
          }
          post {
            success {
              mail to: "${email_recipient}",
                subject: "SUCCESS:${currentBuild.fullDisplayName}",
                body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n ${env.BUILD_URL}"
            }
            failure {
              mail to: "${email_recipient}",
                subject: "FAILURE:${currentBuild.fullDisplayName}",
                body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n ${env.BUILD_URL}"
            }
          }
        }
        