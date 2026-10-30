#!/usr/bin/env bash
# Builds the release APK and installs it on the USB-connected phone.
# When an update kills the running home app, a system thread can resolve HOME while the package is half-replaced and
# drop the "always" home choice as dangling. The HOME role stays, so nothing restores the choice and the next Home
# press asks which launcher to use. So the app is stopped before the install, with Settings in front when it is on
# screen so the system does not start it again meanwhile.
set -euo pipefail
cd "$(dirname "$0")/.."
pkg=io.github.gastoncampana.launcher
# No grep -q in these pipes: quitting early can kill adb with SIGPIPE, which pipefail reports as a failed pipeline.
home_is_ours() {
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
    grep "^$pkg/" > /dev/null
}
./gradlew -q assembleRelease
front=false
if adb shell dumpsys window | grep "mCurrentFocus=.*$pkg" > /dev/null; then front=true; fi
default=false
if home_is_ours; then default=true; fi
if $front; then adb shell am start -W -a android.settings.SETTINGS > /dev/null; fi
adb shell am force-stop $pkg
adb install -r build/outputs/apk/release/islauncher-release.apk
if home_is_ours; then
  if $front; then adb shell input keyevent HOME; fi
elif $default; then
  echo "Android dropped the default home choice: on the next Home press pick this app and \"Always\"." >&2
fi
