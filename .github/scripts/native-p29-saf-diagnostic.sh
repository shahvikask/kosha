#!/usr/bin/env bash
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 35
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
adb shell am instrument -w -r -e class \
  in.growurdhan.redevelopment.NativeSafBackupWriterInstrumentedTest \
  in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner \
  | tee p29-saf-adb.txt
python3 - <<'PY'
from pathlib import Path
import re
data=Path("p29-saf-adb.txt").read_text(encoding="utf-8",errors="replace")
ok=re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*0\s*$",data,re.M)
names=set(re.findall(r"^INSTRUMENTATION_STATUS: test=(\S+)\s*$",data,re.M))
expected={"publishedBackupMustRemainIdentifiableWithPruningDisabled",
          "ambiguousProviderIdentityRefusesBackupPublicationReceipt"}
assert expected.issubset(names), f"Missing new P29 tests {expected-names}"
assert len(ok)==14, f"Expected 14 passed; received {len(ok)}"
assert re.search(r"^OK\s+\(14 tests\)",data,re.M)
assert "FAILURES!!!" not in data and "INSTRUMENTATION_FAILED" not in data
print("P29_SAF_EXACT_SOURCE=14 passed=14 failed=0 skipped=0")
PY
