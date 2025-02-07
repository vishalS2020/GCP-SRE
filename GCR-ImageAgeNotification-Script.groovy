import java.time.LocalDate
def expiringRepos = ""
String expiringRepo = ""
boolean sendEmail = false
boolean sendAlert = false

List<String> image_path = [
'us.gcr.io/GCP-Project/NameSpace/artifact',
]
pipeline {
    agent {
      label "Agent-Label"
    }
    
    environment {email_recipient = 'Email-address'}
    triggers {cron("0 5 * * *")}

    stages { 
        
        stage("Fetch Images >= 21 days Old") {  
            steps {
                script {               
                        echo "Starting Scan"
                        
                        for (def repo in image_path) {
                             
                            try{ 
       sh """gcloud container images list-tags ${repo} --filter="timestamp.datetime < \$(date +%Y-%m-%d -d '21 days ago')" --format='get(digest, tags, timestamp.datetime)' --limit=unlimited |
       while read digest
       do
       echo ${repo}@\${digest} >> GCR-21daysold
       done"""    
      }catch (Exception e) {
                        
                            echo "Error executing gcloud command: ${e.message}"
          
                        } 
                    } // end of for loop
                }
            }
        }

        stage("Generate Report") {  
            steps {
                script {               
                        echo "Analyzing file and generating Images data......."

                        def today = LocalDate.now()   
                        echo "Today's date:${today}" 
                        
                        def tagfileexists = fileExists 'GCR-21daysold'
      	                        
                         if (tagfileexists){
                                                                                     
                                            // Calculate the age of each image
                                             sh '''#!/bin/bash
                                            while IFS=' ' read -r gcr tags Created_Date _; do
                                            newgcr=$(echo "$line" | awk -F' ' '{ print $1 }')
                                            echo "GCR: ${gcr}"
                                            newtags=$(echo "$line" | awk -F' ' '{ print $2 }')
                                            echo "Tags: ${tags}"
                                            createdDate=$(echo "$line" | awk -F' ' '{ print $3 }') 
                                            echo "Created Date: ${Created_Date}" 
                                            Age1=$(date -d "$Created_Date" +%s)
                                            Age2=$(date -d "$today" +%s)
                                            #Age1=$(date -d "$created_Date" +%s)
                                            Age=$(($Age2-$Age1))
                                            echo "$Age"
                                            Days=$(($Age/86400)) 
                                            echo ${gcr} ${tags} ${Created_Date} ${Days} >> output_file_temp
                                            done< GCR-21daysold'''
                                            
                                           
                                            sh "mv output_file_temp output_file"
											sh "rm GCR-21daysold"
                                                                                   
                        expiringRepo = sh(script: "cat output_file", returnStdout: true).trim()

                        echo "Content of the file:"
      
                        if (expiringRepo != "") {
                               sendAlert = true
                               sh "echo '$expiringRepo'"
							   expiringRepos += "<table border=\"1px\" cellspacing=\"0\"><tr><th>GCR</th><th>Tag<th>Created_Date</th><th>Age</th></tr>"
                               def shellOutput = sh(script:'''
                                
                                while IFS=' ' read -r gcr tags Created_Date Days _; do
                                   
                                    # Construct the table row in the shell
                                    table_row="<tr><td>${gcr}</td><td>${tags}</td><td>${Created_Date}</td><td>${Days}</td></tr>"

                                    # Pass the table row back to Groovy
                                    echo "::table_row::${table_row}"
                                done < output_file
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
	                
	                echo "There is no image >=21 days in the  image registry."
	                sendEmail = true
                } 
          }
        }
        
        // Sending Email Notiifcation

        stage("Send Email Notification") {
            steps {
                script {
                    if (sendAlert) { //If the data is present in the table then send email alert    
                        echo "Sending Email Alert to  SRE." 
	                    emailext body: """Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br><br>The GCR images which are older than 21 days for  are below - <br>
                        <br> ${expiringRepos}<br>
                        <br>Thanks,
                        <br> SRE Team<br>
                        <br> More info at: ${env.BUILD_URL}""",
	                    mimeType: 'text/html',
	                    subject: " GCR images > 21 days job status:${currentBuild.currentResult}",
                        to: "${email_recipient}"
                    } else

                    if (sendEmail) { //If the data is not present in the table then send email notification    
                        echo "Sending Email Alert to  SRE." 
	                    emailext body: "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}<br>${currentBuild.currentResult}<br><br>The Jenkins job completed successfully and there is no image >=21 days to report.<br>Have a nice day !!<br><br>Thanks,<br> SRE Team<br><br>More info at: ${env.BUILD_URL}",
	                    mimeType: 'text/html',
	                    subject: " GCR images > 21 days  job status:${currentBuild.currentResult}",
                        to: "${email_recipient}"
                        echo "Email sent to  team"
                    }
                }
                
            }
        } 
  
    }  
}