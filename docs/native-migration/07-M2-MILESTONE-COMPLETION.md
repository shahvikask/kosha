# GrowUrDhan — M2 Milestone Completion and Verification Report

**Milestone:** M2 — Native vault and financial database  
**Verified implementation commit:** `efcaeaabae4f8a65c96fad520aad4aa535dba530`  
**Working branch:** `GUD-Android-ChatGPT` (all other branches read-only)  
**Verification date:** 2026-10-08  
**Outcome:** **PASS for M2 foundation acceptance on CI Android 15/API 35 emulator**; **NOT a production security certification or Play Store approval**.  
**Owner's pre-release decision:** GrowUrDhan has not shipped on Google Play; a new native vault schema is allowed. Preserve the ability to export or intentionally reset unpublished development data; no mandatory published-user migration.

## 1. Auditable GitHub evidence

| Evidence | Verified result |
| --- | --- |
| [Native Android M2 run #37789886674](https://github.com/shahvikask/kosha/actions/runs/37789886674) | GitHub `completed / success`, attempt 1, head SHA exactly `efcaeaa...`, branch `GUD-Android-ChatGPT`, 2026-10-08 14:07–14:20 UTC |
| [Native job #113354150933](https://github.com/shahvikask/kosha/actions/runs/37789886674/job/113354150933) | All required steps completed successfully, including preflight, immutable CASPARSER check, JVM tests, debug/test APK compilation, emulator instrumented tests, and artifact uploads |
| Schema preflight | `PASS: 14 production DDL statements, 7 tables, ledger triggers and snapshot foreign keys.` |
| Domain and app JVM unit tests | `:domain:test` + `:app:testDebugUnitTest` succeeded; JVM test counts were not separately asserted for this report |
| Native debug APK and Android test APK compilation | `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:data:assembleDebugAndroidTest` succeeded |
| Compose instrumentation on Android emulator | **3 tests; 0 failures; 0 errors; 0 skipped** |
| Encrypted vault instrumentation on Android emulator | **7 tests; 0 failures; 0 errors; 0 skipped** |
| [Kosha CI run #37789886769](https://github.com/shahvikask/kosha/actions/runs/37789886769) | GitHub `completed / success`, same implementation SHA; existing hybrid app checks continue to pass |
| CASPARSER protected source diff | CI hash/diff guard passed; manual branch comparison to `GUD-Android-Gmail` showed no changes in `src/casparser-main/**` or frozen parser invocation/dependency files |

The JUnit XML was independently inspected from the GitHub artifact (not inferred merely from a green workflow button). The downloaded ZIP passed integrity inspection and contained exactly two JUnit suites with the counts above. The CI log independently reports all three Compose tests finished, all seven vault tests finished, and `BUILD SUCCESSFUL`.

### Artifact records

| GitHub artifact | ID | Size (bytes) | SHA-256 digest for GitHub artifact archive |
| --- | ---: | ---: | --- |
| `GrowUrDhan-Native-M2-APKs` | `11556681089` | `31,298,483` | `446297833c89a513b8853ccd83b0c764c2f24f027392a7c6fd6edc3cab980130` |
| `GrowUrDhan-Native-M2-Instrumentation-Reports` | `11556083931` | `138,108` | `c275fde1d7346ed45b4a90ae25b24cc686a169f1b12709164e8e901a82d42533` |

These GitHub artifact digests describe the stored ZIP archives. They **are not** release APK signatures, Play AAB attestation, or independent hardware security tests. Artifacts may expire according to GitHub retention policy; this report preserves their IDs and digests.

## 2. Exact Android instrumentation cases confirmed

### Encrypted vault test suite — 7/7

1. `statusDoesNotCreateAndDatabaseIsCiphertext` — no implicit creation, non-plaintext database header, Keystore wrapping, reopen and FK health.
2. `missingWrappedKeyFailsClosedWithoutErasingDatabase` — missing key rejects open without deleting the encrypted database.
3. `missingDatabaseWithSurvivingKeyCannotBeRecreated` — a surviving key cannot silently create a replacement empty financial database.
4. `olderSnapshotIsRejectedWithoutChangingNewerBook` — older source does not overwrite newer statement state and same-file imports remain idempotent.
5. `crossOwnerSnapshotFailureRollsBackAndHashIsIdempotent` — incorrect family ownership rolls back the import; complete valid snapshot is idempotent.
6. `directSqlCannotUpdateOrDeleteLedger` — direct SQL UPDATE and DELETE on append-only `ledger_event` are rejected.
7. `authenticatedBackupRestoresAtomicallyOnlyIntoEmptyVault` — password-protected round-trip, wrong password/tamper rejection, and no overwrite of an existing book.

The Android XML suite is named `in.growurdhan.data.vault.NativeVaultInstrumentedTest`; results: `tests=7 failures=0 errors=0 skipped=0`.

### Compose UI test suite — 3/3

1. `settingsNavigationAndThemeIntent` — accessible settings navigation and state intent.
2. `privacyDestinationShowsActualStorageStatus` — expected privacy content.
3. `vaultIsCreatedOnlyAfterExplicitUserAction` — native vault creation must be user-initiated.

The Android XML suite is named `in.growurdhan.app.presentation.NativeShellTest`; results: `tests=3 failures=0 errors=0 skipped=0`.

**Test environment:** GitHub-hosted Ubuntu runner, Java 17, Android SDK compile target 36, Android emulator Google APIs **API 35, x86_64**, Gradle 8.13. No physical-device testing is claimed.

## 3. Architecture delivered and demonstrated

- Kotlin multi-module native Android project, Hilt DI, MVVM with StateFlow, responsive Material 3 UI.
- Native Zetetic SQLCipher encrypted database in `Context.noBackupFilesDir`, schema version 1 with seven finance tables.
- Generated 256-bit SQLCipher database key wrapped using an Android Keystore non-exportable AES-GCM key, encrypted envelope persisted atomically.
- No silent vault re-creation when wrapped key or encrypted DB is missing, and integrity checks required during open.
- Exact integer scales for monetary amounts and quantities, ownership foreign keys, and database-level ledger UPDATE/DELETE denial triggers.
- Transactional, owner-validated, complete-only, SHA-idempotent statement snapshots; rejects older snapshots and never treats snapshot rows as cash purchases.
- Password-encrypted `GUDNBACK` v1 portable export/import with authenticated AES-GCM and empty-vault-only rollback-safe restoration.
- No CASPARSER changes; native statement parser integration is a future milestone.

These are implementation facts plus exercised tests, **not** proof of an independent security audit or complete feature migration. See [06-M2-ENCRYPTED-VAULT-AS-BUILT.md](06-M2-ENCRYPTED-VAULT-AS-BUILT.md) for implementation details and APIs.

## 4. Failure-to-fix history (why the final run matters)

| Previous CI obstacle | Diagnosis | Resolution demonstrated by final run |
| --- | --- | --- |
| AVD failed to boot due to insufficient runner disk | Android emulator userdata partition exceeded free runner space | Reclaimed unused runner toolchains; emulator booted |
| `AndroidJUnit4ClassRunner` initialization error | Kotlin expression-bodied `@Test` exposed non-void return value | Explicit `Unit` tests; test runner now executes individual tests |
| `PRAGMA secure_delete = ON` rejected | SQLCipher expects query API for result-producing PRAGMA | Execute via `rawQuery` and check active setting |
| `CREATE TABLE snapshot_import` syntax error near `imported_at` | Table-level `UNIQUE` constraint declared before last column | Moved table constraint after columns |
| SQL errors discovered only after long emulator startup | No host-side schema compiler gate | Added `android-native/scripts/validate_schema.py`, running before Android build |

The final run validates that these blockers were resolved sufficiently for the covered M2 test suites. It does not imply there are no other bugs.

## 5. Remaining release blockers and explicit M2 exclusions

1. **Authentication and lifecycle:** No finalized user password/device credential or biometric gate; enforce vault lock/unlock, session timeout and sensitive-screen protection before any commercial distribution.
2. **Full financial behavior:** Portfolio calculations, holding entry/edit journeys, family management, reconciliation UI and existing React algorithms are not yet ported and matched by golden tests.
3. **CASPARSER adapter:** Frozen Python/Chaquopy engine packaging and Kotlin result validation/reconciliation still require independent contract tests; engine remains immutable.
4. **Backup UX and recovery:** No completed Storage Access Framework export/import workflow or tested phone-loss recovery across device types.
5. **External integrations:** Gmail/Google restricted-scope authorization, Play Billing/licences, market prices and scheduled background work remain future milestones.
6. **Device and release coverage:** API 26–36 matrix, physical devices, native 16 KiB memory-page support, ABI coverage, R8 release-minification, signed AAB, Play policy and privacy disclosures are not yet verified.
7. **Security validation:** Manual OWASP MASVS assessment, code/dependency scans, secure migration/failure-injection across devices, backup misuse/rate-limit review and independent penetration testing remain outstanding.
8. **Retention and cleanup:** Old React/Tauri files should only be removed after parity and approved deletion gates; optional developer data import/reset is explicit.

## 6. Exit decision and next milestone

**M2 foundation: COMPLETE AND VERIFIED under the tested GitHub Android CI environment.**  
**Commercial release: NOT READY.**  
**M3 next:** Port and independently golden-test the financial domain, scaled integer math, ledger use cases, holdings/ownership calculations, portfolio totals, snapshot reconciliation and reporting. Maintain the CASPARSER black-box boundary. Reserve parser integration and complete user journeys for later milestones.

For each further change, commit only to `GUD-Android-ChatGPT`, provide its SHA and resulting GitHub workflow run number, and let the owner report the long build result rather than repeatedly polling.
