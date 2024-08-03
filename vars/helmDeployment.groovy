def call(body){

    def repoName = REPONAME
    def featureimage = FEATUREIMAGE
    def envconfigTag = NAMESPACE
    def dockerImagePath = "charts/docker.app.yaml"

    node("worker_docker_slave"){
        properties([
            buildDiscarder(
                logRotator(
                    //artifactDaysToKeepStr: "",
                    artifactNumToKeepStr: "5",
                    //daysToKeepStr: "",
                    numToKeepStr: "5"
                )
            ),
            disableConcurrentBuilds()
        ])

        timestamps {
            stage("Checkout scm"){
                //checkout scm
                checkout([
                    $class: 'GitSCM',
                    branches: scm.branches,
                    extensions: [[$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true, timeout: 15]],
                    userRemoteConfigs: scm.userRemoteConfigs
                ])
            }

            stage("Intialise workspace"){
                println "$repoName $featureimage $envconfigTag"

                if (featureimage == null){
                    println "featureimage not passed while running build so failing this build"
                    throw new RuntimeException("Sonarqube check has failed, this component is under threshold")
                }

                sh "cat charts/docker.app.yaml"
                sh "sed -i '/DOCKER_APP_IMAGE:/c DOCKER_APP_IMAGE: $featureimage' $dockerImagePath"
                sh "cat charts/docker.app.yaml"
            }
        }
    }
}