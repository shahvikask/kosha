# M24 — Encrypted test-only schema v9 DDL checkpoint

Date: 2026-10-16. Work repository `shahvikask/kosha`, branch `GUD-Android-Redevelopment`. Parent SHA before this increment: `7bf76d1452f4b78bdcaecc21130386bf49861b23`. After commit resolve current HEAD from GitHub. **M24 STARTED, NOT ACCEPTED.**

## Implemented source, only within M24

- `android-native/core/database/src/test/resources/schema-v9-frozen.sql` is generated from **unmodified** `src/db/schema.ts` on the frozen `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e` reference. It preserves, without retyping, original `SCHEMA_V1`, `SCHEMA_V5_SQL`, `SCHEMA_V6_SQL`, `SCHEMA_V8_SQL`, and v8/v9 additions `SCHEMA_V8_COLUMNS` plus `SCHEMA_V9_COLUMNS`. Explicit statement separators preserve CREATE TRIGGER BEGIN/END.
- Source definition has 18 tables and **210 base CREATE TABLE columns**, 13 explicitly named indexes (12 ordinary plus 1 UNIQUE), 3 triggers (immutable ledger update/delete and tax-rule update), and **seven additional entity columns** introduced by the v8/v9 ALTER migrations. **Therefore the final schema-v9 table count is 18 and its final column count is 217.** The earlier 210-column catalog is correct for the base DDL but is not the complete post-migration column count.
- `android-native/core/database/src/test/kotlin/in/growurdhan/database/EncryptedSchemaV9Test.kt` has four tests: exact frozen SHA and every DDL statement match; SQLCipher test-only encrypted create and full 217 column+type/index/trigger and empty-file encrypted copy/reopen; wrong-key/foreign-key rejection and refusal to reinitialize; append-only ledger UPDATE/DELETE rejection with a **synthetic zero-value record inside a rolled-back transaction**. Tests ensure **zero persisted rows in all 18 tables**, never use real household records or secrets, and delete temporary database and copied file.
- Test initialization explicitly sets `PRAGMA foreign_keys=ON`, uses a transaction for DDL, sets `PRAGMA user_version=9` and rolls back on errors. It refuses to mutate an existing nonempty or versioned database. **This is not a production migration strategy or vault opener.**

## Actual verification

