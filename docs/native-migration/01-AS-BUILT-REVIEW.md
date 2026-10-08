# GrowUrDhan — Native Android Migration: As-Built Architecture Review

**Date:** 2026-10-08  
**Repository:** `shahvikask/kosha`  
**Working branch:** `GUD-Android-ChatGPT`  
**Baseline commit:** `af96dcc8967ab595e1d9acee78e29e500cb99a9e` (identical to `GUD-Android-Gmail` when review began)  
**Decision status:** Proposed; no native migration authorized for release.  
**Scope:** Source-backed architecture and migration-readiness review, not a claim of exhaustive penetration testing.  
**Hard constraint:** CASPARSER is immutable. No edits under `src/casparser-main/**`, or to its Python parsing logic. Treat associated Python packaging/invocation scripts as frozen until the protected boundary is explicitly agreed and hashed.

## Executive decision

**Proceed by staged replacement, NOT a big-bang rewrite.** Build a native Kotlin/Compose app in a separate, version-controlled Gradle project; migrate existing vaults transactionally; preserve the Python engine through a stable adapter; replace features in audited vertical slices; remove the hybrid stack only after verified parity and migration rollback gates. Never ship a blank native database over a real customer's vault.

## Source inventory and provenance

The GitHub recursive tree for the baseline contains **213 entries and 173 tracked files**. This review inspected the tree, core configuration, production/CI workflow scripts, Android bridge, vault encryption, SQL schema/driver, authentication, backup/restore, parser adapter, Gmail sync, licensing, main UI composition, core context and existing tests. It did not execute device or penetration tests, and is not a line-by-line security attestation of every source file.

### Active runtime

1. `src/App.tsx` and `src/components/**`: React 19 + TypeScript UI, Tailwind 4, Vite.
2. `src/context/KoshaContext.tsx`: approximately 2,050 lines of account, portfolio, import, state and backup orchestration; `LicenseContext.tsx`: licence state.
3. `src/db/platform.ts` always opens `SqliteWasmDriver` against `createVaultStore()`; **not native Room or SQLCipher**.
4. `src/db/sqliteWasmDriver.ts`: in-memory SQLite WASM; serialize after writes via debounced `persist()`, and on close. The Android blob store is implemented by native Android when available.
5. `src/db/encryptedStore.ts`: `NativeKeystoreStore` invokes `vault_blob_load/save`, with a fallback to browser IndexedDB wrapped in `GUDENC01` PBKDF2/AES-GCM. Distinguish the fallback from Android's Keystore-backed file and test it explicitly.
6. `src-tauri/android-overlay/app/src/main/java/in/growurdhan/app/GrowUrDhanPlugin.kt` (approximately 1,083 lines) implements vault file I/O, account/licence, Play Billing, backup pickers, CAS invocation, prices and Gmail.
7. `src-tauri/src/lib.rs` is a Rust/Tauri command bridge. `src/lib/license.ts` selects plugin versus app invocation channels with fallback.
8. `src-tauri/android-overlay/app/src/main/java/in/growurdhan/app/GmailClient.kt` requests Gmail statement PDFs locally.
9. `scripts/android_prepare.mjs` mutates generated Gradle and Manifest content after `tauri android init`; Compose migration must replace this with source-controlled Gradle files.
10. `src/casparser-main/**` is the immutable Python CASPARSER implementation. `scripts/cas_android.py` is the existing Chaquopy entry point. `src/lib/casPython.ts` adapts results for the current TS portfolio model. They are separate layers; preserve the entire existing parser behavior.

### Critical user-facing feature inventory — parity is mandatory

| Existing module | Feature / data to preserve |
| --- | --- |
| `AuthScreen.tsx`, `OnboardingScreen.tsx` | First-use setup, local credentials, security questions, lock, recovery and password rotation |
| `HomeScreen.tsx`, `Charts.tsx` | Net worth, household/member summaries, charts and valuations |
| `HoldingsScreen.tsx`, `HoldingDetailModal.tsx` | Holdings list, filters, categories, edit/detail and historical data |
| `AddHoldingModal.tsx`, `AddRecordModal.tsx` | Manual asset creation, transactional events, cost calculations |
| `AccountsScreen.tsx` | Custody, demat and folio account information |
| `DocumentsScreen.tsx`, `CasConfirmPanel.tsx` | Statement selection, parser feedback, reconciliation and confirmation |
| `FamilyScreen.tsx` | Family membership, vault backup/restore, Gmail opt-in and sync, profile changes |
| `LicenseScreen.tsx`, `LicenseContext.tsx` | Trial, lifetime, Gmail and annual support SKUs; Play restore |
| `TermsGate.tsx`, `FeedbackPrompt.tsx` | Terms, reviews and user feedback |
| `src/lib/eodPrices.ts`, `PriceFetcher.kt` | NSE/BSE/AMFI close/NAV refresh and offline cached values |
| `src/lib/interest.ts`, `emi.ts`, `lots.ts`, `returns.ts`, `taxRules.ts` | Time-dependent financial calculations and conventions |
| `src/lib/backup.ts`, `backupSchedule.ts`, `backupIo.ts` | Manual and scheduled encrypted backup, restore and permissions |
| `src/lib/gmailSync.ts`, `statementMail.ts` | Gmail account association, attachment discovery, owner resolution and history |

