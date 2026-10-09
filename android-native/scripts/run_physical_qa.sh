#!/usr/bin/env bash
# GrowUrDhan native QA: explicit, disposable DEVICE ONLY.
# NEVER run on a family member's phone or a phone containing a real vault.
set -euo pipefail

if [[ "${GUD_QA_DISPOSABLE_DEVICE:-}" != "I_CONFIRM" ]]; then
  echo "REFUSED: set GUD_QA_DISPOSABLE_DEVICE=I_CONFIRM only on a disposable"
  echo "physical Android test handset with no personal accounts or financial data."
  exit 2
fi

for command in adb gradle java python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "REFUSED: required local tool '$command' is not installed."
    exit 2
  fi
done

# A single attached authorized physical test device avoids accidentally
# installing the test APK to the owner's connected personal phone or emulator.
mapfile -t devices < <(adb devices | awk 'NR>1 && $2 == "device" {print $1}')
if [[ "${#devices[@]}" -ne 1 ]]; then
  echo "REFUSED: connect and authorize exactly ONE disposable physical device."
  exit 2
fi
serial="${devices[0]}"
sdk="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$(adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
page_size="$(adb -s "$serial" shell getconf PAGESIZE | tr -d '\r')"
emulator="$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')"
if [[ "$emulator" == "1" ]]; then
  echo "REFUSED: this script is reserved for physical device evidence."
  exit 2
fi
if [[ ! "$sdk" =~ ^[0-9]+$ ]] || ((sdk < 26)); then
  echo "REFUSED: device SDK must be >=26 (observed unknown/unsupported API)."
  exit 2
fi

# Do not overwrite an owner's installed app. The instrumentation installation
# process may replace/update app packages and must ONLY touch a clean QA phone.
if adb -s "$serial" shell pm path in.growurdhan.app | grep -q 'package:'; then
  echo "REFUSED: GrowUrDhan is already installed. Use a different clean QA device."
  exit 2
fi

echo "Disposable physical device accepted."
echo "Android API: $sdk ; ABI: $abi ; memory-page bytes: $page_size"
echo "No serial, personal account, raw device property dump or PDF is recorded."
echo "Starting isolated SQLCipher, Compose and vault-security instrumentation."
echo "NOTE: Gradle output may identify the Android test device; redact it before sharing."

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
python3 android-native/scripts/validate_schema.py
python3 android-native/scripts/security_preflight.py
# Install debug and test APKs only on the confirmed disposable device.
gradle -p android-native :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon

# Aggregate test results without dumping test logs, sensitive app state, or raw XML.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

bases = [
    Path("android-native/app/build/outputs/androidTest-results/connected"),
    Path("android-native/data/build/outputs/androidTest-results/connected"),
]
counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for base in bases:
    reports = list(base.rglob("TEST-*.xml"))
    if not reports:
        raise SystemExit(f"FAILED: no Android JUnit XML reports under {base}")
    for report in reports:
        xml = ET.parse(report).getroot()
        cases = xml.findall(".//testcase")
        for case in cases:
            counts["tests"] += 1
            for kind in ("failure", "error", "skipped"):
                if case.find(kind) is not None:
                    counts[{"failure": "failures", "error": "errors", "skipped": "skipped"}[kind]] += 1
print("Physical-device synthetic instrumentation summary:", counts)
if counts["tests"] < 2 or any(counts[k] for k in ("failures", "errors", "skipped")):
    raise SystemExit("FAILED: instrumentation reports contain failed, skipped or insufficient tests.")
print("PASS: all isolated instrumentation passed. This is NOT a physical security pentest.")
PY

echo "Human security checklist still required: device credential cancellation,"
echo "biometric/PIN fallback, session lock on background, FLAG_SECURE,"
echo "SAF revocation, ARM64 PDFium and 16-KiB page support, recovery on second phone."
echo "See docs/native-migration/18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md"
