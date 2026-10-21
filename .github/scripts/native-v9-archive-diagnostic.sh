#!/usr/bin/env bash
# Native GUDNAT01 target-class acceptance only; not P01-P34 release signoff.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
# Mandatory secure Keyguard on the clean ephemeral emulator. Never claim a
# positive recovery test if the secure device lock cannot be provisioned.
adb shell locksettings set-pin 246810
# Preserve secure Keyguard while keeping the interactive Compose test Activity
# visible.  The original 18-case archive run outlived the emulator's idle
# timeout and all three later Compose tests reported zero Compose hierarchies.
# This is emulator test-host setup, not bypass of OS admission or owner proof.
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent 224
adb shell input swipe 500 1600 500 400 250
adb shell input text 246810
adb shell input keyevent 66
adb shell input keyevent 3

adb install -r "$APP_APK"
adb install -r "$TEST_APK"
# Main suite explicitly enumerates methods; process phases must NEVER run here.
MAIN_TESTS="in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#allNativeV9TableTypesRestoreIntoIndependentEmptySqlcipher,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#wrongPasswordTamperedCipherAndDuplicateRowsNeverPartiallyImport,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#opaqueHistoricalSchemaExtensionCannotBeSilentlyDroppedOnRestore,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#separateNewDeviceStagesNativeCiphertextOnceAndRefusesReplacement,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#nativeStagedTamperAndInterruptedAtomicWriteDoNotPublishVault,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#ownerApprovedFullNativeRestorePublishesIndependentKeystoreCiphertext,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#nativeRecoveryCrashAfterCandidateVerificationKeepsClaimAndNeverAutoRetries,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#restoreInterruptedAfterKeyPublicationIsForensicallyReadableWithoutRepair,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#syntheticLowStorageFailureAfterCandidateVerificationRetainsEncryptedEvidence,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#acceptedTermsReceiptSurvivesClaimOnlyCrashAndTamperingBlocksForensicReview,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#manualResumeVerifiedCandidatePublishesOnlyWithSecondOwnerApproval,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#manualResumeAfterKeyPublicationUsesExistingKeyWithoutReplacingIt,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#tamperedNativeCandidateAndOccupiedTargetNeverResume,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#interruptedManualResumePreservesPublishedKeyForNewExplicitReview,in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#corruptedNativeCandidateCannotBeOfferedForManualResume,in.growurdhan.redevelopment.NativeV9RestoreUiInstrumentedTest#nativeFileAndForensicSelectionRequireDeviceAdmission,in.growurdhan.redevelopment.NativeV9RestoreUiInstrumentedTest#nativeRestoreAndForensicActionsCannotBypassOwnerProofOrTerms,in.growurdhan.redevelopment.NativeV9RestoreUiInstrumentedTest#forensicHintSurvivesFreshOwnerCredentialEntryWithoutApproval"
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner
# Explicitly split the SAME 18 tests.  UI must run while the secured Activity
# is interactive, before expensive encryption cases can time out the display.
IFS=',' read -r -a SELECTED <<< "$MAIN_TESTS"
UI_TESTS=()
CORE_TESTS=()
for selection in "${SELECTED[@]}"; do
  case "$selection" in
    *NativeV9RestoreUiInstrumentedTest#*) UI_TESTS+=("$selection") ;;
    *) CORE_TESTS+=("$selection") ;;
  esac
done
test "${#UI_TESTS[@]}" -eq 3
test "${#CORE_TESTS[@]}" -eq 15
UI_CSV=$(IFS=,; echo "${UI_TESTS[*]}")
CORE_CSV=$(IFS=,; echo "${CORE_TESTS[*]}")
timeout 10m adb shell am instrument -w -r -e class "$UI_CSV" "$RUNNER" \
  | tee native-v9-archive-adb.txt
timeout 25m adb shell am instrument -w -r -e class "$CORE_CSV" "$RUNNER" \
  | tee -a native-v9-archive-adb.txt
# Phase I deliberately stops after a verified candidate is sealed, then the
# emulator force-stops the application process. Phase II uses a new
# instrumentation process and existing on-disk ciphertext/Keystore evidence.
timeout 8m adb shell am instrument -w -r -e class \
  in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#processBoundaryPhaseOnePreservesVerifiedCandidate "$RUNNER" \
  | tee -a native-v9-archive-adb.txt
adb shell am force-stop in.growurdhan.redevelopment
timeout 8m adb shell am instrument -w -r -e class \
  in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#processBoundaryPhaseTwoFreshProcessReauthenticatesAndPublishes "$RUNNER" \
  | tee -a native-v9-archive-adb.txt
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
 "ownerApprovedFullNativeRestorePublishesIndependentKeystoreCiphertext",
 "nativeRecoveryCrashAfterCandidateVerificationKeepsClaimAndNeverAutoRetries",
 "restoreInterruptedAfterKeyPublicationIsForensicallyReadableWithoutRepair",
 "syntheticLowStorageFailureAfterCandidateVerificationRetainsEncryptedEvidence",
 "acceptedTermsReceiptSurvivesClaimOnlyCrashAndTamperingBlocksForensicReview",
 "nativeFileAndForensicSelectionRequireDeviceAdmission",
 "nativeRestoreAndForensicActionsCannotBypassOwnerProofOrTerms",
 "forensicHintSurvivesFreshOwnerCredentialEntryWithoutApproval",
 "manualResumeVerifiedCandidatePublishesOnlyWithSecondOwnerApproval",
 "manualResumeAfterKeyPublicationUsesExistingKeyWithoutReplacingIt",
 "tamperedNativeCandidateAndOccupiedTargetNeverResume",
 "interruptedManualResumePreservesPublishedKeyForNewExplicitReview",
 "corruptedNativeCandidateCannotBeOfferedForManualResume",
 "processBoundaryPhaseOnePreservesVerifiedCandidate",
 "processBoundaryPhaseTwoFreshProcessReauthenticatesAndPublishes",
}
codes=re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$",data,re.M)
assert expected == tests, f"Wrong test set (no financial contents): {tests}"
assert codes.count("0") == 20 and codes.count("1") == 20, f"Wrong instrumentation status counts: {codes}"
assert all(c in ("0","1") for c in codes), "Failed/ignored instrumentation status"
assert len(re.findall(r"^OK\s+\(3 tests\)",data,re.M)) == 1, "Missing passing three-case Compose UI suite"
assert len(re.findall(r"^OK\s+\(15 tests\)",data,re.M)) == 1, "Missing passing fifteen-case archive suite"
assert len(re.findall(r"^OK\s+\(1 test\)",data,re.M)) == 2, "Missing both independent process terminal results"
assert not any(s in data for s in ("FAILURES!!!","INSTRUMENTATION_FAILED"))
print("NATIVE_V9_ARCHIVE=20 passed=20 failed=0 skipped=0")
PY
