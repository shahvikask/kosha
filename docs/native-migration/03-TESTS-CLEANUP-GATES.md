# GrowUrDhan — Native Migration Test Plan, Cleanup Manifest and Release Gates

**Status:** Planned checks; only explicitly recorded baseline CI checks have run.  
**Branch:** `GUD-Android-ChatGPT`  
**Related:** [01-AS-BUILT-REVIEW.md](01-AS-BUILT-REVIEW.md) | [02-NATIVE-COMPOSE-BLUEPRINT.md](02-NATIVE-COMPOSE-BLUEPRINT.md)

## 1. A verifiable cleanup policy — not a blind delete list

Legacy cleanup is required, but is **conditional** on functional parity, a documented choice for preserving/resetting development test data, and a reproducible native Android release candidate. No Play Store version exists to upgrade. Delete only in separate, small commits with a before/after reference scan and mandatory build/test verification.

Each cleanup item needs:
- named new owner/replacement path;
- list of transitive imports/commands/build tasks that used it;
- data compatibility and account/release side-effect assessment;
- green unit, instrumentation, migration, rollback, Play Billing and parser contract tests where relevant;
- removal diff reviewed for accidental parser modification;
- proof Android App Bundle is installable as a **fresh** release and correct application/package ID; optional compatibility install tests if developer test data is converted;
- rollback strategy when existing devices rely on the old layer.

All deletions occur **on `GUD-Android-ChatGPT` only**. Do not delete from `main`, `GUD-Android` or `GUD-Android-Gmail`.

## 2. Exact legacy-file disposition

| Legacy paths | Proposed disposition | Earliest milestone | Replacement / guard |
| --- | --- | --- | --- |
| `src/casparser-main/**` | **PROTECTED: KEEP byte-for-byte** | Never without new authorization | CASPARSER black box. Track source hashes |
| `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py` | **PROTECTED: NO EDITS during migration** | Separate approval only | Parser/invocation boundary frozen; sidecar may be retired only if separately proved out of use, without altering engine |
| `scripts/requirements-cas.txt` | **PROTECTED dependency baseline** | Separate approval only | Hash-locked dependencies for existing parser contract |
| `src/components/screens/*.tsx` | DELETE after parity | M8 | `android-native/feature/*` Compose destinations, screen E2E tests |
| `src/components/modals/*.tsx` | DELETE after parity | M8 | Compose sheets/dialogs/forms, accessibility tests |
| `src/components/layout/*.tsx`, `src/components/common/*.tsx`, `src/components/growth/*.tsx` | DELETE after parity | M8 | Native design system, charts, previews/screenshot tests |
| `src/App.tsx`, `src/main.tsx`, `src/index.css`, `index.html` | DELETE after native startup works | M8 | Kotlin `Application`, `MainActivity`, navigation, Material3 theme |
| `src/context/KoshaContext.tsx`, `src/context/LicenseContext.tsx` | DELETE after services ported | M8 | Kotlin ViewModels, UDF, Hilt use cases |
| `src/db/platform.ts`, `sqliteWasmDriver.ts`, `encryptedStore.ts`, `nodeDriver.ts`, `driver.ts`, `repository.ts`, `schema.ts`, `migrate.ts` | DELETE runtime TS implementation **after** data/DB compatibility validation; retain source history | M8 | Encrypted SQLite, Room/DAO, native schema migrations and legacy extractor |
| `src/lib/*.ts` other than tests | **MAP EACH MODULE BEFORE DELETE** | M8 | Kotlin equivalents for money, returns, interest, tax, backups, lock, prices, Gmail, licences, matching, CAS adapter; never delete a financial calculator without parity |
| `src/types.ts` | DELETE after domain mapping parity | M8 | Kotlin `core/model` and `domain` typed contracts |
| `src/lib/*.test.ts`, `src/db/db.test.ts`, `src/lib/casFixtures.ts` | **KEEP AS ORACLE until golden parity, THEN ARCHIVE** | M8+ | Kotlin/JUnit tests; old tests remain runnable during migration |
| `src-tauri/src/*.rs`, `src-tauri/Cargo.toml`, `src-tauri/build.rs`, `src-tauri/tauri.conf.json`, `src-tauri/capabilities/**`, `src-tauri/permissions/**` | DELETE only after new bundle no longer invokes Tauri | M8 | Native Android Gradle app, Kotlin services; verify no release tools use Cargo |
| `src-tauri/android-overlay/app/src/main/java/in/growurdhan/app/{GrowUrDhanPlugin.kt,GmailClient.kt,PriceFetcher.kt,Diag.kt}` | **PORT then DELETE overlay copies** | M8 | New dedicated Kotlin services. **Do not delete working Kotlin features prematurely** |
| `src-tauri/android-overlay/app/src/main/res/xml/**` | **MIGRATE, NOT BLIND DELETE** | M8 | Native manifest + new XML network/data extraction rules; confirm privacy equivalence |
| `src-tauri/android-overlay/app/proguard-rules.pro` | **REPLACE R8 rules** | M8 | New native packaging keep rules; preserve Chaquopy/Billing interoperability |
| `scripts/android_prepare.mjs`, `scripts/ensure_rgba_icons.py` | DELETE when builds fully native | M8 | Version-controlled Gradle, Android resources and native icon pipeline |
| `scripts/dev.mjs`, `vite.config.ts`, `tsconfig.json`, `src/vite-env.d.ts` | DELETE only when no JS build or local parser tooling relies on them | M8 | Native build; any developer-only tooling separately retained if needed |
| `package.json`, `package-lock.json` | DELETE only after no remaining npm scripts / tools | M8 | Gradle native-only tasks; check CI and marketing tasks first |
| `.github/workflows/android-aab.yml`, `android-apk.yml`, `ci.yml`, `android-enhancements.yml` | **MIGRATE WORKFLOWS**, remove obsolete parts only after native job passes | M8 | Native lint/test/build/security/Play pipeline, source-control signing and proper branch triggers |
| `src-tauri/keystore/gud-test.keystore` | REVIEW THEN REMOVE if not required by test OAuth/signer fixture | M8 | Secure, reproducible test-signing identity; changing test certificate may affect installed APK upgrades and Gmail SHA registration |
| `docs/KOSHA-ARCHITECTURE.md`, `KOSHA-DATA-MODEL.md`, `KOSHA-SECURITY.md`, `GrowUrDhan-Software-Build-Document.md` | **ARCHIVE or retain as historical source** | After release | New as-built native docs; history stays auditable |
| `docs/GMAIL-SYNC.md`, `docs/ANDROID*.md`, `docs/PLAY-CONSOLE.md`, `docs/RELEASE-CHANNELS.md` | UPDATE rather than delete where required | M6-M9 | Play/Gmail/release guide aligned to native app |
| `marketing/**`, `public/brand/**`, `src-tauri/icons/**` | **KEEP/MIGRATE USAGE** | M8 | Privacy/terms/delete URLs, listing assets, Android mipmaps; avoid breaking public policy URLs |
| `.env.example`, `.gitignore`, `README.md` | UPDATE, KEEP | M1-M9 | Native build, secrets and developer onboarding |

