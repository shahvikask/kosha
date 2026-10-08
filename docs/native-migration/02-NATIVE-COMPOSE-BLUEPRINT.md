# GrowUrDhan — Kotlin/Jetpack Compose Native Migration Blueprint

**Status:** Architecture blueprint, 2026-10-08. **M1 native foundation now under implementation.**  
**Working branch only:** `GUD-Android-ChatGPT`  
**Parent review:** [01-AS-BUILT-REVIEW.md](01-AS-BUILT-REVIEW.md)  
**Constraints:** `in.growurdhan.app`, pre-release development-only data, offline-first, financial invariants, CASPARSER black-box preservation. **Owner clarification (2026-10-08): GrowUrDhan has never been published on Google Play. In-place upgrade of production installs is NOT a release requirement; a clean native encrypted schema is authorized.**

## 1. Outcomes and non-goals

### Required outcomes

- All production Android screens implemented in **Kotlin + Jetpack Compose + Material 3**, with adaptive dark/light color schemes, contrast/accessibility, minimal friction and purposeful animations/haptics.
- Strict separation between **Presentation** (Compose + ViewModel + UDF), **Domain** (use cases/rules/ports) and **Data** (SQL, storage, network, parser adapter, billing).
- **Kotlin Coroutines + StateFlow**, structured concurrency, lifecycle-aware collection and bounded background work. **Hilt** for DI with compile-time validation.
- An at-rest-encrypted **new native SQLite schema**, append-only ledger and financial invariants, reliable backups and user-owned data. No mandatory migration from unpublished development builds; network not required for opening the vault.
- Preserve CASPARSER **byte-for-byte** and keep parsing via Chaquopy. Wrap with adapter/validator; do not refactor the parser or patch its result semantics.
- Feature-parity of financial behavior first, controlled architecture simplification second. Existing unpublished test data may be explicitly converted or intentionally reset; preserve the finance correctness rules.
- Replace hybrid infrastructure and unused files **only after** native alternatives are complete, independently tested and safely rolled out.

### Explicit non-goals

- No GrowUrDhan cloud holding/PAN/password database; no new backend without separate product authorization.
- No switch to floating-point currency; no silently reconstructed transaction history.
- No fabricated Play purchase entitlements; choose a stable application ID and SKU plan before first public release.
- No parser engine edits; no blanket rewrite of Python/dependencies.
- No destructive vault reset as a migration shortcut.
- No forced certificate pinning; network TLS hardening must be threat-model based.

## 2. Proposed source tree and enforceable layering

Place Kotlin source **alongside**, not inside the generated Tauri Android directory:

```text
android-native/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.properties
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/in/growurdhan/app/GrowUrDhanApplication.kt
    src/main/java/in/growurdhan/app/MainActivity.kt
    src/main/res/
    src/androidTest/
  core/
    model/              # ID/value objects, error model; no Android imports
    designsystem/       # Material3 theme, typography, components, semantics
    security/           # Keystore-backed keys, cryptography, device auth
    database/           # Encrypted SQLite/Room driver, migrations, DAOs
    testing/            # fake clocks, safe data fixtures, test dispatchers
    parser-api/         # immutable result contract, adapter interface only
  domain/
    src/main/           # ports + use cases; pure Kotlin financial operations
    src/test/
  data/
    src/main/           # repository implementations, network, CAS adapter,
                        # Gmail, Billing, prices, backup and migration
    src/test/
  feature/
    onboarding/
    vault/
    dashboard/
    holdings/
    transactions/
    accounts/
    family/
    statements/
    gmail/
    licence/
    settings/
  migration/
    src/main/           # one-time legacy vault import, read-only extractor
    src/androidTest/    # upgrade/rollback/fault-injection tests
  README.md
```

The number of feature modules can be consolidated for efficient solo development until build times justify more; **layer boundaries and dependency direction cannot be relaxed**.

