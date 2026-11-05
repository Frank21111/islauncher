#!/usr/bin/env bash
# Gives Islauncher what its blocks need, once, on the USB-connected phone (USB debugging on):
# - WRITE_SECURE_SETTINGS, so it can turn its blocking service back on and keep the time automatic during a block;
# - its device admin, with no policies: it only keeps the app from being uninstalled until turned off;
# - notification access and the accessibility service (BlockListener, BlockService).
# No account is touched. Over adb, Android's restricted settings (which keep sideloaded apps from the last two) do not apply.
set -euo pipefail
pkg=io.github.gastoncampana.launcher
adb shell pm path "$pkg" > /dev/null || { echo "Islauncher is not installed: run scripts/install.sh first" >&2; exit 1; }
adb shell pm grant "$pkg" android.permission.WRITE_SECURE_SETTINGS
# No grep -q in these pipes: quitting early can kill adb with SIGPIPE, which pipefail reports as a failed pipeline.
if ! adb shell dumpsys device_policy | grep -F "$pkg/.BlockAdmin" > /dev/null; then
  adb shell dpm set-active-admin --user 0 "$pkg/.BlockAdmin"
fi
adb shell cmd notification allow_listener "$pkg/.BlockListener"
# The accessibility service, keeping the ones already on. Single quotes for the phone's shell: a service name may hold a $.
me="$pkg/.BlockService"
enabled=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case ":$enabled:" in
  *":$me:"* | *":$pkg/$pkg.BlockService:"*) ;;
  ":null:" | "::") adb shell "settings put secure enabled_accessibility_services '$me'" ;;
  *) adb shell "settings put secure enabled_accessibility_services '$enabled:$me'" ;;
esac
adb shell settings put secure accessibility_enabled 1
echo "Done: Settings → Blocks in Islauncher shows each part as on."
