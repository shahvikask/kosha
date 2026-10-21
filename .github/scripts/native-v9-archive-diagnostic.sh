#!/usr/bin/env bash
# Native GUDNAT01 target-class acceptance only; not P01-P34 release signoff.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
timeout 25m adb shell am instrument -w -r -e class \
  in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest \
  in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner \
  | tee native-v9-archive-adb.txt
python3 - <<'PY'
from pathlib import Path
import re
data=Path("native-v9-archive-adb.txt").read_text(encoding="utf-8", errors="replace")
tests=set(re.findall(r"^INSTRUMENTATION_STATUS: test=(\w+)\s*$",data,re.M))
expected={
 "allNativeV9TableTypesRestoreIntoIndependentEmptySqlcipher",
 "wrongPasswordTamperedCipherAndDuplicateRowsNeverPartiallyImport",
 "opaqueHistoricalSchemaExtensionCannotBeSilentlyDroppedOnRestore",
 "separateNewDeviceStagesNativeCiphertextOnceAndRefusesReplacement",
 "nativeStagedTamperAndInterruptedAtomicWriteDoNotPublishVault",
}
codes=re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$",data,re.M)
assert expected == tests, f"Wrong test set (no financial contents): {tests}"
assert codes.count("0") == 5 and codes.count("1") == 5, f"Wrong instrumentation status counts: {codes}"
assert all(c in ("0","1") for c in codes), "Failed/ignored instrumentation status"
assert re.search(r"^OK\s+\(5 tests\)",data,re.M), "Missing clean JUnit terminal status"
assert not any(s in data for s in ("FAILURES!!!","INSTRUMENTATION_FAILED"))
print("NATIVE_V9_ARCHIVE=5 passed=5 failed=0 skipped=0")
PY
