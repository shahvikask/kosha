#!/usr/bin/env bash
# Diagnostic only: does not certify P01-P34 functional parity.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
# Provision the synthetic credential expected by real OS-journey tests.
adb shell locksettings set-pin 246810
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner
PREFLIGHT=in.growurdhan.redevelopment.NativeM26SecureDevicePreflightInstrumentedTest#actualAndroidDeviceIsSecure
timeout 3m adb shell am instrument -w -r -e class "$PREFLIGHT" "$RUNNER" | tee full-android-device-secure-adb.txt
python3 - <<'PY'
from pathlib import Path
s = Path("full-android-device-secure-adb.txt").read_text()
assert "OK (1 test)" in s and "INSTRUMENTATION_STATUS_CODE: 0" in s and "FAILURES!!!" not in s, "Android secure-device preflight failed"
PY
# Run the pre-Python CAS assertion alone in a fresh app process.
# A combined suite can start Chaquopy in another case, invalidating this test.
CAS=in.growurdhan.redevelopment.NativeCasPdfEngineInstrumentedTest#malformedCasContainerNeverStartsChaquopyOnFreshProcess
timeout 6m adb shell am instrument -w -r -e class "$CAS" "$RUNNER" | tee full-android-cas-isolated-adb.txt
# Run cross-process publication in a deliberate phase order, preserving
# candidate evidence across process boundaries. Clear only before phase one.
adb shell pm clear in.growurdhan.redevelopment
PHASE_ONE=in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#processBoundaryPhaseOnePreservesVerifiedCandidate
PHASE_TWO=in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#processBoundaryPhaseTwoFreshProcessReauthenticatesAndPublishes
timeout 8m adb shell am instrument -w -r -e class "$PHASE_ONE" "$RUNNER" | tee full-android-recovery-phase-one-adb.txt
adb shell am force-stop in.growurdhan.redevelopment
timeout 8m adb shell am instrument -w -r -e class "$PHASE_TWO" "$RUNNER" | tee full-android-recovery-phase-two-adb.txt

# These OS UI journeys require separate, genuinely empty app storage.
# Never remove immutable recovery evidence during a single test.
POSITIVE=in.growurdhan.redevelopment.NativeV9RealPositiveRestoreInstrumentedTest
NEGATIVE=in.growurdhan.redevelopment.NativeV9RealSafSelectionInstrumentedTest
adb shell pm clear in.growurdhan.redevelopment
timeout 12m adb shell am instrument -w -r -e class "$POSITIVE" "$RUNNER" | tee full-android-positive-restore-adb.txt
adb shell pm clear in.growurdhan.redevelopment
timeout 12m adb shell am instrument -w -r -e class "$NEGATIVE" "$RUNNER" | tee full-android-negative-saf-adb.txt

# Remaining regression tests cannot accidentally consume these isolated fixtures.
adb shell pm clear in.growurdhan.redevelopment
EXCLUSIONS="$CAS,in.growurdhan.redevelopment.NativeM26SecureDevicePreflightInstrumentedTest,$PHASE_ONE,$PHASE_TWO,$POSITIVE,$NEGATIVE"
timeout 45m adb shell am instrument -w -r -e notClass "$EXCLUSIONS" "$RUNNER" | tee full-android-adb.txt
python3 - <<'PY'
from pathlib import Path
import json
import re
data = Path("full-android-adb.txt").read_text(encoding="utf-8", errors="replace")
isolated = Path("full-android-cas-isolated-adb.txt").read_text(encoding="utf-8", errors="replace")
codes = re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", data, re.M)
completed = sum(code == "0" for code in codes)
iso_codes = re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", isolated, re.M)
iso_ok = re.search(r"^OK\s+\((\d+)\s+tests?\)", isolated, re.M)
iso_completed = sum(code == "0" for code in iso_codes)
bad = [code for code in codes if code not in ("0", "1")]
ok = re.search(r"^OK\s+\((\d+)\s+tests?\)", data, re.M)
summary = {
  "binary_source_sha": __import__("os").environ["APK_SOURCE_SHA"],
  "expected_methods": (int(ok.group(1)) + int(iso_ok.group(1))) if ok and iso_ok else None,
  "isolated_cas_completed": iso_completed,
  "isolated_cas_runner_total": int(iso_ok.group(1)) if iso_ok else None,
  "completed": completed,
  "runner_total": int(ok.group(1)) if ok else None,
  "bad_codes": bad,
  "instrumentation_failed": "INSTRUMENTATION_FAILED" in data or "FAILURES!!!" in data,
}
Path("full-android-diagnostic-summary.json").write_text(json.dumps(summary, indent=2)+"\n")
assert iso_ok is not None and int(iso_ok.group(1)) == 1 and iso_completed == 1 and all(c in ("0", "1") for c in iso_codes) and "FAILURES!!!" not in isolated, "Fresh-process CAS test failed; inspect isolated ADB evidence"
assert ok is not None, "Android runner did not report OK; inspect original ADB failures"
assert completed == summary["runner_total"], f"Runner reports {summary['runner_total']} methods; successful completions {completed}"
assert not bad and not summary["instrumentation_failed"], f"Android test failure: {summary}"
print(f"API35_FULL_ANDROID_DIAGNOSTIC={completed + iso_completed} passed={completed + iso_completed} failed=0 skipped=0")
PY
