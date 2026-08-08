#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYCHAIN_SERVICE="com.daniele21.trafficmonitoring.android-upload"
KEYCHAIN_ACCOUNT="traffic-monitoring-upload"
DEFAULT_STORE_FILE="${HOME}/.keystore/traffic-monitoring-upload.p12"
DEFAULT_KEY_ALIAS="traffic-monitoring-upload"
DEFAULT_UNSIGNED_AAB="${ROOT_DIR}/traffic-monitoring-release-unsigned.aab"
DEFAULT_SIGNED_AAB="${ROOT_DIR}/dist/traffic-monitoring-ci-signed.aab"
VERSION_FILE="${ROOT_DIR}/app/version.properties"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/build-play-release.sh create-key
  bash scripts/build-play-release.sh setup
  bash scripts/build-play-release.sh status
  bash scripts/build-play-release.sh build
  bash scripts/build-play-release.sh build-next
  bash scripts/build-play-release.sh certificate
  bash scripts/build-play-release.sh sign-ci-aab [INPUT_AAB] [OUTPUT_AAB]

Commands:
  create-key   Create a PKCS12 Play upload keystore outside the repository.
               keytool asks for the password interactively. Keep that password safe.
  setup        Store the existing upload-keystore password in macOS Keychain.
  status       Show non-secret signing/version configuration.
  build        Build a signed release AAB using the current versionCode/versionName.
  build-next   Increment versionCode, then build a signed release AAB.
  certificate  Print the upload certificate details/fingerprints.
  sign-ci-aab  Sign an existing unsigned AAB downloaded from CI.

Optional non-secret overrides:
  TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE  Upload keystore path
  TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS   Upload key alias
  ANDROID_HOME                                  Android SDK path

Secrets are read from macOS Keychain and exported only for the Gradle/jarsigner
process lifetime. Never commit a keystore or signing password to Git.
EOF
}

require_macos_keychain() {
    if [[ "$(uname -s)" != "Darwin" ]] || ! command -v security >/dev/null 2>&1; then
        echo "This helper requires macOS Keychain ('security' command)." >&2
        exit 1
    fi
}

store_file() {
    echo "${TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE:-${DEFAULT_STORE_FILE}}"
}

key_alias() {
    echo "${TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS:-${DEFAULT_KEY_ALIAS}}"
}

create_upload_key() {
    if ! command -v keytool >/dev/null 2>&1; then
        echo "Error: keytool not found. Install/use JDK 17 first." >&2
        exit 1
    fi

    local file
    local alias
    file="$(store_file)"
    alias="$(key_alias)"

    if [[ -e "$file" ]]; then
        echo "Refusing to overwrite existing keystore: $file" >&2
        exit 1
    fi

    mkdir -p "$(dirname "$file")"
    chmod 700 "$(dirname "$file")" 2>/dev/null || true

    echo "Creating Play upload keystore:"
    echo "  File : $file"
    echo "  Alias: $alias"
    echo
    echo "keytool will ask for a strong password interactively."
    echo "Save that password securely; the private upload key cannot be reconstructed from the app."
    echo

    keytool -genkeypair \
        -keystore "$file" \
        -storetype PKCS12 \
        -alias "$alias" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000

    chmod 600 "$file" 2>/dev/null || true
    echo
    echo "Upload keystore created. Next run:"
    echo "  bash scripts/build-play-release.sh setup"
}

setup_keychain_password() {
    require_macos_keychain

    local file
    file="$(store_file)"
    if [[ ! -f "$file" ]]; then
        echo "Upload keystore not found at $file." >&2
        echo "Run: bash scripts/build-play-release.sh create-key" >&2
        exit 1
    fi

    echo "Store the Traffic Monitoring upload-keystore password in macOS Keychain."
    echo "Input is handled by Keychain and is not printed or added to shell history."
    security add-generic-password \
        -U \
        -a "$KEYCHAIN_ACCOUNT" \
        -s "$KEYCHAIN_SERVICE" \
        -l "Traffic Monitoring Android upload keystore" \
        -j "Password for the Google Play upload keystore." \
        -w

    echo "Signing password saved in macOS Keychain."
    echo "Verifying that the password opens the configured keystore..."
    load_signing_configuration
    verify_keystore_password >/dev/null
    echo "✅ Keystore password verified."
}

