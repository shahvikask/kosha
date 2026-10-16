# GrowUrDhan implementation status

Updated: 2026-10-16. M24 is started and not accepted. New Android SQLCipher source exists but is not compiled, wired to UI or verified on a device.

## Repository

| Item | Value |
|---|---|
| Remote | `https://github.com/shahvikask/kosha` |
| Working branch | `GUD-Android-Redevelopment` |
| Frozen reference | `GUD-Android-Gmail` @ `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| GitHub tip at the start of this workflow | `e2fd33ad4f27cd4dd33b13d2d07002247261064b` |
| Workflow-file fix | `99064638e0f1c9ac000ec5ed3379ffbe59041dee` |
| Last accepted SHA | `e551472498760f46ab0f3a5b248e963b496f2323` for M00, with limitations |
| Native app | scaffold only, application id `in.growurdhan.redevelopment` |
| CI | Kosha CI run 38912221715 passed on the fix SHA. It does not build `android-native/`. The AAB workflow no longer fails at file load on this branch |

## Milestone

**Current:** M24 — started, not accepted. M00 stays owner-accepted with limitations.

| Milestone piece | Status |
|---|---|
| Instruction files and legacy inventory | on GitHub |
| Schema v9 map | `SchemaV9Catalog` names the 18 tables and 210 columns from `src/db/schema.ts`. There is no table named snapshot |
| Encrypted schema | A unit test links `io.github.willena:sqlite-jdbc` 3.53.4.0, opens SQLCipher legacy 4, checks the header is not plaintext, rejects the wrong key, and deletes the file. `EncryptedSchemaBoundary.SQLCIPHER_LINKED` stays false because production code does not open a database |
| Migrations, triggers, golden rows | NOT STARTED |
| `android-native/` scaffold | debug APK assembled locally. Locked, sample home, and sample holding concept screens |
| Lock behavior | `ConceptLock.vaultOpen` is always false. There is no unlock method and no password field |
| Section 6A screenshots | Owner accepted the three Paparazzi images on `d7868b215f8a69b10c9170cf7614b74012c279ce` |
| SQLCipher and Chaquopy | The JVM SQLCipher driver is a test dependency only. Chaquopy is not linked. No financial row was inserted |
| M25–M34 | NOT STARTED |

## Requirements

P01–P39 and E01–E18 remain **NOT STARTED**. See `parity-register.csv`.

## UI status, separate from financial status

| Surface | Status |
|---|---|
| Locked, sample home, sample holding | Compose source compiled on an earlier SHA. Not rebuilt in this repair |
| Other legacy surfaces | mapped, not built |
| Brand mark | Visible in the accepted concept screenshots. This is not a separate logo audit |

## Gates

| Gate | Status |
|---|---|
| Local debug assemble | PASS. `:app:assembleDebug` was UP-TO-DATE and finished successfully with `ANDROID_HOME=/opt/android-sdk`. The existing debug APK is 9,511,985 bytes |
| Model JVM tests | PASS. Fresh XML at 2026-10-16T14:24:41Z. 10 tests, 0 failures, in the same Gradle invocation as the app tests |
| Compose semantics | PASS. `:app:testDebugUnitTest` wrote 3 tests, 0 failures, at 2026-10-16T14:24:43Z. The three assertions were not changed. Bitmap capture is still not part of this test |
| Bitmap capture | PASS locally via Paparazzi. The owner accepted the three images. See decision D13 |
| `./gradlew :core:model:test` | PASS, 10 tests. See decisions D07 and D08 |
| Boundary tests | PASS at 2026-10-16T18:43:29Z and 2026-10-16T18:43:30Z: 3 tests, 0 failures. This session's Gradle `--rerun-tasks` died in the test worker and wrote no new XML. Direct JUnitCore of the same classes returned OK (3 tests). See decision D17 |
| Native CI | PASS on `63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340`. Run 38925309401. The log says BUILD SUCCESSFUL. It does not print individual test counts. Screenshot pixels were not produced |
| Emulator and device | Emulator run 38948959678 succeeded on `430c171`. Six PNGs were uploaded. Three were opened this session and show the concept screens, not blank frames. That is not a new owner acceptance. No device run |
| Gmail and Play Billing | BLOCKED |
| Security signoff and owner visual acceptance | Concept screenshots accepted. M00 accepted by the owner with limitations. Security signoff is not done |

## Accepted limitations

These were not closed by the owner's acceptance:

- Production code does not link SQLCipher or Chaquopy, and there is no financial table. A test-only JVM driver can open and close a temporary encrypted file.
- Native JVM run 38925309401 passed on `63b5b8d`, not on the accepted tip, and it did not run Paparazzi.
- Emulator run 38948959678 passed on `430c171`. Those pixels are not a new owner acceptance. No device or security review has passed.
- P01–P39 and E01–E18 are not started.
- Screens other than the three concept screens are mapped, not built.

## Next authorized work

Do not mark M24 accepted. Do not insert financial rows. Do not treat the JVM test driver as the Android SQLCipher library. Do not link Chaquopy. Do not dispatch the legacy Tauri AAB. Do not dispatch the native JVM workflow unless the owner says to run it.

## Supplemental M00 emulator screenshot gate — 2026-10-16

M00 remains OWNER-ACCEPTED at `e551472498760f46ab0f3a5b248e963b496f2323` with documented limitations. Separately, the owner authorized real Android emulator pixel capture. Workflow source commit [`430c171782ff967e4f107fb46f7c863ade35faf3`](https://github.com/shahvikask/kosha/commit/430c171782ff967e4f107fb46f7c863ade35faf3) adds only `.github/workflows/m00-emulator-screenshots.yml` and `android-native/scripts/m00-emulator-screenshots.sh`. It does not change the product, the original reference, the parser, financial tables, production SQLCipher, or Chaquopy.

[Run 38948959678](https://github.com/shahvikask/kosha/actions/runs/38948959678) completed with conclusion `success` on that SHA. Job `M00 API35 Compose real-pixel capture` succeeded. Artifact `m00-api35-real-compose-screenshots` is 433184 bytes and was not expired. This session opened `locked-light.png`, `sample-home-light.png`, and `sample-holding-dark.png`. They show the locked disclosure, the sample home figure labeled as not a ledger balance, and the sample holding with unknown cost. The other three PNGs were not opened. This is not a new G14 owner acceptance. M24 stays started and not accepted.

## M24 bounded source extension — 2026-10-16

Added a **test-only**, encrypted SQLCipher schema-v9 fixture derived from frozen source, with four authored JVM acceptance tests. New fixture covers 18 tables and 210 original base columns plus 7 v8/v9 migration columns (=217 final) with source indexes and append-only triggers. Parent SHA `7bf76d1452f4b78bdcaecc21130386bf49861b23`; read live HEAD after commit. Source-only static equality / column inventory passed. **No actual Gradle/JUnit/SQLCipher execution, current-source native CI or paid Actions run.** M24 remains STARTED/NOT ACCEPTED. Prior local Gradle worker failures remain historical. No production SQLCipher/Chaquopy/financial features added. See [M24 checkpoint](../native-migration/24-M24-ENCRYPTED-SCHEMA-TEST-CHECKPOINT.md).

## M24 exact-SHA native JVM test evidence — 2026-10-16

- **Owner-authorized targeted native test SUCCESS**: [GrowUrDhan M24 encrypted schema JVM #38954683658](https://github.com/shahvikask/kosha/actions/runs/38954683658), exact tested source SHA `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`. The GitHub Actions job `Verify frozen encrypted schema v9 (JVM only)` concluded success. GitHub job log reports `TESTED_SOURCE_SHA=ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`, `./gradlew :core:database:test --no-daemon --stacktrace --rerun-tasks` and `BUILD SUCCESSFUL in 1m 49s`. No emulator/AAB workflow was dispatched.
- **Independent JUnit artifact readback**: artifact ID `12070905373` (`m24-encrypted-schema-junit`, 9,769-byte ZIP); all 7 JVM tests passed, 0 failed/errors/skipped. `EncryptedSchemaV9Test`: **4/4** (exact frozen schema, encrypted copy/reopen/217 fields, FK and existing-schema guard, append-only ledger triggers); `DatabaseBoundaryTest`: **2/2**; `SqlCipherOpenTest`: **1/1**.
- **Stage:** **CI JVM PASSED — M24 PARTIAL**, not owner-accepted. The 18-table schema/210 base columns + 7 v8/v9 columns (=217), indexes, three triggers and encrypted empty database copy are verified under test-only SQLCipher on Linux/JVM. **This does not implement production Android SQLCipher, migrate any existing vault, roundtrip populated personal data or prove Android Keystore integration.**
- New CI-specific workflow is `.github/workflows/m24-encrypted-schema-jvm.yml` added in `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`, self-triggered exactly once by workflow file commit; future feature/document pushes do not re-trigger that workflow. The automatically triggered legacy Kosha CI on `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5` is separate and is not database acceptance. No further paid native CI is authorized without the owner.
- Historical local Gradle worker `Unexpected type tag 71` still appears in prior local evidence; the hosted GitHub runner completed this entire targeted test successfully. Do not erase the older failed diagnostics. M00 remains OWNER ACCEPTED; M24 remains STARTED/NOT ACCEPTED.


## 2026-10-16 M24 — synthetic versioned migration contracts SOURCE ONLY

New pure `SchemaV9MigrationPlan` (main, no I/O), and test-scoped `TestOnlyV9Migration` + six synthetic `TestOnlyV9MigrationTest` methods for supported v1–v8 inputs, transaction rollback, corrupt/incompatible version rejection, scale/owner/JSON preservation and encrypted file-copy fixtures. Original current SCHEMA_V1 embeds some v3/v4/v7 fields; synthetic historical projections are NOT authentic old backups. Source/static checks passed; exact pure-plan blob locally compiled with kotlinc and 12 plan assertions PASS. **Six new SQLCipher/JUnit tests NOT RUN.** Prior [M24 JVM #38954683658](https://github.com/shahvikask/kosha/actions/runs/38954683658) PASSED 7/7 on OLD SHA `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`, not current changes. Owner has **NOT authorized any GitHub Actions** in this session. M24 remains OPEN. [Detailed checkpoint](../native-migration/24-M24-VERSIONED-MIGRATION-CONTRACT-CHECKPOINT.md).


## Superseding exact-SHA M24 migration JVM CI — 2026-10-16

- **First owner-authorized targeted run FAILED**, [M24 run 38958340108](https://github.com/shahvikask/kosha/actions/runs/38958340108), tested SHA `05be60757c10e26cc0e0d58d25552fc08cd33b48`. Kotlin main/test compiled; Gradle ran all 13 tests and **2 FAILED**: `TestOnlyV9MigrationTest.allSupportedPriorVersionMarkersMigrateAndPreserveSyntheticOwnersAndMoney` and `injectedDdlFailureRollsBackSchemaVersionAndEverySyntheticRecord`, both at line 79. Actual failing JUnit artifact ID `12072061100`, 14,906 bytes, downloaded and inspected. Both failures arose because synthetic v1/v6 histories appended optional v3/v7 columns through SQLite `ALTER TABLE ADD COLUMN`, whereas the assertion required the *fresh DDL physical CID order*. Exact field values were not missing.
- **Source repair** commit `646e76573e533df0a9db1d41faed3cc84770fbd3` corrected the historical physical-column-order assumption; required fields are now checked as **exact identities**, **unique cardinality**, **integer money/quantity type affinities**, **no REAL columns**, with unchanged owner, JSON, rollback, ledger/FK and backup assertions. All six new tests retained. Only `TestOnlyV9MigrationTest.kt` and the M24 workflow YAML changed; no original/frozen production source.
- **Corrected exact-SHA targeted native run SUCCESS**, [M24 JVM #38958783180](https://github.com/shahvikask/kosha/actions/runs/38958783180), tested `646e76573e533df0a9db1d41faed3cc84770fbd3`, job `117168266417`. GitHub log verifies `TESTED_SOURCE_SHA=646e76573e533df0a9db1d41faed3cc84770fbd3`, `BUILD SUCCESSFUL in 2m 9s`, and `M24_JUNIT_CONFIRMED total=13 new=6 failures=0 errors=0 skipped=0`. The workflow's required-method JUnit gate passed.
- **JUnit XML independently downloaded and inspected**: artifact ID `12071881910`, name `m24-encrypted-schema-junit`, **11,861 bytes**, ZIP SHA-256 `3fd747ad582e4cd6cb9b1974fde0119d708ebd7f967793a59a02480a5ea96af4`. Four XML suites: `TestOnlyV9MigrationTest 6/6`, `EncryptedSchemaV9Test 4/4`, `DatabaseBoundaryTest 2/2`, `SqlCipherOpenTest 1/1`. **Total 13/13 PASS; 0 failures, 0 errors, 0 skipped.**
- Concurrent original/legacy [Kosha CI #38958783184](https://github.com/shahvikask/kosha/actions/runs/38958783184) **SUCCESS** for corrected SHA; Node CI does not substitute for native testing.
- **Status:** M24 scoped migrations now `CI JVM PASSED` on synthetic test-only SQLCipher, **NOT M24 ACCEPTED**. No production Android Keystore/native encrypted vault opener, actual captured original backup format or real-device acceptance. M00 stays owner-accepted. No M25 work. **No AAB/emulator** or frozen CASPARSER changes.

## 2026-10-16 — M24 frozen backup/unknown-field parity source checkpoint

Scanned frozen `src/lib/backup.ts`, `vault.ts`, `src/db/migrate.ts`, `repository.ts`, `schema.ts`; mapped v2 `GUDVLT01`/legacy `KOSHA1` wire formats, backup identity and localStorage pre-SQLite backup semantics. New **5 test-only backup envelope** golden methods plus **2 migration** unknown physical field/fail-closed guard methods **AUTHORED, NOT JVM RUN**; hardened test-only migration preflight against missing/inert append-only triggers and missing account ownership FK. Static source checks + local Python AES-GCM independent smoke PASS; full Kotlin/JUnit current-source compile NOT RUN. Prior 13/13 native JVM PASS remains **older SHA `646e76573e533df0a9db1d41faed3cc84770fbd3` only**. No paid GitHub Actions authorized or dispatched. **M24 STARTED / NOT ACCEPTED**. See [checkpoint](../native-migration/24-M24-FROZEN-BACKUP-ENVELOPE-CHECKPOINT.md).

## 2026-10-16 — superseding targeted M24 20-test JVM evidence

Owner-authorized targeted native [run 38961697701](https://github.com/shahvikask/kosha/actions/runs/38961697701) **SUCCESS** at exact source SHA `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13`. Test-scoped Kotlin code compiled. `:core:database:test` **BUILD SUCCESSFUL**; in-job XML gate confirmed **20/20** required methods (prior 13 + seven new), zero failures/errors/skips. JUnit [artifact 12072663218](https://github.com/shahvikask/kosha/actions/runs/38961697701/artifacts/12072663218), 14135 bytes, SHA-256 `bf291c9f4b50f4f43a922476a7399f253dd8883176143ab3929a5a0c010a2566`. No source corrections needed beyond workflow's required XML method list. Historical two-assertion failure and preceding 13-test success remain recorded. **M24 STILL STARTED / NOT ACCEPTED**: tests use synthetic test-only envelopes/SQLCipher. Captured original backup roundtrips, production Android SQLCipher + Keystore, restore atomicity, device/security acceptance are outstanding. No M25 or AAB/emulator activity.

## 2026-10-16 — M24 bounded production-key foundation SOURCE ONLY

New [frozen exporter vector script](../../scripts/m24-export-frozen-backup-vectors.ts) imports existing `packBackup` and `encryptJson` rather than cloning their wire logic; when run under `tsx`, it emits **synthetic** GUDVLT01 and KOSHA1 test vectors, exact original JSON (including owner/opaque fields and >2^53 amount strings), hashes, and an explicitly synthetic known password. **Script NOT EXECUTED** due to no repository checkout/network in current local runtime; exporter-generated fixture bytes NOT YET CAPTURED or independently Kotlin-decoded. Old hand-authored golden tests are distinct.

New [KeystoreWrappedVaultKey.kt](../../android-native/app/src/main/kotlin/in/growurdhan/redevelopment/KeystoreWrappedVaultKey.kt) creates a non-exportable Android Keystore AES-256-GCM wrapping key and atomically persists a wrapped random 256-bit database key under `noBackupFilesDir`. Existing envelope opens fail-closed on missing/invalid key, invalid length or authentication tag; fresh creation rejects caller-identified legacy vault, and AtomicFile backup metadata is recovered rather than overwritten. Source-only: no Android compile, device or JVM execution for this new class, no SQLCipher Android dependency, SQLCipher database opener, migrations, auth integration or restore transaction. Must not claim encrypted database persistence is implemented yet. **M24 NOT ACCEPTED**. No CI credits spent, no emulator/AAB/M25, untouched original and parser.

## 2026-10-16 — M24 native encrypted persistence SOURCE COMMITTED / NO NEW CI

**Supersedes older production-code-has-no-SQLCipher statement above:** a production-source Android SQLCipher opener now exists in `android-native/app`, but it has **NOT been compiled, executed, integrated into the UI, or accepted**. Pinned `net.zetetic:sqlcipher-android:4.17.0`, `androidx.sqlite:sqlite:2.6.2`; Keystore AES-GCM wrapped random DB key, AtomicFile metadata; non-deleting corruption handler, fail-closed missing-key/db rules; native SQLCipher create/open and staged single-transaction synthetic versioned SQL migration with required table, ledger/FK/money-affinity checks. Frozen v9 asset byte-identical to JVM fixture (18 tables, 3 triggers); frozen migration SQL assets generated by version. `LockedRestoreStaging` only atomically quarantines encrypted original bytes and offers read-only inspection; **does not restore portfolio rows**, approve identity/PAN/DOB or implement replay safety. `LegacyBackupEnvelopeReader` is independent JCE decoder; `scripts/m24-export-frozen-backup-vectors.ts` now hash-pins both frozen exporter sources but **was not executed**. Four new JUnit tests authored (expected 24 total), five Android instrumentation tests authored, **ALL NEW TESTS NOT RUN**. Old JVM 20/20 run [38961697701](https://github.com/shahvikask/kosha/actions/runs/38961697701) at `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13` remains historical only. Local independent Node WebCrypto → standalone Kotlin/JCE *format* smoke passed, not direct exporter or repository Kotlin compilation. No Actions/emulator/AAB/M25. M24 STARTED, **NOT ACCEPTED**. Detailed [checkpoint](../native-migration/24-M24-NATIVE-PERSISTENCE-SOURCE-CHECKPOINT.md).

## 2026-10-16 — M24 local source/reader verification and fail-closed repair (NO NEW CI)

Starting live HEAD `edd0e25385a651e79f412d3a1eb98e94abd75e6d`; frozen Gmail reference unchanged. Repo checkout from local runtime BLOCKED (`git ls-remote` failed: cannot resolve github.com); **actual `scripts/m24-export-frozen-backup-vectors.ts` NOT EXECUTED**, original TypeScript exporter bytes not emitted, native Gradle/Android build NOT RUN (Android SDK/project checkout unavailable). Hash-pin bug in committed exporter script corrected: real Git blob input is `blob <byte count>\\0` with actual NUL, not escaped literal backslash+zero. Locally reproduced correct hash-prefix algorithm against an exact-byte repo Kotlin source.

**Exact Git-blob local Kotlin evidence:** `android-native/core/database/src/main/kotlin/in/growurdhan/database/LegacyBackupEnvelopeReader.kt` reconstructed byte-for-byte from GitHub connector and verified local `git hash-object` before and after repair; updated blob `12f1f8776977df43549b667d89a2bfa4767cefc7`. Compiled with local `kotlinc` and ran JVM harness against locally generated Node WebCrypto *format* vectors: **PASS** modern binary and KOSHA1 password-only decryption; exact opaque owners/JSON + `9007199254740993`/`1234567890123` retained; four wrong-identity/wrong-password/tamper negatives rejected. **This is not an original exporter execution or full repository Gradle/JUnit pass.** Added bounded KOSHA1 Base64 preflight and in-method JUnit assertion (24 methods total remain authored/not run).

**Android source repairs NOT COMPILED:** closed missing `EncryptedVaultStore` class brace (compile blocker); fixed pre-migration checks to require exactly version-applicable foundation tables (V1 baseline 15, added listed_security V5, vault_account V6, gmail_seen V8), and require ALL original v9 fields after migration while permitting unknown valid columns. `LockedRestoreStaging` now bounds actual AtomicFile recovered stream (API26-compatible), checks all owner-bearing collections, rejects duplicate/missing member IDs and incompatible modern arrays; added a sixth Android instrumentation test for plaintext/duplicate/tampered staging. **No authenticated approval, persistent replay receipt, or atomic row-level portfolio restore exists**; no financial data are applied. Last real [M24 CI 38961697701](https://github.com/shahvikask/kosha/actions/runs/38961697701) remains **OLD-SHA 20/20** at `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13`. No Actions triggered, AAB/emulator/M25 untouched. **M24 OPEN — NOT ACCEPTED**.