Dependency direction:
- `app -> feature/*, data, migration, core/designsystem`
- `feature/* -> domain, core/model, core/designsystem`
- `domain -> core/model, core/parser-api` (no Compose/Android/SQL dependencies)
- `data -> domain, core/model, core/database, core/security, core/parser-api`
- `core/parser-api` has **no dependency on CASPARSER implementation**.
- Chaquopy Python binaries packaged by `app` using exact frozen sources; adapter implementation supplied from `data`.
- Only repository/adapter implementation layers access SQLite, HTTP, Keystore, Billing, file pickers and Python.

Use Gradle Kotlin DSL + version catalogs + dependency locking (after evaluating Android/Chaquopy compatibility); Kotlin compiler, Compose compiler plugin, Android Gradle Plugin and Hilt/KSP versions must be pinned to a **tested compatibility matrix**, not invented in docs.

## 3. Core domain model and architecture

Represent domain identifiers as immutable typed values: `EntityId`, `AccountId`, `HoldingId`, `LedgerEventId`, `StatementId`, `GmailAccountId`. Persist existing stable string IDs without regeneration.

Finance primitives:
- `MoneyScaled(value: Long, currency: INR)` with **10,000 scale**, checked add/subtract/multiply and explicit rounding;
- `QuantityScaled(value: Long)` with **100,000,000 scale**;
- `RateBps` and ISO dates; no `Double` within persistence/business-invariant paths.
- Use `BigDecimal` only at documented conversion/financial formula boundaries, with locale-independent parsing and explicit precision/rounding.
- Mirror old schema's accepted negative values for legitimate cash flows; do not introduce unsound global non-negative constraints.

Repository ports: `VaultRepository`, `LedgerRepository`, `HoldingRepository`, `MemberRepository`, `AccountRepository`, `SnapshotRepository`, `PriceRepository`, `BackupRepository`, `GmailSyncRepository`, `LicenceRepository`, `StatementParser`.

Use cases: `UnlockVault`, `RegisterOwner`, `AddHolding`, `AppendLedgerEvent`, `ReconcileStatement`, `ImportValidatedSnapshot`, `DeleteFamilyMember`, `CalculateNetWorth`, `RefreshEodPrices`, `CreateBackup`, `RestoreBackup`, `SyncStatementMail`, `VerifyLicence`.

For every finance operation:
1. Validate normalized input and member ownership.
2. Calculate deterministic changes in a pure domain service.
3. Validate invariants, idempotency and chronology.
4. Commit all changes in one DB transaction.
5. Publish immutable state after commit; never mutate UI models optimistically for money writes.
6. Record a non-sensitive audit outcome; no raw statement bytes/PII/credentials in logs.

### 3.1 UI state contract

Each native screen has one `@HiltViewModel`, or shared ViewModel only for a cohesive nested workflow:
- `StateFlow<ScreenUiState>` with `Loading`, `Ready`, `Empty`, `RecoverableError`, `Locked` where relevant;
- explicit `UiAction` sealed type for user intents;
- `UiEffect` for one-shot navigation, snackbars or external intents;
- `SavedStateHandle` for non-secret navigation parameters only;
- immutable snapshots collected with `collectAsStateWithLifecycle()`.
- cancellation-safe `viewModelScope`; `Dispatchers.IO` for DB/Python/network, injected dispatchers and clocks for tests.
- no transient vault passwords, PAN or Gmail tokens in saved instance state, analytics, UI debug logs or deep links.

Use Material 3 `Scaffold`, navigation destinations, large-font/touch targets, dark/light dynamic schemes with non-dynamic baseline, TalkBack semantics and reduced-motion preferences. Numeric typography must be tabular and masked/unmasked states deliberate; never animate money values in a way that hides discrepancies.

### 3.2 Major screens and navigation graph

```text
START
  -> Security/terms gate
  -> VaultExists? -> Unlock / Recovery : Onboard / Register
  -> Licence access / Trial
  -> MainScaffold:
       Dashboard
       Holdings -> Details -> Add/Edit/Append Record
       Accounts
       Documents -> File picker -> Parser -> Review -> Confirm Import
       Family -> Member Details / Gmail / Backup & Restore
       Settings -> Theme / Privacy / Security / Diagnostics / Terms
```