read_keychain_password() {
    require_macos_keychain
    security find-generic-password \
        -a "$KEYCHAIN_ACCOUNT" \
        -s "$KEYCHAIN_SERVICE" \
        -w 2>/dev/null
}

is_android_sdk() {
    local sdk_path="$1"
    [[ -n "$sdk_path" && -d "$sdk_path/platforms" && -d "$sdk_path/build-tools" ]]
}

configure_android_sdk() {
    local properties_sdk=""
    local candidate=""

    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        properties_sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | tail -n 1)"
    fi

    for candidate in \
        "${ANDROID_HOME:-}" \
        "${ANDROID_SDK_ROOT:-}" \
        "$properties_sdk" \
        "$HOME/Library/Android/sdk" \
        "/opt/homebrew/share/android-commandlinetools"; do
        if is_android_sdk "$candidate"; then
            export ANDROID_HOME="$candidate"
            export ANDROID_SDK_ROOT="$candidate"
            return
        fi
    done

    echo "Android SDK not found." >&2
    echo "Set ANDROID_HOME or sdk.dir in $ROOT_DIR/local.properties." >&2
    exit 1
}

resolve_gradle() {
    if [[ -x "$ROOT_DIR/gradlew" ]]; then
        echo "$ROOT_DIR/gradlew"
        return
    fi
    if command -v gradle >/dev/null 2>&1; then
        command -v gradle
        return
    fi

    echo "Gradle not found. CI currently uses Gradle 8.9." >&2
    echo "Install Gradle locally or add a Gradle wrapper before building." >&2
    exit 1
}

load_signing_configuration() {
    STORE_FILE="$(store_file)"
    KEY_ALIAS="$(key_alias)"

    if [[ ! -f "$STORE_FILE" ]]; then
        echo "Upload keystore not found at $STORE_FILE." >&2
        echo "Run create-key or set TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE." >&2
        exit 1
    fi

    if ! SIGNING_PASSWORD="$(read_keychain_password)" || [[ -z "$SIGNING_PASSWORD" ]]; then
        echo "Signing password is not available in macOS Keychain." >&2
        echo "Run: bash scripts/build-play-release.sh setup" >&2
        exit 1
    fi

    export TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE="$STORE_FILE"
    export TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_PASSWORD="$SIGNING_PASSWORD"
    export TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS="$KEY_ALIAS"
    export TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_PASSWORD="$SIGNING_PASSWORD"
    trap clear_signing_configuration EXIT
}

clear_signing_configuration() {
    unset SIGNING_PASSWORD
    unset TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE
    unset TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_PASSWORD
    unset TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS
    unset TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_PASSWORD
    unset TRAFFIC_MONITORING_JARSIGNER_PASSWORD
    unset TRAFFIC_MONITORING_KEYTOOL_PASSWORD
}

verify_keystore_password() {
    export TRAFFIC_MONITORING_KEYTOOL_PASSWORD="$SIGNING_PASSWORD"
    keytool -list \
        -keystore "$STORE_FILE" \
        -storetype PKCS12 \
        -storepass:env TRAFFIC_MONITORING_KEYTOOL_PASSWORD \
        -alias "$KEY_ALIAS"
}

show_status() {
    local file alias code name
    file="$(store_file)"
    alias="$(key_alias)"
    code="$(sed -n 's/^versionCode=//p' "$VERSION_FILE" 2>/dev/null || true)"
    name="$(sed -n 's/^versionName=//p' "$VERSION_FILE" 2>/dev/null || true)"

    echo "Traffic Monitoring Android release status"
    echo "  Keystore    : $file"
    echo "  Exists      : $([[ -f "$file" ]] && echo yes || echo no)"
    echo "  Key alias   : $alias"
    echo "  versionCode : ${code:-unknown}"
    echo "  versionName : ${name:-unknown}"
    if [[ "$(uname -s)" == "Darwin" ]] && command -v security >/dev/null 2>&1 && \
        security find-generic-password -a "$KEYCHAIN_ACCOUNT" -s "$KEYCHAIN_SERVICE" >/dev/null 2>&1; then
        echo "  Keychain pw : configured"
    else
        echo "  Keychain pw : not configured"
    fi
}

