def call(body){

    def branch = "${env.BRANCH_NAME}"
    def repoName = REPONAME
    def featureimage = FEATUREIMAGE
    def envconfigTag = NAMESPACE

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
                sh "git checkout ${branch}"	
            }

            stage("Intialise workspace"){
                println "$repoName $featureimage $envconfigTag"
                sh """
                    ls -la
                    cat charts/docker.app.yaml
                    helm --version
                """
            }
        }
    }
}