Before any mass deletion run `git grep`, dependency-tree analysis and CI build references. Reserve at least one release cycle for compatibility fixes before permanent removal.

**Explicitly not safe to delete simply because Compose displays the same UI:** legacy vault readers, old backup decryptors, old schema migrations, CASPARSER, Play Billing entitlement logic, import event fingerprints and parser fixture/golden data.

## 3. Mandatory golden/parity data sets

All fixtures must be synthetic or consensually anonymized, never real user statements in public CI.

| Test dataset | Expected assertions |
| --- | --- |
| Brand-new empty vault | Correct schema, empty totals, onboarding, trial state |
| New native schema and optional v9 developer-import fixtures | Fresh encrypted schema integrity; only explicitly supported test-data conversion paths must preserve rows and hashes |
| Multi-member family (Self/Spouse/Child/HUF) | Same member IDs, ownership, primary-member safeguards |
| Holdings across categories | Exact scale, price, quantity, valuation, taxonomy, maturity fields |
| Repeated buy/sell/dividend/interest/split records | Immutable event list, position computation and tax effects |
| Same PDF imported twice | No duplicate holdings/cash flows or ledger events |
| Incomplete or corrupted statement | Zero destructive portfolio writes |
| CAS ownership PAN/PRAN conflicts | No unapproved cross-member import |
| NPS + CDSL + NSDL + CAMS + KFintech statements | Same accepted parser output and explicit reconciliation decisions |
| Legitimately closed holding | Correct closure without affecting unowned holdings |
| Old statement received after new snapshot | Never silently regress to older book |
| No network while unlocked | Display and local writes remain functional |
| Old binary `.kosha`, legacy `KOSHA1` backup | Restore, identity verification, re-export/re-import |
| Device crash after each migration checkpoint | Retry without data loss or half-installed database |
| Device Keystore invalidated / new phone | Descriptive locked-backup recovery, no silent reset |
| Gmail account disconnect/reconnect | No stale cursor use and no cross-account bleed |
| Gmail history >20 records and multiple pages | Complete incremental coverage and durable progress |
| Incorrect password followed by correct PDF credential | Previously failed attachment remains retryable |
| Lifetime + Gmail upgrade + annual support SKUs | Identical entitlements across app upgrade |
| Release minify + ABI + first Play signing | Working Chaquopy, Billing, assets and correct first release package |

## 4. CI jobs to implement on native branch

