#!/usr/bin/env bash
set -euo pipefail
readarray -t beta < <(python3 -c 'import json; c=json.load(open("scripts/release/beta-config.json")); print(c["apkName"]); print(c["applicationId"])')
apk="${beta[0]}"
package="${beta[1]}"
# This is a disposable CI emulator. Never uninstall or change permissions on the user's phone.
adb install "beta-smoke/$apk"
adb logcat -c
adb shell am start -W -n "$package/com.devbehindyou.atomicfilemanager.MainActivity" > beta-smoke/startup.txt
sleep 8
adb shell pidof "$package" > beta-smoke/pid.txt
adb logcat -d -s AndroidRuntime:E > beta-smoke/runtime.txt
if grep -q 'FATAL EXCEPTION' beta-smoke/runtime.txt; then cat beta-smoke/runtime.txt; exit 1; fi
adb shell uiautomator dump /data/local/tmp/atomic-beta-window.xml
adb pull /data/local/tmp/atomic-beta-window.xml beta-smoke/window.xml
adb exec-out screencap -p > beta-smoke/startup.png
python3 - <<'PY'
import json
import xml.etree.ElementTree as ET
c=json.load(open('scripts/release/beta-config.json'))
nodes=ET.parse('beta-smoke/window.xml').iter('node')
if not any(n.get('package') == c['applicationId'] and (n.get('text') or n.get('content-desc')) for n in nodes):
    raise SystemExit('No meaningful app UI was drawn by the signed R8 beta')
print('PASS: exact signed beta installs, stays alive and draws app UI without granting access or changing files')
PY
