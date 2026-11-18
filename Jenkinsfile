pipeline {
    agent { label 'flight-backend' }
    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '5'))
        skipDefaultCheckout(true)
    }
    triggers { pollSCM('H/5 * * * *') }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                script {
                    env.IMAGE_TAG = "${env.GIT_COMMIT.take(12)}-${env.BUILD_NUMBER}"
                }
            }
        }
        stage('Build Java services') {
            steps { sh 'bash ci/backend.sh build' }
        }
        stage('Prepare disposable database') {
            steps { sh 'bash ci/backend.sh database' }
        }
        stage('Automated tests') {
            steps { sh 'bash ci/backend.sh tests' }
        }
        stage('Cucumber workflows') {
            steps { sh 'bash ci/backend.sh cucumber' }
        }
        stage('Build Docker images') {
            steps { sh 'bash ci/backend.sh images' }
        }
    }
    post {
        always {
            junit testResults: '**/target/surefire-reports/TEST-*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'integration-tests/target/cucumber.*,**/target/*-app.jar', allowEmptyArchive: true
            sh 'bash ci/backend.sh cleanup'
        }
    }
}
