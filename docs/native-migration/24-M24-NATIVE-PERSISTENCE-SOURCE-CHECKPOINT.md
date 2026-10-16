# M24 — Native encrypted persistence source checkpoint (NOT ACCEPTED)

Date: 2026-10-16. Remote: `https://github.com/shahvikask/kosha.git`.
Branch: `GUD-Android-Redevelopment`.
Frozen reference: `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`.

## Frozen format and source provenance

- Frozen `src/lib/backup.ts` Git blob `5643af9eeb4ec00dfbf919b58cf998ffab67e858`, `src/lib/vault.ts` `fa311299c08f3a327a465e1175664d778229bb3b`.
- `scripts/m24-export-frozen-backup-vectors.ts` invokes those **unchanged** exporters and checks both frozen Git blob hashes before emitting synthetic GUDVLT01/KOSHA1 JSON vectors; **script itself has NOT EXECUTED** in this session. No authentic user backup or frozen-exporter-produced envelope bytes captured, and no real owner data accessed.
- `LegacyBackupEnvelopeReader.kt` is independent JCE/PBKDF2-SHA256/AES-256-GCM decoder for binary GUDVLT01 and typed password-only KOSHA1 fields; bounded size/KDF, signed GCM tag, raw decrypted JSON preserved as source text; no account authorization or actual restore.
- Four **NEW JUnit test methods** in `LegacyBackupEnvelopeReaderTest.kt`, **NOT RUN**. The M24 targeted workflow is updated (without dispatch) to enforce **24** JVM test methods (prior 20 plus 4).

## Native source implemented (not tested)

- Dependency pin `net.zetetic:sqlcipher-android:4.17.0` and `androidx.sqlite:sqlite:2.6.2` under `android-native/app`. Provider APIs and artifact publicly verified; selected this version to avoid a Kotlin 2.2.10 transitive upgrade from newer 4.18/4.19 releases while project remains Kotlin 2.0.21. Dependency resolution/16-KiB ABI/device behavior **NOT VERIFIED**. Upgrade after matrix confirmation.
- `KeystoreWrappedVaultKey.kt`: random AES-256 key for SQLCipher, encrypted by non-exportable AndroidKeyStore AES-GCM key, `AtomicFile` metadata under `noBackupFilesDir`; refuses missing key/metadata, corrupted envelope, missing DB with surviving key, and identified legacy vaults; key buffer cleared when passed to SQLCipher.
- `EncryptedVaultStore.kt`: ONLY `net.zetetic.database.sqlcipher.SQLiteDatabase`, no Android plaintext open; explicit `System.loadLibrary("sqlcipher")`; **non-deleting** `DatabaseErrorHandler` (upstream default can delete corrupt database); check SQLCipher pragma, quick_check, foreign_key_check, version/metadata, 18 legacy table identities, no REAL affinities, integer monetary fields, ownership FK and ledger UPDATE/DELETE trigger SQL.
- Fresh schema asset copied **byte for byte** from test-only v9 fixture (18 tables, 3 triggers). Versioned SQL upgrade asset extracted from frozen TypeScript (step method counts: v3=34, v4=2, v5=4, v6=1, v7=2, v8=4, v9=4); transaction advances version and rolls back on DDL, FK or integrity error; no silent reset. Unknown valid legacy SQL columns are intended to survive ALTER operations. Native v1–v8 genuine database format compatibility **NOT VERIFIED**.
- `LockedRestoreStaging.kt`: one-time atomic *encrypted original archive* quarantine in `noBackupFilesDir`, reject plaintext/unknown envelopes, read-only decrypt and owner inspection. It does not commit family/holding/ledger rows, finish account/PAN/DOB/security-answer verification, or implement replay-approval receipts. This is explicitly **NOT a completed transactional portfolio restore**.
- Five new isolated Android instrumentation test methods in `EncryptedVaultStoreInstrumentedTest.kt` cover encrypted header, restart, wrong key, corrupted copy without source modification, append-only ledger, FK, synthetic v8 upgrade, and missing-table refusal. **NOT RUN**, no Android emulator/device/Gradle run.
- Original React/Tauri, approved logo, frozen CASPARSER unchanged. No M25, AAB or emulator workflow.

