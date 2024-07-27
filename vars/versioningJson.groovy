import groovy.json.JsonBuilder

void call(String ecrTagName, String targetJson, String branch){
    def registryPath = "public.ecr.aws/j9k0i2s2/dev-or-employee-system"
    def json = readJson file: targetJson
    def originalversion = json.version
    
    def releaseVersion = "${originalversion}"
    def majorVersion = originalversion.split("\\.", -1)[0]
    def minorVersion = originalversion.split("\\.", -1)[1]
    def buildVersion = originalversion.split("\\.", -1)[2]

    echo "build version is: $buildVersion"
    buildVersion = buildVersion.toInteger() + 1
    def newJsonVersion = "${majorVersion}.${minorVersion}.${buildVersion}"

    def artifactId = ecrTagName.toLowerCase()
    def imageVars = artifactId + '-' + branch + '-' + originalversion
    def imageTag = "${registryPath}:${imageVars}"   

    echo """###########################################
            |############# Version Details #############
            |##########################################
            |Current JSON version : ${originalversion}
            |Release version to be : ${releaseVersion}
            |New JSON version : ${newJsonVersion}
            |Docker Image will be : ${imageTag}
            |##########################################""".stripMargin()

    return [originalversion, releaseVersion, newJsonVersion, imageTag]
}