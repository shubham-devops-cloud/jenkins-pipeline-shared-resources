import groovy.json.JsonBuilder;
import org.common.SonarQubeDetails

void call(String targetPom, string projectType){
    def sonarKey, sonarProps, sonarResult, sonarProjectName
    def sonarExtURL = "http://192.168.0.112:9000"
    def mavenHome = "/opt/maven/bin/mvn"

    node("worker_docker_slave"){
        def scannerHome = tool name: 'SonarScanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'

        stage("Sonar: Checkout"){
            checkout scm
        }

        stage("Sonar: Validate"){
            sh "pwd"
            sh "ls -ltr"
        }

        //attempt to create the sonar project advance and assign qualitygate
        stage("Sonar: Set-up"){
            def sonarQubeDetails = new SonarQubeDetails()
            Boolean doSetup = true

            try{
                withCredentials([string(credentialsId: 'SonarQube-Token', variable: 'sonarCred')]) {
                    println "SONAR CREDENTIALS ARE READY"
                }
            }
            catch(e){
                println "SONAR CREDENTIALS ARE NOT SET"
                println e
                doSetup = false
            }

            if(doSetup){
                withCredentials([string(credentialsId: 'SonarQube-Token', variable: 'sonarCred')]) {
                    def pom = readMavenPom file: targetPom
                    def artifactId = pom.artifactId
                    def groupId = pom.groupId 

                    sonarKey = groupId + ":" + artifactId + ":" + env.BRANCH_NAME
                    sonarProjectName = artifactId + "-" + env.BRANCH_NAME
                    def sonarName = pom.name

                    def sonarQualityGateName = sonarQubeDetails.getProjectGate(artifactId)
                    def defaultQualityGateName = sonarQubeDetails.getProjectGate("default")

                    def url
                    Boolean newProject = false

                    //Check is project exists
                    try{
                        url = new URL (sonarExtURL + "/api/projects/search?projects=${sonarKey}")
                        sh "curl -u ${sonarCred}: ${url} -o liveProjects.json"
                        sh "cat liveProjects.json"

                        def liveProjectsJson = readJSON file: "liveProjects.json"

                        //Does the response from sonarqube contain a project
                        if (liveProjectsJson.paging.total == 0){
                            //The project doesn't exist....Create new one
                            try{
                                println "Sonar project doesn't exist so creating new project in Sonarqube"
                                url = new URL (sonarExtURL + "/api/projects/create")
                                sh "curl -u ${sonarCred}: -d \"project=${sonarKey}&name=${sonarProjectName}\" ${url}"
                                newProject = true
                            }
                            catch(e){
                                println "Was unable to setup sonarProject it may already exist"
                                println e
                            }
                        }
                    }
                    catch(e){
                        println "Something went wrong while checking the sonarProject"
                        println e
                    }


                    if(!newProject){
                        println "The sonar project already exist"
                    }
                    else{
                        println "Assigning the qualityGate " + sonarQualityGateName + " to the sonar Project"
                        try{
                            url = new URL (sonarExtURL + "/api/qualitygates/select")
                            sh "curl -u ${sonarCred}: -d \"projectKey=${sonarKey}&gateName=${sonarQualityGateName}\" ${url}"
                        }
                        catch(e){
                            println "Was unable to assign the quality gate to the project"
                            println e
                        }
                    }
                }
            }
        }

        try{
            stage("Sonar: Analysis"){
                withSonarQubeEnv('SonarQube'){
                    if (projectType == "python" || projectType == "go"){
                        sh "${scannerHome}/bin/sonar-scanner -Dsonar.sources=. -Dsonar.projectKey=${sonarKey} -Dsonar.projectName=${sonarProjectName}"
                    }
                    else if (projectType == "java"){
                        sh "${mavenHome} clean verify sonar:sonar -Dsonar.projectKey=${sonarKey} -Dsonar.projectName=${sonarProjectName}"
                    }
                    else{
                        println "projectType not updated in Jenkinsfile"
                    }
                }
            }

            stage("Sonar: Results"){
                sh "sleep 180"
                //Get the report task written by sonar with taskID
                if (projectType == "python" || projectType == "go"){
                    def props = readProperties file: '.scannerwork/report-task.txt'
                }
                else if (projectType == "java"){
                    def props = readProperties file: 'target/sonar/report-task.txt'
                }
                else{
                    println "projectType not updated in Jenkinsfile"
                }
                

                sh "cat .scannerwork/report-task.txt"
                def sonarServerUrl = props['serverUrl']
                def ceTaskUrl = props['ceTaskUrl']
                def ceTask

                withCredentials([string(credentialsId: 'SonarQube-Token', variable: 'sonarCred')]) {
                    //Get analysisId from sonar
                    def url = new URL(ceTaskUrl)
                    echo "waiting for analysis to complete...."
                    def analysisId
                    def attemptCounter = 0

                    while(analysisId == null && attemptCounter < 30){
                        sleep 5
                        sh "curl -u ${sonarCred}: ${url} -o ceTask.json"
                        def ceProps = readJSON file: "ceTask.json"
                        sh "cat ceTask.json"
                        analysisId = ceProps['task']['analysisId']
                        attemptCounter++
                    }
                    echo "ID: $analysisId"


                    //Get analsis result from Sonar
                    url = new URL(sonarServerUrl + "/api/qualitygates/project_status?analysisId=" + analysisId)
                    sh "curl -u ${sonarCred}: ${url} -o qualityGate.json"
                    def qgProps = readJSON file: "qualityGate.json"
                    def qualitygate = qgProps['projectStatus']['status']

                    if(qualitygate == "OK" || qualitygate == "WARN"){
                        echo "Quality Gate passed..... login to ${sonarExtURL}"
                        println(new JsonBuilder(qgProps).toPrettyString())
                        sonarResult = "passed"
                        sonarProps = qgProps
                    }
                    else{
                        echo "Quality Gate failed..... login to ${sonarExtURL}"
                        println(new JsonBuilder(qgProps).toPrettyString())
                        sonarResult = "failure"
                        sonarProps = qgProps
                    }
                }
            }
        }
        catch(Exception e){
            echo "Error: ${e}"
            echo "SONAR checks are failed: the quality gate will not of been tested"
            sonarResult = "aborted"
            qgProps = [:];
            sonarProps = qgProps
        }
        finally{
            sonarProps.sonarResult = sonarResult
            return sonarProps
        }
    }
}