## Verification and evidence

- Last old source native [JVM run 38961697701](https://github.com/shahvikask/kosha/actions/runs/38961697701) **SUCCESS 20/20** at tested SHA `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13`; cannot transfer that result to current code.
- Local independent smoke (not frozen module): Node WebCrypto synthetic 600k PBKDF2 envelopes decoded by standalone `kotlinc`/Java-JCE Kotlin jar; both binary and KOSHA1 preserved owner, unknown, `9007199254740993` and `1234567890123`, rejected tampering and mismatched identity: **PASS**. This exercised source-derived wire logic, **NOT execution of committed TypeScript exporter nor compiled repository Kotlin source**.
- Static remote GitHub source/asset check: bundled fresh schema byte-identical to frozen JVM fixture, 18 tables, 3 triggers; version asset contains expected frozen source-derived phase counts: **PASS**. SQLCipher/Keystore production Kotlin compilation, dependency resolution, all four new JUnit tests, five Android instrumented tests: **NOT RUN**.
- Internet access from local container unavailable (`git ls-remote` cannot resolve github.com); GitHub connector wrote files directly. Working tree cleanliness **UNVERIFIED**. No CI/Actions budget spent.

## Acceptance blockers

1. Actual frozen TypeScript exporter script execution, captured interoperable vectors, Kotlin decoder comparison and true historical fixtures.
2. Verify native Kotlin/Gradle compile with pinned AAR, Android API behavior and security of non-deleting corruption handler; 16-KiB and ARM64 hardware testing.
3. Genuine v1–v8 SQLCipher source migration data parity, rollback under storage faults, unknown fields and append-only triggers after real device opens; process-level lifecycle lock.
4. Full identity/PAN/DOB/recovery/replay/atomic row-level restore implementation and its security tests; staging is deliberately non-committing.
5. Real-device testing, owner security acceptance, release-gate tests.

**M24 OPEN, NOT ACCEPTED.** Read AGENTS.md and spec v1.2 again next session; exact source HEAD must be re-resolved after documentation commit. No Actions dispatch without explicit owner authorization.

## 2026-10-16 — bounded source repair and local exact-blob Kotlin verification

- GitHub live starting HEAD `edd0e25385a651e79f412d3a1eb98e94abd75e6d`; frozen Gmail `af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Local repo cannot clone: `git ls-remote` DNS failed. Native Android SDK/Gradle dependencies not available locally; no GitHub Actions authorized.
- Source repair: pin script hash prefix to actual Git NUL byte (local Node hash check PASS); close incomplete `EncryptedVaultStore.kt` class bracket; before upgrade require all 15 frozen V1 tables plus V5 listed_security, V6 vault_account and V8 gmail_seen when applicable, reject missing/FK/ledger guards, then verify all v9 field names after upgrades without discarding unknown extension fields. **None of these Android changes have been compiled or run.**
- Owner-aware staging now checks member uniqueness and owner references across holdings, ledger events, accounts and goals; malformed v2 arrays rejected; bounded copy read limits even when AtomicFile recovers `.bak`. An additional Android test requires refusing plaintext, duplicate staging and tampered archive without touching a live vault. Instrumentation total now **six unrun** methods.
- Locally materialized exact bytes of published Kotlin `LegacyBackupEnvelopeReader.kt`: Git blob SHA `12f1f8776977df43549b667d89a2bfa4767cefc7` verified with `git hash-object`; `kotlinc` compilation and standalone synthetic Node WebCrypto GUDVLT01/KOSHA1 two-format decrypt/4 negative tests **PASS**; opaque owner IDs and string integer invariants retained. This is format compatibility only, NOT executed frozen `packBackup`/`encryptJson`. Add oversize-wire preflight and corresponding JUnit assertion (24 expected methods remain **NOT RUN**). No exporter-generated files checked in; no private backups touched.
- **M24 still open**. Absolutely NO authenticated live restore/replay-safe atomic row import, no production Android compile/runtime, no 16KiB/ARM64 or physical device proof. Need checkout environment or explicitly authorized source-pinned targeted CI to close compile blockers; CI requires separate owner approval.
