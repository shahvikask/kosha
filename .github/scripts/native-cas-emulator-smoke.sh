#!/usr/bin/env bash
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
adb shell am instrument -w -r \
  -e class 'in.growurdhan.redevelopment.NativeCasPdfEngineInstrumentedTest,in.growurdhan.redevelopment.NativeCasOutputReviewGateInstrumentedTest' \
  in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner \
  | tee cas-runtime-adb.txt
# am instrument may return zero despite test failures. Parse the raw
# original test output, not just the GitHub runner's exit code.
python3 - <<'PY'
from pathlib import Path
import re
data = Path("cas-runtime-adb.txt").read_text()
expected = {
    "arbitraryBytesNeverReachPythonPdfParser",
    "tooLargeOrMissingPdfNameIsRejectedBeforePasswordHandling",
    "explicitFullPanIsRequiredBeforePythonIsInvoked",
    "packagedFrozenCasparserAndPdfiumActuallyLoadOnAndroid",
    "singleSyntheticOriginalFormatAccountPassesStrictPayloadGate",
    "multipleAccountsCannotAssumeFirstDematOwnerOwnsEverything",
    "validMultiHoldingSourceSubtotalReconcilesToFrozenParserTotal",
    "tamperedIndividualHoldingCannotHideBehindConsistentGrandTotals",
    "missingStringAndNonfiniteSourceAmountsAreNotConvertedToZero",
    "reportIntegrityAndFullOwnerPanAreMandatoryBeforePreview",
}
methods = set(re.findall(r"^INSTRUMENTATION_STATUS: test=(\S+)$", data, re.M))
completed = len(re.findall(r"^INSTRUMENTATION_STATUS_CODE: 0\s*$", data, re.M))
assert methods == expected, f"Expected named CAS tests; got {sorted(methods)}"
assert completed == len(expected), f"Expected 10 successful methods, got {completed}"
assert re.search(r"\bOK \(10 tests\)", data), "Instrumentation did not finish cleanly"
assert "FAILURES!!!" not in data and "INSTRUMENTATION_FAILED" not in data
print("API35_NATIVE_CAS_RUNTIME_CONFIRMED=10 passed=10 failed=0 skipped=0")
PY
