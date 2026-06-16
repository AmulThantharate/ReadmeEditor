pipeline {
    agent any

    tools {
        jdk 'jdk-21'
        maven 'maven-3.9'
    }

    environment {
        // Artifactory server ID configured in Jenkins Global Configuration
        SERVER_ID = 'jfrog-artifactory-server'
        
        // Target repositories in Artifactory
        RELEASE_REPO = 'maven-release-local'
        SNAPSHOT_REPO = 'maven-snapshot-local'
    }

    stages {
        stage('Checkout 📥') {
            steps {
                checkout scm
            }
        }

        stage('Artifactory Configuration ⚙️') {
            steps {
                script {
                    // Obtain Artifactory Server instance defined in Jenkins
                    def server = Artifactory.server(env.SERVER_ID)
                    
                    // Create Maven Build instance
                    def rtMaven = Artifactory.newMavenBuild()
                    rtMaven.tool = 'maven-3.9' // Must match the name of the Maven tool configured in Jenkins
                    
                    // Configure deployment and resolution repositories
                    rtMaven.deployer releaseRepo: env.RELEASE_REPO, snapshotRepo: env.SNAPSHOT_REPO, server: server
                    rtMaven.resolver releaseRepo: env.RELEASE_REPO, snapshotRepo: env.SNAPSHOT_REPO, server: server
                    
                    // Attach to pipeline context to share build info in subsequent steps
                    env.rtMaven = rtMaven
                }
            }
        }

        stage('Build & Test 🏗️') {
            steps {
                script {
                    // Run the build using Jenkins Artifactory plugin helper
                    // This collects deployment artifacts and build details automatically
                    def buildInfo = Artifactory.newBuildInfo()
                    
                    // Run maven clean install/deploy
                    env.rtMaven.run pom: 'pom.xml', goals: 'clean deploy', buildInfo: buildInfo
                    
                    // Save build info reference to environment
                    env.buildInfo = buildInfo
                }
            }
        }

        stage('Publish Build Info 📊') {
            steps {
                script {
                    // Publish build info to Artifactory
                    def server = Artifactory.server(env.SERVER_ID)
                    server.publishBuildInfo(env.buildInfo)
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo 'Pipeline completed successfully! Artifacts pushed to JFrog Artifactory.'
        }
        failure {
            echo 'Pipeline failed. Please check build logs.'
        }
    }
}
