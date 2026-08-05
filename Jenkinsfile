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
        string(name: 'BOOKS_PATH', defaultValue: '/vol1/1000/books', description: '主书库路径，映射到 /scanfolder')
        string(name: 'BOOKS_GID', defaultValue: '1001', description: '主书库目录所属用户组 GID')
        text(
            name: 'BOOKS_MOUNTS',
            defaultValue: '',
            description: '附加书库，每行：宿主机绝对路径:/scanfolder/子目录[:ro|rw]；默认 ro'
        )
        string(
            name: 'BOOKS_GIDS',
            defaultValue: '',
            description: '附加书库所需的数字 GID，多个值使用逗号分隔'
        )
        string(
            name: 'FONTS_PATH',
            defaultValue: '/vol1/1000/books/fonts',
            description: '可选主字体目录宿主机路径；配置后只读映射到 /fontfolder'
        )
        string(
            name: 'FONTS_GID',
            defaultValue: '1001',
            description: '主字体目录所属用户组 GID；配置 FONTS_PATH 时必填'
        )
        text(
            name: 'FONT_MOUNTS',
            defaultValue: '',
            description: '可选附加字体目录，每行：宿主机绝对路径:/fontfolder/子目录[:ro]；始终只读'
        )
        string(
            name: 'FONT_GIDS',
            defaultValue: '',
            description: '附加字体目录所需的数字 GID，多个值使用逗号分隔'
        )
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '紧急部署时跳过后端测试')
        string(
            name: 'IMAGE_RETENTION_COUNT',
            defaultValue: '3',
            description: '部署成功后前端、后端各保留的镜像版本数（1-50，包含当前版本）'
        )
    }

    environment {
        APP_NAME = 'aibook'
        COMPOSE_PROJECT_NAME = 'aibook'
        PRODUCTION_ENV_CREDENTIAL_ID = 'aibook-production-env'
        PREVIOUS_IMAGES_FILE = '.aibook-previous-images'
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
                    def commitSubject = sh(
                        script: 'git log -1 --pretty=%s',
                        returnStdout: true
                    ).trim().replaceAll(/\s+/, ' ')
                    def maxTitleLength = 48
                    def abbreviatedSubject = commitSubject.length() > maxTitleLength
                        ? "${commitSubject.take(maxTitleLength)}…"
                        : commitSubject
                    if (!abbreviatedSubject) {
                        abbreviatedSubject = shortCommit
                    }
                    env.RELEASE_TAG = "${env.BUILD_NUMBER}-${shortCommit}"
                    env.BACKEND_IMAGE = "aibook-backend:${env.RELEASE_TAG}"
                    env.FRONTEND_IMAGE = "aibook-frontend:${env.RELEASE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${abbreviatedSubject}"
                    currentBuild.description = "提交 ${shortCommit}"
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
