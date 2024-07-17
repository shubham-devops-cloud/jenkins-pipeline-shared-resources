import groovy.json.JsonBuilder;
import org.common.SonarQubeDetails

void call(String targetPom){
    def sonarKey, sonarProps, sonarResult, sonarProjectName
    def sonarExtURL = "http://192.168.0.112:9000/"

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

                    println "QualityGateName: $sonarQualityGateName"
                    println "Default QualityGateName: $sonarQualityGateName"
                }

            }
        }
    }
}