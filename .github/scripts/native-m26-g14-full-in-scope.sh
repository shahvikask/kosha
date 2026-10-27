#!/usr/bin/env bash
# M26/G14 first-market-release full in-scope regression. D57 financial
# archive backup/export/recovery is excluded, not waived or treated as passed.
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35"
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RUNNER=in.growurdhan.redevelopment.test/androidx.test.runner.AndroidJUnitRunner

run_case_group() {
  local label="$1" suite="$2" duration="$3"
  adb shell am force-stop in.growurdhan.redevelopment || true
  timeout "$duration" adb shell am instrument -w -r -e class "$suite" "$RUNNER" |
    tee "m26-full-$label-adb.txt"
}

# All in-scope encrypted family, custody, nominee, goals, holdings and CAS
# account/replay cases, not just a preselected five-case subset.
run_case_group books in.growurdhan.redevelopment.NativeBooksRepositoryInstrumentedTest 35m
run_case_group accounts in.growurdhan.redevelopment.NativeAccountRepositoryInstrumentedTest 18m
run_case_group dossier in.growurdhan.redevelopment.NativeEmergencyDossierInstrumentedTest 8m
run_case_group lifecycle in.growurdhan.redevelopment.NativeActivityLifecycleInstrumentedTest 8m

# Separate Compose/UI processes prevent latent interaction state from carrying
# from one destination or viewport into another.
run_case_group navigation in.growurdhan.redevelopment.NativeM26OwnerLensNavigationInstrumentedTest 8m
run_case_group mutation in.growurdhan.redevelopment.NativeM26OwnerLensMutationInstrumentedTest 8m
run_case_group documents in.growurdhan.redevelopment.NativeHouseholdDocumentAccessGateInstrumentedTest 5m
run_case_group backup-disabled in.growurdhan.redevelopment.NativeBaseReleaseScopeInstrumentedTest 5m
run_case_group compact-navigation in.growurdhan.redevelopment.NativeBooksNavigationAccessibilityInstrumentedTest 5m
run_case_group visual in.growurdhan.redevelopment.NativeM26VisualEvidenceInstrumentedTest 8m

# Synthetic 360dp/2x screenshots are safe to extract: NEVER screenshot a
# real admitted financial vault or genuine private CAS.
mkdir -p m26-full-safe-visual
adb exec-out run-as in.growurdhan.redevelopment cat cache/g14-nav-light-360dp-2x.png > m26-full-safe-visual/g14-light-360dp-2x.png
adb exec-out run-as in.growurdhan.redevelopment cat cache/g14-nav-dark-360dp-2x.png > m26-full-safe-visual/g14-dark-360dp-2x.png
run_case_group real-books in.growurdhan.redevelopment.NativeM26RealBooksAccessibilityInstrumentedTest 12m

python3 - <<'PY'
from pathlib import Path
from re import findall, search, M
from struct import unpack
expected = {
  "books": 46, "accounts": 12, "dossier": 3, "lifecycle": 2,
  "navigation": 1, "mutation": 1, "documents": 1,
  "backup-disabled": 1, "compact-navigation": 1, "visual": 3,
  "real-books": 2,
}
for name, count in expected.items():
    p = Path(f"m26-full-{name}-adb.txt")
    s = p.read_text(encoding="utf-8", errors="replace")
    matched = search(r"^OK\s+\((\d+)\s+tests?\)", s, M)
    codes = findall(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", s, M)
    assert matched and int(matched.group(1)) == count, f"{name}: required {count} tests"
    assert codes.count("0") == count and all(c in ("0", "1") for c in codes), f"{name}: missing passing instrumentation"
    assert "FAILURES!!!" not in s and "INSTRUMENTATION_FAILED" not in s, f"{name}: test failure"
for theme in ("light", "dark"):
    p = Path(f"m26-full-safe-visual/g14-{theme}-360dp-2x.png")
    raw = p.read_bytes()
    assert len(raw) > 512 and raw.startswith(bytes.fromhex("89504e470d0a1a0a")), p
    w,h = unpack(">II", raw[16:24])
    assert w == 360 and h >= 100, (p,w,h)
assert sum(expected.values()) == 73
print("M26_G14_FULL_IN_SCOPE=73 passed=73 failed=0 skipped=0")
PY
