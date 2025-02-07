#!/usr/bin/groovy


/*--------------------------------------------------------------------------*/
/*        Job handles managing of infrastructure with terraform             */
/*           Job is called from a multi-branch pipeline job.                */
/*--------------------------------------------------------------------------*/

def AGENT_LABEL = null
def PROJECT_ID = null
def INSTANCE_ID = null
def DATABASE_ID = null
def I9PROJECT_ID = null
def SA = null


properties([
    parameters(
                [choice(name: 'ENV', defaultValue: "qa", choices: ["dev","qa","uat","prd"].join("\n")),
                choice(name: 'SERVICE_ACCOUNT',description: 'Service Account To Get Role', choices: ["SERVICE_ACCOUNT1","SERVICE_ACCOUNT2"].join("\n")),
                choice(name: 'DATABASE',description: 'Database to apply role', choices: ["database1","database2","database3"].join("\n")),
                ]
            )
        ]
)

node('master') {
    stage('Set agent'){
        if(params.ENV == "dev"  || params.ENV == "qa") {
            AGENT_LABEL = "Agent-Label"
            INSTANCE_ID = "GCP-Instance"
            PROJECT_ID = "GCP-Project-Name"
            PROJECT_ID2="GCP-Project-Name"
        }
        if(params.ENV == "uat") {
            AGENT_LABEL = "Agent-Label"
            INSTANCE_ID = "GCP-Instance"
            PROJECT_ID = "GCP-Project-Name"
            PROJECT_ID2="GCP-Project-Name"
        }
        if(params.ENV == "prd") {
            AGENT_LABEL = "Agent-Label"
            INSTANCE_ID = "GCP-Instance"
            PROJECT_ID = "GCP-Project-Name"
            PROJECT_ID2="GCP-Project-Name"
        }
        DATABASE_ID="$params.DATABASE-$params.ENV"
        SA="$SERVICE_ACCOUNT-$ENV@$PROJECT_ID"+".iam.gserviceaccount.com"
        currentBuild.displayName = "$INSTANCE_ID/$DATABASE_ID"
        currentBuild.description = "INSTANCE_ID: $INSTANCE_ID DATABASE_ID=$DATABASE_ID"
    }
}

pipeline {

    agent {label "${AGENT_LABEL}"}
    options {
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '50'))
    }
    stages {
        stage("Add Iam Binding") {
            steps {
                    script {
                            sh """
                                gcloud config set project ${PROJECT_ID}
                                gcloud config list
                                gcloud spanner databases add-iam-policy-binding ${DATABASE_ID} --instance=${INSTANCE_ID} --member="serviceAccount:${SA}" --role=roles/spanner.databaseUser --condition=None
                            """

                    }
            }
        }
    }

/*--------------------------------------------------------------------*/
/*        Cleanup - delete workspace, email victims if job fails      */
/*--------------------------------------------------------------------*/

    post {
        failure {
            mail to: 'Email-address', subject: "${env.JOB_NAME} failed", body: "Check the URL below for more info\n\n${env.BUILD_URL}"
        }
        unstable{
            mail to: 'Email-address', subject: "${env.JOB_NAME} failed", body: "Check the URL below for more info\n\n${env.BUILD_URL}"
        }
    }
}