pipeline {
    agent none
    options {
        timestamps()
        disableConcurrentBuilds()
        skipStagesAfterUnstable()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '5'))
        skipDefaultCheckout(true)
    }
    triggers { pollSCM('H/5 * * * *') }
    stages {
        stage('Build, test and publish') {
            agent { label 'flight-backend' }
            stages {
                stage('Checkout') {
                    steps {
                        deleteDir()
                        script {
                            def revision = checkout scm
                            env.GIT_COMMIT = revision.GIT_COMMIT
                            env.IMAGE_TAG = "${env.GIT_COMMIT.take(12)}-${env.BUILD_NUMBER}"
                            env.AUTO_DEPLOY = (revision.GIT_BRANCH in [
                                'origin/jenkins-ci', 'refs/remotes/origin/jenkins-ci', 'jenkins-ci'
                            ]).toString()
                        }
                    }
                }
                stage('Verify publishing identity') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'flight-ecr-publisher',
                            usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                            sh 'bash ci/publish.sh verify'
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
                stage('Publish images to ECR') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'flight-ecr-publisher',
                            usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                            sh 'bash ci/publish.sh'
                        }
                    }
                }
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/TEST-*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'integration-tests/target/cucumber.*,**/target/*-app.jar,target/release-images.tsv,target/release-tag.txt', allowEmptyArchive: true
                    sh 'bash ci/backend.sh cleanup'
                }
            }
        }
        stage('Deploy backend') {
            when { expression { env.AUTO_DEPLOY == 'true' } }
            steps {
                // No agent here: the release job needs the same single worker.
                build job: 'flight-backend-release', wait: true, propagate: true,
                    parameters: [
                        string(name: 'IMAGE_TAG', value: env.IMAGE_TAG),
                        booleanParam(name: 'CHECK_ONLY', value: false)
                    ]
            }
        }
    }
}
