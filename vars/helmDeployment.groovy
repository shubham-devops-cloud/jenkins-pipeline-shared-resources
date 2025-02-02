def call(body){

    def repoName = REPONAME
    def featureimage = FEATUREIMAGE
    def envconfigTag = NAMESPACE
    def dockerImagePath = "charts/values.yaml"

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

                if (featureimage?.trim()?.isEmpty()){
                    throw new RuntimeException("featureimage not passed while running build so failing this build")
                }

                sh "cat charts/values.yaml"
                sh "sed -i '/DOCKER_APP_IMAGE:/c DOCKER_APP_IMAGE: $featureimage' $dockerImagePath"
                sh "cat charts/values.yaml"
            }

            stage('Chart Linting'){
                dir("charts"){
                    sh "pwd"
                    sh "helm lint ."        
                }
            }

            // stage('Deploying application on k8s'){
            //     withCredentials([kubeconfigContent(credentialsId: 'KUBE-CONFIG', variable: 'KUBECONFIG_CONTENT')]) {
            //         sh "helm upgrade --install -n ${envconfigTag} ${repoName} ./charts --debug --timeout 900s --wait" 
            //     }
            // }
        }
    }
}