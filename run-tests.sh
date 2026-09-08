#!/usr/bin/env bash
# Full automated verification for iTantra. No human interaction required.
#
#   ./run-tests.sh            unit tests + lint + APK
#   ./run-tests.sh --device   also boots a headless emulator and runs the UI tests
#
# Requires: JDK 17 + 21, Android SDK. Set ANDROID_HOME if it is not in the default place.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

AVD_NAME="${AVD_NAME:-itantra_test}"
WITH_DEVICE=0
[[ "${1:-}" == "--device" ]] && WITH_DEVICE=1

echo "==> Unit tests"
./gradlew :app:testDebugUnitTest --console=plain

echo "==> Lint"
./gradlew :app:lintDebug --console=plain

echo "==> Debug APK"
./gradlew :app:assembleDebug --console=plain

if [[ $WITH_DEVICE -eq 1 ]]; then
  if ! adb shell true >/dev/null 2>&1; then
    echo "==> Booting headless emulator ($AVD_NAME)"
    nohup emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim \
      -no-snapshot -gpu swiftshader_indirect >/tmp/itantra-emulator.log 2>&1 &
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
      sleep 5
    done
  fi
  echo "==> Instrumented UI tests"
  ./gradlew :app:connectedDebugAndroidTest --console=plain
fi

echo
echo "==> Results"
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET

def tally(pattern, label):
    t = f = e = 0
    files = glob.glob(pattern, recursive=True)
    for p in files:
        r = ET.parse(p).getroot()
        t += int(r.get('tests', 0))
        f += int(r.get('failures', 0))
        e += int(r.get('errors', 0))
    if files:
        status = "PASS" if (f + e) == 0 else "FAIL"
        print(f"  {label:14s} {t:4d} tests  {f} failures  {e} errors   [{status}]")

tally('app/build/test-results/testDebugUnitTest/*.xml', 'unit')
tally('app/build/outputs/androidTest-results/connected/**/*.xml', 'instrumented')
PY
