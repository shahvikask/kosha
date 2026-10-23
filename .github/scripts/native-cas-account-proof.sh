#!/usr/bin/env bash
set -euo pipefail
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am force-stop in.growurdhan.redevelopment || true
set +e
adb shell am instrument -w -r -e class 'in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest#pdfAdapterStaleCustodyReviewRefusesApprovedSqlcipherWrites' in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner | tee cas-account-adb.txt
test_status=${PIPESTATUS[0]}
set -e
test "$test_status" -eq 0
grep -q 'OK (1 test)' cas-account-adb.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_FAILED' cas-account-adb.txt
