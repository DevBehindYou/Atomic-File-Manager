#!/usr/bin/env bash
set -euo pipefail

# Run only in the disposable GitHub Actions emulator.
[[ "${GITHUB_ACTIONS:-}" == "true" ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]

case "${1:-}" in
  320) resolution=640x1440 ;;
  390) resolution=780x1740 ;;
  800) resolution=1600x2200 ;;
  *) echo "Expected a 320, 390 or 800 dp viewport" >&2; exit 2 ;;
esac

package=com.devbehindyou.atomicfilemanager
test_class="$package.FileOptionsVisualCaptureTest"
output="build/file-options-visual/$1"
mkdir -p "$output"

adb shell wm size "$resolution"
adb shell wm density 320
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb install -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

for theme in light dark; do
  if [[ "$theme" == dark ]]; then
    adb shell cmd uimode night yes
  else
    adb shell cmd uimode night no
  fi
  for scale in 1.0 1.5; do
    variant="$output/$theme-$scale"
    mkdir -p "$variant"
    adb shell settings put system font_scale "$scale"
    adb shell am force-stop "$package"
    for state in file-options view sort filter; do
      adb shell run-as "$package" rm -f "files/atomic-file-options-visual/$state.png" \
        "files/atomic-file-options-visual/$state.txt"
    done
    adb shell am instrument -w -e captureAtomicFileOptions true -e class "$test_class" \
      "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$variant/instrumentation.log"
    grep -Fq 'OK (1 test)' "$variant/instrumentation.log"
    for state in file-options view sort filter; do
      for extension in png txt; do
        adb exec-out run-as "$package" cat "files/atomic-file-options-visual/$state.$extension" > "$variant/$state.$extension"
        test -s "$variant/$state.$extension"
      done
      grep -Fxq "fontScale=$scale" "$variant/$state.txt"
      grep -Fxq "widthDp=$1" "$variant/$state.txt"
      ui_mode="$(awk -F= '$1 == "uiMode" {print $2}' "$variant/$state.txt")"
      [[ -n "$ui_mode" ]]
      if [[ "$theme" == dark ]]; then
        (( (ui_mode & 48) == 32 ))
      else
        (( (ui_mode & 48) == 16 ))
      fi
    done
  done
done
