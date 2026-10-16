# GrowUrDhan implementation status

Updated: 2026-10-16. M24 is started and not accepted. A test opens SQLCipher. Production code does not.

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
