#!/usr/bin/env bash
# Diagnostic only: does not certify P01-P34 functional parity.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
# Configure a real lockscreen credential; fail closed if Android cannot confirm it.
adb shell locksettings set-pin 2468
adb shell dumpsys trust > full-android-device-trust.txt
grep -Eq 'isDeviceSecure=(true|1)|deviceSecure=(true|1)' full-android-device-trust.txt || { echo "Secure Android lock not confirmed" >&2; exit 1; }
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
# The runner can return zero despite failed tests; check raw output too.
timeout 45m adb shell am instrument -w -r \
  in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner \
  | tee full-android-adb.txt
python3 - <<'PY'
from pathlib import Path
import json
import re
data = Path("full-android-adb.txt").read_text(encoding="utf-8", errors="replace")
codes = re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", data, re.M)
completed = sum(code == "0" for code in codes)
bad = [code for code in codes if code not in ("0", "1")]
ok = re.search(r"^OK\s+\((\d+)\s+tests?\)", data, re.M)
summary = {
  "binary_source_sha": __import__("os").environ["APK_SOURCE_SHA"],
  "expected_methods": int(ok.group(1)) if ok else None,
  "completed": completed,
  "runner_total": int(ok.group(1)) if ok else None,
  "bad_codes": bad,
  "instrumentation_failed": "INSTRUMENTATION_FAILED" in data or "FAILURES!!!" in data,
}
Path("full-android-diagnostic-summary.json").write_text(json.dumps(summary, indent=2)+"\n")
assert ok is not None, "Android runner did not report OK; inspect original ADB failures"
assert completed == summary["runner_total"], f"Runner reports {summary['runner_total']} methods; successful completions {completed}"
assert not bad and not summary["instrumentation_failed"], f"Android test failure: {summary}"
print(f"API35_FULL_ANDROID_DIAGNOSTIC={completed} passed={completed} failed=0 skipped=0")
PY
