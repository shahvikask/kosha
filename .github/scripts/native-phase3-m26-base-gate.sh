#!/usr/bin/env bash
# Owner decision D57: first-market-release M26 family/owner-lens proof.
# Archived vault backup and financial restore do not belong in this gate.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner
CASES=in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#custodyIdentifiersAndNomineeAreNeverSilentlyTruncated,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#guardianMustNotBeChildOrNonindividualAndFailurePreservesFamily,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#custodyEditRejectsTruncatedIdentifiersWithoutChangingEncryptedAccount,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#editingExistingMinorRefusesIneligibleGuardianWithoutWrites,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#nomineeGoalAndCustodyLifecycleSurviveEncryptedRestartWithoutOwnerLeakage
timeout 14m adb shell am instrument -w -r -e class "$CASES" "$RUNNER" | tee phase3-m26-integrity-adb.txt
adb shell am force-stop in.growurdhan.redevelopment
NAV=in.growurdhan.redevelopment.NativeM26OwnerLensNavigationInstrumentedTest#selectedMemberLensScopesFamilyAccountsGoalsAndHoldings
timeout 10m adb shell am instrument -w -r -e class "$NAV" "$RUNNER" | tee phase3-m26-navigation-adb.txt
SCOPE=in.growurdhan.redevelopment.NativeBaseReleaseScopeInstrumentedTest#financialVaultBackupAndRestoreAreAbsentFromFirstRelease
timeout 5m adb shell am instrument -w -r -e class "$SCOPE" "$RUNNER" | tee phase3-base-scope-adb.txt
DOCS=in.growurdhan.redevelopment.NativeHouseholdDocumentAccessGateInstrumentedTest#memberLensCannotDisplayOrStartHouseholdDocumentReview
timeout 5m adb shell am instrument -w -r -e class "$DOCS" "$RUNNER" | tee phase3-m26-document-access-adb.txt
MUTATION=in.growurdhan.redevelopment.NativeM26OwnerLensMutationInstrumentedTest#ownerSwitchRevokesStaleMoneyEventAndManualPriceSelection
timeout 8m adb shell am instrument -w -r -e class "$MUTATION" "$RUNNER" | tee phase3-m26-mutation-adb.txt
ACCESSIBILITY=in.growurdhan.redevelopment.NativeBooksNavigationAccessibilityInstrumentedTest#compactPhoneTwoHundredPercentTextWrapsAndAnnouncesSelectedTab
timeout 5m adb shell am instrument -w -r -e class "$ACCESSIBILITY" "$RUNNER" | tee phase3-m26-navigation-accessibility-adb.txt
VISUAL=in.growurdhan.redevelopment.NativeM26VisualEvidenceInstrumentedTest
timeout 8m adb shell am instrument -w -r -e class "$VISUAL" "$RUNNER" | tee phase3-m26-visual-adb.txt
# These are isolated synthetic Compose controls. NEVER capture a real admitted vault.
mkdir -p phase3-m26-visual-evidence
adb exec-out run-as in.growurdhan.redevelopment cat cache/g14-nav-light-360dp-2x.png > phase3-m26-visual-evidence/g14-nav-light-360dp-2x.png
adb exec-out run-as in.growurdhan.redevelopment cat cache/g14-nav-dark-360dp-2x.png > phase3-m26-visual-evidence/g14-nav-dark-360dp-2x.png
python3 - <<'PY'
from pathlib import Path
import re
for filename, expected in (("phase3-m26-integrity-adb.txt", 5), ("phase3-m26-navigation-adb.txt", 1), ("phase3-base-scope-adb.txt", 1), ("phase3-m26-document-access-adb.txt", 1), ("phase3-m26-mutation-adb.txt", 1), ("phase3-m26-navigation-accessibility-adb.txt", 1), ("phase3-m26-visual-adb.txt", 3)):
    s = Path(filename).read_text(encoding="utf-8", errors="replace")
    codes = re.findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", s, re.M)
    passed = sum(c == "0" for c in codes)
    summary = re.search(r"^OK\s+\((\d+)\s+tests?\)", s, re.M)
    assert summary is not None and int(summary.group(1)) == expected, filename + " runner failed"
    assert passed == expected and all(c in ("0","1") for c in codes), filename + " contains nonpassing instrumentation"
    assert "FAILURES!!!" not in s and "INSTRUMENTATION_FAILED" not in s, filename + " failed"
# Validate real PNG signatures and width from the original emulator screen captures.
import struct
for theme in ("light", "dark"):
    name = Path(f"phase3-m26-visual-evidence/g14-nav-{theme}-360dp-2x.png")
    raw = name.read_bytes()
    assert len(raw) > 512 and raw.startswith(bytes.fromhex("89504e470d0a1a0a")), name
    width, height = struct.unpack(">II", raw[16:24])
    assert width == 360 and height > 100, (name, width, height)
print("M26_BASE_FAMILY_SCOPE=13 passed=13 failed=0 skipped=0")
PY
