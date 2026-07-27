#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${1:-${PROJECT_DIR}/docker/.env.production}"
STATE_FILE="${2:-}"
DEPLOY_STATE_VOLUME="${DEPLOY_STATE_VOLUME:-aibook-deploy-state}"
TEMP_STATE_FILE=""

cleanup() {
    if [[ -n "${TEMP_STATE_FILE}" ]]; then
        rm -f "${TEMP_STATE_FILE}"
    fi
}
trap cleanup EXIT

if [[ -z "${STATE_FILE}" ]]; then
    TEMP_STATE_FILE="$(mktemp)"
    if ! docker run --rm \
        -v "${DEPLOY_STATE_VOLUME}:/state" \
        postgres:16-alpine \
        cat /state/previous.env > "${TEMP_STATE_FILE}"; then
        echo "错误：没有找到持久化的上一版本信息。" >&2
        exit 1
    fi
    STATE_FILE="${TEMP_STATE_FILE}"
fi

"${SCRIPT_DIR}/deploy.sh" rollback "${ENV_FILE}" "${STATE_FILE}"
