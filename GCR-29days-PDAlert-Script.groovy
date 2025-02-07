import static groovy.io.FileType.FILES

def expiringRepos = ""
String expiringRepo = ""
boolean sendEmail = false
boolean sendAlert = false

List<String> image_path = [
'us.gcr.io/GCP-Project/NameSpace/Artifact',
]
pipeline {
    agent {
      label "agent-Label"
    }
    environment {email_recipient = 'Email-Address, PD-Alert-Email Address'}
    triggers {cron('H 8 * * *')}
    stages { 
        
        stage("List Images >= 29 days") {  
            steps {
                script {               
                        echo "Starting"
                        
                        for (def repo in image_path) {
                             
                            try{ 
       sh """gcloud container images list-tags ${repo} --filter="timestamp.datetime < \$(date +%Y-%m-%d -d '28 days ago')" --format='get(digest, tags, timestamp.datetime)' --limit=unlimited |
       while read digest
       do
       echo ${repo}@\${digest} >> 29days-GCR-delete
       done"""    
      }catch (Exception e) {
                        
                            echo "Error executing gcloud command: ${e.message}"
          
                        } 
                    } // end of for loop
                    
      def tagfileexists = fileExists '29days-GCR-delete'
      if (tagfileexists){
      expiringRepo = sh(script: "cat 29days-GCR-delete", returnStdout: true).trim()
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
                                done < 29days-GCR-delete
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
	                
	                echo "There is no image >=29 days in the repo list"
	                sendEmail = true
                } 
          }
        }
        stage("Send Notification") {
            steps {
                script {
                    if (sendAlert) {
                        echo "Sending PD Alert to SRE." 
	                    emailext body: """Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br><br>Following GCR images are older than 29 days for Team_Name.<br><br>Steps to Perform:<br>
                        <br>1. Please verify the live image in UAT(Project Name) and PROD(Project-Name)project.
                        <br>2. If image is live in the environment, report to the Team Leaders and check with app owner/Tech Lead if image can be rebuild and deployed.
                        <br>3. If image is not live in the UAT and PROD environment, Delete the images.
                        <br>4. If image is live in any of the lower environment(DEV and QA), Report to the Team Leaders and delete the image.
                        <br><br> Note: The images greater than 29 days will put the team under "Change Freeze" on 30th day<br>
                        <br> ${expiringRepos}<br>
                        <br>Thanks,
                        <br>SRE Team<br>
                        <br><br> GCR Manual Cleaner: Jenkins Pipeline-URL<br>
                        <br><br> GAR Manual Cleaner: Jenkins Pipeline-URL<br>
                        <br> More info at: ${env.BUILD_URL}""",
	                    mimeType: 'text/html',
	                    subject: "PD Alert:GCR images older than 29 days",
                        to: "${email_recipient}"
                    } else

                    if (sendEmail) {
                        echo "Sending Email Alert to SRE." 
	                    emailext body: "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br>${currentBuild.currentResult}<br><br>The Jenkins job completed successfully and there is no image >=29 days to report.<br>Have a nice day !!<br><br>Thanks,<br>SRE Team<br><br>More info at: ${env.BUILD_URL}",
	                    mimeType: 'text/html',
	                    subject: "GCR images older than 29 days job status:${currentBuild.currentResult}",
                        to: "Email_address"
                        echo "Email sent to team"
                    }
                }
                
            }
        } 
  
    }  
}
                