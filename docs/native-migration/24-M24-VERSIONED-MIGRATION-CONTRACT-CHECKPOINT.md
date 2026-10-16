# M24 — Test-only versioned v1–v9 migration and populated synthetic goldens

**Date:** 2026-10-16. **Repository/branch:** shahvikask/kosha / GUD-Android-Redevelopment. **Starting SHA:** `3a7254dd41d120c65215efa82920145ea9ffc226`. **Final source SHA:** look up live GitHub HEAD after commit. **Stage:** SOURCE IMPLEMENTED / PURE PLAN LOCAL TESTED / SQLCIPHER MIGRATION JVM TESTS **NOT RUN**. **M24 NOT ACCEPTED**.

## Boundaries and actual implementation

- `android-native/core/database/src/main/kotlin/in/growurdhan/database/SchemaV9MigrationPlan.kt` is a **pure, non-I/O** version plan mapping supported v1–v8 inputs to source-defined stages 2–9. V2 has no independent DDL in frozen original; versions outside 1..9 reject without resetting data. It creates no Android database, keys, connection, table, services or financial records.
- `android-native/core/database/src/test/kotlin/in/growurdhan/database/TestOnlyV9Migration.kt` is **test-only** SQLCipher/JDBC exercise code. It checks frozen `src/db/schema.ts` SHA256, reads original migration SQL/column arrays (`SCHEMA_V3_COLUMNS`, `SCHEMA_V4_COLUMNS`, `SCHEMA_V5_SQL`, `SCHEMA_V6_SQL`, `SCHEMA_V7_COLUMNS`, `SCHEMA_V8_COLUMNS` + `SCHEMA_V8_SQL`, `SCHEMA_V9_COLUMNS`), validates source metadata/user_version and baseline table/types, refuses unsupported versions/foreign-key damage/failed quick_check and performs **single-transaction DDL + v9 metadata update** with rollback on any failure. Existing columns are skipped only when their SQL type is compatible. No destructive DROP TABLE/reset is performed during **migration**.
- Its `seedPriorVersion` constructs **synthetic legacy-compatible projections**, not copies of real historical backup formats. Original's current `SCHEMA_V1` already embeds many v3/v4/v7 columns; the test explicitly drops representative removable optional columns from disposable fixtures to exercise v3/v4/v7 add-back. This is **not** proof for every actual historical on-device layout, arbitrary user extension or Android restore.
- `android-native/core/database/src/test/kotlin/in/growurdhan/database/TestOnlyV9MigrationTest.kt`: **six new synthetic JUnit methods authored, NOT EXECUTED**. Cover deterministic plan including v2 no-op; eight input version markers, complete final 18 table/217 field names, 13 indexes, three append-only triggers and FK rejection; exact scaled-int money/quantity/charges and two synthetic owner records; unknown nested JSON retention; encrypted v8+v9 file copy/reopen; injected mid-DDL rollback preserving data/schema/version; corrupt metadata, future version, missing base table, wrong key and disposable corrupt ciphertext rejection. No real household records, credentials or statement bytes.
- Original React/Tauri/TypeScript and protected CASPARSER, original brand, .github workflows and production `EncryptedSchemaBoundary.SQLCIPHER_LINKED=false` / `OPENS_DATABASE=false` remain untouched. The SQLCipher dependency is **testImplementation** only, not a shipping vault driver.

## Actual verification in this session

1. GitHub source SHA and frozen branch verified: frozen ref `af96dcc8967ab595e1d9acee78e29e500cb99a9e`.
2. Static source-parity JS comparison of frozen migration arrays: 34 v3, 2 v4, 2 v7, 3 v8, 4 v9 columns. Original 41 DDL statements: 18 tables, 13 indexes (including one unique), three triggers and seven final ALTER additions. Lexical bracket checks **PASS** for all three Kotlin files. These do not prove compilation or execution of the JDBC migration.
3. The **exact GitHub blob** of `SchemaV9MigrationPlan.kt` (`git hash-object` identity verified locally) compiled with local kotlinc, and **12 isolated plan assertions PASSED**. This was an isolated Kotlin smoke, **not full Gradle :core:database:test**.
4. **No GitHub Actions dispatched** (owner did not authorize CI). The six new migration JUnit methods are **NOT RUN**; no Java/SQLCipher migration runtime, populated backup or Android test completed this session. The older [M24 JVM #38954683658](https://github.com/shahvikask/kosha/actions/runs/38954683658) **PASSED 7/7** on earlier `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`, and does not validate this new source.

## Remaining requirements / blockers

1. Owner-approved exact-new-SHA Gradle/JUnit test execution of six new methods. Diagnose and repair actual fixture/JVM defects without weakening assertions; document all JUnit XML and terminal status. Prior historical local Gradle worker failure remains recorded; hosted previous 7-test result was green.
2. Reconstruct exact captured historical layouts and old encrypted backup formats; verify source-specific constraints/defaults and strict v1→v9 migrations with populated golden records and unknown-field retention. Current synthetic projections are deliberately **not** represented as captured originals.
3. Production SQLCipher/Android Keystore/rollback-safe on-device migrations, authentic backup/restore, atomic low-storage recovery and production owner isolation are not yet implemented or accepted. Need separately scoped owner authorization.
4. Mobile device, comprehensive AppSec and independent release gates remain M34; M25+ not started.

**Decision:** M24 stays **STARTED / NOT ACCEPTED**. No source test is upgraded to CI JVM PASSED without an actual newer-SHA run. No changes to financial code or frozen originals.
