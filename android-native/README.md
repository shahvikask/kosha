# GrowUrDhan — Native Kotlin Android

**Branch:** `GUD-Android-ChatGPT`  
**Status:** M1 Compose foundation and M2 encrypted vault passed GitHub Android CI. **M3 financial-domain implementation is being verified; not ready for Play release.**  
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

**Last confirmed full M2 pass:** [run #37789886674](https://github.com/shahvikask/kosha/actions/runs/37789886674), commit `efcaeaa`, including 7/7 vault and 3/3 Compose instrumented tests. **Do not treat M3 domain tests as passing until its own newer GitHub run succeeds.**

## Financial-domain boundary (M3)

`domain/finance/` is pure and **does not modify stored finances or call CASPARSER**. The richer `FinancialEvent` type mirrors TS transaction metadata; existing M2 `ledger_event` storage lacks some fields (trade unit price, fees, supersession). `CalculateFamilyPortfolio` has a conservative read adapter for legacy M2 supported kinds, refusing unsupported correction lineage rather than inventing cost history.

Until the native ledger schema and backup codec preserve the richer event fields, complete persisted FIFO/corporate-action parity is an **open gate**. The existing `VaultRepository` also exposes separate reads rather than a single atomic financial book snapshot. See [M3 implementation report](../docs/native-migration/08-M3-FINANCIAL-DOMAIN-IMPLEMENTATION.md).

## Non-negotiable boundaries

- CASPARSER under `src/casparser-main/**` and existing Python invocation/dependency scripts are immutable. No edits or parser logic refactors.
- The React/Tauri reference application and developer vaults remain intact; no destructive migration or legacy cleanup before validated native parity.
- The Keystore key protects local storage at rest; it is **not** the user's vault login password. Authentication/session locking and device-loss recovery UI remain release blockers.
- The current APK is **not** for public Google Play distribution.
