# GrowUrDhan — M7 Authenticated Custody, Holding Identity and CAS Verification (As Built)

**Working branch:** `GUD-Android-ChatGPT` only  
**Baseline:** M6 independently verified at `3a4dfa0790f548b6132b43c5d2cf4897ab8cd9e6` (see [M6 verification](16-M6-FAMILY-ONBOARDING-VERIFICATION.md)).  
**M7 verification status:** **SOURCE COMMITTED; new JVM/Android CI evidence pending independent owner handoff.**  
**Product status:** Pre-release native rewrite, not a signed commercially approved Google Play application.

## 1. Actual owner-facing journey

The existing member flow now leads to a secondary **Accounts & holdings** screen, reachable from an unlocked vault and family setup. The Statements screen offers the same setup path when no eligible CAS identity is enrolled.

1. The user creates/selects an existing family member in the encrypted vault.
2. The user selects registrar `CAMS` or `KFINTECH` and supplies an **account nickname**; the onboarding screen explicitly discourages using PAN, a login password or a full account number as a nickname.
3. After authenticated submission, `VaultRepository.registerCustodyAccount` creates a UUID-keyed account with a family-owner foreign key in one SQL transaction.
4. The user chooses the owned registrar account and enters the exact fund scheme name, ISIN and folio from their statement. The onboarding policy validates name, ISIN shape, folio shape, ownership, account type, duplicates and limits.
5. A **fresh device credential confirmation after staging** is required for each account/holding write. The staged request remains memory-only; an expired or cancelled session cannot commit. The production repository also requires recently confirmed authorization independently of Compose.
6. `registerCasHolding` inserts the holding **and** its verified account/ISIN/folio link atomically. **Quantity and current price are zero**, no historical purchase cost is inferred and **no ledger event is invented**.
7. The user returns to Statements and picks a PDF. The native adapter always supplies enrolled CAS mappings to the parser contract. A review can be prepared only when the parsed registrar, owner-selected family member, ISIN, folio and scheme name match a single enrolled mutual-fund record. The parser's EQUITY/FIXED_INCOME exposure category is *not* a separate fund identity and must not cause otherwise exact matches to fail. User acceptance and fresh device credential remain required for the underlying M3 snapshot transaction.

No new financial event is written merely because an account or holding is registered. Existing React/Tauri financial books are not automatically migrated or overwritten.

## 2. Encrypted schema v3 and rollback boundaries

`VaultSchema.VERSION = 3`, preserving the original v1 financial table layout and v2 append-only ledger columns. Fresh installs and v1/v2 upgrades run in a single SQL transaction; interrupted upgrades roll back, and integrity/foreign-key checking follows.

A new `holding_cas_identity` table links `holding_id`, `owner_id`, `account_id`, `isin` and `folio`; foreign keys bind the same owner to the existing holding and custody account. The insertion trigger also requires matching account, member and mutual-fund category. The update/delete triggers reject subsequent modification of enrolled identity records; a future audited correction mechanism would require a deliberate schema/API design.

The immutable financial ledger remains **append-only** with scaled integer money (1e4) and quantity (1e8). The reviewed-statement book fingerprint includes the new identity table, so changing a mapping after review invalidates the receipt before commit. Unknown historical purchase cost remains UNKNOWN rather than today's market quote.

This schema adds identity mapping **without replacing** the existing `holding` or `ledger_event` tables and without silently rewriting old tax-lot history.

## 3. CAS source boundary and supported coverage

`NativeCasPdfReader` computes SHA-256 from the selected document bytes; `CasImportContract.validate(..., enrolledIdentities = ...)` checks strict immutable identity records rather than choosing among fuzzy name matches. Ambiguous, missing, cross-owner, issuer-mismatched, ISIN-mismatched, folio-mismatched or scheme-mismatched records are rejected.

The narrow M7 enrollment currently supports **CAMS and KFintech mutual-fund accounts**. NSDL, CDSL and NPS are **not** verified as fully mapped import flows in M7, even though the frozen parser may recognize those formats. If the immutable parser omits mandatory folio/ISIN evidence for a particular record, the app fails closed; it does **not** make an unverified guess.

The pure domain contract retains a legacy nullable enrollment parameter used by older tests; it can perform exact normalized name+category matching only when no enrolled identity registry is supplied. The **production PDF adapter always supplies a non-null registry**, including when it is empty. Eight additional synthetic parser-contract tests exercise exact owner/registrar/ISIN/folio matching, missing or duplicate records, changed scheme names, and correct acceptance of frozen-parser exposure classifications.

