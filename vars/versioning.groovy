void call(String ecrTagName, String targetPom, String branch){
    def registryPath = "public.ecr.aws/j9k0i2s2/dev-or-employee-system"
    def pom = readMavenPom file: targetPom
    def originalversion = pom.version
    
    def releaseVersion = "${originalversion}"
    def majorVersion = originalversion.split("\\.", -1)[0]
    def minorVersion = originalversion.split("\\.", -1)[1]
    def buildVersion = originalversion.split("\\.", -1)[2]

    echo "build version is: $buildVersion"
    buildVersion = buildVersion.toInteger() + 1
    def newPomVersion = "${majorVersion}.${minorVersion}.${buildVersion}"

    def artifactId = ecrTagName.toLowerCase()
    def imageVars = artifactId + '-' + branch + '-' + originalversion
    def imageTag = "${registryPath}:${imageVars}"   

    echo """###########################################
            |############# Version Details #############
            |##########################################
            |Current POM version : ${originalversion}
            |Release version to be : ${releaseVersion}
            |New POM version : ${newPomVersion}
            |Docker Image will be : ${imageTag}
            |##########################################""".stripMargin()

    return [originalversion, releaseVersion, newPomVersion, imageTag]
}