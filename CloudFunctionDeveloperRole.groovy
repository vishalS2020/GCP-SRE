/*
Dev:

QA:

UAT:
Agent-name

PROD:

*/
        
        currentBuild.displayName = "${params.CloudFunction} - ${params.AGENT}"
        pipeline {
          agent {
           label "${AGENT}"
          }
          environment {
              no_proxy = 'googleapis.com'
          }
          stages {
            stage('Commands') {
              steps {
                sh """
                    ${SCRIPT}
                """
                }
              }
            }
			
			
Note: SCRIPT example: gcloud functions add-iam-policy-binding ${CloudFunction}  --member=user:Email-Address --role=roles/cloudfunctions.developer --region=us-east1
			