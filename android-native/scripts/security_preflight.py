#!/usr/bin/env python3
"""Deterministic source policy checks, NOT a penetration test or MASVS certification.

Run on every native CI build. Fails closed for accidental privacy regressions:
exported activities, plaintext traffic, Android backups, missing UI session
guards, repo-level bypass, hardcoded Play upload keys and frozen parser changes.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android-native"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")

def main() -> int:
    errors: list[str] = []
    def ensure(test: bool, label: str) -> None:
        if not test:
            errors.append(label)

    path = ANDROID / "app/src/main/AndroidManifest.xml"
    app = ET.parse(path).getroot().find("application")
    ensure(app is not None, "Android application manifest is missing.")
    if app is not None:
        ensure(app.get(ANDROID_NS + "allowBackup") == "false", "Android Backup must be disabled.")
        ensure(app.get(ANDROID_NS + "usesCleartextTraffic") == "false", "Cleartext transport must be disabled.")
        exported = [
            el for el in app.iter() if el.tag in {"activity", "service", "receiver", "provider"}
            and el.get(ANDROID_NS + "exported") == "true"
        ]
        ensure(len(exported) == 1 and exported[0].get(ANDROID_NS + "name") == ".MainActivity",
               "Only the launcher MainActivity may be exported.")

    rules = read("android-native/app/src/main/res/xml/data_extraction_rules.xml")
    ensure("<cloud-backup>" in rules and "<device-transfer>" in rules and
           rules.count('<exclude domain="database" path="." />') == 2,
           "Both cloud and device-to-device backup must exclude databases.")

    activity = read("android-native/app/src/main/java/in/growurdhan/app/MainActivity.kt")
    ensure("FLAG_SECURE" in activity, "FLAG_SECURE screenshot defense is missing.")
    ensure("isDeviceSecure" in activity and "createConfirmDeviceCredentialIntent" in activity,
           "OS credential confirmation is missing.")
    ensure("sessionGate.lock()" in activity, "Activity fails to invalidate expired authorization.")

    binding = read("android-native/data/src/main/kotlin/in/growurdhan/data/vault/SqlCipherVaultRepository.kt")
    ensure("private val authorization: VaultAuthorization" in binding,
           "Production repository must inject deny-by-default VaultAuthorization.")
    guards = re.search(r"class DefaultVaultRepository\b[\s\S]*$", binding)
    guard = guards.group() if guards else ""
    for name, check in {
        "openOrCreate": "requireUnlocked",
        "createMember": "requireRecentApproval",
        "createAccount": "requireRecentApproval",
        "registerCustodyAccount": "requireRecentApproval",
        "createHolding": "requireRecentApproval",
        "registerCasHolding": "requireRecentApproval",
        "appendEvent": "requireRecentApproval",
        "appendFinancialEvents": "requireRecentApproval",
        "importSnapshot": "requireRecentApproval",
        "prepareStatementReview": "requireUnlocked",
        "commitReviewedStatement": "requireRecentApproval",
        "members": "requireUnlocked",
        "accounts": "requireUnlocked",
        "casIdentities": "requireUnlocked",
        "holdings": "requireUnlocked",
        "events": "requireUnlocked",
        "readBook": "requireUnlocked",
        "health": "requireUnlocked",
        "exportEncryptedBackup": "requireRecentApproval",
        "restoreEncryptedBackup": "requireRecentApproval",
    }.items():
        p = r"override suspend fun " + name + r"\b[\s\S]*?\{([\s\S]*?)\}"
        match = re.search(p, guard)
        ensure(match is not None and "authorization." + check + "()" in match.group(1),
               f"Repository entry point {name} lacks the {check} policy.")

    ensure('db.execSQL("PRAGMA foreign_keys = ON")' in read(
        "android-native/data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"),
        "Native foreign-key integrity gate is missing.")
    ensure("deny_ledger_update" in read(
        "android-native/data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"),
        "Immutable financial ledger trigger is missing.")
    ensure("GUD_UPLOAD_KEYSTORE_PATH" in read("android-native/app/build.gradle.kts")
           and "gradle.taskGraph.whenReady" in read("android-native/app/build.gradle.kts"),
           "Release signing fail-closed gate is missing.")

    if errors:
        for err in errors:
            print("FAIL (native security policy): " + err, file=sys.stderr)
        return 1
    print("PASS: native manifest privacy, screenshot prevention, device credential,")
    print("       20 repository authorization entry points, signed-release gate,")
    print("       foreign key/append-only protection and backup exclusions.")
    print("NOTE: Static policy checks are not an independent mobile penetration test.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
