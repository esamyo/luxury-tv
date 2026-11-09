#!/bin/sh
# Install Luxury TV on a TV and make it the home screen and the screensaver.
# Usage:
#   tools/install.sh 192.168.1.50                         builds from this folder, then installs
#   tools/install.sh 192.168.1.50 luxurytv.apk            installs a downloaded release, no build
set -e
TV="${1:?usage: tools/install.sh <tv ip> [apk]}"
APK="$2"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
command -v adb >/dev/null || { echo "adb not found: install Android platform-tools" >&2; exit 1; }
if [ -z "$APK" ]; then
  cd "$(dirname "$0")/../launcher"
  [ -f local.properties ] || echo "sdk.dir=${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
  ./gradlew assembleRelease -q --console=plain
  APK=app/build/outputs/apk/release/app-release.apk
fi
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
adb connect "$TV" >/dev/null
adb -s "$TV" install -r "$APK"
adb -s "$TV" shell cmd package set-home-activity --user 0 com.esamyo.luxurytv/.MainActivity
# The Luxury clock as the screensaver: gold by day, silver at night, with the greeting and weather.
adb -s "$TV" shell settings put secure screensaver_enabled 1
adb -s "$TV" shell settings put secure screensaver_components com.esamyo.luxurytv/.ClockDream
adb -s "$TV" shell input keyevent HOME
echo "Luxury TV is installed, set as Home and as the screensaver."
