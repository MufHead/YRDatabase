pipeline {
    agent any

    tools {
        // 配置 JDK 17
        jdk 'JDK17'
    }

    environment {
        // 构建配置
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '检出代码...'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '开始构建项目...'
                script {
                    if (isUnix()) {
                        sh './gradlew clean build --no-daemon'
                    } else {
                        bat 'gradlew.bat clean build --no-daemon'
                    }
                }
            }
        }

        stage('Test') {
            steps {
                echo '运行测试...'
                script {
                    if (isUnix()) {
                        sh './gradlew test --no-daemon'
                    } else {
                        bat 'gradlew.bat test --no-daemon'
                    }
                }
            }
            post {
                always {
                    // 发布测试报告
                    junit '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Archive') {
            steps {
                echo '归档构建产物...'
                // 归档 JAR 文件
                archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true
            }
        }

        stage('Publish') {
            when {
                // 仅在 master 分支发布
                branch 'master'
            }
            steps {
                echo '发布到仓库...'
                script {
                    if (isUnix()) {
                        sh './gradlew publish --no-daemon'
                    } else {
                        bat 'gradlew.bat publish --no-daemon'
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ 构建成功！'
        }
        failure {
            echo '❌ 构建失败！'
        }
        always {
            // 清理工作空间
            cleanWs()
        }
    }
}
