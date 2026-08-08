#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/java17-env.sh"

APP_ID="com.daniele21.trafficmonitoring.debug"
ACTIVITY_CLASS="com.daniele21.trafficmonitoring.MainActivity"
GRADLE="${ROOT_DIR}/gradlew"
DEVICE_SERIAL=""
AVD_NAME=""
SHOW_LOGS="false"
CLEAR_DATA="false"
ADB_PATH="${ADB:-}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-emulator-debug.sh [options]

Options:
  --device SERIAL       Specific ADB device/emulator serial.
  --avd NAME            Use/start this Android Virtual Device.
  --adb PATH            Custom absolute path to adb.
  --clear-data          Clear the debug app data before launching.
  --logs                Follow logcat for the debug app after launch.
  --list-avds           List locally configured Android Virtual Devices and exit.
  --help, -h            Show this help.

Examples:
  # Build, install and launch on the first running emulator/device
  bash scripts/run-emulator-debug.sh

  # Use an already-running named AVD, or start it if needed
  bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35

  # Launch and stream only this app's logs
  bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --logs

  # Re-test first-run behavior with a clean local database
  bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --clear-data
EOF
}

LIST_AVDS="false"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)
            DEVICE_SERIAL="${2:-}"
            shift 2
            ;;
        --avd)
            AVD_NAME="${2:-}"
            shift 2
            ;;
        --adb)
            ADB_PATH="${2:-}"
            shift 2
            ;;
        --clear-data)
            CLEAR_DATA="true"
            shift
            ;;
        --logs|--logcat)
            SHOW_LOGS="true"
            shift
            ;;
        --list-avds)
            LIST_AVDS="true"
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

is_android_sdk() {
    local sdk_path="$1"
    [[ -n "$sdk_path" && -d "$sdk_path/platform-tools" ]]
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

    echo "Error: Android SDK not found." >&2
    echo "Set ANDROID_HOME, or create local.properties with sdk.dir=/path/to/sdk." >&2
    exit 1
}

