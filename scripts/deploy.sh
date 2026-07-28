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
COMPOSE_OVERRIDE_FILE=""

cleanup_temp_files() {
    if [[ -n "${COMPOSE_OVERRIDE_FILE}" && -f "${COMPOSE_OVERRIDE_FILE}" ]]; then
        rm -f "${COMPOSE_OVERRIDE_FILE}"
    fi
}
trap cleanup_temp_files EXIT

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

trim_whitespace() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "${value}"
}

env_file_value() {
    local key="$1"
    sed -n "s/^${key}=//p" "${ENV_FILE}" | tail -n 1
}

yaml_quote() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '"%s"' "${value}"
}

validate_mount_path() {
    local label="$1"
    local path="$2"

    if [[ "${path}" != /* ]]; then
        echo "错误：${label}必须是绝对路径：${path}" >&2
        return 1
    fi
    if [[ "${path}" == "/" ]]; then
        echo "错误：${label}不能挂载文件系统根目录。" >&2
        return 1
    fi
    if [[ "${path}" == *$'\n'* || "${path}" == *$'\r'* || "${path}" == *$'\t'* ]]; then
        echo "错误：${label}不能包含控制字符：${path}" >&2
        return 1
    fi
    if [[ "/${path#/}/" == */../* ]]; then
        echo "错误：${label}不能包含 .. 路径段：${path}" >&2
        return 1
    fi
}

prepare_books_override() {
    local mounts_config
    local gids_config
    local raw_line
    local line
    local source_path
    local target_path
    local mount_mode
    local extra_part
    local gid
    local read_only
    local mount_count=0
    local gid_count=0
    local normalized_mounts
    local normalized_gids
    local seen_targets=$'\n'
    local seen_gids=$'\n'

    mounts_config="${BOOKS_MOUNTS:-$(env_file_value BOOKS_MOUNTS)}"
    gids_config="${BOOKS_GIDS:-$(env_file_value BOOKS_GIDS)}"
    normalized_mounts="${mounts_config//;/$'\n'}"
    normalized_gids="${gids_config//[;,]/$'\n'}"
    normalized_gids="${normalized_gids// /$'\n'}"

    if [[ -z "$(trim_whitespace "${normalized_mounts}")" \
            && -z "$(trim_whitespace "${normalized_gids}")" ]]; then
        return
    fi

    COMPOSE_OVERRIDE_FILE="$(mktemp)"
    printf '%s\n' "services:" "  backend:" > "${COMPOSE_OVERRIDE_FILE}"

    while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
        line="$(trim_whitespace "${raw_line}")"
        if [[ -z "${line}" || "${line}" == \#* ]]; then
            continue
        fi

        IFS=':' read -r source_path target_path mount_mode extra_part <<< "${line}"
        source_path="$(trim_whitespace "${source_path:-}")"
        target_path="$(trim_whitespace "${target_path:-}")"
        mount_mode="$(trim_whitespace "${mount_mode:-ro}")"

        if [[ -n "${extra_part:-}" || -z "${source_path}" || -z "${target_path}" ]]; then
            echo "错误：BOOKS_MOUNTS 格式应为 宿主机路径:容器路径[:ro|rw]：${line}" >&2
            return 1
        fi
        validate_mount_path "书库宿主机路径" "${source_path}"
        validate_mount_path "书库容器路径" "${target_path}"

        if [[ "${target_path}" != /scanfolder/* ]]; then
            echo "错误：附加书库容器路径必须位于 /scanfolder/ 下：${target_path}" >&2
            return 1
        fi
        if [[ "${mount_mode}" != "ro" && "${mount_mode}" != "rw" ]]; then
            echo "错误：挂载模式只支持 ro 或 rw：${mount_mode}" >&2
            return 1
        fi
        if [[ "${seen_targets}" == *$'\n'"${target_path}"$'\n'* ]]; then
            echo "错误：BOOKS_MOUNTS 中容器路径重复：${target_path}" >&2
            return 1
        fi
        seen_targets+="${target_path}"$'\n'

        if ((mount_count == 0)); then
            printf '%s\n' "    volumes:" >> "${COMPOSE_OVERRIDE_FILE}"
        fi
        if [[ "${mount_mode}" == "ro" ]]; then
            read_only=true
        else
            read_only=false
        fi
        {
            printf '      - type: bind\n'
            printf '        source: %s\n' "$(yaml_quote "${source_path}")"
            printf '        target: %s\n' "$(yaml_quote "${target_path}")"
            printf '        read_only: %s\n' "${read_only}"
            printf '        bind:\n'
            printf '          create_host_path: false\n'
        } >> "${COMPOSE_OVERRIDE_FILE}"
        echo "  附加书库：${source_path} -> ${target_path} (${mount_mode})"
        mount_count=$((mount_count + 1))
    done <<< "${normalized_mounts}"

    while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
        gid="$(trim_whitespace "${raw_line}")"
        if [[ -z "${gid}" || "${gid}" == \#* ]]; then
            continue
        fi
        if [[ ! "${gid}" =~ ^[0-9]+$ ]]; then
            echo "错误：BOOKS_GIDS 只能包含数字 GID：${gid}" >&2
            return 1
        fi
        if [[ "${seen_gids}" == *$'\n'"${gid}"$'\n'* ]]; then
            continue
        fi
        seen_gids+="${gid}"$'\n'

        if ((gid_count == 0)); then
            printf '%s\n' "    group_add:" >> "${COMPOSE_OVERRIDE_FILE}"
        fi
        printf '      - %s\n' "$(yaml_quote "${gid}")" >> "${COMPOSE_OVERRIDE_FILE}"
        gid_count=$((gid_count + 1))
    done <<< "${normalized_gids}"

    if ((mount_count == 0 && gid_count == 0)); then
        rm -f "${COMPOSE_OVERRIDE_FILE}"
        COMPOSE_OVERRIDE_FILE=""
        return
    fi

    echo "已生成附加书库挂载配置：${mount_count} 个目录，${gid_count} 个附加 GID。"
}

prepare_books_override

compose() {
    local compose_args=(
        --project-name "${COMPOSE_PROJECT_NAME}"
        --env-file "${ENV_FILE}"
        --file "${COMPOSE_FILE}"
    )
    if [[ -n "${COMPOSE_OVERRIDE_FILE}" ]]; then
        compose_args+=(--file "${COMPOSE_OVERRIDE_FILE}")
    fi
    "${COMPOSE_COMMAND[@]}" "${compose_args[@]}" "$@"
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