The actual frozen files under `src/casparser-main/**` and protected `scripts/cas_*.py`/Android parser shim paths have **not been edited** as part of M7. The production Android/PDFium release variant still needs signed-arm64 **real hardware and real consented PDF** smoke/fuzz tests; passing synthetic domain tests are not the same as validating the full immutable PDF parser.

## 4. Backup and recovery compatibility

The portable **password-encrypted logical backup** payload moves to format **3**, including `holding_cas_identity`. Restoring v3 into a **new empty vault** recreates the encrypted owner/account/holding/identity relationships, with SQL foreign keys and transaction rollback on any failure.

Older development backup payload formats **1 and 2** remain supported; they have no CAS mapping and **must never fabricate one**. Android tests check v3 roundtrip, v2 restore without invented identity, and real encrypted v2→v3 database upgrade without altering historical ledger rows.

The database's Android Keystore key **does not travel to another device**; cross-device recovery uses the separately password-protected logical backup. A consumer export/restore document-picker interface, genuine second-phone restore, lost-passphrase handling and recovery documentation in the app are **not yet released**. See [physical-device and recovery acceptance protocol](18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md).

## 5. Automated regression additions (pending verified results)

Source tests now cover:
- `CustodyHoldingOnboardingTest` — owner-scoped registrar labels, validation and limits, identity normalization, duplicate/cross-owner rejection, and zero-cost/zero-quantity holdings.
- `CasEnrolledIdentityVerificationTest` — **seven** strict production-like PDF mapping tests that reject missing/duplicate/cross-owner/cross-registrar identity, altered folio/ISIN and mismatched fund names or categories.
- `CustodyHoldingViewModelTest` — locked reads, explicit post-staging authentication, once-only write, missing owner, cancellation and no automatic ledger transaction.
- `NativeCustodyHoldingInstrumentedTest` — actual Android SQLCipher enrollment, atomic rollback, database reopen, v3 backup, v2 logical-restore compatibility and v2→v3 migration.
- `CustodyHoldingShellTest` and additional statement-navigation tests — secondary onboarding route, form staging, no unsolicited write, and route out of missing CAS mappings.
- Retained M1–M6 JVM golden, encrypted vault, identity/Keystore/backup, ledger, authorization, CAS contract and Compose tests.
- `android-native/scripts/validate_schema.py` — production-DDL-derived SQL migration and integrity preflight, including v3 mapping constraints and immutability.

**Do not mark these M7 tests PASS** until the exact GitHub Actions source SHA, downloaded JUnit XML, APK contents, CASPARSER diff guard and API-matrix results have been inspected. A green test runner alone is not an independent pentest.

## 6. Physical security, account recovery and release-signing gates

A disposable physical-device test runner, [`run_physical_qa.sh`](../../android-native/scripts/run_physical_qa.sh), is committed. It demands an explicit operator opt-in, refuses an already-installed GrowUrDhan app, checks API/ABI/page size, runs schema/source preflights and connected synthetic SQLCipher/Compose instrumentation, and summarizes anonymous JUnit counts. It has **not** been executed on the owner's phone in this development handoff.

The [physical-device, recovery and release signing protocol](18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md) covers Android API/OEM variants, actual ARM64 CASPARSER/PDFium, 16 KiB page-size loading, real OS credential flow, background lock, screenshot prevention, malicious document and URI behavior, separate-device encrypted backup recovery, independent OWASP MASVS/MASTG testing and owner-approved signing provenance.

Real **production** upload signing remains owner-controlled through [`native-signed-aab.yml`](../../.github/workflows/native-signed-aab.yml), a manual protected GitHub workflow with pinned upload certificate. Disposable CI signing does **not** establish Play upload readiness. Do not paste keys or backup passwords into chats, GitHub issues or build logs.

## 7. Still open before commercial release

1. Test actual supported CAMS/KFintech CAS PDF fixtures on a real ARM64 phone, verify all parser identity fields and challenge malformed/encrypted PDFs.
2. Complete registry mappings and owner/document identity validation for NSDL/CDSL/NPS rather than advertising universal CAS coverage.
3. Run independent mobile app penetration testing and triage/retest all critical/high findings.
4. Build and test consumer encrypted backup/recovery UI, including genuine cross-device migration and non-empty-vault rejection.
5. Run the physical OEM/API/page-size matrix; validate binder, SAF/document chooser, biometric and process-death behavior.
6. Obtain owner-controlled release-key artifact, Play internal test signoff, non-dev versioning, privacy/data-safety policies, appropriate permissions and licensing approval.

**Milestone disposition:** M7 authenticated CAS account/holding enrollment and identity-first domain integration are implemented in source; **CI scope verification is pending**. Play Store production and third-party security certifications are **NOT APPROVED**.
