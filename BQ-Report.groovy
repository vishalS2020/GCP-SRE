#!/usr/bin/groovy
import static groovy.io.FileType.FILES
def agent_label = ""
def bqDataTable = ""
def log_date, partner, account, action, application
String bqOutput = ""
String bqResult = ""
properties([
    parameters (
        [choice(choices: ['Project-Name1','Project-Name2','Project-Name3','Project-Name4','Project-Name5'], description: 'please select BQ project', name: 'bq_projects'),
        string(name: "req_number", defaultValue: 'RITM000000',description: "ServiceNow_Request_Number"),
		text(defaultValue: '''Please Provide Your Query''', name: 'BQ_Statement')
        ])
        ])
		
	node('master') {
        stage('Initialization') {
            if(params.bq_projects == "Project-Name"){
                agent_label = "Agent_Label"
		project="Project-Name"
            }else
            if(params.bq_projects == "Project-Name"){
                agent_label = "Agent_Label"
                project="Project-Name"
            }else
	    if(params.bq_projects == "Project-Name"){
                agent_label = "Agent_Label"
                project="Project-Name"
	    }else
            if(params.bq_projects == "Project-Name"){
                agent_label = "Agent_Label"
                project="Project-Name"
	    }else
            if(params.bq_projects == "Project-Name"){
                agent_label = "tf-ews-es-pfm-prd"
                project="Project-Name"
			   }			
            }
    } 
		
	pipeline {
        options {
            buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30')
            disableConcurrentBuilds()
            }
            agent {label "${agent_label}"}
               environment {email_recipient = 'es-i9hq-sre@equifax.com'}
            stages{             
                stage('Run BQ') {
                    steps {
                        script {
                            currentBuild.displayName = "$params.req_number Project:[$bq_projects]"
                            

                            echo "BQ Statement to run: $BQ_Statement"
                            sh "gcloud config set project $project"
                            bqResult = sh(script: 'bq query --use_legacy_sql=false --format=pretty "$BQ_Statement"',returnStdout: true).trim()
                            sendChat = true
                            bqOutput = sh(script: 'bq query --use_legacy_sql=false --format=csv "$BQ_Statement"',returnStdout: true).trim()
                            
                            if (bqOutput != "") {
                            sendEmail = true
                            sendChat = true
                            sh "echo '$bqOutput' > bqresult"
                            bqDataTable += "<table border=\"1px\" cellspacing=\"0\">"

                            def isHeader="YES"
                            def shellOutput = sh(script:'''
                                
                                while IFS=',' read -r log_date partner account action application _; do
                                    # echo "[$log_date $partner $account $action $application]"
                                    if [[ "$isHeader" == "YES" ]]; then
                                        table_row="<tr style="background-color: gray;font-weight: bold;"><th>${log_date}</th><th>${partner}</th><th>${account}</th><th>${action}</th><th>${application}</th></tr>"
                                        isHeader="NO"
                                    else
                                        # Construct the table row in the shell
                                        table_row="<tr><td>${log_date}</td><td>${partner}</td><td>${account}</td><td>${action}</td><td>${application}</td></tr>"
                                    fi 
                                    
                                    # Pass the table row back to Groovy
                                    echo "::table_row::${table_row}"
                                done < bqresult
                            ''', returnStdout: true).trim()
                            

                            // Process the output and append to bqDataTable in Groovy
                            shellOutput.split('::table_row::').each { row ->
                                if (row) { 
                                    bqDataTable += row
                                }
                            }

                            }
	                    bqDataTable += "</table>"
	                    
			            }
                    }
                }
                
                stage("Email Notify") {
	            steps {
	                script {
                        
	                    if (sendEmail) {
	                        emailext body: "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br>SNOW Request #:$req_number<br> Project:[$bq_projects] completed successfully.<br> <br> ${bqDataTable}<br><br>Thanks,<br>SRE Team",
	                                //charset: 'UTF-8',
	                                mimeType: 'text/html',
	                                subject: "Data Query-OnDemand Alert:${currentBuild.currentResult}",
	                                to: "${email_recipient}"
	                    }
	                }
	            }
	        }

                	                stage("GChat Notify") {
	                        steps {
	                              script {
                        
	                                   if (sendChat) {
	                                       try{
	                                           SENDIT_BQ = sh (script: "echo '${currentBuild.fullDisplayName}:${currentBuild.currentResult},${bq_projects}:${BQ_Statement}\n,\n $bqResult,\n ${env.BUILD_URL}'",returnStdout: true).trim()
                    		googlechatnotification(
                    			url: "Gchat-webhook-URL",
        message: SENDIT_BQ,sameThreadNotification: 'false')
	                                       }catch (Exception e) {}
	                                   }
	                       
	                }
	            }
	        }
		}
    }				