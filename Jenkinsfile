// Cleanup /home/jenkins/.m2/repository recursively
def mvnCleanup_r(deepListMap) {
  deepListMap.each { entry ->
    sh """
      if [ -d '/home/jenkins/.m2/repository/io/cloudtrust/${entry.key}' ]; then
        rm -rf '/home/jenkins/.m2/repository/io/cloudtrust/${entry.key}'
      fi
    """
    mvnCleanup_r(entry.value)
  }
}

pipeline {
  agent any
  options {
    timestamps()
    timeout(time: 3600, unit: 'SECONDS')
  }
  parameters {
    string(name: 'SKIP_TESTS', defaultValue: 'false')
    string(name: 'ALT_DEPLOYMENT_REPOSITORY', defaultValue: 'artifactory::default::https://artifactory-test.multi.west.ch.elca-cloud.net/artifactory/cloudtrust-mvn/')
  }
  stages {
    stage('Build') {
      agent {
        label 'jenkins-slave-maven-ct'
      }
      steps {
        script {
          sh 'printenv'
          def modules = [
            "cloudtrust-parent": [
              "cloudtrust-test-tools": [],
              "cloudtrust-common": [],
              "kc-cloudtrust-module": ["kc-cloudtrust-common": []],
              "kc-cloudtrust-testsuite": ["kc-cloudtrust-test-tools": []]
            ]
          ]
          def builtModules = []
          // because we call "install" and call "rm" in a shared volume, we need a lock.
          lock("cloudtrust-parent") {
            withCredentials([usernamePassword(credentialsId: 'sonarqube', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
              def sonar_opts = "\"-Dsonar.login=${USER}\" \"-Dsonar.password=${PASS}\""
              // recurse in the children, run tests and code analysis
              modules["cloudtrust-parent"].each { submodule ->
                try {
                  sh """
                    cd "${submodule.key}"
                    mvn -B -T4 clean install \
                      -DskipTests=${params.SKIP_TESTS} \
                      spotbugs:spotbugs pmd:pmd dependency-check:check \
                      -Dsonar.java.spotbugs.reportPaths=target/spotbugsXml.xml \
                      -Dsonar.java.pmd.reportPaths=target/pmd.xml \
                      ${sonar_opts} \
                      sonar:sonar
                    cd ..
                  """
                  builtModules += submodule
                } catch (Exception e) {
                  mvnCleanup_r(builtModules)
                  throw e
                }
              }
            }
            // build the parent last, and deploy
            def isMaster = ""
            if (env.BRANCH_NAME == "master") {
              isMaster = "deploy -DaltDeploymentRepository=${params.ALT_DEPLOYMENT_REPOSITORY}"
            }
            sh """
              mvn -B -T4 install -DskipTests=true ${isMaster}
            """
            if (env.BRANCH_NAME != "master") {
              // we ran "install" on a PR, cleanup ~/.m2/repository/
              mvnCleanup_r(modules)
            }
          }
        }
      }
    }
  }
}