Native edge cases: Android back navigation, process recreation, window-size changes, file picker cancellation, permission persistence, offline/open-vault behavior, multi-user privacy toggles, keyboard and accessibility traversal.

## 4. CASPARSER: immutable boundary

Define a Kotlin interface `StatementParser` whose `parse(PdfBytes, password, filename)` returns a typed **Result**. The `data` adapter uses `Python.getInstance().getModule("cas_android").callAttr("parse_b64", ...)` or a demonstrably equivalent byte-preserving interface exposed by the **existing** engine. Preserve the exact existing `cas_health` and `cas_parse` response shapes until contract tests pass.

No edits to:
- `src/casparser-main/**`;
- `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py` or parser dependency pinning **without explicit separate user authorization**;
- parser algorithms, result codes, classification, extraction, rates or private patches.

Allowed outside the parser:
- strict PDF length and format checks before invocation;
- capped memory, dedicated dispatcher, cancellation handling around results, and crash isolation compatible with Chaquopy;
- schema/type validation of the returned JSON, explicit `parseComplete`, owner matching and tolerance-based reconciliation checks;
- mapping of existing `CasPythonResult` into Kotlin immutable entities **without modifying source data**;
- transaction commit only after confirmed user intent or trusted, audited automatic-import policy.

Record SHA-256 manifests of frozen parser sources and known-safe synthetic golden fixtures before the first native parser integration. Verify hashes every CI run that touches packaging. DO NOT use this task to fix or update the engine.

## 5. Database design, encrypted storage and migration choices

### 5.1 Database decision gate

**Do not introduce plaintext Room by default.** Evaluate:
- Room with a vetted, actively supported SQLCipher/SQLite encryption integration and a random database key wrapped by Android Keystore;
- or a reviewed Keystore-encrypted native SQLite approach whose transaction durability and threat-model properties are demonstrably equal or stronger than the current native `EncryptedFile` snapshot.

Select only after validating licensing, supply-chain trust, process recovery, incremental migrations, Android version/ABI support, 16 KiB memory-page support as applicable, backup and performance. `EncryptedSharedPreferences` and `EncryptedFile` are **legacy compatibility mechanisms**; plan their replacement without breaking old vault access.

Implement a new encrypted native schema with explicitly mapped financial invariants. **Schema v9 import is optional for pre-release test data**, not a mandatory supported migration path. Where optional import is offered, document checksums and table counts before/after. Preserve the business meaning of:
- all existing `entity`, `account`, `instrument`, `holding`, `ledger_event`, `price_observation`, `import_batch`, `source_document`, `statement_position`, `tax_rule_set`, `liability`, `goal`, `setting`, `egress_log`, `listed_security`, `vault_account` and `gmail_seen`;
- ledger UPDATE/DELETE guard (and precisely the existing intentional deletion semantics);
- `cas_latest` metadata, duplicate keys, indexes, account/member foreign keys and import provenance;
- monetary and quantity integer scales, sorted transaction history and rounded displays.

### 5.2 Pre-release vault creation and optional developer-data conversion

```text
NOT_STARTED
  -> PREFLIGHT
  -> LEGACY_READ_ONLY
  -> ENCRYPTED_STAGING
  -> IMPORTING
  -> VALIDATING
  -> NATIVE_COMMITTED
  -> USER_CONFIRMED
  -> ROLLBACK_RETAINED
  -> RETIRED_LEGACY (future gate only)

Any error before NATIVE_COMMITTED -> recoverable FAILED,
leave prior vault and old app reading path intact.
```

