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
