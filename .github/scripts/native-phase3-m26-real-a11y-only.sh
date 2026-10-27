#!/usr/bin/env bash
# Owner D57: targeted in-scope G14 regression; no archive backup or restore.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner
REAL_SCREEN=in.growurdhan.redevelopment.NativeM26RealBooksAccessibilityInstrumentedTest
timeout 12m adb shell am instrument -w -r -e class "$REAL_SCREEN" "$RUNNER" | tee phase3-m26-real-books-a11y-adb.txt
python3 - <<'PY'
from pathlib import Path
import re
s=Path("phase3-m26-real-books-a11y-adb.txt").read_text(encoding="utf-8",errors="replace")
codes=re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$",s,re.M)
assert re.search(r"^OK\s+\(2 tests\)",s,re.M), "Missing 2-case Android PASS"
assert codes.count("0")==2 and all(c in ("0","1") for c in codes), "Android case missing or failing"
assert "FAILURES!!!" not in s and "INSTRUMENTATION_FAILED" not in s, "Android fail"
print("M26_REAL_BOOKS_A11Y=2 passed=2 failed=0 skipped=0")
PY
