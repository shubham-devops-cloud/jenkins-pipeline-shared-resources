import groovy.json.JsonBuilder;
import org.common.SonarQubeDetails

void call(String targetPom){
    def sonarKey, sonarProps, sonarResult, sonarProjectName
    def sonarExtURL = "http://192.168.0.112:9000"

    node("worker_docker_slave"){
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
                        url = new URL (sonarExtURL + "/web_api/api/projects/search?projects=${sonarKey}")
                        sh "curl -u ${sonarCred}: ${url} -o liveProjects.json"
                        sh "cat liveProjects.json"

                        def liveProjectsJson = readJSON file: "liveProjects.json"

                        //Does the response from sonarqube contain a project
                        if (liveProjectsJson.paging.total == 0){
                            //The project doesn't exist....Create new one
                            try{
                                println "Sonar project doesn't exist so creating new project in Sonarqube"
                                url = new URL (sonarExtURL + "/web_api/api/projects/create")
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
                            url = new URL (sonarExtURL + "/web_api/api/qualitygates/select")
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
    }
}