Review each feature's edge cases and legal disclosures during parity sign-off; a visual replica without these behaviors is incomplete.

## Current financial data and compatibility contracts

- `src/db/schema.ts`: **schema version 9**. The DDL comment about SQLCipher is historical and conflicts with `src/db/platform.ts`; the current Android execution path is SQLite WASM.
- Primary tables: `vault_meta`, `entity`, `account`, `instrument`, `holding`, `ledger_event`, `price_observation`, `import_batch`, `source_document`, `statement_position`, `tax_rule_set`, `liability`, `goal`, `setting`, `egress_log`, `listed_security`, `vault_account`, `gmail_seen`.
- Money and quantities: scaled SQLite **INTEGER** values, with string/BigInt conversion at TS boundaries. Schema comments specify 10,000 units/rupee and 100,000,000 units/asset quantity. Kotlin must use checked `Long` arithmetic, decimal conversion rules and explicit overflow tests; never use `Double` for accounting.
- `ledger_event` has triggers preventing update/delete; exceptions exist in repository delete/wipe workflows that temporarily remove the delete trigger. Native implementation must model intentional deletion rights explicitly, transactionally and with tests.
- `src/db/repository.ts` contains the key CRUD and SQL queries; model/SQL parity must be verified by schema inspection and golden-record comparison, not just API signatures.
- Android `vault.enc` is written using `EncryptedFile` with `MasterKey` / Android Keystore and AES256_GCM_HKDF_4KB. The **vault login password is a UI lock, not the phone vault file decryption key**.
- A `GUDENC01` envelope exists for IndexedDB/browser fallback; its key material is not interchangeable with the native `EncryptedFile` key. A migration must inspect the actual store used on the installed device.
- Encrypted backups use `GUDVLT01` / `GUDVAULT` version 2, PBKDF2-SHA256 (600,000 iterations) and AES-GCM; key material is `GrowUrDhan\n{normalized-email}\n{password}`. The code also handles older `KOSHA1` backup envelopes. Preserve read support for supported legacy formats before any exporter is retired.
- Backup payload includes `VaultAccount`, family members, holdings, ledger events, demat/folio accounts, goals and `casLatest`; inspect for records **not covered** by the v2 backup (e.g. source documents, price history, Gmail seen state) before choosing backup-based migration.
- `GmailClient` encrypted preferences hold account identifiers and synchronization cursors; preserve user consent, but do not transfer stale OAuth tokens or silently enable Gmail.

## Principal review findings and migration blockers