Exact sequence:
1. For fresh native installs, create an encrypted native vault under the new schema. For developer devices containing legacy `vault.enc` or `GUDENC01`, prompt to **export, convert, or deliberately reset** existing test data; do not silently overwrite the old vault.
2. If optional legacy conversion is selected, read the legacy `EncryptedFile` with its original Keystore alias/key under a compatible test-signed build, or restore from a locked `.kosha` export. No production signing key exists to preserve from a Play release.
3. In the trusted app process, deserialize the SQLite WASM image or read through a temporary retained legacy adapter; export **all required tables/metadata**, with documented type/scaling and deterministic JSON/SQL compatibility. No unencrypted staging file on shared storage.
4. Stage into a new **encrypted** native database with a fresh Keystore-wrapped key; never reuse user login password as the device DB key.
5. Validate schema version, row counts, IDs, relation mappings, full referential integrity, no missing ledger events, asset/member totals and deterministic portfolio outputs. Cross-check backup export/reimport and representative cost/tax reports.
6. Atomically install the new encrypted database and record migration state in authenticated durable metadata. Retain old developer data during conversion; for explicit fresh-start reset, verify consent before deleting it.
7. Launch native financial UI only after a fresh encrypted database is initialized or an optional migration validates successfully. Expose guided retry/reset/restore on failure.
8. Retain encrypted optional-migration rollback evidence until the developer confirms results. For first public Play release there is no mandatory pre-existing install cohort.

Test both modes: native `EncryptedFile` and IndexedDB-encrypted fallback; never assume all installations have one location. Validate KeyStore invalidation/device transfer/reinstall behavior against the supported locked backup restore path.

### 5.3 Legacy backup support

Create a robust, versioned **native encrypted backup format** for the first production release. The old `GUDVLT01` (magic 8 bytes, iterations 4 big-endian bytes, salt 16, IV 12, GCM ciphertext) and `KOSHA1` readers are **optional developer-data import compatibility**, not obligatory new-backup formats. If implemented, use synthetic fixed vectors and verify post-restore financial invariants.

The new production backup format must preserve all native required tables, price history and import state. Explicitly version the format. Do not mutate the old `GUDVLT01` definition; optionally support it for existing development data.

### 5.4 Atomicity and failure safety

- Insert/update holdings, statement snapshots and any adjustment events in one checked DB transaction.
- Snapshot selection must be owner verified, complete, reconciled and chronological.
- Restore into staging; no immediate `wipeVaultCompletely` on the live database.
- Record durable checkpoints to resume interrupted migrations; apply a single migration lease.
- Database export/flush must survive process kill, device storage exhaustion and restore cancellation.
- Before the first Play release, document new native vault recovery. Optional conversion of legacy development installs must retain the source until explicitly confirmed.

## 6. Native services

| Capability | Planned Kotlin implementation | Mandatory contract |
| --- | --- | --- |
| Vault and session | Keystore-backed encrypted DB, PBKDF2 credential verification compatibility, BiometricPrompt optional | Offline unlock, auto-lock, zero token/password logs, recovery |
| Statement parser | Chaquopy adapter outside frozen engine | Same inputs/outputs, golden parity, no storage of PDF bytes |
| Gmail | Google Authorization API + OkHttp HTTP client + durable state machine | Restricted scope, account isolation, full pagination, no skipped failed PDF |
| Price refresh | Kotlin network client, validated NSE/BSE/AMFI close/NAV | Explicit refresh, offline cache, non-identifying requests |
| Billing | Google Play Billing client | Existing SKU ownership, trial lifecycle, restore across upgrade |
| Backup | Storage Access Framework + encrypted backup import/export | Existing `.kosha` restores; no unencrypted persistent copy |
| Scheduling | WorkManager where OS policy permits | User consent, connectivity, retry and background limits |
| Diagnostics | Sanitized, off by default | Never tokens, password, PAN, PRAN or PDF bytes |
| Navigation | Navigation Compose with typed routes | Correct lock gates, deep link validation, predictive back |
| Observability | Opt-in crash data or local-only diagnostics | Explicit privacy design and bounded retention |

## 7. Migration milestones / acceptance gates