1. **Source boundary check:** immutable CASPARSER hashes against approved baseline; fail on any parser change.
2. **Dependency audit:** locked Gradle Maven/Chaquopy dependencies, vulnerability/license report, secret scan, SBOM.
3. **Kotlin static:** formatter, Android Lint, Detekt, compiler warnings reviewed, Kotlin API checks.
4. **Unit:** JUnit (compatible runner), MockK, Turbine, `kotlinx-coroutines-test` for domain, ownership, pricing and ViewModels.
5. **Storage:** Room/native SQL migration tests; PRAGMA `integrity_check`, `foreign_key_check`, SQL triggers and rollback.
6. **Parser contract:** frozen synthetic PDFs -> golden JSON; run outside engine implementation; verify checksum.
7. **UI:** Compose instrumentation for auth, dashboard, add holding, family, CAS review, backup and licence.
8. **Security:** OWASP MASVS sampling, network response caps, Android exported surface, WebView-removal verification.
9. **Build:** Android SDK target policy, release shrink, Chaquopy ABI compatibility, 16 KiB device memory-page requirements, output signed or explicitly unsigned release AAB as appropriate.
10. **Artifacts:** `bundletool` inspect metadata/ABI, install test on multiple Android versions, capture crash/migration logs with no PII.
11. **Release:** explicit human approval, Play internal testing first, staged rollout, pause/rollback strategy.

Each job must have `if-no-files-found: error` for mandatory output; do not hide test failures with `|| true` or `npm ci || npm install` for release builds.

## 5. Verification matrix and exit criteria

| Gate | Deterministic verification | Release block |
| --- | --- | --- |
| CASPARSER immutability | Check SHA-256 manifests of protected files, no diffs | Always |
| Database schema | Structural comparison, constraints, indices, triggers, migrations | Always |
| Financial parity | Golden value and event comparator: TS versus Kotlin, exact scaled units | Always |
| Native vault initialization | Fresh encrypted vault, restart, low storage and crash recovery; optional developer conversion tested separately | Always |
| Backups | `GUDVLT01`, legacy backup and new export round-trip with synthetic users | Always |
| Security | Keystore handling, no plaintext persistent DB, auth, privacy and transport | Always |
| Core UX | Compose journey tests, TalkBack, themes, rotation, large fonts | Always |
| CAS imports | All engine-backed golden fixtures and incomplete/mismatch negative tests | Always |
| External services | Offline cache, prices, Play purchases, Gmail auth/history | When enabled |
| Release | AAB signature, package, version monotonicity, supported ABI and Play policy | Always |
| Cleanup | No remaining imports/build references, disabled old subsystem, parser untouched | Before each deletion |

**Exit result must name PASS / FAIL / BLOCKED / NOT RUN and supply actual command + CI run or device evidence.** Architectural documents alone do not earn production sign-off.

## 6. Baseline vs new tests — actual status, not promises

- Reviewed branch initial commit: `af96dcc8967ab595e1d9acee78e29e500cb99a9e`.
- GitHub Actions run `37752674990` (Kosha CI): **PASS** for npm installation, TypeScript check, production frontend build, core tests and snapshot tests.
- The same workflow does **not** execute `npm run test:db`. Database test status: **NOT RUN** in that workflow.
- Android workflow run `37752673250`: **FAILURE (unknown cause)** in GitHub run metadata; returned no jobs, so Android compilation status is **UNVERIFIED**, not proven failed.
- Native Compose build/test: **NOT RUN**; native project does not exist yet at the initial review stage.
- CASPARSER engine changes: **NONE** in the planning scope.
- Legacy source cleanup: **NOT STARTED** by design; deletion requires native parity.

## 7. Security and privacy non-regression checklist

- No new remote service for money, identity, statement contents or credentials.
- All secrets redacted from diagnostics/telemetry; production logging opt-in by default off.
- No Gmail access token exposed to business UI state or backup.
- No plaintext `*.db`/PDF/password written to external or cache directories during migration.
- No accounts/PAN included in Firebase Analytics/crash payloads.
- BiometricPrompt used only as supported user-authentication gate; cannot supplant backed-up recovery identity.
- `FLAG_SECURE` policy considered for sensitive screens with user accessibility/troubleshooting tradeoffs.
- Complete OAuth restricted-scope requirements and accurate Play Data Safety listing before Gmail release.
- Require external attack-surface tests on Android before native Play distribution.

## 8. Handoff checklist for each implementation PR

```text
[ ] Scope / threat model / data impact recorded
[ ] Exact changed files and replacement ownership recorded
[ ] No protected CASPARSER changes (hash check)
[ ] No off-branch writes
[ ] Data backup/migration compatibility demonstrated
[ ] Security, financial and UX parity acceptance met
[ ] Unit + integration + device tests: actual results attached
[ ] Gradle, dependency and signing changes reviewed
[ ] No customer data or secrets in fixtures/logs
[ ] Cleanup includes proof of no remaining references
[ ] Rollback steps reviewed
[ ] No claim of production readiness without owner approval
```

## 9. Proposed implementation sequence

Current: **M1** — checked-in Kotlin/Compose Material3 scaffold, pinned toolchain, DI, navigation, local appearance state, unit/Compose smoke tests and native CI. After M1 passes compilation and tests, begin **M2 fresh encrypted native database proof**, with optional opt-in legacy developer-data conversion instead of mandatory unpublished-version upgrades.

Do not skip M2 to obtain a polished UI: financial customer data preservation is the release's highest-risk dependency.