| ID | Priority | Source evidence | Migration impact / decision |
| --- | --- | --- | --- |
| A-01 | P0 | `GrowUrDhanPlugin.kt`, `vault_blob_save`: existing `vault.enc` deleted before replacement write | Data-loss window; native storage must use two-phase write + fsync/atomic swap or transactional encrypted DB, with fault-injection tests |
| A-02 | P0 | `KoshaContext.tsx`, `restoreLockedBackup`: calls `wipeVaultCompletely()`, then multiple independent writes | Failure may leave partial restored book; native restore must stage, validate and atomically commit, with old vault retained |
| A-03 | P0 | `src/db/platform.ts`, `sqliteWasmDriver.ts`, `encryptedStore.ts` | Room alone is unencrypted; select/test actual encrypted SQLite strategy before implementation |
| A-04 | P0 | `schema.ts`, `repository.ts`, `KoshaContext.tsx` | Full schema/data semantics and append-only exceptions must survive native migration |
| A-05 | P0 | `casPython.ts` and `cas_android.py` contract | Freeze parser; preserve `cas_parse`, `cas_health`, JSON error/field contract; add independent validation and contract tests outside parser |
| A-06 | P0 | `gmailSync.ts`: parsing failure can be marked seen; filename/sender owner hints drive import | Silent missed statements and owner confusion; do not carry unsafe behavior into native port |
| A-07 | P0 | `GmailClient.kt`: `listSince` processes one history response with `maxResults=20` | Potential missed Gmail messages; design complete pagination and durable cursors |
| A-08 | P1 | `GmailClient.kt`: `forget` does not remove all account cursors; `httpGet` reads full response | Reconnect consistency and memory/DoS risk |
| A-09 | P1 | `statementMail.ts`: sender detection by substring of untrusted `From` text | Require structured mail identity signals and parsed-owner checks, not filename or raw sender text alone |
| A-10 | P1 | `GrowUrDhanPlugin.kt`: one customer-visible Google sign-in error contains a developer-specific email | Remove before new Play build; redact diagnostics |
| A-11 | P1 | `src-tauri/tauri.conf.json`: `versionCode: 2001` vs as-built document `2002` | Inspect actual latest Play artifact; new `versionCode` must be strictly higher than every published build |
| A-12 | P1 | `scripts/android_prepare.mjs`: modifies ephemeral generated Gradle/Manifest files | Replace with checked-in Android project and version catalogs; audit targetSdk, signing and installed package compatibility |
| A-13 | P1 | `.github/workflows/ci.yml`: runs lint/build/core/snapshot, but **not** `npm run test:db` | Restore database migration tests as mandatory release gate |
| A-14 | P1 | `.github/workflows/android-aab.yml`: release only `aarch64` target; `if-no-files-found: warn` | Validate full supported ABI coverage and require an AAB artifact; do not automatically publish from migration PR |
| A-15 | P1 | `src-tauri/keystore/gud-test.keystore` tracked despite `.gitignore` | Treat as nonproduction fixture; ensure no production key enters repo and separate debug/release identities |
| A-16 | P1 | `docs/PLAY-CONSOLE.md` and `docs/ANDROID.md` describe fewer products than `src/lib/license.ts` | Reconcile real live Play SKUs and existing entitlements before moving Billing to Kotlin |
| A-17 | P2 | `KoshaContext.tsx` approximately 2,050 lines, `GrowUrDhanPlugin.kt` approximately 1,083 lines | Decompose via contracts and ViewModels into testable use cases without changing semantics |
| A-18 | P1 | Android workflow for baseline commit reported failure with no job details in queried API; `Kosha CI` passed | Native build success is **not** established; investigate workflow triggering/configuration separately |

**Severity interpretation:** These are source-observed risks and migration blockers, not proof of successful exploitation. Some pre-existing Gmail findings require deeper end-to-end validation; none are marked remediated here.

## Deployment environment and facts requiring verification

- Package/application ID: `in.growurdhan.app`. Do not change on the production update track.
- Tauri config version: `0.2.0`, Android `versionCode 2001` on reviewed branch; source document previously specified `2002`.
- Current generated Android build targets API 36 via `scripts/android_prepare.mjs`; Gradle files are not wholly committed.
- Chaquopy integration uses plugin `17.0.0`, Python `3.12`, and pinned `pypdfium2==5.13.0` with `python-dateutil` and `typing-extensions` dependencies.
- `android-aab.yml` uses Node 22, JDK 17, Android API 36, NDK 27.2 and Rust targets; a new Compose project will require an independently versioned, reviewed Kotlin/AGP/JDK toolchain.
- `ci.yml` on starting commit `af96dcc8967a`: GitHub Actions run `37752674990` completed successfully (type check, frontend build, core and snapshot tests). **No emulator, CAS parser golden, database test or Android migration result was established by this CI run.**
- Android workflow run `37752673250` reported failure; returned job list was empty. Cause is **unconfirmed**; do not infer that Android compilation itself failed.
- Mobile OAuth: `gmail.readonly` is a restricted Google scope; production consent and scope verification must be completed separately.
- Both `GUD-Android` and `GUD-Android-Gmail` are read-only inputs during this project. All writes target `GUD-Android-ChatGPT`.

## ADR-001: Native target, staged release

**Decision:** Establish a Kotlin-first Gradle multi-module codebase under `android-native/`, as a separate build target. Preserve the existing hybrid build during migration. Introduce an in-app migration entry point only once read-only legacy extraction and durable native restore are proven. Do not replace the existing production artifact or remove Tauri until all parity and recovery gates pass.

**Rationale:** This yields testable incremental outcomes while protecting existing device vaults, billing entitlements and statement parsing.

**Unresolved decisions:** final encrypted SQLite driver; exact Keystore migration implementation; fallback IndexedDB treatment; published Play versionCode; Google OAuth production status; source-document/price-history preservation scope; native app minSdk/ABI policy; 16 KiB memory page/device compatibility. Resolve these with proof-of-concept and tests before implementation of data migration.

## Review exit conditions

1. Source inventory and blocker register are committed (this document).
2. Detailed native module plan, migration state machine and feature parity roadmap are committed.
3. Removal inventory lists precise legacy paths with objective deletion gates.
4. First implementation milestone is a separate, safe, reversible Kotlin/Compose scaffold **without existing-vault writes or parser changes**.
5. Do not claim device build or native functional verification until tooling runs them.
