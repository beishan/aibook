#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/docker-compose.yml"
ACTION="${1:-deploy}"
ENV_FILE="${2:-${PROJECT_DIR}/docker/.env.production}"
STATE_FILE="${3:-${PROJECT_DIR}/.aibook-previous-images}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-aibook}"
BACKUP_VOLUME="${BACKUP_VOLUME:-aibook-backups}"
DEPLOY_STATE_VOLUME="${DEPLOY_STATE_VOLUME:-aibook-deploy-state}"
BACKUP_RETENTION_COUNT="${BACKUP_RETENTION_COUNT:-10}"
HEALTH_RETRIES="${HEALTH_RETRIES:-36}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"
IMAGE_RETENTION_COUNT="${IMAGE_RETENTION_COUNT:-5}"

if docker compose version >/dev/null 2>&1; then
    COMPOSE_COMMAND=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_COMMAND=(docker-compose)
else
    echo "错误：未找到 docker compose 或 docker-compose。" >&2
    exit 1
fi

if [[ ! -r "${ENV_FILE}" ]]; then
    echo "错误：生产环境变量文件不可读：${ENV_FILE}" >&2
    exit 1
fi

compose() {
    "${COMPOSE_COMMAND[@]}" \
        --project-name "${COMPOSE_PROJECT_NAME}" \
        --env-file "${ENV_FILE}" \
        --file "${COMPOSE_FILE}" \
        "$@"
}

container_image() {
    local container_name="$1"
    docker inspect --format '{{.Config.Image}}' "${container_name}" 2>/dev/null || true
}

container_health() {
    local container_name="$1"
    docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "${container_name}" 2>/dev/null || echo "missing"
}

record_previous_images() {
    local backend_image
    local frontend_image

    backend_image="$(container_image aibook-backend)"
    frontend_image="$(container_image aibook-frontend)"

    {
        printf 'BACKEND_IMAGE=%q\n' "${backend_image}"
        printf 'FRONTEND_IMAGE=%q\n' "${frontend_image}"
    } > "${STATE_FILE}"

    echo "已记录部署前镜像："
    echo "  backend=${backend_image:-<首次部署>}"
    echo "  frontend=${frontend_image:-<首次部署>}"
}

backup_database() {
    local backup_name

    if ! docker inspect aibook-postgres >/dev/null 2>&1; then
        echo "PostgreSQL 容器尚未运行，跳过首次部署前备份。"
        return 0
    fi

    backup_name="aibook-$(date '+%Y%m%d-%H%M%S').dump"
    docker volume create "${BACKUP_VOLUME}" >/dev/null

    echo "正在备份 PostgreSQL：${backup_name}"
    docker exec aibook-postgres pg_dump -U aibook -d aibook -Fc |
        docker run --rm -i \
            -v "${BACKUP_VOLUME}:/backups" \
            postgres:16-alpine \
            sh -c "cat > '/backups/${backup_name}'"

    docker run --rm \
        -v "${BACKUP_VOLUME}:/backups" \
        postgres:16-alpine \
        sh -c "ls -1t /backups/aibook-*.dump 2>/dev/null | awk 'NR > ${BACKUP_RETENTION_COUNT}' | while IFS= read -r file; do rm -f \"\$file\"; done"
}

wait_for_containers() {
    local attempt
    local all_healthy
    local container_name
    local status
    local containers=(
        aibook-postgres
        aibook-redis
        aibook-minio
        aibook-backend
        aibook-frontend
    )

    for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
        all_healthy=true
        echo "容器健康检查 ${attempt}/${HEALTH_RETRIES}"

        for container_name in "${containers[@]}"; do
            status="$(container_health "${container_name}")"
            echo "  ${container_name}: ${status}"
            if [[ "${status}" != "healthy" ]]; then
                all_healthy=false
            fi
        done

        if [[ "${all_healthy}" == "true" ]]; then
            return 0
        fi

        sleep "${HEALTH_INTERVAL_SECONDS}"
    done

    return 1
}

