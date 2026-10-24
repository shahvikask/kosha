#!/usr/bin/env bash
set -euo pipefail
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
set +e
adb shell am instrument -w -r -e class 'in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#custodyIdentifiersAndNomineeAreNeverSilentlyTruncated,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#guardianMustNotBeChildOrNonindividualAndFailurePreservesFamily,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#custodyEditRejectsTruncatedIdentifiersWithoutChangingEncryptedAccount,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#editingExistingMinorRefusesIneligibleGuardianWithoutWrites,in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#nomineeGoalAndCustodyLifecycleSurviveEncryptedRestartWithoutOwnerLeakage' in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner | tee m26-adb.txt
status=${PIPESTATUS[0]}
set -e
test "$status" -eq 0
grep -q 'OK (5 tests)' m26-adb.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_FAILED' m26-adb.txt

# Separate original ADB transcript for independent encrypted native archive roundtrip.
set +e
adb shell am instrument -w -r -e class 'in.growurdhan.redevelopment.NativeV9ArchiveInstrumentedTest#m26FamilyCustodyNomineeGoalsRoundtripIntoIndependentSqlcipher' in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner | tee m26-archive-adb.txt
archive_status=${PIPESTATUS[0]}
set -e
test "$archive_status" -eq 0
grep -q 'OK (1 test)' m26-archive-adb.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_FAILED' m26-archive-adb.txt

set +e
adb shell am instrument -w -r -e class 'in.growurdhan.redevelopment.NativeM26OwnerLensNavigationInstrumentedTest#selectedMemberLensScopesFamilyAccountsGoalsAndHoldings' in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner | tee m26-compose-adb.txt
compose_status=${PIPESTATUS[0]}
set -e
test "$compose_status" -eq 0
grep -q 'OK (1 test)' m26-compose-adb.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_FAILED' m26-compose-adb.txt