increment_version_code() {
    if [[ ! -f "$VERSION_FILE" ]]; then
        echo "Missing $VERSION_FILE" >&2
        exit 1
    fi

    local current next tmp
    current="$(sed -n 's/^versionCode=//p' "$VERSION_FILE" | tail -n 1)"
    if [[ ! "$current" =~ ^[0-9]+$ ]]; then
        echo "Invalid versionCode in $VERSION_FILE: '$current'" >&2
        exit 1
    fi
    next=$((current + 1))
    tmp="$(mktemp)"
    awk -v next="$next" '
        /^versionCode=/ { print "versionCode=" next; found=1; next }
        { print }
        END { if (!found) print "versionCode=" next }
    ' "$VERSION_FILE" > "$tmp"
    mv "$tmp" "$VERSION_FILE"
    echo "versionCode: $current -> $next"
}

build_release() {
    load_signing_configuration
    verify_keystore_password >/dev/null
    configure_android_sdk
    local gradle
    gradle="$(resolve_gradle)"

    cd "$ROOT_DIR"
    "$gradle" :app:bundleRelease --stacktrace

    local source_aab="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
    if [[ ! -f "$source_aab" ]]; then
        echo "Expected release AAB not found: $source_aab" >&2
        exit 1
    fi

    local code name safe_name output
    code="$(sed -n 's/^versionCode=//p' "$VERSION_FILE" | tail -n 1)"
    name="$(sed -n 's/^versionName=//p' "$VERSION_FILE" | tail -n 1)"
    safe_name="$(echo "$name" | tr -c 'A-Za-z0-9._-' '-')"
    mkdir -p "$ROOT_DIR/dist"
    output="$ROOT_DIR/dist/traffic-monitoring-${safe_name}-vc${code}.aab"
    cp "$source_aab" "$output"

    jarsigner -verify "$output" >/dev/null
    shasum -a 256 "$output" > "$output.sha256"

    echo
    echo "✅ Signed Android App Bundle created and verified:"
    echo "  $output"
    echo "  SHA-256: $output.sha256"
    echo "  versionCode: $code"
    echo "  versionName: $name"
    echo
    echo "Upload this .aab to Google Play Console > Testing > Internal testing."
}

show_certificate() {
    load_signing_configuration
    export TRAFFIC_MONITORING_KEYTOOL_PASSWORD="$SIGNING_PASSWORD"
    keytool -list -v \
        -keystore "$STORE_FILE" \
        -storetype PKCS12 \
        -storepass:env TRAFFIC_MONITORING_KEYTOOL_PASSWORD \
        -alias "$KEY_ALIAS"
}

sign_ci_aab() {
    local input_aab="${1:-$DEFAULT_UNSIGNED_AAB}"
    local output_aab="${2:-$DEFAULT_SIGNED_AAB}"

    if [[ ! -f "$input_aab" ]]; then
        echo "Unsigned CI bundle not found: $input_aab" >&2
        exit 1
    fi
    if [[ "$input_aab" == "$output_aab" ]]; then
        echo "Input and output AAB paths must differ." >&2
        exit 2
    fi
    if ! command -v jarsigner >/dev/null 2>&1; then
        echo "jarsigner not found. Use JDK 17." >&2
        exit 1
    fi

    load_signing_configuration
    mkdir -p "$(dirname "$output_aab")"
    export TRAFFIC_MONITORING_JARSIGNER_PASSWORD="$SIGNING_PASSWORD"

    jarsigner \
        -keystore "$STORE_FILE" \
        -storetype PKCS12 \
        -storepass:env TRAFFIC_MONITORING_JARSIGNER_PASSWORD \
        -keypass:env TRAFFIC_MONITORING_JARSIGNER_PASSWORD \
        -signedjar "$output_aab" \
        "$input_aab" \
        "$KEY_ALIAS"

    jarsigner -verify "$output_aab" >/dev/null
    shasum -a 256 "$output_aab" > "$output_aab.sha256"

    echo "✅ Signed and verified CI AAB:"
    echo "  $output_aab"
    echo "  SHA-256: $output_aab.sha256"
}

case "${1:-help}" in
    create-key)
        create_upload_key
        ;;
    setup)
        setup_keychain_password
        ;;
    status)
        show_status
        ;;
    build)
        build_release
        ;;
    build-next)
        increment_version_code
        build_release
        ;;
    certificate)
        show_certificate
        ;;
    sign-ci-aab)
        sign_ci_aab "${2:-}" "${3:-}"
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
