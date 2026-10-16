#!/usr/bin/env bash
# M00 real Android emulator screenshots. No personal data or vault is used.
set -Eeuo pipefail
APP_ID="in.growurdhan.redevelopment"
ACTIVITY="$APP_ID/.MainActivity"
OUT="${GITHUB_WORKSPACE:-$(pwd)}/m00-emulator-evidence"
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell wm size 1080x1920
adb shell wm density 420

# A real, compiled Compose activity; do not use Robolectric or layoutlib here.
(
  cd "${GITHUB_WORKSPACE:-$(pwd)}/android-native"
  ./gradlew :app:assembleDebug --no-daemon
)
adb install -r "${GITHUB_WORKSPACE:-$(pwd)}/android-native/app/build/outputs/apk/debug/app-debug.apk"

tap_exact_text() {
  local target="$1"
  local xml="$OUT/window.xml"
  adb shell uiautomator dump /sdcard/gud-window.xml >/dev/null
  adb exec-out cat /sdcard/gud-window.xml > "$xml"
  python3 - "$xml" "$target" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, expected = sys.argv[1:]
root = ET.parse(path).getroot()
matches = []
for node in root.iter("node"):
    label = node.attrib.get("text", "")
    if label == expected:
        b = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if b:
            a = list(map(int, b.groups()))
            matches.append(((a[0]+a[2])//2, (a[1]+a[3])//2))
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one visible Compose text {expected!r}, got {len(matches)}")
print(*matches[0])
PY
}

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  python3 - "$OUT/$name.png" <<'PY'
import struct, sys, os
f=sys.argv[1]
b=open(f,"rb").read()
assert b[:8] == b"\x89PNG\r\n\x1a\n", f"Not a PNG: {f}"
w,h=struct.unpack(">II",b[16:24])
assert w >= 320 and h >= 560 and len(b)>12000,(f,w,h,len(b))
print(f"REAL_EMULATOR_PNG {os.path.basename(f)} {w}x{h} {len(b)}bytes")
PY
}
tap_and_wait() {
  local expected="$1"
  local pos
  pos="$(tap_exact_text "$expected")"
  adb shell input tap $pos
  sleep 2
}

for mode in light dark; do
  if [[ "$mode" == "light" ]]; then
    adb shell cmd uimode night no
  else
    adb shell cmd uimode night yes
  fi
  adb shell am force-stop "$APP_ID" || true
  adb shell am start -W -n "$ACTIVITY"
  sleep 3
  capture "locked-$mode"
  tap_and_wait "View sample layout"
  capture "sample-home-$mode"
  tap_and_wait "View sample holding"
  capture "sample-holding-$mode"
done
python3 - "$OUT" <<'PY'
import hashlib, pathlib, sys
folder=pathlib.Path(sys.argv[1])
files=sorted(folder.glob("*.png"))
assert len(files)==6, f"Expected six real emulator shots, got {len(files)}"
digests={hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
assert len(digests)==6, "At least two images are identical; screen/navigation/theme capture failed"
print("M00_REAL_EMULATOR_SCREENSHOTS_PASS 6/6 unique PNG files")
PY
rm -f "$OUT/window.xml"
