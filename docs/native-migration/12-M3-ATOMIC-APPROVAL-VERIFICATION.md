# GrowUrDhan — M3 Reviewed Statements and Financial Parity: Verified CI Report

**Date:** 2026-10-08  
**Repository:** `shahvikask/kosha`  
**Source branch:** `GUD-Android-ChatGPT` (no other branch changed)  
**As-tested implementation commit:** `863ac5d21649bcee00523be15c0a3de35f6cd1e6`  
**Native Android M3 workflow:** [run #37856520668](https://github.com/shahvikask/kosha/actions/runs/37856520668), [native-build job #113581900226](https://github.com/shahvikask/kosha/actions/runs/37856520668/job/113581900226)  
**Original/reference Kosha CI:** [run #37856520670](https://github.com/shahvikask/kosha/actions/runs/37856520670)  
**Verification decision:** **PASS — M3 backend atomic statement approval, selected finance-parity engines and preserved SQLCipher/Compose regressions in CI scope.** This is **not** a full-feature migration, independent penetration test, statutory validation, signed Play-release approval or user-authentication certification.

## 1. Independent evidence and environment

The GitHub Actions API confirms that Native Android M3 run `37856520668` completed with `conclusion=success`, attempt `1`, from `GUD-Android-ChatGPT` at the exact as-tested SHA above. The native job's required steps, including Kotlin JVM tests, SQL migration preflight, immutable CASPARSER guard, debug/test APK compilation, Android API-35 emulator tests and all artifact uploads, concluded successfully. Run timestamps: started `2026-10-08T22:56:05Z`, completed by `2026-10-08T23:08:38Z`.

All **three original workflow artifact ZIPs** were downloaded. Integrity verification via ZIP CRC succeeded for each. SHA-256 of the downloaded archives independently matched the artifact digests exposed by GitHub. The result counts below were obtained directly by parsing the archived JUnit `TEST-*.xml` files, **not inferred from the green workflow badge**.

The separate `Kosha CI` run `37856520670` returned `completed / success` at the same exact SHA. Two independent GitHub branch comparisons—since the previous verified M3 baseline and against `GUD-Android-Gmail`—showed **no changes** to `src/casparser-main/**`, `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py`, or `scripts/requirements-cas.txt`. The dedicated native workflow `git diff --exit-code` CASPARSER guard passed as well.

**CI environment:** GitHub-hosted Ubuntu runner; Java 17, Gradle 8.13, Android SDK compile API 36, Android 15/API 35 Google APIs x86_64 emulator. Physical-device and multi-API results are **not** claimed.

## 2. JVM tests: 52 passing, no failures/errors/skips

| Archived JUnit suite | Tests | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `in.growurdhan.domain.finance.FinancialGoldenTest` | 17 | 0 | 0 | 0 |
| `in.growurdhan.domain.finance.FinanceParityExtensionsTest` | **19** | 0 | 0 | 0 |
| `in.growurdhan.domain.finance.AtomicBookUseCaseTest` | **6** | 0 | 0 | 0 |
| `in.growurdhan.domain.vault.FinanceTest` | 4 | 0 | 0 | 0 |
| `in.growurdhan.domain.preferences.AppearanceUseCasesTest` | 3 | 0 | 0 | 0 |
| `in.growurdhan.app.presentation.AppViewModelTest` | 1 | 0 | 0 | 0 |
| `in.growurdhan.app.presentation.VaultViewModelTest` | 2 | 0 | 0 | 0 |
| **Total** | **52** | **0** | **0** | **0** |

The new finance parity tests exercise clamped EMI dates (including leap-year and catch-up), stopped/paused plans, post-date idempotency, published reference rate defaults, exact scaled-integer accrual/coupon arithmetic and overflow, small-savings classification, hybrid estimated versus explicitly disclosed split precedence, exact remainder apportioning without duplicated asset value, and statement cost-basis safety.

Two additional atomic-book JVM tests ensure that preparing a statement review does **not** commit events, and that an explicitly approved receipt reaches the repository exactly once. These JVM tests verify use-case behavior using mocks; the transaction and encryption path is exercised separately on the emulator.

## 3. Android instrumentation: 21 passing, no failures/errors/skips

| Android JUnit class, resolved from each `testcase.classname` | Cases | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `NativeVaultInstrumentedTest` — M2 encrypted vault guards | 7 | 0 | 0 | 0 |
| `NativeFinancialPersistenceInstrumentedTest` — M3 ledger v2/migration/backups | 5 | 0 | 0 | 0 |
| **`NativeStatementApprovalInstrumentedTest` — new approval/rollback tests** | **6** | **0** | **0** | **0** |
| `NativeShellTest` — Compose shell/accessibility/explicit vault action | 3 | 0 | 0 | 0 |
| **Total** | **21** | **0** | **0** | **0** |

The Android data module exposes one aggregate JUnit XML suite with `tests=18`, while its individual test cases identify the **7 + 5 + 6** classes above. The app module's XML contains the three Compose cases.

### All six new statement-approval device cases passed

1. `reviewDoesNotWriteAndApprovalImportsOpeningOnce` — review remains read-only; approved source observation plus ledger opening balance persists; repeat approval reports `ALREADY_IMPORTED` without a duplicate; book survives reopen.
2. `financialChangeAfterReviewRejectsStaleApprovalWithNoExtraEvents` — an intervening BUY invalidates the receipt and prevents an old plan from inserting rows; a new review can be prepared.
3. `modifiedReceiptPlanAndSourceAreRejectedByHmac` — edits to proposed review warnings or statement rows fail receipt validation; unmodified receipt remains usable.
4. `conflictedOwnerCannotCommitObservationOrCreateLedgerEvents` — ownership conflict causes atomic approval to fail without changes to the ledger.
5. `zeroQuantitySnapshotIsObservationNotFalseBuy` — an empty unit observation is saved without inventing a zero-quantity trade.
6. `subsequentStatementOnlyCommitsDeltaAndCannotRewind` — later custodian snapshots insert **only the balance delta**; a chronologically older statement is rejected.

The retained 12 vault/persistence Android cases separately covered encrypted SQLCipher file storage, Android Keystore key recovery/failure, non-destructive missing-data behavior, SQL ledger immutability, family ownership checks, v1→v2 upgrade of a real encrypted DB, rich event persistence/reopen, multi-row rollback, password-protected v2 backup roundtrip, and legacy v1-format backup restoration. The three Compose tests still pass.

## 4. SQLite preflight, compilation, and immutable reference code

The production-DDL-derived preflight logged:

`PASS: 14 v1 DDL + 6 v2 migrations; fresh and upgraded vaults, rollback, immutable ledger, rich event kinds, correction ownership and backup-compatible columns.`

It validates a fresh v2 database, non-destructive upgrading of a v1 financial ledger, interrupted migration rollback, correction-owner constraints, append-only UPDATE/DELETE triggers, schema integrity, and old backup-compatible columns. This host SQLite preflight is **not** a SQLCipher security test; the real encrypted-vault checks ran on the emulator.

The Gradle JVM test step logged `BUILD SUCCESSFUL`; the separate APK compile step logged `BUILD SUCCESSFUL`; emulator boot succeeded, followed by **18/18 data tests** and **3/3 Compose tests**, and Android Gradle reported `BUILD SUCCESSFUL`. Protected CASPARSER source and packaging inputs are unmodified.

## 5. Downloaded and independently hash-verified artifacts

GitHub listed all artifacts under the successful native run, unexpired at inspection time. The artifact ZIP contents were examined; sizes below are GitHub's **ZIP archive** sizes, and hashes are SHA-256 **of each ZIP**, not a signing certificate or release APK hash.

| GitHub artifact | Artifact ID | ZIP size (bytes) | GitHub SHA-256 and downloaded ZIP SHA-256 (matched) |
| --- | ---: | ---: | --- |
| [GrowUrDhan-Native-M3-JVM-Reports](https://github.com/shahvikask/kosha/actions/runs/37856520668/artifacts/11584117743) | `11584117743` | `30,735` | `9de547ee5b22706c0a4e98ce15a6a1dd8d4e7242b2105ba575e2e3f7b6d5fe82` |
| [GrowUrDhan-Native-M3-Instrumentation-Reports](https://github.com/shahvikask/kosha/actions/runs/37856520668/artifacts/11584388565) | `11584388565` | `354,154` | `44ce838b9e8140ade46c7b89e230623d718800f6a3f92311fa8c7417a452d304` |
| [GrowUrDhan-Native-M3-APKs](https://github.com/shahvikask/kosha/actions/runs/37856520668/artifacts/11584577125) | `11584577125` | `31,491,624` | `2875e9f69cd1aa148c6c79d33c69a11e2a3d48202157118c7dda6d0a2d0c54dd` |

**Internal APK archive files, separately enumerated:**

- `app/build/outputs/apk/debug/app-debug.apk` — `26,692,063` bytes.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — `1,000,560` bytes.
- `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` — `11,963,595` bytes.

These are **development/debug APKs** plus instrumentation APKs, not a production signed AAB, tested store bundle, or evidence of ABI/16-KiB-page compliance. GitHub artifact links may expire under repository retention settings; IDs and digests are recorded for traceability.

## 6. As-built behavior and security scope

The verified M3 backend uses a **read-only review followed by a separate explicit commit**. The review includes normalized statement inputs, full proposal, book fingerprint and per-process HMAC; commit verifies the receipt, rejects a changed book, replays reconciliation, checks owner/chronology and writes `snapshot_import`, `statement_position` and append-only ledger events **inside one SQLite transaction**. The six new emulator tests cover specific successful, conflicting, modified, stale, empty-unit and delta-only cases.

The financial engines now provide pure Kotlin calculations for EMI catch-up schedules, simple fixed-savings interest and coupons, and exact apportioned hybrid-asset exposure, adding to previously verified FIFO, taxes, XIRR and portfolio totals. They do not silently post recurring financial transactions.

**Assurance limits:** A passing HMAC receipt test does not prove a malicious on-device process cannot bypass all application boundaries. The HMAC is per-process and is **not** a user's password, biometric authentication, or cryptographic proof that normalized statement lines came from a PDF; the source digest is caller-supplied at the current API boundary. We have **not** performed an independent adversarial security audit or penetration test in this verification.

A missing historical purchase price is deliberately recorded as **unknown** rather than replaced with today's market quotation; it must not silently produce trusted taxable gains. Ambiguous multi-lot cost corrections fail closed until individual acquisition evidence exists. These are domain safeguards, **not** guarantees that every possible tax-lot and product scenario has been checked.

## 7. Open feature and commercialization blockers

1. **End-user authentication and explicit consent UI:** Complete native unlock/session/biometric boundary and show every proposed statement change before an authenticated user action invokes approval.
2. **Statement origin and parser contract:** Integrate the immutable Python CASPARSER engine through a validated Kotlin adapter; verify that statement rows and their source SHA match actual input bytes, accounts, and owners. Frozen CASPARSER files must not change.
3. **Remaining legacy financial parity:** Finish detailed FD/RD/PPF/SSY accrual/catch-up, `growth.ts`, `returnMetrics.ts`, `costBasis.ts`, wider corporate actions, taxes, liabilities and full UI golden parity.
4. **Broader test and security scope:** Physical devices, Android API 26–36, target ABIs and 16-KiB native memory page support, restoration to replacement phones, OWASP MASVS, dependency scanning, abuse/race/performance checks and independent pentest.
5. **Commercial release engineering:** Billing/licensing, Gmail authorization if included, price-download privacy safeguards, release-signed AAB, Android/Play policy and privacy declarations. No commercial release has been approved.
6. **Legacy cleanup gate:** React/Tauri/Rust financial algorithms and immutable CASPARSER reference implementation remain intact until full parity and explicit cleanup approval.

## 8. Milestone disposition

**M3 verified scope: PASS — 52/52 JVM + 21/21 Android = 73/73 tests, zero failures/errors/skips**, with preflight, APK artifacts, CASPARSER guard and reference Kosha CI all successful on **implementation commit `863ac5d21649bcee00523be15c0a3de35f6cd1e6`**.

**M3 complete financial-feature parity: OPEN. Google Play production approval: NOT READY.** The report's documentation-only commit is later than the tested code commit; it must not be misrepresented as a separately tested implementation change. Subsequent code changes require their own workflow and artifact verification.
