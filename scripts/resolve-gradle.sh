#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_VERSION="8.9"
GRADLE_SHA256="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
BOOTSTRAP_ROOT="${ROOT_DIR}/.tooling"
GRADLE_HOME="${BOOTSTRAP_ROOT}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"
ZIP_PATH="${BOOTSTRAP_ROOT}/gradle-${GRADLE_VERSION}-bin.zip"
DOWNLOAD_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    exit 0
fi

if [[ -x "${GRADLE_BIN}" ]]; then
    echo "${GRADLE_BIN}"
    exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "Error: neither Gradle nor curl is available." >&2
    echo "Install curl or provide a Gradle 8.9 binary on PATH." >&2
    exit 1
fi

if ! command -v unzip >/dev/null 2>&1; then
    echo "Error: unzip is required to bootstrap Gradle ${GRADLE_VERSION}." >&2
    exit 1
fi

mkdir -p "${BOOTSTRAP_ROOT}"

echo "Gradle not found locally; bootstrapping Gradle ${GRADLE_VERSION} into ${BOOTSTRAP_ROOT}." >&2
echo "This is a one-time download; subsequent runs reuse the local copy." >&2

if [[ ! -f "${ZIP_PATH}" ]]; then
    curl --fail --location --retry 3 --output "${ZIP_PATH}.tmp" "${DOWNLOAD_URL}"
    mv "${ZIP_PATH}.tmp" "${ZIP_PATH}"
fi

if command -v shasum >/dev/null 2>&1; then
    actual_sha="$(shasum -a 256 "${ZIP_PATH}" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
    actual_sha="$(sha256sum "${ZIP_PATH}" | awk '{print $1}')"
else
    echo "Error: shasum or sha256sum is required to verify Gradle." >&2
    exit 1
fi

if [[ "${actual_sha}" != "${GRADLE_SHA256}" ]]; then
    rm -f "${ZIP_PATH}"
    echo "Error: Gradle ${GRADLE_VERSION} checksum mismatch." >&2
    echo "Expected: ${GRADLE_SHA256}" >&2
    echo "Actual  : ${actual_sha}" >&2
    echo "The downloaded archive was removed; rerun to retry." >&2
    exit 1
fi

rm -rf "${GRADLE_HOME}"
unzip -q "${ZIP_PATH}" -d "${BOOTSTRAP_ROOT}"

if [[ ! -x "${GRADLE_BIN}" ]]; then
    echo "Error: Gradle bootstrap completed but ${GRADLE_BIN} is missing." >&2
    exit 1
fi

echo "${GRADLE_BIN}"