| Milestone | Deliverable | Must pass before proceeding |
| --- | --- | --- |
| M0 — Evidence baseline | As-built review, feature parity inventory, protected parser hashes, CI results | No code changes to parser; review signed off |
| M1 — Native skeleton | Checked-in Gradle project, Material3 theme, Hilt, navigation, typed UDF screen states, sample **nonfinancial** screen | Kotlin compilation, unit+Compose smoke tests, no runtime change to shipping app |
| M2 — Crypto & data proof | Encrypted SQLite candidate + legacy read-only extractor + backup vectors | Atomic migration, migration rollback, data/ledger reconciliation on synthetic and old-version device snapshots |
| M3 — Financial domain | Kotlin ports/use cases, checked scaled-integer math, repositories, event/positions/returns | Golden parity with TS financial outputs; append-only and transaction tests |
| M4 — CAS integration | Immutable Chaquopy packaging + Kotlin adapter + external validator + review UI | Frozen parser hash unchanged; PDF golden contract pass |
| M5 — Accounts & portfolio | Compose onboarding/auth, dashboard, holdings, records, account/family screens | End-to-end on-device parity; accessibility, offline and process-death tests |
| M6 — External integrations | Prices, Play Billing, Gmail, backup scheduler and account deletion | OAuth/Billing/privacy validation; restart and failure tests |
| M7 — Native pre-release validation | Fresh native install, explicit optional developer-data conversion, backup/restore | Encrypted vault, recovery and Play signing verified on devices |
| M8 — Legacy cleanup | Remove Tauri/React/Rust/bridge and unused scripts in audited batches | Deletion checklist complete, AAB built, no runtime references, all parity tests green |
| M9 — Commercial sign-off | Security assessment, Play policy and Data Safety re-evaluation, staged release | Owner approval; no unresolved critical/high issues |

Each milestone must yield reviewable code and actual test evidence. M8 deletion is prohibited before M7 gates pass.

## 8. UX design rules and acceptance

- Design system: Material 3 dynamic light/dark with deterministic brand fallback, 4/8dp spacing rhythm, tabular INR values, readable time-of-day/date/number locales.
- Minimize steps for view holdings, add holding, import statement, restore backup and refresh prices.
- Mask financial and identity values at rest/lock; expose reveal controls accessible to TalkBack.
- Confirmation screen displays account/member, source, statement date, totals, reconciliation delta, proposed added/changed/closed holdings; never silently close an unmatched holding.
- Every error message differentiates retryable, needs-user-input and permanently rejected states.
- Haptics only for meaningful success/failure and never repeated rapidly; honor system reduced motion.
- Compare UI screenshot baselines across small phone, large phone, font scale 200%, dark/light, Android system contrast modes and right-to-left where applicable.

## 9. Security and performance engineering standards

- Follow OWASP MASVS threat-model based tests across device storage, auth, networking, platform interaction and code integrity.
- HTTPS-only network configuration; no blanket certificate pinning without endpoint maintenance plan.
- Tokens handled by native authorization services, short-lived and not exposed to Compose saveable state.
- R8 full-mode shrink/obfuscation in release once reflection/Chaquopy/serialization/Billing rules are proven.
- Dependency scanning, license compliance, secret scanning, CI signing separation and release-bundle signature verification.
- User-private financial content must never enter crash reports, push payloads, GitHub Actions logs or remote analytics.
- Measure cold/warm startup, memory under large CAS PDFs, battery under background sync, DB commit p95, parser invocation latency, app-not-responding and frame jank on physical Android devices.

## 10. Decision requests before destructive or release-impacting work

1. **Answered:** No Play releases exist. A fresh native database is authorized. No mandatory in-place migration of legacy development installs; optional developer-data conversion or explicit reset only.
2. Confirm minimum supported Android version and legacy devices; current Tauri config minimum is API 26.
3. If you want to preserve developer test records, supply a synthetic or consented `.kosha` backup for voluntary import testing; do not post personal credentials or PAN.
4. Confirm whether Gmail is a release-critical feature or can be gated until OAuth approval/security remediation.
5. Confirm product/licence SKU catalog against the actual Play Console.
6. Choose a vetted encrypted SQLite driver only after M2 proof, including process-death and tamper tests.
7. Explicitly authorize the first Play production release and deletion of unused legacy code only at M7/M8 gates.

**Next engineering action:** Implement M1 native Gradle/Compose scaffold on `GUD-Android-ChatGPT`, without touching the frozen parser, legacy vault or Play release workflows.
