# GrowUrDhan — M3 Native Financial Domain and SQLCipher v2 Verification Report

**Report date:** 2026-10-08  
**Source branch:** `GUD-Android-ChatGPT` only  
**As-tested implementation commit:** `e25fec7080b197fca893a0dd578c325e8cac490d`  
**Native Android workflow:** [Run #37807320946](https://github.com/shahvikask/kosha/actions/runs/37807320946), [job #113414881745](https://github.com/shahvikask/kosha/actions/runs/37807320946/job/113414881745)  
**Reference/hybrid workflow:** [Kosha CI #37807321066](https://github.com/shahvikask/kosha/actions/runs/37807321066)  
**Decision:** **PASS — M3 calculation, persisted ledger v2, v1 migration, backup and atomic-read increment within the CI test scope**. **NOT YET full native finance parity, security certification, or Play production readiness**.

## 1. Independent verification method

The repository's actual GitHub workflow run metadata was queried and compared with the branch head. GitHub reported `completed / success`, attempt `1`, head SHA `e25fec7080b197fca893a0dd578c325e8cac490d`, branch `GUD-Android-ChatGPT`. All named native job steps concluded successfully. The native run started at `2026-10-08T16:15:10Z` and finished at approximately `16:30:31Z`.

The archived `GrowUrDhan-Native-M3-JVM-Reports` and `GrowUrDhan-Native-M3-Instrumentation-Reports` ZIP files were downloaded and verified intact. All test suite counts below were taken from the JUnit XML test-result files **inside** these GitHub artifacts. The compiled APK ZIP was also downloaded; its integrity check passed and its files were inspected.

The `Kosha CI` run on the **same exact commit SHA** also returned `completed / success`. A branch diff against `GUD-Android-Gmail` showed **no changed files** under the frozen `src/casparser-main/**` and existing CASPARSER invocation/dependency scripts. The workflow's separate CASPARSER immutability guard also passed.

## 2. Test results — 46/46 passing, none skipped

### JVM / pure Kotlin

| JUnit test suite | Cases | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `FinancialGoldenTest` — TypeScript accounting parity oracles | 17 | 0 | 0 | 0 |
| `AtomicBookUseCaseTest` — single-snapshot read, preview isolation, correction lineage | 4 | 0 | 0 | 0 |
| `FinanceTest` — core finance model/domain | 4 | 0 | 0 | 0 |
| `AppearanceUseCasesTest` | 3 | 0 | 0 | 0 |
| `AppViewModelTest` | 1 | 0 | 0 | 0 |
| `VaultViewModelTest` | 2 | 0 | 0 | 0 |
| **JVM total** | **31** | **0** | **0** | **0** |

Evidence: archived `domain/build/test-results/test/TEST-*.xml` and `app/build/test-results/testDebugUnitTest/TEST-*.xml`. The Gradle `:domain:test :app:testDebugUnitTest` job concluded `BUILD SUCCESSFUL`.

Notable financial scenarios include FIFO gains (₹12,000), ₹2,77,000 remaining cost, ₹3,24,500 valuation, exact scaled-integer arithmetic, date-safe FY/AY, debt versus equity mutual funds, SGB treatment, grandfathering, snapshot tolerance/deltas, corporate actions, member ownership, and XIRR. The four atomic-reader tests verify that the portfolio and preview use cases invoke `readBook()`, not three independent getters.

### Android emulator — instrumentation

| JUnit test suite | Cases | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `NativeVaultInstrumentedTest` — prior M2 vault protections | 7 | 0 | 0 | 0 |
| `NativeFinancialPersistenceInstrumentedTest` — new M3 encrypted ledger tests | 5 | 0 | 0 | 0 |
| `NativeShellTest` — Compose navigation/privacy/explicit vault creation | 3 | 0 | 0 | 0 |
| **Android total** | **15** | **0** | **0** | **0** |
| **Combined JVM + Android** | **46** | **0** | **0** | **0** |

In the device XML, the two data-module classes are aggregated under one `12`-case suite element; the individual `testcase.classname` values distinguish the **7 older vault cases** from the **5 new persistence cases**. The app XML contains the three Compose cases.

**Five new M3 device cases (confirmed in XML):**

1. `encryptedV1DatabaseUpgradesWithoutLosingEvents` — encrypted v1 SQLCipher/Keystore vault upgraded to v2, retaining historic member, holding and event data through reopen.
2. `financialKindsFeesAndCorrectionsSurviveCloseAndReopen` — BUY, BONUS, SPLIT, SNAPSHOT_ADJUSTMENT and CORRECTION, transaction fees and supersession survive read/close/reopen.
3. `batchedLedgerInsertRollsBackOnMissingOrCrossOwnerSupersession` — a failed batch rolls back its earlier inserts and cross-owner corrections fail.
4. `versionTwoBackupRoundTripRestoresExactRichLedger` — encrypted portable v2 backup restores all rich metadata and valid foreign keys.
5. `versionOnePortableBackupImportsToVersionTwoDefaults` — authenticated v1-format developer backup restores to v2, safely defaulting unknown optional fields.

**Seven retained vault device cases:** encrypted-on-disk header and Keystore binding, missing key protection, missing database protection, historical statement chronology, cross-member import rollback and SHA idempotency, direct SQL ledger UPDATE/DELETE protection, password-encrypted backup authentication/rollback.

**Three Compose device cases:** `settingsNavigationAndThemeIntent`, `privacyDestinationShowsActualStorageStatus`, and `vaultIsCreatedOnlyAfterExplicitUserAction`.

**Test environment:** GitHub-hosted Ubuntu 24.04 runner; JDK 17, Gradle 8.13, compile SDK 36; Android 15/API 35, Google APIs x86_64 emulator. These results do not cover other API levels or physical devices.

## 3. Migration and financial integrity preflight

The Python host-side `android-native/scripts/validate_schema.py` job used the SQL extracted from **production** `VaultSchema.kt`, rather than a separate SQL copy, and logged:

`PASS: 14 v1 DDL + 6 v2 migrations; fresh and upgraded vaults, rollback, immutable ledger, rich event kinds, correction ownership and backup-compatible columns.`

This includes:
- fresh v2 schema and in-place migration from valid v1;
- interrupted migration rollback (no partially added columns);
- preservation of v1 event values and event immutability;
- ownership constraints, valid correction ancestors and non-duplicate supersession;
- explicit rich financial event kind versus constrained legacy carrier;
- foreign-key and SQLite integrity checks.

The real **encrypted SQLCipher migration** and backup behavior were separately exercised by the five Android cases above. The host SQLite check alone is not treated as proof of SQLCipher correctness.

## 4. APK and report artifacts

All three GitHub artifact records are associated with **run #37807320946** and were present and unexpired when inspected. Digests are GitHub's SHA-256 digest of each **artifact ZIP**, not the hash of an individual APK nor a Play release signature.

| GitHub artifact | ID | Archive size | SHA-256 digest |
| --- | ---: | ---: | --- |
| [M3 JVM reports](https://github.com/shahvikask/kosha/actions/runs/37807320946/artifacts/11563756635) | `11563756635` | 26,767 bytes | `354a4d7c6226a277b7470c77d36f4c7c65b6dd49a957dc96920b447776313c7b` |
| [M3 debug APKs](https://github.com/shahvikask/kosha/actions/runs/37807320946/artifacts/11564305478) | `11564305478` | 31,437,051 bytes | `2667ec4aeb53951a7110d2ee1af496979f355b37ac357ee5f7fd1204c0f6007b` |
| [M3 Android test reports](https://github.com/shahvikask/kosha/actions/runs/37807320946/artifacts/11564825826) | `11564825826` | 70,465 bytes | `4d429c08df80e022d8facb4a68430dc867da191e7e4b35b7841b465fe4489e19` |

**APK archive internal files (ZIP integrity confirmed):**

- `app/build/outputs/apk/debug/app-debug.apk` — 26,675,679 bytes.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 1,000,560 bytes.
- `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` — 11,930,827 bytes.

These are **debug/development artifacts**, not a release-signed Play AAB. Artifact availability is subject to GitHub retention.

## 5. As-built financial and privacy boundaries verified

- One private SQLCipher database with 256-bit key wrapped by Android Keystore. No plaintext family database server or remote financial-data synchronization.
- Production v2 schema adds `financial_kind`, nullable `unit_price_scaled`, `charges_scaled` and nullable `supersedes_event_id` without rewriting the append-only v1 ledger.
- `appendFinancialEvents` writes a validated event batch inside one SQLite write transaction, with financial-kind/owner/supersession checks.
- `VaultRepository.readBook()` reads members, holdings and events under one SQL read transaction; both family portfolio and statement reconciliation preview use it.
- Portable encrypted backup **logical format 2** preserves the richer ledger. The v1 logical format remains restorable into an **empty v2 vault** without guessing missing metadata.
- Snapshot imports are observations, not purchases. The reconciliation planner remains a preview, does not automatically mutate the book and does not auto-close absent statement holdings.
- CASPARSER and legacy React/Tauri data paths remain unchanged.

These items are verified only to the extent of the listed source implementation and passing tests; they are **not a penetration test, cryptographic certification or formal financial-model equivalence proof**.

## 6. Gaps: do not approve public release or delete legacy code

1. **Full financial parity is still incomplete.** Continue equivalent Kotlin algorithms/golden vectors for `interest.ts`, `emi.ts`, `growth.ts`, `lookthrough.ts`, `returnMetrics.ts`, `costBasis.ts`, fixed-income/liability scenarios and all product-specific rules.
2. **Statement acceptance transaction remains open.** The app needs user-reviewed, TOCTOU-safe reconciliation plus atomic commit from immutable statement evidence; the current preview is not an import completion workflow.
3. **App user authentication is incomplete.** Android Keystore protection at rest is not a login/session gate. Implement and validate password/biometric lock, timeout and sensitive UI protections before commercial use.
4. **Native journeys are incomplete.** Portfolio, family/member/holding edits, CASPARSER Kotlin bridge, backup picker/recovery, market data, Gmail integration and Play Billing remain work.
5. **Broader device/release evidence is missing.** Test API 26–36, physical devices, backup recovery across hardware, ABI and 16 KiB page compatibility, signed release AAB, R8 configuration, security scans, Play policy review and independent AppSec testing.
6. **Reference application remains the parity oracle.** No React/Tauri deletion, no forced data loss and no modifications to frozen Python CASPARSER files before explicit cleanup gates.
7. **Tax computations require current statutory validation** prior to filing or advising users; the frozen test inputs are programming parity fixtures, not authoritative tax advice.

## 7. Milestone disposition

**M3 financial-domain foundation and native ledger v2 persistence: VERIFIED PASS** on commit `e25fec7080b197fca893a0dd578c325e8cac490d` under the stated CI environment.

**M3 full product/financial parity: OPEN**, pending the excluded features above. **GrowUrDhan commercial readiness: NOT APPROVED**. Future work must remain on `GUD-Android-ChatGPT`, with CASPARSER frozen, and subsequent long-running GitHub Actions results handed back for owner YES/NO confirmation.