resolve_adb() {
    local candidate=""
    for candidate in \
        "$ADB_PATH" \
        "$(command -v adb 2>/dev/null || true)" \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "$HOME/Library/Android/sdk/platform-tools/adb" \
        "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"; do
        if [[ -n "$candidate" && -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    done

    echo "Error: adb not found. Install Android SDK platform-tools or pass --adb /path/to/adb." >&2
    exit 1
}

resolve_emulator() {
    local candidate=""
    for candidate in \
        "$(command -v emulator 2>/dev/null || true)" \
        "${ANDROID_HOME:-}/emulator/emulator" \
        "$HOME/Library/Android/sdk/emulator/emulator" \
        "/opt/homebrew/share/android-commandlinetools/emulator/emulator"; do
        if [[ -n "$candidate" && -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    done
    return 1
}

require_gradle_wrapper() {
    if [[ ! -f "$GRADLE" ]]; then
        echo "Error: Gradle wrapper is missing: $GRADLE" >&2
        echo "Pull the latest repository branch before running local builds." >&2
        exit 1
    fi
    if [[ ! -x "$GRADLE" ]]; then
        echo "Error: Gradle wrapper is not executable: $GRADLE" >&2
        echo "Run: chmod +x gradlew" >&2
        exit 1
    fi
    if [[ ! -f "$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar" ]]; then
        echo "Error: gradle/wrapper/gradle-wrapper.jar is missing." >&2
        exit 1
    fi
}

online_devices() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

running_avd_serial() {
    local wanted="$1"
    local serial=""
    local name=""

    for serial in $(online_devices | grep '^emulator-' || true); do
        name="$($ADB -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true)"
        if [[ "$name" == "$wanted" ]]; then
            echo "$serial"
            return
        fi
    done
    return 1
}

wait_for_avd() {
    local wanted="$1"
    local waited=0
    local serial=""

    while [[ $waited -lt 180 ]]; do
        serial="$(running_avd_serial "$wanted" || true)"
        if [[ -n "$serial" ]]; then
            local booted
            booted="$($ADB -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
            if [[ "$booted" == "1" ]]; then
                echo "$serial"
                return
            fi
        fi
        sleep 2
        waited=$((waited + 2))
    done

    echo "Error: AVD '$wanted' did not finish booting within 180 seconds." >&2
    exit 1
}

configure_android_sdk
require_gradle_wrapper
ADB="$(resolve_adb)"
EMULATOR="$(resolve_emulator || true)"

if [[ "$LIST_AVDS" == "true" ]]; then
    if [[ -z "$EMULATOR" ]]; then
        echo "Error: emulator binary not found in the Android SDK." >&2
        exit 1
    fi
    "$EMULATOR" -list-avds
    exit 0
fi

echo "===================================================="
echo "  Traffic Monitoring Android — Emulator Debug"
echo "===================================================="
echo "Android SDK : $ANDROID_HOME"
echo "ADB         : $ADB"
echo "Gradle      : $GRADLE"

if [[ -n "$DEVICE_SERIAL" ]]; then
    if ! online_devices | grep -qx "$DEVICE_SERIAL"; then
        echo "Error: requested device '$DEVICE_SERIAL' is not online." >&2
        exit 1
    fi
elif [[ -n "$AVD_NAME" ]]; then
    DEVICE_SERIAL="$(running_avd_serial "$AVD_NAME" || true)"
else
    DEVICE_SERIAL="$(online_devices | head -n 1 || true)"
fi

if [[ -z "$DEVICE_SERIAL" ]]; then
    if [[ -z "$AVD_NAME" ]]; then
        echo "No online ADB device/emulator found." >&2
        if [[ -n "$EMULATOR" ]]; then
            echo "Available AVDs:" >&2
            "$EMULATOR" -list-avds >&2 || true
            echo "Run again with: bash scripts/run-emulator-debug.sh --avd <AVD_NAME>" >&2
        fi
        exit 1
    fi

    if [[ -z "$EMULATOR" ]]; then
        echo "Error: emulator binary not found; cannot start AVD '$AVD_NAME'." >&2
        exit 1
    fi

    if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
        echo "Error: AVD '$AVD_NAME' does not exist." >&2
        echo "Available AVDs:" >&2
        "$EMULATOR" -list-avds >&2 || true
        exit 1
    fi

    mkdir -p "$ROOT_DIR/build"
    echo "Starting AVD: $AVD_NAME"
    nohup "$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full \
        > "$ROOT_DIR/build/emulator-$AVD_NAME.log" 2>&1 &
    DEVICE_SERIAL="$(wait_for_avd "$AVD_NAME")"
fi

configure_jdk17
ADB_CMD=("$ADB" -s "$DEVICE_SERIAL")

echo "Device      : $DEVICE_SERIAL"
if [[ -n "$AVD_NAME" ]]; then
    echo "AVD         : $AVD_NAME"
fi
echo "Java home   : $JAVA_HOME"
echo "Java        : $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo "App ID      : $APP_ID"

echo "Building and installing debug app..."
cd "$ROOT_DIR"
"$GRADLE" :app:installDebug --stacktrace

if [[ "$CLEAR_DATA" == "true" ]]; then
    echo "Clearing debug app data..."
    if ! CLEAR_RESULT="$("${ADB_CMD[@]}" shell pm clear "$APP_ID" 2>&1 | tr -d '\r')"; then
        echo "Error: failed to clear app data for $APP_ID." >&2
        echo "$CLEAR_RESULT" >&2
        exit 1
    fi
    if [[ "$CLEAR_RESULT" != "Success" ]]; then
        echo "Error: Android did not confirm that app data was cleared for $APP_ID." >&2
        echo "pm clear output: $CLEAR_RESULT" >&2
        exit 1
    fi
    echo "App data clear confirmed: $CLEAR_RESULT"
fi

echo "Launching Traffic Monitoring..."
if ! "${ADB_CMD[@]}" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 \
    >/dev/null 2>&1; then
    "${ADB_CMD[@]}" shell am start -n "$APP_ID/$ACTIVITY_CLASS"
fi

echo "✅ Debug app launched on $DEVICE_SERIAL"
echo "Tip: debug and Play/release builds can coexist because debug uses $APP_ID."

if [[ "$SHOW_LOGS" == "true" ]]; then
    echo "Streaming logcat for $APP_ID (Ctrl+C to stop)..."
    PID="$("${ADB_CMD[@]}" shell pidof -s "$APP_ID" || true)"
    if [[ -n "$PID" ]]; then
        "${ADB_CMD[@]}" logcat --pid="$PID"
    else
        "${ADB_CMD[@]}" logcat | grep --line-buffered "$APP_ID"
    fi
fi
