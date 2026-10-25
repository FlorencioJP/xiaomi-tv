#!/bin/sh
# Build the launcher, install it on the TV and make it the Home app.
#   tools/install.sh            build + install + set as Home
#   tools/install.sh --no-home  build + install only
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TV() { "$ROOT/.venv/bin/python" "$ROOT/tools/tv.py" "$@"; }
APK="$ROOT/launcher/app/build/outputs/apk/debug/app-debug.apk"

(cd "$ROOT/launcher" && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew -q testDebugUnitTest assembleDebug)

# push + pm install: `adb install` can hang waiting for an on-screen confirmation
TV --push "$APK" /data/local/tmp/home.apk
TV 'pm install -r /data/local/tmp/home.apk; rm /data/local/tmp/home.apk'
# Android 10+ only shows the Wi-Fi name to apps with location access
TV 'pm grant com.cleantv.home android.permission.ACCESS_FINE_LOCATION'

if [ "$1" != "--no-home" ]; then
  # set-home-activity is ignored on this Google TV build, and disabling the setup wizard
  # froze it and came back: the Home key goes through an accessibility service instead
  TV 'settings put secure enabled_accessibility_services com.cleantv.home/com.cleantv.home.HomeKeyService; settings put secure accessibility_enabled 1'
  TV 'dumpsys accessibility | grep -m1 "Bound services"'
  TV 'input keyevent KEYCODE_HOME; sleep 2; dumpsys activity activities | grep -m1 mFocusedApp'
fi
