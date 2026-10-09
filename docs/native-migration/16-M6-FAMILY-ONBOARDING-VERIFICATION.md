# GrowUrDhan — M6 Family Onboarding Independent Verification Report

**Report date:** 2026-10-08 (owner local; workflows ran 2026-10-09 UTC)  
**Repository and sole authorized branch:** `shahvikask/kosha` / `GUD-Android-ChatGPT`  
**As-tested implementation commit:** `3a4dfa0790f548b6132b43c5d2cf4897ab8cd9e6`  
**Verification result:** **PASS for the defined M6 native CI scope.** 102 JVM + 35 Android API-35 tests passed; 35 more Android tests passed independently on **each** of APIs 26, 30 and 36. Zero failures, errors or skipped cases in the downloaded JUnit XML.  
**Release decision:** **NOT a Google Play production or independent security-audit approval.**

## 1. Reproducible GitHub Actions evidence

| Source workflow | Run / job | Status | Source commit |
| --- | --- | --- | --- |
| [GrowUrDhan Native Android M3](https://github.com/shahvikask/kosha/actions/runs/37888065112) (contains M6 suites) | Run `37888065112`, job `113682527128` | **success** | `3a4dfa0790f` |
| [Native API Device Matrix](https://github.com/shahvikask/kosha/actions/runs/37888065106) | Run `37888065106`; API 26 job `113682477672`, API 30 job `113682477903`, API 36 job `113682477785` | **success (all three jobs)** | `3a4dfa0790f` |
| [Kosha CI](https://github.com/shahvikask/kosha/actions/runs/37888065100) | Run `37888065100`, job `113682454892` | **success** | `3a4dfa0790f` |

All GitHub run API records have `completed` status, `conclusion=success`, and the **identical full source SHA** above. The native workflow passed its SQL schema preflight, source security preflight, immutable CASPARSER guard, JVM test step, debug APK/test APK compile, ephemeral signed release AAB smoke test, API-35 emulator suites and required artifact-upload steps.

**Independent method:** Downloaded all six workflow ZIP archives directly from the original GitHub runs; checked complete ZIP CRC integrity; independently recomputed SHA-256 for each ZIP and matched it to GitHub's artifact digest; parsed every archived `TEST-*.xml` JUnit report for actual `testcase` elements and failure, error and skipped nodes. Android Gradle's XML suite title can be an aggregate name, so class totals below are grouped by each **individual `testcase.classname`**, rather than treating the aggregate suite title as the only executed test class.

**Environment:** CI JVM on Ubuntu/JDK 17 and Gradle 8.13; Android API 35 emulator for main native run; separately provisioned Android API 26 (Android 8), API 30 (Android 11) and API 36 (Android 16) emulators for the device matrix. This report does **not** claim an M6 physical-device run.

## 2. M6 JVM tests: all 13 newly introduced cases passed

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `in.growurdhan.domain.vault.FamilyMemberOnboardingTest` | **6** | 0 | 0 | 0 |
| `in.growurdhan.app.presentation.FamilyMemberViewModelTest` | **7** | 0 | 0 | 0 |
| **M6 additions total** | **13** | **0** | **0** | **0** |

The six domain cases verify a first **Self** member becoming the only primary, whitespace normalization, rejection of an invalid first relationship, allowed secondary relationships, blank/control-character/oversized names, duplicate names and owner IDs, member-count limit and inconsistent primary-owner state. The seven app ViewModel cases cover locked read denial, read-only form entry, fresh credential epoch after staging, once-only save, validation rejection before authentication, cancelled/stale in-memory requests, and error handling that avoids leaking private SQL exception text.

An Android device-credential prompt is **not** exercised by JVM mocks. JVM results therefore demonstrate state-machine behavior, not an independently validated real biometric/user-authentication implementation.

## 3. Retained JVM golden and regression suites: all passed

| Archived JUnit class | Tests |
| --- | ---: |
| `FinancialGoldenTest` | 17 |
| `FinanceParityExtensionsTest` | 19 |
| `AtomicBookUseCaseTest` | 6 |
| `CasImportContractTest` | 12 |
| `SavingsProjectionParityTest` | 10 |
| `AppearanceUseCasesTest` | 3 |
| `FinanceTest` | 4 |
| `CasPdfBytesTest` | 5 |
| `AppViewModelTest` | 1 |
| `StatementReviewViewModelTest` | 4 |
| `VaultSessionGateTest` | 4 |
| `VaultViewModelTest` | 4 |
| **Retained JVM total** | **89** |
| **Retained (89) + new M6 (13)** | **102** |

All **102/102 JVM tests passed**, with **zero failures, errors or skips** in the archived XML. These retained tests cover financial golden and integer-scale calculations; EMI, interest, exposure and projection parity; atomic financial book behavior and statement reconciliation; immutable CAS source contract; statement PDF input bounds; presentation state; appearance and vault-session safety.

## 4. M6 Android additions: all eight device tests passed

| Actual JUnit testcase class | New cases | Result |
| --- | ---: | --- |
| `NativeFamilyOnboardingInstrumentedTest` | **4** | **4/4 PASS** |
| `FamilyOnboardingShellTest` | **4** | **4/4 PASS** |
| **M6 device total** | **8** | **8/8 PASS** |

**Four SQLCipher-backed encrypted-persistence tests** cover: first Self/primary plus dependent persisting across encrypted vault close/reopen; invalid/duplicate registration rollback with prior members preserved; invalid first relationship not creating a primary; and an inconsistent legacy vault missing a primary failing closed rather than rewriting historical records.

**Four Compose navigation/consent tests** cover: a Statements no-owner error offering a direct **Add family member** route; first-owner form requiring a name before staging (not writing) the request; successful save navigating back to owner selection; and the unlocked empty-vault shortcut **Add first family member**.

These tests use synthetic isolated data, not a person's holdings. They do not bypass authenticated production flow to insert a CAS holding, and they do not demonstrate end-to-end parsing of a user's PDF.

## 5. Retained Android SQLCipher, authorization and Compose tests

| JUnit testcase class | API-35 tests | Result |
| --- | ---: | --- |
| `NativeVaultInstrumentedTest` | 7 | PASS |
| `NativeFinancialPersistenceInstrumentedTest` | 5 | PASS |
| `NativeStatementApprovalInstrumentedTest` | 6 | PASS |
| `NativeAuthorizationInstrumentedTest` | 3 | PASS |
| `NativeShellTest` | 6 | PASS |
| **Retained Android total** | **27** | **27/27 PASS** |
| **Retained (27) + M6 new (8)** | **35** | **35/35 PASS** |

The retained suites exercise Keystore/SQLCipher encrypted-vault lifecycle, non-destructive error recovery, append-only ledger and SQL v1→v2 migration, encrypted backup compatibility and transaction rollback, repository authorization checks, six statement-review approval/rejection cases, and Compose vault/navigation/consent behavior.

**API-35 instrumentation by module:** `app` **10/10** (4 onboarding + 6 retained Compose) and `data` **25/25** (4 onboarding + 21 retained encrypted-vault/financial/authorization). The original JUnit files contain no failures/errors/skips.

## 6. Additional emulator API matrix: three separate 35/35 passes

| Emulator | App/Compose | Data/SQLCipher | Total | Failed / errors / skips |
| --- | ---: | ---: | ---: | ---: |
| Android API 26 (Android 8.0) | 10 | 25 | **35/35 PASS** | **0 / 0 / 0** |
| Android API 30 (Android 11) | 10 | 25 | **35/35 PASS** | **0 / 0 / 0** |
| Android API 36 (Android 16) | 10 | 25 | **35/35 PASS** | **0 / 0 / 0** |
| **Matrix total** | 30 | 75 | **105/105** | **0 / 0 / 0** |

The matrix artifacts contained the **same case-class distribution** as the API-35 run. Accordingly, this milestone has evidence of **102 JVM test executions plus four successful Android emulator runs × 35 cases = 242 passing executions**, while distinct test cases remain 137 (102 JVM + 35 Android). Do not misinterpret repeats across emulators as additional distinct test scenarios.

## 7. Independent artifact ZIP verification

All six artifacts below were downloaded in full, ZIP CRC-checked, independently SHA-256 hashed, and found to **match their GitHub-published ZIP digests exactly**. GitHub artifact IDs are immutable references to the original as-tested runs, subject to retention expiry.

| GitHub artifact | ID | ZIP bytes | SHA-256 ZIP (GitHub = independently computed) |
| --- | ---: | ---: | --- |
| [Native JVM Reports](https://github.com/shahvikask/kosha/actions/runs/37888065112/artifacts/11597645052) | `11597645052` | `48,744` | `661b57188969ed38d95ffa3fc4b3ed58f91a0ba8415949f06a58479d023d2734` |
| [Native Instrumentation Reports](https://github.com/shahvikask/kosha/actions/runs/37888065112/artifacts/11597697270) | `11597697270` | `214,220` | `3ff9d5d885e254ef0a9d980bfc5c463fd5c14b2862ec4d296b09f5770c67eace` |
| [Native Debug APKs](https://github.com/shahvikask/kosha/actions/runs/37888065112/artifacts/11596959222) | `11596959222` | `31,631,659` | `53c1bd0b95fc9df251163c7767a3982e77bbc6657fddef04861e7e2be1b0bfb8` |
| [API 26 Reports](https://github.com/shahvikask/kosha/actions/runs/37888065106/artifacts/11597396956) | `11597396956` | `122,102` | `4578b056e3e20582fe0639dce553e761239f90caf335c486804e733a39737c11` |
| [API 30 Reports](https://github.com/shahvikask/kosha/actions/runs/37888065106/artifacts/11597975482) | `11597975482` | `104,624` | `9524a8ec6854be19ae225a7dda33ab830051100a6fe4a29d2131b329f70c5269` |
| [API 36 Reports](https://github.com/shahvikask/kosha/actions/runs/37888065106/artifacts/11597865329) | `11597865329` | `168,345` | `ea95acc3bb1e460843b75ab93a6961793d3a5f9b36711a87debfd64e29004691` |

The native debug APK ZIP contains exactly these **three** development artifacts. Each internal APK was independently opened as an Android ZIP, had readable `AndroidManifest.xml` and DEX entries, and passed its own ZIP CRC checks:

| APK inside archive | Bytes | Independent SHA-256 APK |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `26,790,426` | `f04265ad1536dcd1c4ccc356bedd7292a8df42d1c997ed4af9beab580c512ab6` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `1,010,804` | `aeea6597dfa098e77c45c3644926ae14f597d79f1b72280eff9ef71abd7f9344` |
| `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `11,979,979` | `bfe07d9ae40843de4d35de4c857a0eaefee55c741e15243dc2497afc598d558b` |

This confirms build outputs are available and internally readable; it is **not** proof of production Android signing, runtime behavior across every ABI, Play install, or binary security compliance.

**Additional signed-release check (separate, NOT downloadable production release):** The native workflow built a minified release Android App Bundle (`bundleRelease`), signed it using a throwaway CI certificate and recorded `jar verified.` followed by `PASS: release AAB built, minified and signed with throwaway CI certificate.` The step deletes the temporary bundle/keystore. No Play upload key or production-signed AAB was examined or approved.

## 8. SQLite and native source-policy safeguards

GitHub Actions logs show the host schema preflight passing **14 v1 DDL + 6 v2 migrations**, including fresh DB and upgrade, rollback, immutable ledger, correction ownership, rich financial events and backup-compatible columns. The separate native static security preflight logged **PASS** for manifest privacy, screenshot prevention, secure device credential, **16 repository authorization entry points**, signed-release gate, SQL foreign-key/append-only protection and backup exclusions. These are scripted source checks, not independently executed penetration tests.

The native workflow's separate **immutable CASPARSER guard passed**. Independent repository comparisons at the exact tested SHA found **zero differences** in all frozen `src/casparser-main/**` and protected bridge/dependency files compared with the previous verified native baseline `076a7c68` and protected reference branch `GUD-Android-Gmail`. Protected scripts include `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py` and `scripts/requirements-cas.txt`.

All M6 implementation files were committed **solely** on `GUD-Android-ChatGPT`; no legacy Tauri/React financial source or protected parser files appear in this M6 increment.

## 9. Existing reference application: Kosha CI success

Companion Kosha CI `37888065100` completed successfully on the **same source SHA**. Its console reported the existing JavaScript/TypeScript reference checks as **512 passed, 0 failed**; separate snapshot tests as **32 passed, 0 failed**; and database tests as **78 passed, 0 failed**. This is **622 successful reference checks** across three groups, distinct from the native JUnit 102 + 35 numbers.

## 10. What was implemented versus what remains open

**Verified M6 development increment:** The native vault provides an authenticated **Add first family member / Manage family members** route. When Statements cannot find an owner, it provides an **Add family member** recovery route instead of just Dismiss. The first person must be `Self` and becomes sole primary; later members must have an allowed nonprimary relationship. Registrations validate names and ownership, are performed atomically in the real encrypted repository, use unrelated UUID owner IDs, require a *new* Android credential confirmation after form staging, and do not silently repair inconsistent legacy family books.

**Explicit limits and next gates:**

1. The secure device-credential challenge is not a GrowUrDhan-specific account identity/password/biometric trust model. The UI and repository policy require more end-to-end real-device, threat-model and MASVS testing.
2. Adding a family owner **does not automatically create holdings**. An encrypted vault with no known holding will still reject an unknown CAS ISIN/folio mapping; verified holding/account onboarding is the next functional gate.
3. CASPARSER Python internals are intentionally frozen; PDF origin, parser/runtime packaging, full supported-device ABI scope and document-owner matching still require independent end-to-end real-statement tests.
4. Restore/backup file-picking UX, full finance-product parity, physical-device coverage, repeatable AAB signing with production secrets, distribution policies, 16-KiB native support checks and a full AppSec audit remain incomplete.
5. Reference-app/legacy code and customer data were **not** migrated, reset or deleted.

## 11. Final milestone sign-off and traceability

**M6 CI verification verdict: PASS** for 13/13 new onboarding JVM, 8/8 new Android, 89/89 retained JVM and 27/27 retained Android tests. Four emulator APIs 26/30/35/36 passed all applicable Android cases, with no failures, errors or skips. All expected debug APK and JUnit artifacts were present and cryptographically cross-checked against GitHub artifact ZIP digests. Matrix and reference-app CI were green. CASPARSER immutability and SQLite/static-policy preflights passed.

**As-tested implementation SHA:** `3a4dfa0790f548b6132b43c5d2cf4897ab8cd9e6`. This report is a **documentation-only commit made after the passing runs**; it must never be used as evidence that a future code change has passed CI. The product remains an internally tested pre-release, **not** commercially authorized for Google Play.
