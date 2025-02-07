import static groovy.io.FileType.FILES

def expiringRepos = ""
String expiringRepo = ""
String untagRepo = ""
boolean sendEmail = false
boolean sendAlert = false

List<String> image_path = [
'us.gcr.io/Project/Repo/artifact',
]
pipeline {
    agent {
      label "Agent-Name"
    }
   environment {email_recipient = 'Email-address'}
   triggers {cron('H 20 * * *')}
    stages { 
        
            stage("Remove SNAPSHOT Images Older than 5 days") {
            steps {
                script {
                    for (def repo in image_path) {
                    cutoffDate = sh(script: "date +%Y-%m-%d -d '5 days ago'", returnStdout: true).trim()

                    sh '''#!/bin/bash
                    gcloud container images list-tags ''' + repo + ''' --format='get(digest, tags, timestamp.datetime)' --limit=unlimited | grep 'SNAPSHOT' | 
                    while read line; do
                    digest=$(echo "$line" | awk -F' ' '{ print $1 }')
                    tag=$(echo "$line" | awk -F' ' '{ print $2 }')
                    timestamp=$(echo "$line" | awk -F' ' '{ print $3 }')

                    if [[ $timestamp < ''' + cutoffDate + ''' ]]; then
                    gcloud container images delete --force-delete-tags --quiet ''' + repo + '''@$digest
                   echo ''' + repo + '''@$digest $tag $timestamp >> snapshot-GCR-delete
                   fi
                   done
                   '''
                    }
                }
            }
        }

        stage("Delete untagged GCR images") {
            steps {
                script {
                   for (def repo in image_path) {
                   try {
                       sh """gcloud container images list-tags ${repo} --filter='-tags:*' --format='get(digest)' --limit=unlimited |
                       while read digest
                       do
                       gcloud container images delete --quiet ${repo}@\${digest}
                       echo ${repo}@\${digest} >> untag-GCR-delete
                       done"""
                    }catch (Exception e) {echo "${repo} is clean, noimage to delete"} 
                }
            }
        }
    }    
    
    stage("Generate Report") {  
            steps {
                script {      
                   
      // Merge the files  content
      def snaptagfileexists = fileExists 'snapshot-GCR-delete'
      if(snaptagfileexists){
                             def untagfileexists = fileExists 'untag-GCR-delete'
                             if(untagfileexists){
                                                sh "cat snapshot-GCR-delete untag-GCR-delete > GCR-delete" 
                             }
                             else
                             sh "cat snapshot-GCR-delete > GCR-delete" 
      }
      
      def tagfileexists = fileExists 'GCR-delete'

      if (tagfileexists){
      expiringRepo = sh(script: "cat GCR-delete", returnStdout: true).trim()
      echo "Content of the file:"
      
                if (expiringRepo != "") {
                               sendAlert = true
                               sh "echo '$expiringRepo'"
                               expiringRepos += "<table border=\"1px\" cellspacing=\"0\"><tr><th>GCR</th><th>Tag<th>Created_Date</th></tr>"
                               def shellOutput = sh(script:'''
                                
                                while IFS=' ' read -r gcr tags Created_Date _; do
                                   
                                    # Construct the table row in the shell
                                    table_row="<tr><td>${gcr}</td><td>${tags}</td><td>${Created_Date}</td></tr>"

                                    # Pass the table row back to Groovy
                                    echo "::table_row::${table_row}"
                                done < GCR-delete
                            ''', returnStdout: true).trim()
                            
                            // Process the output and append to expiringRepos(table) in Groovy
                            shellOutput.split('::table_row::').each { row ->
                                if (row) { 
                                    expiringRepos += row
                                }
                            }
                              
                            }
                            expiringRepos += "</table>"
			
	                }
	                else 
	                
	                echo "There is no SNAPSHOT image >=5 days or untag image in the image registry."
	                sendEmail = true
                } 
          }
        }
    
    
        stage('Notify Chat Room') {
		         steps {
                script {
                def tagfileexists = fileExists 'snapshot-GCR-delete'
                if (tagfileexists){
                MESSAGE_TAGGED = sh (script: "echo snapshot-GCR-delete",returnStdout: true).trim()
    	        LIST_TAGGED = MESSAGE_TAGGED.split(' ')
    	        for (String item : LIST_TAGGED){
               SENDIT_TAGGED = sh (script: "cat ${item}",returnStdout: true).trim()
      googlechatnotification(url: "Google-chat-webhook-URL",
      message: SENDIT_TAGGED,sameThreadNotification: 'false')
              }
            }else {echo 'No GCR to delete with Snapshot tag. Notification is not sent'}
		      }
		           script {
               def untagfileexists = fileExists 'untag-GCR-delete'
               if (untagfileexists){
               MESSAGE_UNTAGGED = sh (script: "echo untag-GCR-delete",returnStdout: true).trim()
    	         LIST_UNTAGGED = MESSAGE_UNTAGGED.split(' ')
    	         for (String item : LIST_UNTAGGED){
               SENDIT_UNTAGGED = sh (script: "cat ${item}",returnStdout: true).trim()
               googlechatnotification(url: "Google-chat-webhook-URL",
               message: SENDIT_UNTAGGED,sameThreadNotification: 'false')
             }
           }else {echo 'No untagged GCR found to delete. Notification is not sent'}
        }
    }
}
        
        stage("Send Email Notification") {
            steps {
                script {
                    if (sendAlert) {
                        echo "Sending Email Alert to SRE." 
	                    emailext body: """Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br><br>The GCR images with 'SNAPSHOT' tag which are older than 5 days and any UNTAG images for  are deleted today.<br>
                        <br> ${expiringRepos}<br>
                        <br>Thanks,
                        <br>SRE Team<br>
                        <br> More info at: ${env.BUILD_URL}""",
	                    mimeType: 'text/html',
	                    subject: "GCR UNTAG and Snapshot tag images job status:${currentBuild.currentResult}",
                        to: "${email_recipient}"
                    } else

                    if (sendEmail) {
                        echo "Sending Email Alert to SRE." 
	                    emailext body: "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br>${currentBuild.currentResult}<br><br>The Jenkins job completed successfully and there is no image with 'SNAPSHOT' tag >=2 days or Untag images to report.<br>Have a nice day !!<br><br>Thanks,<br>SRE Team<br><br>More info at: ${env.BUILD_URL}",
	                    mimeType: 'text/html',
	                    subject: "GCR UNTAG and Snapshot tag images job status:${currentBuild.currentResult}",
                        to: "${email_recipient}"
                        echo "Email sent to team"
                    }
                }
                
            }
        } 
  
    }  
}