persist_release_state() {
    local current_state_file
    current_state_file="$(mktemp)"

    {
        printf 'BACKEND_IMAGE=%q\n' "$(container_image aibook-backend)"
        printf 'FRONTEND_IMAGE=%q\n' "$(container_image aibook-frontend)"
    } > "${current_state_file}"

    docker volume create "${DEPLOY_STATE_VOLUME}" >/dev/null

    if [[ -s "${STATE_FILE}" ]]; then
        docker run --rm -i \
            -v "${DEPLOY_STATE_VOLUME}:/state" \
            postgres:16-alpine \
            sh -c 'cat > /state/previous.env' < "${STATE_FILE}"
    fi

    docker run --rm -i \
        -v "${DEPLOY_STATE_VOLUME}:/state" \
        postgres:16-alpine \
        sh -c 'cat > /state/current.env' < "${current_state_file}"

    rm -f "${current_state_file}"
}

rollback_from_state() {
    local previous_backend_image
    local previous_frontend_image

    if [[ ! -r "${STATE_FILE}" ]]; then
        echo "错误：找不到回滚状态文件：${STATE_FILE}" >&2
        return 1
    fi

    # 此文件只由 record_previous_images 生成，内容经过 shell 转义。
    # shellcheck disable=SC1090
    source "${STATE_FILE}"
    previous_backend_image="${BACKEND_IMAGE:-}"
    previous_frontend_image="${FRONTEND_IMAGE:-}"

    if [[ -z "${previous_backend_image}" || -z "${previous_frontend_image}" ]]; then
        echo "没有完整的上一版本镜像，无法自动回滚（通常发生在首次部署）。" >&2
        return 1
    fi

    export BACKEND_IMAGE="${previous_backend_image}"
    export FRONTEND_IMAGE="${previous_frontend_image}"

    echo "正在回滚："
    echo "  backend=${BACKEND_IMAGE}"
    echo "  frontend=${FRONTEND_IMAGE}"

    compose up -d --no-build backend frontend

    if ! wait_for_containers; then
        echo "错误：回滚后服务仍未恢复健康。" >&2
        compose logs --no-color --tail=200 backend frontend || true
        return 1
    fi

    echo "已恢复上一版本。"
}

cleanup_repository_images() {
    local repository="$1"
    local current_image="$2"
    local kept=0
    local image_ref
    local image_refs=()

    while IFS= read -r image_ref; do
        image_refs+=("${image_ref}")
    done < <(
        docker image ls "${repository}" \
            --format '{{.Repository}}:{{.Tag}}' |
            grep -v ':<none>$' |
            awk '!seen[$0]++'
    )

    for image_ref in "${image_refs[@]}"; do
        if [[ "${image_ref}" == "${current_image}" ]]; then
            kept=$((kept + 1))
            continue
        fi

        if ((kept < IMAGE_RETENTION_COUNT)); then
            kept=$((kept + 1))
            continue
        fi

        echo "清理旧镜像：${image_ref}"
        docker image rm "${image_ref}" >/dev/null 2>&1 || true
    done
}

cleanup_images() {
    cleanup_repository_images "aibook-backend" "$(container_image aibook-backend)"
    cleanup_repository_images "aibook-frontend" "$(container_image aibook-frontend)"
}

deploy_release() {
    record_previous_images
    backup_database

    echo "正在更新 aibook 服务。"
    if ! compose up -d --remove-orphans; then
        echo "Compose 更新失败，尝试恢复上一版本。" >&2
        rollback_from_state || true
        return 1
    fi

    if ! wait_for_containers; then
        echo "新版本健康检查失败，输出最近日志并尝试回滚。" >&2
        compose logs --no-color --tail=200 || true
        rollback_from_state || true
        return 1
    fi

    persist_release_state
    echo "aibook 新版本部署成功。"
}

case "${ACTION}" in
    validate)
        compose config --quiet
        echo "Docker Compose 配置校验通过。"
        ;;
    test)
        docker build \
            --file "${PROJECT_DIR}/backend/Dockerfile" \
            --target test \
            --tag "aibook-backend-test:${RELEASE_TAG:-local}" \
            "${PROJECT_DIR}/backend"
        ;;
    build)
        compose build backend frontend
        ;;
    deploy)
        deploy_release
        ;;
    health)
        wait_for_containers
        ;;
    cleanup)
        cleanup_images
        ;;
    rollback)
        rollback_from_state
        ;;
    logs)
        compose logs --no-color --tail=200
        ;;
    *)
        echo "用法：$0 {validate|test|build|deploy|health|cleanup|rollback|logs} [env-file] [state-file]" >&2
        exit 2
        ;;
esac
