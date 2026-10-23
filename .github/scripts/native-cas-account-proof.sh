#!/usr/bin/env bash
set -euo pipefail
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am force-stop in.growurdhan.redevelopment || true
: > cas-account-adb.txt
for case in \
  'in.growurdhan.redevelopment.NativeCasOutputReviewGateInstrumentedTest#nonDematIssuerCannotInheritMultiAccountDematExemption' \
  'in.growurdhan.redevelopment.NativeCasOutputReviewGateInstrumentedTest#multiDematAccountPayloadPreservesPerPositionIssuerIdentity' \
  'in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#issuerCustodyBoundSameIsinCommitsBothAndRejectsReplay' \
  'in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#issuerCustodyChangeAfterReviewCannotCommit' \
  'in.growurdhan.redevelopment.NativeCasOutputReviewGateInstrumentedTest#mixedDematAndExplicitFundFolioAdmittedWithoutGuessingCustody' \
  'in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#mixedDepositoryAndFundFolioSnapshotCommitsAtomicallyWithReplayGuard'
do
  set +e
  adb shell am instrument -w -r -e class "$case" in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner | tee -a cas-account-adb.txt
  result=${PIPESTATUS[0]}
  set -e
  test "$result" -eq 0
done
test "$(grep -c 'OK (1 test)' cas-account-adb.txt)" -eq 6
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_FAILED' cas-account-adb.txt
