# GrowUrDhan Native Android — M1

**Status:** Development-only native foundation. **Do not publish or install as an upgrade over an existing vault.** The native build shares the future package identity `in.growurdhan.app` but it has **no finance database or parser integration yet**.

## Implementation

- `app`: Compose / Material 3, accessible adaptive navigation, Hilt ViewModel, UDF intents.
- `core:model`: pure Kotlin immutable appearance data.
- `domain`: repository interface and use cases.
- `data`: Hilt-bound DataStore implementation, storing **only appearance preferences**.
- UI supports device/light/dark themes, Android 12+ dynamic colors, small/wide layouts and accessible navigation.

## Build and test

Requires JDK 17, Android SDK API 36 and Gradle 8.13. GitHub Actions installs the **pinned Gradle version** via `gradle/actions/setup-gradle` so no checked-in Gradle wrapper binary is required for CI at this milestone.

From the repository root:

```sh
gradle -p android-native :domain:test :app:testDebugUnitTest
gradle -p android-native :app:assembleDebug :app:assembleDebugAndroidTest
```

With a running Android emulator/device:

```sh
gradle -p android-native :app:connectedDebugAndroidTest
```

All sensitive data is **absent** from this initial scaffold, rather than mocked. No customer or synthetic balances are invented.

## Non-negotiable boundaries

- CASPARSER under `src/casparser-main/**` and its existing Python invocation scripts are immutable.
- The old React/Tauri app remains intact. No native code reads or writes `vault.enc` yet.
- Native encryption and data migration design is tracked in `docs/native-migration/**`.
- The prior Gradle overlay and native app are separate build targets. No production release workflow points at `android-native/` yet.
- Version code `3000` and `0.3.0-dev` are **development candidates**, not verified against a Play release. Confirm release monotonicity before publishing.
- No legacy code removal until migration and feature parity pass the documented gates.

## Next milestone: M2

Design/select the encrypted SQLite provider, establish a native schema and safe import format, build failure-atomic vault writes, and prove recovery under simulated process death. Since no Play version has been published, unsupported customer migrations need not block a deliberate fresh schema; however, do not silently erase a test user’s existing data.
