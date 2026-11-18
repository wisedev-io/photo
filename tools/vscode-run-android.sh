#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${ANDROID_HOME:-}" ]]; then
    SDK="${ANDROID_HOME}"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    SDK="${ANDROID_SDK_ROOT}"
elif [[ -d "${HOME}/Library/Android/sdk" ]]; then
    SDK="${HOME}/Library/Android/sdk"
elif [[ -d "${HOME}/Android/Sdk" ]]; then
    SDK="${HOME}/Android/Sdk"
else
    echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi

ADB="${SDK}/platform-tools/adb"
EMULATOR="${SDK}/emulator/emulator"

if [[ ! -x "${ADB}" ]]; then
    echo "ADB not found at ${ADB}. Install Android SDK Platform-Tools." >&2
    exit 1
fi
if [[ ! -x "${EMULATOR}" ]]; then
    echo "Android Emulator not found at ${EMULATOR}. Install the Android Emulator SDK package." >&2
    exit 1
fi

export ANDROID_HOME="${SDK}"
export ANDROID_SDK_ROOT="${SDK}"
"${ADB}" start-server
"${ROOT}/gradlew" -p "${ROOT}" :app:assembleDebug

DEVICE="$("${ADB}" devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1; exit }')"
if [[ -z "${DEVICE}" ]]; then
    if ! pgrep -f '[q]emu-system-aarch64' >/dev/null; then
        AVD="$("${EMULATOR}" -list-avds | sed -n '1p')"
        if [[ -z "${AVD}" ]]; then
            echo "No Android emulator is configured. Create an AVD in Android Device Manager." >&2
            exit 1
        fi
        echo "Starting Android emulator '${AVD}' without snapshots (2 GB RAM)..."
        "${EMULATOR}" -avd "${AVD}" -no-snapshot -no-boot-anim -memory 2048 >/dev/null 2>&1 </dev/null &
    else
        echo "Waiting for the running Android emulator to become available..."
    fi
fi

for _ in $(seq 1 180); do
    DEVICE="$("${ADB}" devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1; exit }')"
    if [[ -n "${DEVICE}" ]] &&
        [[ "$("${ADB}" -s "${DEVICE}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        echo "Android emulator ${DEVICE} is ready."
        exit 0
    fi
    sleep 2
done

echo "Android emulator did not finish booting within 6 minutes." >&2
exit 1
