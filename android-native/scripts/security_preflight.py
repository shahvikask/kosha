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

def repository_binding_errors(binding: str) -> list[str]:
    """Verify every production entry point follows its policy-bearing wrapper.

    Static source checks are deliberately structural heuristics and complement,
    rather than replace, Kotlin tests and Android penetration testing.
    """
    errors: list[str] = []
    if "class DefaultVaultRepository" not in binding:
        return ["Production repository policy binding is missing."]
    production = binding.split("class DefaultVaultRepository", 1)[1]
    headers = list(re.finditer(
        r"(?m)^    override suspend fun ([A-Za-z][A-Za-z0-9]*)\(",
        production,
    ))
    sections: dict[str, list[str]] = {}
    for i, match in enumerate(headers):
        tail = headers[i + 1].start() if i + 1 < len(headers) else len(production)
        sections.setdefault(match.group(1), []).append(production[match.start():tail])

    expected = {
        "status": "delegate.status()",
        "openOrCreate": "sessionBound.openOrCreate()",
        "close": "sessionBound.close()",
        "createMember": "approvedWrite { delegate.createMember(",
        "registerFamilyMember": "approvedWrite { delegate.registerFamilyMember(",
        "createAccount": "approvedWrite { delegate.createAccount(",
        "registerCustodyAccount": "approvedWrite { delegate.registerCustodyAccount(",
        "createHolding": "approvedWrite { delegate.createHolding(",
        "registerCasHolding": "approvedWrite { delegate.registerCasHolding(",
        "appendEvent": "approvedWrite { delegate.appendEvent(",
        "appendFinancialEvents": "approvedWrite { delegate.appendFinancialEvents(",
        "importSnapshot": "approvedWrite { delegate.importSnapshot(",
        "prepareStatementReview": "sessionBound.read {",
        "commitReviewedStatement": "approvedWrite { delegate.commitReviewedStatement(",
        "members": "sessionBound.read { delegate.members() }",
        "accounts": "sessionBound.read { delegate.accounts() }",
        "casIdentities": "sessionBound.read { delegate.casIdentities() }",
        "holdings": "sessionBound.read { delegate.holdings() }",
        "events": "sessionBound.read { delegate.events() }",
        "readBook": "sessionBound.read { delegate.readBook() }",
        "health": "sessionBound.read { delegate.health() }",
        "exportEncryptedBackup": "sessionBound.readRecentlyApprovedBytes { delegate.exportEncryptedBackup(",
        "restoreEncryptedBackup": "approvedWrite { delegate.restoreEncryptedBackup(",
    }
    unclassified = sorted(set(sections) - set(expected))
    for method in unclassified:
        errors.append(f"Unclassified production repository method {method}; set an explicit policy.")
    for name, route in expected.items():
        found = sections.get(name, [])
        if len(found) != 1 or route not in found[0]:
            errors.append(f"Production repository entry point {name} bypasses {route}")
    if "prepareStatementReview" in sections and (
        "delegate.prepareStatementReview(" not in sections["prepareStatementReview"][0]
    ):
        errors.append("Statement review no longer delegates through session-bound read.")

    if "private val delegate = SqlCipherVaultRepository(context, mutationAuthorization = authorization)" not in production:
        errors.append("SQLCipher production delegate has no transaction authorization guard.")
    if "authorization.requireRecentApproval()" not in production or (
        "withContext(ApprovedMutationRevision(originalRevision))" not in production
    ):
        errors.append("The originally approved credential revision is not carried into writes.")

    # Verify the transaction itself rechecks approval BEFORE recording success.
    try:
        body = binding.split("private suspend fun <T> mutate(", 1)[1].split(
            "/** This binding is the only production", 1
        )[0]
    except IndexError:
        return errors + ["SQLCipher financial mutation transaction is missing."]
    action_at = body.find("val result = action(db)")
    approval_at = body.find("gate.requireRecentApproval()", action_at)
    revision_at = body.find("gate.sessionRevision() == approvedRevision", action_at)
    commit_at = body.find("db.setTransactionSuccessful()", action_at)
    if not (0 <= action_at < approval_at < commit_at and action_at < revision_at < commit_at):
        errors.append("SQLCipher transaction must recheck approval and revision before commit.")
    return errors


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
    # Current architecture uses shared guard helpers, not repeated inline
    # authorization in every wrapper. Scan full method sections: matching only
    # to the first closing brace would miss expression bodies and nested lambdas.
    errors.extend(repository_binding_errors(binding))
    helper = read("android-native/domain/src/main/kotlin/in/growurdhan/domain/vault/AuthorizedVaultAccess.kt")
    ensure("connectionMutex.withLock" in helper and "withContext(NonCancellable)" in helper,
           "Vault open/close or cancellation cleanup serialization is missing.")
    ensure("authorization.requireUnlocked()" in helper and
           "authorization.sessionRevision() == authorizedRevision" in helper,
           "Private read or vault opening no longer binds to the original credential.")
    ensure("authorization.requireRecentApproval()" in helper and
           "bytes.fill(0)" in helper,
           "Portable encrypted export lacks post-operation approval and buffer wiping.")

    ensure('db.execSQL("PRAGMA foreign_keys = ON")' in read(
        "android-native/data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"),
        "Native foreign-key integrity gate is missing.")
    ensure("deny_ledger_update" in read(
        "android-native/data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"),
        "Immutable financial ledger trigger is missing.")
    ensure("GUD_UPLOAD_KEYSTORE_PATH" in read("android-native/app/build.gradle.kts")
           and "gradle.taskGraph.whenReady" in read("android-native/app/build.gradle.kts"),
           "Release signing fail-closed gate is missing.")

    signed_workflow = read(".github/workflows/native-signed-aab.yml")
    ensure("approved_commit_sha:" in signed_workflow
           and "APPROVED_COMMIT_SHA" in signed_workflow
           and "signed_cert=$(keytool -printcert -jarfile" in signed_workflow
           and "Development versionName must be replaced" in signed_workflow,
           "Owner-approved SHA, final signer pin, or release version gate is missing.")

    recovery = read(
        "android-native/app/src/main/java/in/growurdhan/app/presentation/PortableRecoveryViewModel.kt")
    ensure("MAX_CIPHERTEXT_BYTES" in recovery and "34 * 1024 * 1024" in recovery
           and "vault.restoreEncryptedBackup(password, encrypted)" in recovery
           and "session.canCommitFinancialChanges()" in recovery
           and "inFlightPassword?.fill" in recovery,
           "Encrypted recovery size, authentication or password wiping gate is missing.")
    ensure("ActivityResultContracts.CreateDocument" in activity
           and "backupPickerOpen" in activity
           and "CredentialAction.PORTABLE_BACKUP" in activity,
           "Backup must use SAF with a new Android credential challenge.")

    if errors:
        for err in errors:
            print("FAIL (native security policy): " + err, file=sys.stderr)
        return 1
    print("PASS: native manifest privacy, screenshot prevention, device credential,")
    print("       production repository authorization routes and transaction-time checks,")
    print("       foreign key/append-only protection, encrypted recovery bounds,")
    print("       owner-pinned AAB signing and backup exclusions.")
    print("NOTE: Static policy checks are not an independent mobile penetration test.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
