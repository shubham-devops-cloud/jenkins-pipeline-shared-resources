def call(body){

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST   //Its telling the closure (body) to look at the delegate first to find properties 
    body.delegate = config                          //assign the config map as delegate for the body object
    body()                                          //variables are scoped to the config map
    
    def branch = "${env.BRANCH_NAME}"
    def doBuild = true
    //def originalversion, releaseVersion, newPomVersion, imageTag
    def registryName = 'public.ecr.aws/j9k0i2s2/dev-or-employee-system'

    node("worker_docker_slave"){
        properties([
            buildDiscarder(
                logRotator(
                    //artifactDaysToKeepStr: "",
                    artifactNumToKeepStr: "5",
                    //daysToKeepStr: "",
                    numToKeepStr: "5"
                )
            )
        ])

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

        //This stage is to stop rebuild of compenents if nothing has changes in the repo
        stage("Check last commit (if the build stops here there are no changes)"){
            def USER = sh(script: 'git log -1 --format=%an', returnStdout: true).trim()

            if(USER == "jenkins-docker"){
                echo """####################################################### 
                            | No code change, the last change was to the version
                            | The build has been skipped to avoid the infinite loop
                            | The last user to commit was ${USER}
                            | #######################################################""".stripMargin()
                
                try{
                    timeout(time: 60, unit: 'SECONDS'){
                        doBuild = input(
                            id: 'Proceed1', 
                            message: 'Tick the box to container build', 
                            parameters: [
                                [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: 'continue to build']
                            ]
                        )
                    }
                }
                catch(err){
                    doBuild = false
                    echo "timeout occured, build is not continuing"
                }
            }

            if(doBuild){
                def ecrTagName = config.ecrTagName.trim()
                println "Registry name: $registryName"

                //call versioning and work on next maven version
                stage("Get Version Details"){
                    def versionArray = versioning(ecrTagName, config.targetPom, branch)
                    
                    originalversion = versionArray[0]
                    releaseVersion = versionArray[1]
                    newPomVersion = versionArray[2]
                    imageTag = versionArray[3]
                }

                stage("Build Docker Image"){
                    def filedocker = config.dockerFile.trim()
                    println "DOCKEFILE: $filedocker"
                    sh "id"
                    sh "docker build -t ${imageTag} --file=${config.dockerFile} ."
                }

                stage("Versioning - updating to new release"){
                    sh """
                        sed -i 's/$originalversion/$newPomVersion/g' pom.xml                     
                    """

                }
            }
        }
    }
}