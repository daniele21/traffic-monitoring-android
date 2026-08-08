#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_BIN="$(bash "${ROOT_DIR}/scripts/resolve-gradle.sh")"
exec "${GRADLE_BIN}" "$@"
