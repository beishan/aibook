pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        string(name: 'NAS_HOST', defaultValue: '192.168.31.155', description: '飞牛 NAS 局域网 IP 或域名')
        string(name: 'FRONTEND_PORT', defaultValue: '8291', description: '前端对外端口')
        string(name: 'BACKEND_PORT', defaultValue: '8292', description: '后端对外端口')
        string(name: 'BOOKS_PATH', defaultValue: '/vol1/1000/books', description: '书籍存储路径')
        string(name: 'BOOKS_GID', defaultValue: '1001', description: '书库目录所属用户组 GID')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '紧急部署时跳过后端测试')
    }

    environment {
        APP_NAME = 'aibook'
        COMPOSE_PROJECT_NAME = 'aibook'
        PRODUCTION_ENV_CREDENTIAL_ID = 'aibook-production-env'
        PREVIOUS_IMAGES_FILE = '.aibook-previous-images'
        IMAGE_RETENTION_COUNT = '5'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'git@github.com:beishan/aibook.git'
                script {
                    def shortCommit = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()
                    env.RELEASE_TAG = "${env.BUILD_NUMBER}-${shortCommit}"
                    env.BACKEND_IMAGE = "aibook-backend:${env.RELEASE_TAG}"
                    env.FRONTEND_IMAGE = "aibook-frontend:${env.RELEASE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${shortCommit}"
                }
            }
        }

        stage('Validate') {
            steps {
                withCredentials([file(
                    credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                    variable: 'AIBOOK_ENV_FILE'
                )]) {
                    sh './scripts/deploy.sh validate "$AIBOOK_ENV_FILE"'
                }
            }
        }

        stage('Backend Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                withCredentials([file(
                    credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                    variable: 'AIBOOK_ENV_FILE'
                )]) {
                    sh './scripts/deploy.sh test "$AIBOOK_ENV_FILE"'
                }
            }
            post {
                always {
                    sh 'docker image rm "aibook-backend-test:${RELEASE_TAG}" >/dev/null 2>&1 || true'
                }
            }
        }

        stage('Build Images') {
            steps {
                withCredentials([file(
                    credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                    variable: 'AIBOOK_ENV_FILE'
                )]) {
                    sh './scripts/deploy.sh build "$AIBOOK_ENV_FILE"'
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(
                    credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                    variable: 'AIBOOK_ENV_FILE'
                )]) {
                    sh './scripts/deploy.sh deploy "$AIBOOK_ENV_FILE" "$PREVIOUS_IMAGES_FILE"'
                }
            }
        }

        stage('Public Health Check') {
            steps {
                sh '''
                    set -eu

                    check_url() {
                        name="$1"
                        url="$2"
                        attempt=1

                        while [ "$attempt" -le 20 ]; do
                            echo "${name} health check ${attempt}/20: ${url}"
                            status="$(curl -sS -o /dev/null -w '%{http_code}' \
                                --connect-timeout 5 --max-time 10 "${url}" || true)"
                            if [ "${status}" = "200" ]; then
                                echo "${name} health check passed."
                                return 0
                            fi
                            echo "${name} returned ${status:-curl-error}; retrying in 5s."
                            sleep 5
                            attempt=$((attempt + 1))
                        done

                        echo "${name} health check failed."
                        return 1
                    }

                    check_url backend "http://${NAS_HOST}:${BACKEND_PORT}/actuator/health"
                    check_url frontend "http://${NAS_HOST}:${FRONTEND_PORT}/"
                '''
            }
        }
    }

    post {
        success {
            withCredentials([file(
                credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                variable: 'AIBOOK_ENV_FILE'
            )]) {
                sh './scripts/deploy.sh cleanup "$AIBOOK_ENV_FILE" || true'
            }
            echo "aibook ${env.RELEASE_TAG} 构建和部署成功。"
        }
        failure {
            script {
                if (fileExists(env.PREVIOUS_IMAGES_FILE)) {
                    withCredentials([file(
                        credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID,
                        variable: 'AIBOOK_ENV_FILE'
                    )]) {
                        sh './scripts/rollback.sh "$AIBOOK_ENV_FILE" "$PREVIOUS_IMAGES_FILE" || true'
                    }
                }
            }
            echo 'aibook 构建或部署失败，已执行可用的自动回滚。'
        }
        always {
            cleanWs()
        }
    }
}
