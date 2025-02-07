#!/usr/bin/groovy
import static groovy.io.FileType.FILES

def agent_label = "Agent-Label"
def bq_projects = "Project-Name"
def bqDataTable = ""
String bqOutput = ""
String bqResult = ""
pipeline {
            options {
            buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30')
            disableConcurrentBuilds()
            }
            agent {label "${agent_label}"}
            environment {email_recipient = 'Email-Address1,Email-address2'}
            triggers {cron('H 2 * * *')}
            stages{               
                    stage('Run BQ') {
                                      steps {
                                             script {
                                                    sh """gcloud config set project $bq_projects"""
                                                    def files = []
                                                    try {
                                                         files = sh(script: "ls -1 jenkins/BigQuery/PROD", returnStdout: true).trim().split('\n')
                                                        } 
                                                        catch (Exception e) 
                                                        {
                                                         println "Error: ${e}"
                                                    }
                                                    println "Files in jenkins/BigQuery/PROD:"
                                                    files.each { sqlFile ->
                                                    currentBuild.displayName = "[${env.BUILD_NUMBER}]:${sqlFile}"   
                                                    bqResult = sh(script: "bq query --use_legacy_sql=false --format=sparse < jenkins/BigQuery/PROD/$sqlFile"  , returnStdout: true).trim()
                                                    sendChat = true
                                                    
                                                    bqOutput = sh(script: "bq query --use_legacy_sql=false --format=csv < jenkins/BigQuery/PROD/$sqlFile"  , returnStdout: true).trim()

                                                    if (bqOutput != "") {
                                                                        sendEmail = true
                                                                        
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
                            }        
                    
                    
                    stage("Email Notify") {
	                        steps {
	                              script {
                        
	                                   if (sendEmail) {
	                                            emailext body: "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br>Project:[$bq_projects] completed successfully.<br> <br> ${bqDataTable}<br><br>Thanks,<br>SRE Team",
	                                mimeType: 'text/html',
	                                subject: "Data Query Alert:${currentBuild.currentResult}",
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
	                                           SENDIT_BQ = sh (script: "echo '${currentBuild.fullDisplayName}:${currentBuild.currentResult}\n,${bq_projects}\n,$bqResult\n,\n ${env.BUILD_URL}'",returnStdout: true).trim()
                    		googlechatnotification(
                    			url: "G-Chat-Webhook",
        message: SENDIT_BQ,sameThreadNotification: 'false')
	                                       }catch (Exception e) {}
	                                   }
	                       
	                }
	            }
	        }
                                
        }       
    }
	
	
Note: Update jenkins\BigQuery\PROD\ews-sre-tools-prd-bq.sql file or add new sql file in the  jenkins\BigQuery\PROD\ folder to schedule the query. This file will be in the Git-Repo.
