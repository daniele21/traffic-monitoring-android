#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.daniele21.trafficmonitoring.debug"
DEVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE="${2:-}"
      shift 2
      ;;
    --package)
      PACKAGE="${2:-}"
      shift 2
      ;;
    -h|--help)
      cat <<EOF
Usage: bash scripts/device-validation-status.sh [--device SERIAL] [--package PACKAGE]

Prints a compact Android/OEM background-execution snapshot for M5 field validation.
EOF
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "/opt/homebrew/share/android-commandlinetools/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done
  echo "adb not found" >&2
  exit 1
}

ADB="$(resolve_adb)"
ADB_ARGS=()
if [[ -n "$DEVICE" ]]; then
  ADB_ARGS=(-s "$DEVICE")
fi

adb_shell() {
  "$ADB" "${ADB_ARGS[@]}" shell "$@"
}

serial="$($ADB "${ADB_ARGS[@]}" get-serialno 2>/dev/null || true)"
manufacturer="$(adb_shell getprop ro.product.manufacturer | tr -d '\r')"
model="$(adb_shell getprop ro.product.model | tr -d '\r')"
sdk="$(adb_shell getprop ro.build.version.sdk | tr -d '\r')"
release="$(adb_shell getprop ro.build.version.release | tr -d '\r')"
pid="$(adb_shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
standby="$(adb_shell am get-standby-bucket "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"

printf '\nTraffic Monitoring — Device Validation Status\n'
printf '%s\n' '============================================='
printf 'Serial          : %s\n' "${serial:-unknown}"
printf 'Device          : %s %s\n' "${manufacturer:-unknown}" "${model:-unknown}"
printf 'Android         : %s (SDK %s)\n' "${release:-unknown}" "${sdk:-unknown}"
printf 'Package         : %s\n' "$PACKAGE"
printf 'Process PID     : %s\n' "${pid:-not running}"
printf 'Standby bucket  : %s\n' "${standby:-unavailable}"

printf '\nPackage state\n-------------\n'
adb_shell dumpsys package "$PACKAGE" 2>/dev/null \
  | grep -E 'stopped=|enabled=|hidden=|suspended=' \
  | head -n 8 || true

printf '\nPower / idle signals\n--------------------\n'
adb_shell dumpsys power 2>/dev/null \
  | grep -E 'mIsPowered=|mBatteryLevel=|mLowPowerModeEnabled=|mWakefulness=' \
  | head -n 10 || true

printf '\nBattery-optimization whitelist match\n------------------------------------\n'
adb_shell dumpsys deviceidle whitelist 2>/dev/null \
  | grep -F "$PACKAGE" || echo 'No whitelist entry found (or command unavailable).'

printf '\nWorkManager / jobs mentioning package\n-------------------------------------\n'
adb_shell dumpsys jobscheduler "$PACKAGE" 2>/dev/null \
  | grep -E 'JOB #|RUNNABLE|WAITING|traffic-monitoring-recovery|RecoveryWorker' \
  | head -n 30 || true

printf '\nUse this output together with a validation ZIP; it is diagnostic context, not a pass/fail result by itself.\n'
