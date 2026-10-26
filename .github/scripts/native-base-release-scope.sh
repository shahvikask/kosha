#!/usr/bin/env bash
# Base-market-release acceptance only. Financial archive recovery tests belong to next upgrade.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
APP_APK=android-native/app/build/outputs/apk/debug/app-debug.apk
TEST_APK=android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner
CASE=in.growurdhan.redevelopment.NativeBaseReleaseScopeInstrumentedTest#financialVaultBackupAndRestoreAreAbsentFromFirstRelease
timeout 5m adb shell am instrument -w -r -e class "$CASE" "$RUNNER" | tee base-release-scope-adb.txt
python3 - <<'PY'
from pathlib import Path
import re
s=Path("base-release-scope-adb.txt").read_text(encoding="utf-8",errors="replace")
codes=re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$",s,re.M)
assert re.search(r"^OK\s+\(1 test\)",s,re.M), "Missing one-case Android PASS"
assert codes.count("0")==1 and all(c in ("0","1") for c in codes), "Bad instrumentation status"
assert "FAILURES!!!" not in s and "INSTRUMENTATION_FAILED" not in s, "Android scope test failed"
print("BASE_RELEASE_SCOPE=1 passed=1 failed=0")
PY
