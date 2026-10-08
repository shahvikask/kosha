# GrowUrDhan — Native Kotlin Android

**Branch:** `GUD-Android-ChatGPT`  
**Status:** M1/M2 and M3 financial domain, SQLCipher ledger-v2, and reviewed statement approval **CI VERIFIED**: 52/52 JVM and 21/21 Android tests on commit `863ac5d`. Full finance parity, native consent UI and Google Play release remain OPEN.  
**Application ID:** `in.growurdhan.app` (pre-release development only). No Google Play version has been published.

## Modules and active implementation

- `app`: Kotlin, Compose Material 3, Hilt, accessible adaptive navigation, vault and appearance ViewModels. No completed public-facing financial UI/auth gate yet.
- `core:model`: scaled money and quantity types, family/holding/ledger identities, SQLite-compatible data contracts.
- `domain`: pure Kotlin use cases; M3 adds FIFO tax lots, corrections, split/bonus events, snapshot reconciliation planning, member net worth, date-safe tax rules and XIRR with TS-based golden fixtures.
- `data`: Keystore-wrapped key, SQLCipher Android native vault, transactional statement records, authenticated encrypted backup, DataStore appearance settings.
- `scripts/validate_schema.py`: host-side SQLite DDL and append-only ledger preflight.

## Build and test

Requires JDK 17, Android SDK API 36 and Gradle 8.13. CI installs pinned Gradle using `gradle/actions/setup-gradle` and runs both native instrumentation suites on an Android API 35 emulator.

From repository root:

```sh
gradle -p android-native :domain:test :app:testDebugUnitTest
python3 android-native/scripts/validate_schema.py
gradle -p android-native :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest
# Requires connected Android emulator/device:
gradle -p android-native :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

**Latest verified full native M3 baseline:** [run #37856520668](https://github.com/shahvikask/kosha/actions/runs/37856520668), implementation commit `863ac5d`. 52/52 JVM tests, 18/18 data-module Android tests (including six statement-approval tests), and 3/3 Compose instrumentation tests all passed. The v1-to-v2 SQLCipher migration/backup path remains covered by passing regression tests.

## Financial-domain boundary (M3)

`domain/finance/` remains a pure Kotlin financial engine. Native **schema v2** adds `financial_kind`, optional unit price, charges and correction-lineage columns without deleting/recreating v1 ledger rows. `VaultRepository.appendFinancialEvents` writes approved rich events in a single transaction; `VaultRepository.readBook` captures members/holdings/events under one SQL read transaction and powers the portfolio and statement-preview use cases. Portable encrypted backup format 2 retains full event metadata and accepts format-1 development exports.

**Verified:** schema-v2 migration, backup compatibility and encrypted ledger cases passed on [native M3 run #37807320946](https://github.com/shahvikask/kosha/actions/runs/37807320946). Full native financial UI, remaining TS engine parity, app authentication and CASPARSER bridge are still separate work. See [M3 implementation](../docs/native-migration/08-M3-FINANCIAL-DOMAIN-IMPLEMENTATION.md) and [M3 persistence design](../docs/native-migration/09-M3-LEDGER-PERSISTENCE-AND-ATOMIC-READS.md).

## Non-negotiable boundaries

- CASPARSER under `src/casparser-main/**` and existing Python invocation/dependency scripts are immutable. No edits or parser logic refactors.
- The React/Tauri reference application and developer vaults remain intact; no destructive migration or legacy cleanup before validated native parity.
- The Keystore key protects local storage at rest; it is **not** the user's vault login password. Authentication/session locking and device-loss recovery UI remain release blockers.
- The current APK is **not** for public Google Play distribution.

See the source-linked [M3 verification report](../docs/native-migration/10-M3-VERIFICATION-RESULTS.md) for exact JUnit counts, API-35 Android test coverage, archive digests and incomplete commercial release gates.

## M3 financial parity and atomic reviewed statements (CI verified)

The subsequent iteration adds `VaultRepository.prepareStatementReview` and `commitReviewedStatement`. Review is read-only and receives a source-and-book-bound, per-process HMAC receipt; explicit approval recomputes the planner and writes the imported statement observation plus proposed events in **one fail-closed transaction**. It also keeps missing historical cost **unknown** rather than treating today's CAS quote as purchase cost, and rejects ambiguous multi-lot cost rebasing.

Pure domain ports for loan EMI dates, small-savings simple accruals/coupons and hybrid exposure splits have **19 new JVM golden cases**, plus **6 new Android instrumented approval cases** and 2 use-case tests. This increment **passed** [Native Android M3 run #37856520668](https://github.com/shahvikask/kosha/actions/runs/37856520668), with all 73 JVM and Android tests passing. See [M3 atomic reconciliation and parity](../docs/native-migration/11-M3-ATOMIC-STATEMENT-AND-FINANCE-PARITY.md) and [verification evidence](../docs/native-migration/12-M3-ATOMIC-APPROVAL-VERIFICATION.md).

These are not production-ready user flows: the review receipt does not authenticate a user or validate that caller-supplied statement rows were generated by CASPARSER. No automatic EMI/interest posting, FD/RD compounding parity or statement consent UI is yet provided.
