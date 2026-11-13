#!/usr/bin/env bash
# Build the debug APK, put it on the phone that is plugged in, and start it.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -d /opt/android-studio/jbr ]]; then
    export JAVA_HOME=/opt/android-studio/jbr
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.david.tessel.debug/dev.david.tessel.MainActivity
