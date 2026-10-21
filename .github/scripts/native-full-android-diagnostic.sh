#!/usr/bin/env bash
# Diagnostic only: does not certify P01-P34 functional parity.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
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
  "binary_source_sha": "02cd5868547c21240b9524b8a8914b30173e2386",
  "expected_methods": 139,
  "completed": completed,
  "runner_total": int(ok.group(1)) if ok else None,
  "bad_codes": bad,
  "instrumentation_failed": "INSTRUMENTATION_FAILED" in data or "FAILURES!!!" in data,
}
Path("full-android-diagnostic-summary.json").write_text(json.dumps(summary, indent=2)+"\n")
assert completed == 139, f"Expected 139 successful completions, saw {completed}"
assert summary["runner_total"] == 139, f"Runner total mismatch {summary['runner_total']}"
assert not bad and not summary["instrumentation_failed"], f"Android test failure: {summary}"
print("API35_FULL_ANDROID_DIAGNOSTIC=139 passed=139 failed=0 skipped=0")
PY