- Source-only parity verification against frozen GitHub `src/db/schema.ts`: **PASS** for all **41** source-matched SQL statements: 18 CREATE TABLE + 12 ordinary CREATE INDEX + **1 CREATE UNIQUE INDEX** + 3 CREATE TRIGGER + 7 ALTER TABLE. No unexplained statement.
- Verified all 18 table names and 210 base columns agree with `SchemaV9Catalog.kt`, seven required v8/v9 entity fields are present, SQL lexical parentheses balanced, **no REAL financial types**, Kotlin lexical braces balanced.
- **Gradle/JUnit/actual SQLCipher runtime tests NOT RUN**. This environment has `kotlinc`, but not a checked-out repository, Gradle runner, or cached `io.github.willena:sqlite-jdbc` JAR. Current-SHA native CI **NOT RUN, NOT AUTHORIZED**. Do not infer test success from structural parity.
- Historical exact-SHA native JVM PASS: [run 38925309401](https://github.com/shahvikask/kosha/actions/runs/38925309401) tested `63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340`, before this schema addition. Latest legacy Node CI PASS for starting SHA `7bf76d1452f4b78bdcaecc21130386bf49861b23` is [run 38950501642](https://github.com/shahvikask/kosha/actions/runs/38950501642) and **does not test native database code**.

## Still missing before M24 acceptance

1. Execute `:core:database:test` and new tests under real test-only SQLCipher. Investigate any JVM / SQL / trigger failure and fix **source**, never weaken assertions. Current old local Gradle worker `Unexpected type tag 71` remains known.
2. Bind production Android SQLCipher and Android Keystore **only under a separate owner-authorized later scope**; current `EncryptedSchemaBoundary.SQLCIPHER_LINKED=false`, `OPENS_DATABASE=false`, and `PLAINTEXT_OPEN_ALLOWED=false` remain unchanged.
3. Implement and test migrations from older existing vaults with lossless rollback, corrupt/unknown schema rejection, original data preservation and backup field retention; current test intentionally refuses existing data and tests **only an empty encrypted file copy**, not production portable backup.
4. Expand golden synthetic lineage/money scale/immutability and source parity fixtures, no real private customer data; cross-device & identity integrations remain subsequent milestones.
5. Owner-authorized, exact-new-`main`? **No**: exact `GUD-Android-Redevelopment` SHA native CI only; no legacy Tauri AAB; no change to frozen branches or protected CASPARSER.

## Decision boundary

This is a SOURCE IMPLEMENTED / STATIC VERIFIED checkpoint only. M00 remains owner-accepted. M24 remains OPEN. No CI spending requested or dispatched.

## Superseding exact-SHA verified JVM evidence — 2026-10-16

**Native JVM CI PASSED** on tested source `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5` in [run #38954683658](https://github.com/shahvikask/kosha/actions/runs/38954683658), job 117154441202. Workflow addition only; underlying frozen SQL/Kotlin tests unchanged from `e7d72b71bb98618ae187246557f1f2cb5fda343c`. Actual GitHub job log prints the exact tested SHA and `BUILD SUCCESSFUL in 1m 49s`.

**JUnit artifact downloaded and opened:** artifact 12070905373, 9,769-byte `m24-encrypted-schema-junit`; all tests **7 PASSED**, 0 failed, 0 errors, 0 skipped:
- EncryptedSchemaV9Test: 4/4 passing for source equality, encrypted v9 schema/reopen/empty copy, foreign-key and guard, immutable trigger/rollback.
- DatabaseBoundaryTest: 2/2 passing.
- SqlCipherOpenTest: 1/1 passing.

Previous statement `Gradle/JUnit/actual SQLCipher runtime tests NOT RUN` is historical pre-CI evidence, **superseded for this exact tested SHA only**. The tests prove an empty **JVM SQLCipher** schema, not populated backup retention, Android SQLCipher, Keystore, production migrations or full M24 acceptance. M24 remains OPEN. Legacy frozen original and approved branding unchanged.

## 2026-10-16 source update (not superseding exact-old-SHA JVM CI)

New `RestoreV9Plan` and `RestoreV9InsertTransaction` core source locally compiled against byte-exact Git blobs with 1 accepted/12 rejected row plans and 1 fake committed/5 rejected transactional cases; Android `AndroidRestoreV9SqlTarget` compiled against a synthetic interface stub only. **NO actual SQLCipher native Android compilation, encrypted database restore transaction, JUnit XML or device gate passed for the new source**. The historical SQLCipher JVM results in this checkpoint apply to their precise historical SHAs only. **M24 NOT ACCEPTED**.

## 2026-10-17 — strictly read-only JSON adapter source, zero new SQLCipher evidence

Added source `FrozenJsonV9Adapter.kt` to derive v9 rows exclusively from GCM-authenticated original v2 plaintext, and read-only `LockedRestoreStaging.previewAuthenticatedV9`. Earlier 20/20 M24 JVM CI result remains confined to old SHA `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13`. Neither changed parser nor SQLCipher Android adapter compiled on the new branch source because Android SDK/Gradle checkout/AAR unavailable. Authored source tests now 24 JVM and 7 Android; **NO current-SHA test execution**. Immutable trigger, FKs, encrypted row import, Keystore and device gates NOT VERIFIED. No Actions dispatched. **M24 NOT ACCEPTED.**

## 2026-10-17 — schema's absent holding quantity makes historical fallback a P0 loss-risk

Native v9 `holding` has no quantity column (original architecture derives positions from `ledger_event`). Frozen old `positions.ts` can derive positive quantities *without persisted events*. New exact-blob Kotlin `RestoreLedgerPreflight` refuses those data states, standalone PASS 3 accepted/8 denied. This is **not actual SQLCipher migration, trigger, foreign key or Android compilation**; prior 20/20 CI on older SHA remains historical. 24 JVM/7 Android @Test methods not rerun, no Actions. M24 NOT ACCEPTED.

## 2026-10-17 — eleven original metadata columns mapped; no SQLCipher build acceptance

Verified the canonical frozen v9 SQL `m24-frozen-v9.sql` Git blob `f1db349c8fd729ab2180d650208c6246ac950aa2` contains all 11 new first-class source-to-v9 metadata columns (account.dp_id/client_id/bo_id/nominee, instrument.isin, holding.notes/needs_review, ledger_event.import_batch_id/source_ref/notes, goal.priority). Exact-source `RestoreV9Plan` host Kotlin compilation/execution and 11 original metadata assertions PASS; the Android `FrozenJsonV9Adapter` source was mapped but NOT compiled against SDK/AAR. **No SQLCipher transaction, Gradle JUnit, FK, trigger or device proof on newest source.** 24 authored JVM and 7 authored Android methods unrun. Historical 20/20 JVM run applies only older SHA. M24 NOT ACCEPTED.

## 2026-10-17 — exact-SHA full 24-test JVM regression and app Kotlin compile confirmed

Run #39023121369 at source `aa8aaa6bd240170d0472aa341d44a56b4e9aa60d`: JVM 24/24 PASS; native app Kotlin compile FAILED due to incorrect `DatabaseErrorHandler` lambda arity in `EncryptedVaultStore.kt`. Fixed and ran [#39023384556](https://github.com/shahvikask/kosha/actions/runs/39023384556) on `38d349a2676a4c99cc9d80dfcf07a1a03efa793d`: attempt 1 Paparazzi plugin resolution failure, attempt 2 **SUCCESS** with 24/24 XML and `:app:compileDebugKotlin` success on SDK35 and pinned SQLCipher. Artifact 12097686982 (SHA256 `e4256aab792616494935849c211b5a37ff17d63a0c86f74b41e9e636a710dd40`). Full encrypted row restore, runtime key/error handling, Android instrumentation 7, migration/physical-device/AppSec still unverified. M24 NOT ACCEPTED.

## 2026-10-17 — actual Android encrypted schema/immutable ledger/FK and populated restore transaction CI PASS

Source fix `d1d872368ebb16c5055c561a415fec85e777c324` aligns instrumentation-only SQLCipher4.17 `DatabaseErrorHandler { _, _ -> ... }`, never deletes corrupt vaults. Dedicated source-SHA-pinned workflow `m24-android-instrumentation.yml`. [Run #39024627068](https://github.com/shahvikask/kosha/actions/runs/39024627068) SHA `c5ead91ebc332c9a35c3e8e8803dd266039db84a`: API35 x86_64 emulator 7/7 PASS, SQLCipher creates/reopens cipher (not plaintext SQLite header), immutable ledger update/delete aborts, account FK rejection, wrong key/corrupt-copy preserving original, Keystore reopen and synthetic marker migration. [Run #39025190962](https://github.com/shahvikask/kosha/actions/runs/39025190962) SHA `900fa54bf7e73f5e17612dda01a08ed95abd64ec`: 7/7 PASS with stronger existing 7th test applying actual typed plan via `AndroidRestoreV9SqlTarget` and `RestoreV9InsertTransaction` into new isolated SQLCipher, exact 64-bit amount/source JSON across reopen; duplicate denial; FK violation after earlier row inserts rolls back ALL native rows, including after close/reopen. XML evidence [artifact #12098585952](https://github.com/shahvikask/kosha/actions/runs/39025190962/artifacts/12098585952) SHA256 `dc434010cd3b93c898fe04e65450031d8fb322f8a7afb34e5f021be3746fd3bf`, `M24_ANDROID_JUNIT_CONFIRMED total=7 failures=0 errors=0 skipped=0`. Tested on emulator ONLY. Authentic historical backup GCM recovery→target key→replay claim→promotion, real v1-v8 migrations, power-loss/low-space, real device/AppSec/owner signoff still OPEN. M24 NOT ACCEPTED.

## 2026-10-17 — M24 candidate/claim crash isolation hardened; EXACT SAME SHA JVM 24/24 + Android 10/10 CI PASS

Live starting HEAD `7dbf5366a1dfe64572551056b666a14ff5adce65`. Source audited `ApprovedNewVaultRestore` and `RestoreOnceJournal` (per-fingerprint claims), `EncryptedRestorePublisher` (reserved atomic non-overwrite), `EncryptedVaultStore`, `KeystoreWrappedVaultKey`, and `VaultLifecycleLock`. Detected **P0 inter-archive interrupted restore** gap: after an aborted attempt before live publication, existing `restore-claims/*.claim`, `vault-key.v1.restore-*`, `growurdhan-v9.sqlcipher.restore-*`, and SQLCipher sidecars did not universally block a new DIFFERENT archive or blank vault following removal of only pending staged bytes. Added read-only `RestoreArtifactGuard` in native app, called under `VaultLifecycleLock` by BOTH approved restore and fresh database creation. No cleanup/reset. Synthetic tenth Android test covers claim, candidate key, database and WAL artifacts, refusing new restore and blank vault, and preserving original bytes. Older 9 Android test cases unchanged.

**Combined exact-SHA** [M24 #39048444830](https://github.com/shahvikask/kosha/actions/runs/39048444830) at `dce3b579794d2029a6e3e710978c3dd360c79649`, attempt 1 terminal **SUCCESS**: JVM `24 methods, failures=0, errors=0, skipped=0`; Android API35 x86_64 emulator `10 methods, failures=0, errors=0, skipped=0`; `:app:compileDebugAndroidTestKotlin` SUCCESS. [JVM XML artifact #12106283093](https://github.com/shahvikask/kosha/actions/runs/39048444830/artifacts/12106283093) digest `sha256:a9cfefe1bd0aff982a309ab4b87984daee4015861c8391003df3458c7dd1b4d3`; [Android XML artifact #12107355117](https://github.com/shahvikask/kosha/actions/runs/39048444830/artifacts/12107355117) digest `sha256:95891702392f925d8394517a2b4ab5a9cde27e104dfecd63cd431dbd903ae100`. Standard Kosha CI #39048444831 same SHA SUCCESS. Subsequent documentation-only commits use `[skip ci]`; tested SHA remains separate.

**Unresolved:** deliberately permanent replay claims have NO owner-reviewed resume/forensic release procedure; crash/low-storage/power-loss at each publication boundary untested, candidate envelope/cipher repair and full metadata fidelity or zero-event legacy position policy incomplete, genuine historic v1–v8 external samples and ARM64 physical device/16KiB, independent AppSec and owner signoff missing. Current source tested on emulator and JVM ONLY; no production acceptance. M24 STARTED / NOT ACCEPTED.

## 2026-10-17 — frozen KOSHA1 optional crypto metadata verified; historical migration remains open

Real source compatibility proof is scoped to encrypted KOSHA1 outer metadata. The original account/ownership, immutable trigger, FK and new SQLCipher GCM restore probes remain present in the complete Android 14-test regression. Not a proof of all v9 historical columns or financial event histories.

**Exact test evidence:** corrected source `327b24211ab6626e17836f941be485f354689636`, [M24 GitHub Actions #39056976727](https://github.com/shahvikask/kosha/actions/runs/39056976727), attempt 1 terminal SUCCESS 2026-10-17T18:25:09Z: database **25/25** JVM and Android SDK35 x86_64 **14/14** instrumentation; 0 failure/error/skip. [JVM JUnit #12109976229](https://github.com/shahvikask/kosha/actions/runs/39056976727/artifacts/12109976229), SHA256 `7dfd88a1e6e621539109d143df36f4a7940177b54130becbaef868fc49a4b2dd`; [Android JUnit #12110257579](https://github.com/shahvikask/kosha/actions/runs/39056976727/artifacts/12110257579), SHA256 `544c3ab960370bad824197a2e720dec3bcfb786900d9b1636447aea839cc92be`. Standard CI #39056976721 SUCCESS on same SHA. Initial M24 [#39056752917](https://github.com/shahvikask/kosha/actions/runs/39056752917) FAILED only due new test calling decoder method on encoder; corrected test-only expression, no assertions removed. Documentation-only later commits use `[skip ci]` and are not source-tested. **M24 STARTED / NOT ACCEPTED**: true historic inner schema, full first-class parity, owner-approved durable-claim recovery, power-loss/low-storage/device/AppSec/owner signoff still OPEN.

## 2026-10-18 — bounded M24 orphaned AtomicFile backup guard (CI PENDING)

2026-10-18 M24: New RestoreArtifactGuard blocks orphaned restore-pending.v1.kosha.bak and vault-key.v1.bak before blank-vault creation, preserving financial evidence. New API35 instrumentation orphanedAtomicBackupsCannotBeSilentlyReplacedByBlankVault verifies immutable backup bytes and absence of both live vault and key; Android expected regression now 15, JVM still 25. Source 9475c851a14d0d06c7e948e38090fd9cdf8de2d4; workflow/15-test exact-source SHA 5f15eaca62ceeb30bd490a40953c693d38c0182f. Run https://github.com/shahvikask/kosha/actions/runs/39085997854 started, conclusion NOT VERIFIED when this entry authored; inspect live results before calling pass. Prior verified CI 39056976727 25 JVM + 14 Android on older 327b24211ab6626e17836f941be485f354689636 only. No physical device, AppSec, owner approval or historical v1-v8 parity; M24 STARTED NOT ACCEPTED. Original frozen references untouched.
