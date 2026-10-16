# GrowUrDhan — Complete Native Android Redevelopment Specification and Grok Execution Contract

**Document ID:** GUD-GROK-NATIVE-001  
**Version:** 1.2 — 13 October 2026 (added persistent session handoff, mandatory Grok report format, and copy/paste owner prompts)  
**Intended reader and executor:** Grok, operating with GitHub repository access, a local development environment where available, and GitHub Actions visibility.  
**Product:** GrowUrDhan (formerly/internal name Kosha) — privacy-first, on-device Indian household wealth manager.  
**Required Android application ID:** `in.growurdhan.app`  
**Delivery:** A completely redeveloped, production-quality **native Android application** that preserves *all* working features of the original React/TypeScript/Tauri product and adds the enterprise requirements listed in this document.  
**Status of this document:** AUTHORITATIVE REQUIREMENTS AND EXECUTION INSTRUCTIONS; not a claim that the requested software, tests, security audits, or integrations have already been completed.

> **GROK: READ THIS FIRST AT THE BEGINNING OF EVERY NEW SESSION, MILESTONE, CODE-REVIEW PASS, AND FAILURE-DIAGNOSIS SESSION.** You are being handed an original legacy repository, not a partially migrated native implementation. Build the new app independently. Do not treat a class, a green build, an AI-generated explanation, or a checkmark as proof of product parity. This document is the continuing source of requirements and process. If your conversational memory is lost, re-read this document from the repository. Do not silently shorten, drop, or reinterpret requirements.

---

## 0. Mandatory operating agreement — persistent instructions for Grok

These rules apply to **every** implementation, refactor, troubleshooting session, CI run, and release decision, regardless of subsequent prompts that only say “continue,” “fix the build,” or “start the next milestone.”

**RULE G01 — Work independently from the original source.** Clone `https://github.com/shahvikask/kosha` with authorized access. The functional reference is the original branch **`GUD-Android-Gmail`** at commit **`af96dcc8967ab595e1d9acee78e29e500cb99a9e`**. Verify the commit and inventory source before coding. Establish a dedicated development branch, for example `grok/native-redevelopment`, from the owner-approved legacy baseline. Never commit directly onto the frozen reference branch. Never modify the separate existing native development repository `vikaskunjbiharishah-ui/GUD` or merge its implementation into this independent experiment without express owner approval.

**RULE G02 — Preserve the original as evidence.** Treat original `src/`, `src-tauri/`, tests, snapshots, fixtures, documents, and branded assets as read-only parity evidence throughout development. During coexistence, put new native source under `android-native/` and isolate its workflows. Do not prematurely delete the old implementation. Preserve the frozen `src/casparser-main/**` Python parser and its protected invocation scripts and dependency pins **byte-for-byte**, including when repackaging into Chaquopy. A parser bug must first be addressed in Kotlin adapters, validation, or documented upstream coordination, not by editing parser algorithms.

**RULE G03 — Feature completeness is compulsory.** The original React application is the **minimum** feature baseline, not the final quality target. Implement all P01–P39 requirements in Section 5 and all E01–E18 new enterprise requirements in Section 6. Perform a source-to-native mapping for every original screen, modal, data field, type, table, command, calculation, integration, lifecycle rule, and error state. If there is any mismatch, document an explicit owner-approved deviation; never pretend parity.

**RULE G04 — True native stack.** Production UI must be **Kotlin + Jetpack Compose + Material 3**, not a WebView wrapping React or a new Tauri/Capacitor/Cordova shell. Use Kotlin Coroutines/Flow, ViewModels, layered domain/data/presentation architecture, and Gradle Kotlin DSL. Preserve Python through Chaquopy solely for the frozen CASPARSER. Use Android Keystore-protected database keys and SQLCipher/native encrypted SQLite with thoroughly tested adapters. Do not select a cloud-first architecture or store financial books on a GrowUrDhan backend.

**RULE G05 — Financial integrity is non-negotiable.** Scaled-integer money and quantities, append-only money events, explicit corrections/supersession, idempotent imports, deterministic FIFO and cash-flow analysis, source/date-qualified prices, unknown-cost honesty, owner/account segregation and atomic persistence must never be compromised. Do not invent financial histories, acquisition costs, quotes, tax laws, CAS ownership, or purchase entitlements.

**RULE G06 — Always use an evidence-driven cycle.** For each milestone: audit reference → publish traceable checklist → design → implement entire scope → run local checks and unit tests → inspect `git diff` with AI review plus independent static tooling → run integration tests → commit a coherent milestone → deliberately dispatch the relevant GitHub Actions → **monitor the exact run** → diagnose any failure → repair source/tests → repeat validation on the **new exact source SHA** until green, or explicitly mark a genuine external blocker. Never mark PASSED for tests not run. Do not stop at “workflow triggered,” “queued,” “build produced an APK,” or “rerun started.”

**RULE G07 — Do not fake green.** Never delete/disable failing tests, loosen assertions, skip secure gates, hard-code expected financial results, change the tested SHA behind a workflow, or count an older successful run as a later commit’s verification. If a test itself is defective, prove the defect and fix the test without diluting behavioral coverage. A failing, skipped, cancelled, timed-out, or unobserved gate is not a passing gate.

**RULE G08 — Preserve CI budgets.** Local compilation, targeted JVM tests, Python/TypeScript golden tests and static checks precede paid Android emulator builds. Do not trigger expensive GitHub Actions on every WIP commit. Use deliberate `workflow_dispatch` with exact SHA, grouped milestone jobs and relevant reusable workflows. Keep coherent commits, squash experimental history only on **your own unshared development branch**, and avoid rewriting shared history. New source commits after a green run require new verification before acceptance.

**RULE G09 — Security first.** No secrets or personal financial data in Git, logs, CI artifacts, screenshots, telemetry, or external AI prompts. Use synthetic/anonymized tests. No plaintext persistent vault or exported SQLite image; no user secrets in saved Compose state. Enforce authorization at the data mutation boundary, not only in UI. Follow current mobile security guidance and commission independent AppSec review before release.

**RULE G10 — Honest external integrations.** Gmail OAuth, Google Cloud consent/restricted scopes, Play Billing products, signing keys, legal policies, store listing, genuine bank/statement samples, and real device access may require owner action. Build interfaces and safe offline behavior while blocked, then explicitly label provider/device tests BLOCKED—not PASS. Never simulate real entitlement verification or claim external integration works based on policy-unit tests.

**RULE G11 — Build monitoring is your responsibility when authorized.** Use your connected GitHub tool/plugin or `gh` to check workflow run state, jobs, logs, test reports and artifacts. After each change intended to repair CI, run the affected tests and check a newly dispatched, source-pinned workflow. Continue this **diagnose → fix → verify** loop until the milestone’s required automated checks pass; escalate only for genuine unresolved external prerequisites or bounded technical blockers and report evidence. Do not claim an unseen future result.

**RULE G12 — Be explicit about the stage of acceptance.** Distinguish `SOURCE MAPPED`, `IMPLEMENTED`, `LOCAL TESTED`, `CI JVM PASSED`, `CI EMULATOR PASSED`, `REAL DEVICE PASSED`, `SECURITY ACCEPTED`, and `OWNER ACCEPTED`. Physical-phone, cross-device, ARM64 CAS, Gmail consent and Play release signoffs can only be credited from actual evidence.

**RULE G13 — Rehydrate instructions across sessions.** At the first development commit, add `AGENTS.md` at the new branch root containing Rules G01–G15 (or a verbatim link to this committed document, plus the critical rules). Commit this complete specification under `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md`. Begin *every* later milestone and build repair by opening those files, the source-parity matrix and the current milestone’s issue. Maintain `docs/implementation/STATUS.md`, `docs/implementation/SESSION-HANDOFF.md`, and `docs/implementation/DECISION-LOG.md` across sessions. Never rely on Grok's chat memory alone; repository instructions are the durable memory.

**RULE G14 — Commercial design quality is a release gate, not decorative polish.** Re-read Section 6A before implementing or debugging **every** UI-facing feature. Preserve the approved original GrowUrDhan logo artwork and its proportions, navy/gold/teal brand identity and appropriate wordmark/tagline. Build the distinct “GrowUrDhan Heritage Modern Wealth” Material 3 system detailed below, not a generic banking clone, generic default green Material app, or derivative copy of a competitor. Deliver all contracted screen states; capture sanitized light/dark, compact/expanded and font-scaled evidence; run the G14 Design QA workflow and obtain owner visual acceptance. UI parity and UI beauty both matter, but must never conceal uncertain financial values or reduce security/accessibility. A screen is not DONE because it compiles or a screenshot exists.

**RULE G15 — Mandatory session reporting and continuation protocol.** Every new prompt, milestone, code review, or failed-build diagnosis MUST start by re-reading repository `AGENTS.md`, this committed specification and durable status/handoff files; verify remote/branch/HEAD and actual CI evidence; print the Section 17.3 startup receipt. Finish every substantial session with the full Section 17.4 delivery report, committed `SESSION-HANDOFF.md` and a ready-to-paste next prompt that mandates reading `AGENTS.md` again. A summary without required evidence does not complete a milestone. See Section 17 for the complete contract.

**Escalation discipline:** If a requirement conflicts with security, law, an unavailable service, a frozen component, or an undocumented product decision, implement all independent safe work, log the exact blocker, propose the minimal options and request owner approval only for the decision actually needed. Do not use an ambiguity as a reason to leave unrelated requirements unimplemented.

---

## 1. Mission, baseline, sources, and scope boundaries

### 1.1 Product mission

GrowUrDhan is a secure, offline-capable, family-centric Indian financial record book. It handles holdings and liabilities across many asset categories; family owners, accounts, folios, custodians and goals; manually entered money events; imported consolidated account statements; financial calculations; price/NAV snapshots; Gmail-assisted retrieval; protected backup/restore; and commercial licensing. The **user owns the records**. No GrowUrDhan-operated backend may collect or store household positions, PAN, account numbers, credentials, statement PDFs, or transaction histories.

### 1.2 Authoritative source precedence

For original product behavior, use this order:

1. **Actual original working implementation and tests** from `shahvikask/kosha`, `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`; pin/source-hash the reference. This is the strongest evidence of *as-built behavior*. Its React app runs via Tauri; the branch also includes Gmail behavior to inventory.
2. **`docs/GrowUrDhan-Software-Build-Document.md`** in the original repo — as-built architecture and API at `GUD-Android@95461c482bfdce65dbb2bbdd1315f73968fc43db`, dated 28 September 2026. This documents an **older branch**: investigate later Gmail-branch changes before assuming it is fully current.
3. **Original TypeScript contracts and tests:** `src/types.ts`, `src/db/schema.ts` (schema version **9**), `src/db/repository.ts`, `src/lib/*.ts`, `src/lib/*.test.ts`, `src/db/db.test.ts`, `src/App.tsx`, React screens/modals and Android bridge sources.
4. **This document's additional enterprise requirements, new native architecture and secure acceptance rules.** These may deliberately strengthen the legacy design. Record and test intentional differences.
5. Historical design documents are references, not overrides of the current source; `docs/KOSHA-ARCHITECTURE.md`, `KOSHA-DATA-MODEL.md` and `KOSHA-SECURITY.md` are older Phase 0 material.

For expanded requirements, this specification consolidates the 39-item feature register and M24–M34 redevelopment milestones previously tracked in [the master requirements issue](https://github.com/vikaskunjbiharishah-ui/GUD/issues/1). Grok **does not need write access to that other repository** to understand or execute this document. Do not copy its evolving implementation code; conduct an independent rebuild from the legacy source.

### 1.3 Required outcome and exclusions

**Required:** Android APK for internal/device testing, production-capable Android App Bundle (AAB) when signed inputs are available, complete documented source, a working local encrypted book, full feature parity, enterprise enhancements, reproducible builds, and exact-SHA verification evidence.

**Not required unless separately approved:** desktop/web production client, GrowUrDhan-hosted financial data service, cloud household accounts, migration of unpublished APKs through Play's automatic update mechanism, replacement of frozen Python CASPARSER, production Gmail verification without owner Cloud setup, production Play billing without product/signing setup.

**Important legacy-data interpretation:** The app is not yet a published Play production install. Nevertheless, **encrypted old `.kosha`/`GUDVLT01`/`KOSHA1` backup import and retention of original data semantics are product-parity requirements**. A direct in-place upgrade of unpublished debug APKs is optional; independently restorable old encrypted archives are not to be discarded without explicit owner acceptance. Never overwrite legacy source/device records silently.

### 1.4 Current versus target technology

| Layer | Legacy React/Tauri app | New native requirement |
|---|---|---|
| Screens | React 19, TypeScript, Vite 6, Tailwind 4 | Kotlin, Jetpack Compose, Material 3; no WebView for financial UI |
| Android host | Tauri 2 and Rust shell + Android plugin | Android `Application` and `MainActivity`, Kotlin navigation and services |
| Database | SQLite WebAssembly, snapshot stored as `vault.enc` via Android Keystore-backed encrypted file | SQLCipher/SQLite native encrypted database; Keystore-wrapped random DB key; indexed schema and migrations |
| Financial rules | TypeScript `src/lib/*` | Pure Kotlin domain modules and golden-test comparisons |
| Statement parsing | Python `casparser` through Chaquopy | **Exact same frozen Python parser** packaged with Chaquopy, Kotlin typed adapter around output |
| Price sources | NSE/BSE close and AMFI NAV | Official dated HTTPS adapters, local cache, provenance and stale/fallback UI |
| Authentication | App password, account identity, recovery questions, auto-lock | Secure app-specific identity/password plus local hardware trust/recovery; no weaker questions-only takeover |
| Billing | Google Play Billing/native plugin and SKU policies | Native Play Billing adapter, trust/acknowledgment/restore and entitlement lifecycle |
| Build | npm/Vite, Rust/Tauri, Android overlay | Gradle Kotlin DSL, AGP/Kotlin/Compose/Chaquopy compatibility-pinned tools |
| Tests | TypeScript domain/db + Android bridge workflows | Kotlin JVM, contract, SQLCipher integration, Compose/UI, Android instrumented, ARM64 hardware and AppSec |

**Historical clarification:** Legacy `vault.enc` is *not SQLCipher*. The legacy app password does not decrypt the on-device encrypted snapshot. Native redevelopment must not pretend otherwise or copy flawed assumptions into a migration reader.

---

## 2. Architecture and engineering contracts

### 2.1 Required module separation

Suggested source tree; actual module split may vary if justified, but dependency directions may not:

```text
android-native/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  gradle/wrapper/
  app/                       # Android entry point, manifest, navigation, packaging
  core/model/                # immutable domain IDs, money, quantities, contracts
  core/designsystem/         # approved logo, theme, typography, components
  core/security/             # Keystore/biometrics/password/session policy
  core/database/             # SQLCipher, migrations, DAO, append-only constraints
  core/parser-api/           # frozen CAS output contract; parser code external
  core/testing/              # synthetic fixtures, frozen clocks, test helpers
  domain/                    # pure Kotlin finance, ownership, portfolio use cases
  data/                      # repository, backup, prices, Gmail, billing, parser adapter
  feature/auth/
  feature/home/
  feature/holdings/
  feature/accounts/
  feature/family/
  feature/statements/
  feature/documents/
  feature/insights/
  feature/goals/
  feature/gmail/
  feature/backup/
  feature/settings/
  feature/licence/
  migration/                 # read-only legacy import, independent validation
  docs/
```

Architecture: **Compose UI → ViewModel/StateFlow → domain use case → repository port → SQLCipher/Keystore/Chaquopy/network adapter**. Domain modules must not require Android framework classes, UI toolkit, database APIs, or network libraries. Use Hilt or an equivalently testable dependency-injection framework, Kotlin Coroutines with structured cancellation, lifecycle-aware collection, explicit success/failure types, and injectable clock, dispatcher and transport.

Use a verified, reproducible AGP/Kotlin/Compose/Chaquopy/Gradle dependency matrix. Pin compatible versions and maintain Gradle wrapper checksums, dependency locking and SBOM. Do not blindly copy historical dependency versions if they are unsupported or vulnerable in the target build. Assess minSdk >=26, supported modern targetSdk, 64-bit device behavior, and Play's current 16-KiB/page-size and policy requirements before release.

### 2.2 Exact numerical invariants

* Currency/amounts use **signed scaled integers**, INR minor scale **10,000**, with overflow-checked operations.
* Quantity uses scale **100,000,000**, matching the source semantics; represent large/external values losslessly.
* Use integer/BigInteger/BigDecimal at rigorously defined boundaries. Do not persist financial amounts as `REAL`, `Double`, or locale-formatted strings.
* Money rounding and day/date/fiscal-year rules must match source golden vectors unless an intentional, versioned, owner-approved correction is made.
* Preserve true transaction dates; never insert made-up first buys, prices or cost basis to repair an incomplete portfolio.
* Cash flows for XIRR, realized/unrealized gains, tax estimations and allocation look-through must be deterministic with injected dates and quotes.
* Display financial-year assumptions, rate-as-of dates, legal-tax-rule version and **estimated/not-advice** labels. Do not hard-code historical small-savings rates as current future rates; maintain an approved versioned rules policy without silently revising posted events.

### 2.3 Encrypted relational data model and durability

Implement a versioned schema with at least these logical domains (schema v9 is the authoritative legacy inventory): identity/account/household entity, custody/folio account, financial instrument, holding, immutable `ledger_event`, lot projection, price observation, source document, import batch, individual statement position, snapshots and statement history, tax rule set, loan/liability, goal/tag links, nominee/guardian/ownership evidence, app settings, authentication metadata, safe egress/diagnostics audit, listed-security catalog, Gmail mailbox/link/cursor/dedup state, licence/entitlement state and encrypted backup format/version.

Preserve **all source fields** and unknown-but-valid metadata through explicit canonical columns, versioned typed extended data, or separately documented lossless extension tables. Each mapping needs a deterministic migration test; do not silently discard unrecognized fields. Use composite foreign keys and scoped uniqueness for member/account/folio/ISIN where relevant; define delete/reassign rules for guardians, family members and goals. Explicitly test referential integrity and transaction rollback.

`ledger_event` is append-only: database `UPDATE` and `DELETE` triggers reject mutations. Changes are represented through new correction/supersession events, not destructive rewrites. Import batches are idempotent by verified source fingerprint and event identity. Each repository money mutation rechecks authorized session state **inside** the same SQL transaction immediately before commit. Cancellations/crashes never leave half-imported books.

### 2.4 Security, local-only privacy and recovery

* Create a random SQLCipher database key, wrap it via Android Keystore, consider OS key invalidation, device lock change, biometric enrollment and hardware capabilities. Do not rely on app password as the Keystore data key.
* Implement app-account password/auth controls, 180-day rotation behavior if retained from source, secure recovery and cross-phone encrypted backup restoration. No security-questions-only bypass of the Keystore/device gate. If source recovery behavior conflicts with current secure design, clearly label the approved change and preserve user data recovery without downgrade.
* Bind authorized operations to the current session/challenge revision. After backgrounding, locking, OS re-authentication or operation supersession, reject stale parser/import/backup results. Wipe in-memory passwords and bytes after use. No saved-state secrets.
* Implement min/max parser/PDF limits, JSON type validation, malicious input defense, MIME and document-provider safety, SQL parameter binding, safe error redaction, backup zip-bomb prevention, bounded Gmail downloads and OS-compatible network TLS settings.
* Never log PAN, DOB, emails with ownership evidence, access tokens, full account numbers, PDF bytes, database keys, unlock secrets, backup passphrases, personal statements, or unmasked portfolio data to CI.
* Make diagnostics explicitly opt-in, locally reviewable, bounded and sanitized; allow user deletion. No unsolicited financial analytics upload.
* Provide consented, prominent warnings for emergency sheet/CSV/print exports because they deliberately create plaintext outside the encrypted vault.
* Support offline portfolio, edits, calculated values, and backups. Network may be used only for approved market updates, Gmail OAuth/sync, Play entitlement and user-directed destinations; document each destination and data egress.

### 2.5 Frozen CASPARSER bridge

`src/casparser-main/**`, `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py` and original parser dependency behavior are **protected**. Record SHA-256 for each frozen file before migration; CI must verify protected hashes. Use existing Chaquopy parser invocation and JSON response semantics, adding a typed Kotlin adapter and strict validator outside the parser. Test genuine PDF bytes (with consent) on a real ARM64 Android device later, and synthetic parser-shaped JSON in routine CI. Do not regard simulated JSON as proof that a real PDF can be parsed.

Use a two-phase safe import: parse and stage (read-only review) → verify document identity, member/folio/PRAN, chronology, completeness, source fingerprint, conflicts and user consent → atomic commit. `SNAPSHOT_ADJUSTMENT` is reserved for registrar/report reconciliation and must never become a guessed BUY. Failed or incomplete imports cannot delete or silently overwrite holdings.

---

## 3. Original product functionality — mandatory native screens and workflows

Preserve every navigation route, modal, validation, and state found in source. At minimum:

| Original screen/workflow | Native acceptance |
|---|---|
| First install / onboarding | Welcome, registration, terms/version consent, privacy explanation, account setup, trial/license gates, secure first vault creation and recovery preparation |
| Authentication and auto-lock | Name/email/PAN and DOB as appropriate, password, questions or secure equivalent, rotation, forgotten password, Android lock, process/background timeout; no data shown without proper admission |
| Home dashboard | Household assets, liabilities, net worth, invested, realized/unrealized, gain %, snapshot/price freshness, history/growth graphs, concentration, health and upcoming payments |
| Holdings | All 8 categories, asset-specific add/edit, subcategories, price/source fields, acquisition cost uncertainty, owner/tag/account filters, search, archived/closed holdings, detail and full ledger timeline |
| Accounts | Bank, demat, folio, AMC/RTA, broker, depository, DP/client/BO, PRAN and custodians; verified owner-account links, nominees; distinct same ISIN across families/accounts |
| Documents | File picker, password-protected CAS/CSV/TXT and supported statements, validation, review/approval, historical import batches, tax records, searchable records and explainable conflicts |
| Family | Primary profile, family members/relationships/HUF, guardian, joint ownership decisions, masked PAN, goals, family-wide filters, sync preferences, backup, diagnostics, emergency dossier |
| Manual records | Full 11-event semantics; user-allowed actions and registrar-only snapshot adjustment; validated accounting, correction/supersession and chronological FIFO outcomes |
| Returns, loans and tax | XIRR, allocation, cost, tax gains by FY, interest accrual and due dates, EMI states/repayments, income/events and honest estimates |
| Settings and commercial | Light/dark, privacy, auto-backup, official price options, SGB valuation choices, help, terms, feedback/rating, licenses, Play purchase/restore and security/backup management |
| Gmail statement automation | Member-selected inboxes, OAuth, download/classification/dedup, review, monthly approximate schedule, error/retry, disconnect and data retention |

The old route enum is `HOME`, `HOLDINGS`, `ACCOUNTS`, `DOCUMENTS`, `FAMILY`; Kotlin may improve navigation grouping but may not hide source functionality behind dead routes.

---

## 4. Original data categories, asset details, and event taxonomy

### 4.1 Exact eight top-level asset categories and supported subcategories

The original `src/types.ts` uses:

| Category ID | User-facing coverage / reference subcategories |
|---|---|
| `EQUITY` | Large cap; Mid cap; Small cap; Multi & flexi cap; Sector & thematic; Other |
| `MUTUAL_FUND` | Equity fund; Debt fund; Hybrid fund; Index / ETF; ELSS (tax saving); Other |
| `GOLD_SGB` | Sovereign Gold Bond; Physical gold (24K); Digital gold; Other |
| `FIXED_INCOME` | PPF; EPF; NPS; Sukanya Samriddhi (SSY); Bank FD; SCSS; NSC; KVP; POMIS; Post Office RD; RBI Floating Rate Bond; Bond / GOI; Other |
| `REAL_ESTATE` | Residential; Commercial; Land; REIT; InvIT; Other |
| `SPECIALISED` | PMS; AIF; Unlisted equity; ESOP; Other |
| `CASH_BANK` | Savings account; Current account; Liquid / overnight; Other |
| `LIABILITY` | Home loan; Car loan; Education loan; Personal loan; Other |

An empty position is valid when an account/holding has been enrolled but no events exist. Category design must support category-specific fields and validation, not just a single nickname/free-text asset form. Additional sanctioned subcategories may be additive but cannot silently rename or collapse source categories.

### 4.2 Preserve original `HoldingDetails` field contract

Native form, persistence, details, edit, backup, restore and statement mapping must preserve, where applicable:

`goldHoldMode`, `goldPurity`, `goldHallmark`, `earlyExitDate`, `annualContributionMinor`, `contributionOn`, `compounding`, `tdsApplicable`, `npsTier`, `npsCra`, `npsPfm`, `faceValueMinor`, `areaSqFt`, `ownership`, `ownershipPercent`, `aifCategory`, `commitmentMinor`, `capitalCalledMinor`, `ifsc`, `strategyName`, `custodian`, `originalTenorMonths`, `rateType`, `linkedHoldingId`, `mfPlan`, `mfOption`, `mfHeldAs`, `depository`, `dpId`, `clientId`, `boId`, `altHoldMode`, `monthlyIncomeMinor`, `incomeDay`, `incomeStart`, `incomeEnd`.

Also preserve the original `Holding` fields, including name, symbol/folio, ISIN, category, subcategory, owner, scaled quantity, average purchase cost, current price, price source/as-of/staleness, day change, review status, SGB benchmark/issue/coupon/maturity, loan rate/institution/masked account/EMI/status/remaining tenor, purchase date, 2018 grandfathering FMV, exposure nodes and wrapper, hybrid allocation splits, notes, location, goal/tag array, AMC, RTA, AMFI, advisor, scheme type, UCC, exchange, demat/DP and custody links. Cross-check **every field in current original `src/types.ts` and schema v9**, including fields added after the 2026-09-28 build document. Maintain a machine-readable full mapping.

### 4.3 Ledger events and permissions

Exactly these original event names must be recognized:

`OPENING_BALANCE`, `SNAPSHOT_ADJUSTMENT`, `BUY`, `SELL`, `BONUS`, `SPLIT`, `DIVIDEND`, `INTEREST`, `VALUATION_UPDATE`, `REPAYMENT`, `CORRECTION`.

User workflows may create the applicable 10 events, with validation and appropriate explanations. `SNAPSHOT_ADJUSTMENT` is a validated registrar reconciliation event, not an ordinary user trade. Preserve quantity, unit price, amount, charges, event date, source/import fingerprint, owner/holding, notes, `supersedesEventId` and creation time. Sale proceeds and realized P&L must account for fees in documented order; check lots and negative position rules. Historical edits create **new** events. A correction cannot erase the evidence trail. No statement may be converted into synthetic purchases simply to make cost look known.

---

## 5. Canonical complete feature-parity register P01–P39

Each ID is a **separate acceptance requirement**. Create `docs/implementation/parity-register.csv` with columns `ID, legacy_path, target_path, test_paths, owner, status, source_sha, tested_sha, evidence, deviation`. Each feature begins `NOT AUDITED` until Grok has inspected the actual source. Completion requires both working UI or documented non-UI behavior **and** persistent-state/invariant tests.

| ID | Required scope and acceptance contract | Primary legacy evidence | Planned milestone |
|---|---|---|---|
| **P01** | Secure app-account registration: email, name, masked/verified PAN if explicitly collected, DOB and original two security-question identity fields; correct first-run gates and consent; privacy-safe validation | `src/components/screens/AuthScreen.tsx`; `src/lib/auth.ts` | M25 |
| **P02** | Login, lock, background timeout, 180-day password aging/rotation, credential/session handling, forgotten-password recovery with secure equivalent, device/process recreation | `src/lib/autoLock.ts`; `src/context/KoshaContext.tsx`; `src/lib/auth.ts` | M25 |
| **P03** | Read old locked encrypted `.kosha` archives (`GUDVLT01`, historical `KOSHA1` as supported), verify authentication/identity, preview exact data, refuse tampered inputs, atomically restore to empty authorized native vault without silent loss | `src/lib/backup.ts`, `backupIo.ts`, original schema | M25 |
| **P04** | Lossless mapping from schema v9 entity/account/instrument/holding/ledger/snapshot/goals, all original relationships and metadata; native v1+ encrypted schema and migrations | `src/db/schema.ts`; `src/db/repository.ts` | M24 |
| **P05** | Eleven event types; signed-integer event math, fees, append-only corrections, immutable audit trail, exact identity and chronology | `src/types.ts`, ledger DB trigger, `KoshaContext.tsx` | M24/M27 |
| **P06** | Full family create/edit/authorized removal, primary cannot be casually removed, relationship/guardian/PAN/DOB/HUF/legal classification, ownership isolation | `src/components/screens/FamilyScreen.tsx` | M26 |
| **P07** | Account management: demat/folio/bank/other custody, issuer and custodian, owner, DP/client/BO identifiers, masked account references and nominees; edit/links/search | `src/components/screens/AccountsScreen.tsx`; `src/types.ts` | M26 |
| **P08** | Goals with target amount/date, priority, owner, goal-to-holding tags and drill-down; persistence, backup and cross-member rules | `FamilyScreen.tsx`; `src/types.ts::FamilyGoal` | M26 |
| **P09** | Eight exact asset categories with complete user-visible subcategories, search and sensible accessible taxonomy | `src/types.ts::ASSET_CATEGORIES/ASSET_SUBCATEGORIES` | M27 |
| **P10** | Add holding wizard with source-specific fields, quantity, acquisition date/value/cost, pricing and owner account, review-before-save, validation and empty-holding support | `src/components/modals/AddHoldingModal.tsx` | M27 |
| **P11** | SGB, gold, FD, PPF/SSY/EPF, NPS, REIT/InvIT, AIF, PMS, unlisted, property and loans support all original `HoldingDetails` fields and avoid dropped metadata | `src/types.ts::HoldingDetails`; `AddHoldingModal.tsx` | M24/M27 |
| **P12** | Full holding detail/read/edit/archive/close, all metadata, linked custody/account/goal/liability, custom dated valuation, explicit unknown cost and acquisition correction | `src/components/modals/HoldingDetailModal.tsx` | M27 |
| **P13** | User entry and validation for BUY/SELL/DIVIDEND/INTEREST/BONUS/REPAYMENT including fees, dates, amount scaling and atomically updated book | `src/components/modals/AddRecordModal.tsx` | M27 |
| **P14** | SPLIT/CORRECTION/OPENING_BALANCE/VALUATION_UPDATE with appropriate UI; supersession and zero-to-open-to-closed history; source-only `SNAPSHOT_ADJUSTMENT` | `src/types.ts::EventType`; `KoshaContext.tsx` | M27 |
| **P15** | All holdings, member/category/closed search/sort/filter, owner-lens consistency, exact event history and related accounts | `src/components/screens/HoldingsScreen.tsx` | M27 |
| **P16** | Exact FIFO cost and sale P&L, owner isolation, unknown-cost warning, taxable realization and source-to-Kotlin golden vectors | `src/lib/lots.ts`, `positions.ts`, `costBasis.ts` | M29 |
| **P17** | Documents screen full statement, tax and records sections, historical searches, navigation, record explanations, export/print | `src/components/screens/DocumentsScreen.tsx` | M28/M29 |
| **P18** | Import supported CAS PDF and CSV/TXT, passwords, live source bytes to parser, matched/staged review, approval, rejection, idempotency and incomplete parsing guard | `DocumentsScreen.tsx`; `src/lib/casParser.ts`, `casPython.ts` | M28 |
| **P19** | CAMS, KFINTECH, NSDL, CDSL and NPS/PRAN issuer identity mapping with real owner/folio checks, no email/name-only auto-match | `src/lib/statementMail.ts`; `src/types.ts` | M28 |
| **P20** | Statement/import batch history, snapshots and balances, reconciling earlier/later statements without rewinding positions, duplicate suppression and safe healing/cleanup | `src/lib/snapshot.ts`, `matchHolding.ts`, `KoshaContext.tsx` | M28 |
| **P21** | FY-specific gain classifications, STCG/LTCG, grandfathering, tax estimate report, tax-lot traceability and printable export; tax rules verified for target tax year | `src/lib/taxRules.ts`; `DocumentsScreen.tsx` | M29 |
| **P22** | EPF, PPF, SSY, SCSS, NSC, KVP, POMIS, FD, SGB and relevant schemes: calculated interest/coupons, due dates, published rates and idempotent catch-up | `src/lib/interest.ts`; holding forms | M29 |
| **P23** | EMI calculation, repayment schedule, paying/paused/stopped states, remaining installment/tenor, linked liabilities, no double catch-up | `src/lib/emi.ts`; `AddHoldingModal.tsx` | M29 |
| **P24** | Household/member XIRR, annual/absolute returns, income series, growth, realized/unrealized, asset allocation, hybrid look-through and exposure normalization | `src/lib/returns.ts`, `returnMetrics.ts`, `growth.ts`, `lookthrough.ts` | M29 |
| **P25** | Home graphics and books-health: historic assets/invested, movement since last statement, concentration, freshness, upcoming events, warnings, masked privacy and offline | `src/lib/homeDashboard.ts`; `HomeScreen.tsx` | M29/M31 |
| **P26** | NSE/BSE/AMFI security catalog/symbol/ISIN search and official close/NAV refresh; distinguish source dates and market close from live quotes | `src/lib/eodPrices.ts`; native Android bridge | M30 |
| **P27** | Dated price provenance, local offline cache, stale display and manual price override; no fabricated future prices or false real-time claims | `src/lib/eodPrices.ts`; `HoldingDetailModal.tsx` | M30 |
| **P28** | Encrypted backup export and restore with versioned format, password separation, authenticated bounds, cross-phone recovery and migration integrity | `src/lib/backup.ts`, `backupIo.ts` | M25/M31 |
| **P29** | User-controlled SAF backup folder, optional auto-backup on vault open/import, retention of last five (validate original source), failure warnings and restore test | `src/lib/backupSchedule.ts`; `FamilyScreen.tsx` | M31 |
| **P30** | Emergency family dossier/print/share/export with masked identifiers by default and explicit warning/consent for plaintext output; valid owner grouping | `src/components/modals/EmergencySheetModal.tsx` | M31 |
| **P31** | Settings parity: theme, privacy, SGB RBI-versus-market choice, IBJA input as supported, account settings, portfolio preferences and user controls | `FamilyScreen.tsx`; original context/settings | M31 |
| **P32** | Optional diagnostics with clear opt-in/out, retention/redaction and privacy; no raw PDF, PAN, password, credentials, private account values | Original Android plugin diagnostics; family settings | M31 |
| **P33** | Approved GrowUrDhan logo/color identity, Hindi onboarding if present, responsive native navigation, TalkBack, dark/light, scalable layouts, smooth and honest UI | `BrandLogo.tsx`; `OnboardingScreen.tsx`; original assets | M31 |
| **P34** | Gmail connect/disconnect/status, one or more OAuth accounts, bounded historical scanning, verified attachment discovery and durable duplicate tracking | `src/lib/gmailSync.ts`; Android `GmailClient.kt` | M32 |
| **P35** | Family-member to mailbox many-to-many mapping, shared/family inbox, PAN/PRAN/CAS ID matching, per-account cursors, quarantine/review and no cross-owner imports | `src/lib/statementMail.ts`; `FamilyScreen.tsx` | M32 |
| **P36** | Onboarding, versioned Terms of Use/Privacy acceptance, feedback and Play rating prompt, original timing/snooze semantics or documented upgrade | `OnboardingScreen.tsx`; `TermsGate.tsx`; `FeedbackPrompt.tsx` | M31/M33 |
| **P37** | 30-day trial and existing lifetime/Gmail/upgrade/support entitlement semantics and restore purchases; source SKUs `gud_lifetime`, `gud_gmail`, `gud_gmail_upgrade`, `gud_support_annual` | `src/lib/license.ts`; `LicenseScreen.tsx` | M33 |
| **P38** | Current Play Billing, acknowledgments, purchase integrity, signed AAB, package/version channel correctness, permissions, legal and policy checks | Android plugin/bridge, original release workflows | M33 |
| **P39** | Exact-SHA full regression, ARM64 genuine PDF parser, real phones/API coverage, restore across phones, mobile AppSec/penetration review, performance/accessibility, owner signoff | All original/native tests and release evidence | M34 |

**All source and current-original tests must be inspected for additional capabilities that this summary might not name.** This register is an acceptance floor, not a permit to ignore undocumented but working behavior. M24–M34 labels are maintained for requirement traceability, even though this Grok effort begins from the old codebase and develops its own new native tree.

---

## 6. New enterprise-grade requirements beyond the legacy application — E01–E18

These are **new or materially strengthened deliverables**, separate from preserving original behavior. Do not mark them implemented simply because the old app has a related feature.

| ID | New enterprise requirement | Verifiable acceptance |
|---|---|---|
| **E01** | Premium “Heritage Modern Wealth” Material Design 3 system (Section 6A) | Original artwork + documented exact brand token lineage; bespoke navy/gold/teal light/dark; complete Compose component library and design specs; owner-reviewed visual snapshots for every important flow; adaptive navigation, states, accessibility, G14 CI visual gate |
| **E02** | Accessibility and international-ready UX | TalkBack labels/order, 200% font scaling, minimum 48dp touch targets, tested WCAG contrast, reduced motion, readable INR grouping; Hindi onboarding where product baseline requires; no clipped financial values; Section 6A QA matrix |
| **E03** | Consistent family/household lens across every feature | One owner/group selection controls Home, holdings, accounts, documents, goals, tax, insights and exports; data never bleeds across owners |
| **E04** | Strong legal/beneficial ownership and custody evidence | HUF, guardians/minors, nominees, lawful owner per custody account, optional joint beneficial-share rules only after explicit owner-approved policy; no guessed fractional allocations |
| **E05** | Enterprise-grade secure recovery lifecycle | Keystore+SQLCipher; secure app password; single-use offline recovery and/or security-approved comparable mechanism; lost-device/cross-phone recovery; strict identity verified legacy encrypted backup migration |
| **E06** | Expanded statement issuer support with explanation | CAMS/KFINTECH/NSDL/CDSL/NPS and relevant PDF/CSV/TXT paths, source identity and confidence, categorized quarantine, review corrections, full import history and actionable diagnostics |
| **E07** | Multi-Gmail account household workflow | Many Gmail mailboxes ↔ many members; OAuth minimum scopes; revoked account behavior; durable cursors; approximate monthly WorkManager; manual sync, last success/error, retries and staged approval |
| **E08** | Source-qualified finance and uncertainty UX | Unknown cost, estimated value, stale quotes, unverified source, ownership conflicts and partial statement are visibly distinct; no numerical confidence theater |
| **E09** | Enhanced portfolio analytics | Member/category drill-down, investment vs liability separation, exposure concentration, goal progress, contributions/income/expense time series and comparable accurate financial golden tests |
| **E10** | Safe market-data adapters and provenance | NSE/BSE/AMFI official-source validation, as-of dates, offline cache, configurable policy and rate limits, no leak of PAN/holding/account data in requests |
| **E11** | Strong diagnostics and private support export | Consent-driven redacted logs, safe error IDs, export/delete controls, no PII, local health indicator and effective offline guidance |
| **E12** | Resilient source and encrypted backup formats | Incremental schema migrations, tamper/error detection, restore-on-empty safety, app-version compatibility, backup expiry/retention options, verified cross-phone import |
| **E13** | Enterprise onboarding and data-quality coaching | Education about offline storage, backup necessity, why unknown cost is not guessed, CAS mapping prerequisites, privacy; contextual warnings rather than silent automation |
| **E14** | Modular testability and maintainability | Pure financial domain, interfaces for network/parser/billing, deterministic fixtures, modern static analysis, typed error models, documented dependency graph |
| **E15** | Supply-chain/AppSec discipline | SBOM, pinned dependency graph, secret scan, dependency risk checks, OWASP MASVS-informed threat model, independent pen test and documented remediation |
| **E16** | Commercial licensing policy with fail-closed checks | Native Play Billing + acknowledgement + robust restore, controlled offline grace, trial and SKU policy, refund/revocation behavior; no local-only fake purchase verification |
| **E17** | Measurable quality and release pipeline | Exact-SHA manually dispatched CI gates, unit/golden/instrumentation/device evidence, standardized dashboard, production AAB signer verification, owner-controlled release |
| **E18** | Privacy-first implementation and architecture observability | Explicit network egress inventory, no household wealth backend, privacy-safe export audit, cold-start/error/performance metrics collected without personal financial content |

**Provider dependency policy:** E07 and E16 can be fully designed and unit-tested without Cloud/Play credentials, but cannot be called end-to-end complete until genuine OAuth/Play interactions are verified with owner-supplied service configuration. Release signoff may remain blocked even if all offline code is complete.

---

## 6A. Binding enterprise UI/UX design and visual-redesign acceptance contract — “Heritage Modern Wealth”

**Status:** Mandatory product specification, not aspirational art direction. Applies to **G14 rule**, **E01/E02/E03/E08/E09/E13**, original **P01–P39** screens, **M00, M26–M33** UI development, and **M34** owner acceptance. Grok must treat this section as an executable design system: source inventory, prototypes, composables, screenshot fixtures, accessibility audits, and actual navigation/integration behavior. **All numeric colors/dimensions below are initial design decisions that must be contrast-tested and visually approved; they are not permission to override the original logo or conceal data.**

### 6A.1 Original approved identity — verified exact assets, lockups, and brand palette

The original `GUD-Android-Gmail` commit includes **real approved artwork**, not just a vague name. **Reuse these files directly** rather than generating a new logo or tracing one from a screenshot:

| Original read-only path | Usage | Protection rule |
|---|---|---|
| `public/brand/logo-full.png` | Full official lockup and approved-brand reference | Preserve untouched archival copy; export native density assets without stretching or changing shape. |
| `public/brand/logo.png` | Compact original logo | Preserve; use only for legible native placements. |
| `public/brand/mark.png` | Square mark | Preserve aspect ratio, shape and inset. |
| `public/brand/mark.svg` | Vector artwork and exact source geometry | Compare native `VectorDrawable` or approved PNG fallback pixel-for-pixel at standardized scale. |
| `src/components/common/BrandLogo.tsx` | Original wordmark/tagline rules | Retain wordmark semantics: “Grow” + gold-underlined “Ur” + teal “Dhan”; official tagline “Private Indian Family Wealth & Records”. |
| `src/index.css` | Original theme palette and asset-category colors | Extract into documented native design tokens before changing visual treatment. |

**Approved mark identity:** navy rounded square/tile (`#07131F`), fine gold outline and gold rupee-like symbol (`#F5C14A`) with a small teal growth tip (`#2DD4BF`); the original `mark.svg` uses a `100 × 100` viewBox and `rx=22` outer tile corner. **Do not change the mark's geometry, meaning, symbol, letter spacing in baked assets, or proportions.** Preserve the original `mark.svg` SHA (`52fd0aa1a9262e6d2ba8ab431c2955c6df5c417c`) as a reference at initial audit; verify the checked-out source independently. PNG export/downsampling may be adapted for Android density, but color identity and shape may not be silently redesigned. For store/splash adaptive icons, respect Android masking and safe-zone requirements; use original mark with measured padding and verify all mask shapes. Android's system splash screen may temporarily show a centered mark; it must not render customer financial data.

**Source-identical brand values:** original dark design tokens include background `#07131F`, chrome `#0C1C2C`, card `#102536`, border `#1E3A4F`, primary text `#F4F7FB`, muted text `#9BB3C7`, primary accent `#F5C14A`, secondary accent `#2DD4BF`, positive `#34D399`, negative `#FB7185`, and warning `#FBBF24`. Original light tokens include background `#F6EFE3`, chrome `#FFFAF3`, surface `#FFFFFF`, border `#EADCC8`, text `#1C1917`, muted `#6B5E52`, amber/gold accent `#D97706`, and teal accent `#0F766E`. Grok shall preserve these original values in a `BrandSourceTokens` reference file, then derive accessibility-tested Material 3 semantic roles. The logo's **internal** original colors stay exact even if adapted **surrounding UI** uses accessible shades.

**Brand decision:** Adopt **GrowUrDhan Heritage Modern Wealth**: a warm, credible Indian family-wealth identity blending deep-ink/navy, restrained gold, teal, soft ivory and editorial financial typography. Feel: **calm + trustworthy + precise + private + premium**, not a high-frequency trading terminal, gaudy gold luxury imitation, casino-green P&L app, gimmicky 3D dashboard, or generic neobank template. Keep one consistent brand across app icon, splash, registration, dashboard, investment detail, forms, statements, settings, backups and Play graphics.

Before making a design change, Grok must commit `docs/design/BRAND-INVENTORY.md` with filenames, SHA-256 hashes, dimensions, extracted palette and screenshots for light/dark original screens (synthetic account only). Create `docs/design/BRAND-APPROVAL.md` containing a side-by-side **original official logo** vs **native rendered official logo**, a clear `PRESERVED/OWNER APPROVED` state, and documented image-size/mask changes. **Never replace the logo with generated AI art, a competitor's logo, an emoji or a randomly designed rupee icon.** If original raster assets lack an Android-specific size, generate only derivative exports from the approved files and preserve original sources.

### 6A.2 Ten curated design/product references and what GrowUrDhan should borrow (not copy)

These are **ten reference products**, **not** a scientifically ranked “top 10” or an award claim. Official product/help pages are linked so Grok can inspect **current** public screenshots and verify visual patterns at its own execution date. Where a product does something different from GrowUrDhan, import only the **abstract interaction principle**, not its underlying cloud-business model, logo, proprietary illustrations, exact chart layout, trademarked color system or trade execution workflows. Do not imply affiliation with any of the references.

| # | Reference and official design/product evidence | Inspiration to blend into GrowUrDhan | What not to copy |
|---|---|---|---|
| **1** | **[INDmoney — Family Net Worth](https://www.indmoney.com/networth/family-accounts)** | Clearly distinguish **All Family / one member** net-worth views; make assets and liabilities scannable by family lens; clean family-focused overview. | Cloud-linked account invitations/KYC, user data aggregation assumptions, identical interface or palette. |
| **2** | **[Groww — External mutual-fund dashboard](https://groww.in/help/mutual-funds/discoverable/why-should-i-import-my-external-funds-to-groww--36)** | Beginner-friendly holdings readability; easy invested/current/returns understanding; efficient route to allocation and performance. | Trading promotions, brokerage flows, encouragement to transact, or visual imitation. |
| **3** | **[Kuvera — Wealth overview](https://www.kuvera.in/)** | Reduced cognitive noise, calm investment information hierarchy, purposeful whitespace, few confident calls to action. | Proprietary recommendation/transaction onboarding and marketing treatment. |
| **4** | **[Zerodha Coin — Investment details](https://support.zerodha.com/category/mutual-funds/understanding-mutual-funds/getting-started-with-coin/articles/my-investment)** | Transparent data-rich instrument details: invested/current amount, XIRR, average/current NAV, units, folio and history; useful density without clutter. | Zerodha trade execution and exactly matching screen hierarchy/visual branding. |
| **5** | **[ET Money — Portfolio health](https://www.etmoney.com/portfolio-health)** | Explainable concentration and portfolio-health warnings; actionable but restrained risk summaries tied to evidence. | Unsupported scoring, financial advice or unverified investment recommendations. |
| **6** | **[MProfit — Family-office analytics](https://www.mprofit.in/family-offices/)** | Mature multi-asset/family grouping, tax and ledger evidence, detailed drilldowns suitable for family offices. | Professional trading/accounting features that lack supported GrowUrDhan data; unlicensed brand treatment. |
| **7** | **[Monarch Money — Customizable dashboard](https://help.monarch.com/hc/en-us/articles/360058127551-Customizing-Your-Dashboard)** | Modular cards, reorderable priority panels where useful, meaningful cash/wealth/goals hierarchy. | Cloud syncing, heavy customization burden, or duplicate dashboards. |
| **8** | **[Copilot Money — Dashboard](https://help.copilot.money/en/articles/6045480-dashboard-tab-overview)** | Delightful but data-legible analytic charts, precise time context, minimal distraction and progressive detail. | Fake predictive trendlines, finance gamification or copying proprietary visual assets. |
| **9** | **[YNAB — Goal tracking](https://www.ynab.com/features/goal-tracking)** | Visual progress toward verified targets; plain-language feedback about remaining amount and deadline. | Assuming a full budget/envelope accounting product exists; savings “on track” claims without valid calculation. |
| **10** | **[Wealthfront — Account and investment overview](https://www.wealthfront.com/)** | Calm editorial hierarchy, confidence-building space around important wealth figures, long-horizon explanation and account detail. | Automated investing, forecasts and advisory results not supported by GrowUrDhan. |

**The recommended blend, not ten competing styles:** ~35% INDmoney + MProfit **family/ownership and reliable wealth accounting**, ~25% Monarch + Wealthfront **premium clarity and card hierarchy**, ~20% Zerodha Coin + Groww **fast, legible holdings and transparent returns**, ~10% Copilot **controlled chart polish**, ~10% ET Money + YNAB + Kuvera **explainable health, goals and minimalist restraint**. These are **heuristic design weights**, not exact feature quotas or a mandate to copy any UI. The **overriding visual identity remains GrowUrDhan’s existing navy/gold/teal logo** and a native Material 3 system. All financial operations must remain data-correct and offline-first, regardless of what a reference app does.

**Material 3 is the implementation standard**, beyond those ten app inspirations. Review the current official [Material 3 in Compose guidance](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive navigation guidance](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation), and [Android Compose accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Confirm API/library compatibility before using newer components.

### 6A.3 Two fully authored visual themes, not a global dark-mode switch

Produce actual designed themes, with the **same semantic information hierarchy** and equivalent contrasts in both:

**Light: “Ivory Ledger”** — warm paper-like app background based on original `#F6EFE3`; clean `#FFFFFF` financial cards and `#FFFAF3` chrome; original ink text near `#1C1917`; refined amber/copper-gold for primary interactive accents (`#D97706` as a *candidate*, contrast-test its text role); teal `#0F766E` for secondary information and confirmation; quiet fine borders `#EADCC8`. The logo still contains its original navy/gold/teal artwork. Use background/surface contrast to organize, not excessive drop shadows.

**Dark: “Midnight Ledger”** — original inky navy `#07131F`; raised financial surfaces derived from `#102536`; chrome `#0C1C2C`; near-white text `#F4F7FB`; gold `#F5C14A` for priority actions and selected accents; teal `#2DD4BF` for confirmed/informational accents. Avoid all-pure-black where it destroys brand character, glowing neon gradients, glowing shadows and low-contrast dusty gold. Positive and negative figures must be understandable without color.

**Token implementation:** Put a reviewed `GrowUrDhanColorSchemes.kt` under `core/designsystem` with `lightColorScheme()` and `darkColorScheme()` covering `primary`, `onPrimary`, `primaryContainer`, `secondary`, `tertiary`, `background`, `surface`, `surfaceContainer*`, `onSurface`, `onSurfaceVariant`, `outline`, `error`, `onError`, `inverseSurface`, plus disabled/focus/overlay policies. Record how each role derives from the original colors; do **not** merely paste original web CSS hex into every composable. Add a stable brand default and an **opt-in** Android 12+ dynamic-color mode: dynamic wallpaper palettes must never mutate the official logo or erase financial chart semantics; if product owner prefers consistent premium branding, keep branded color mode as default. Android system light/dark mode, manual overrides and persisted preferences must work offline.

**Semantic palette rules:** `positive` = gain/correct/received, `negative` = loss/debit/overdue, `warning` = review/stale/incomplete, `information` = neutral explainers, `unknown` = incomplete evidence (neutral outlined treatment), `liability` = separately labeled asset/debt treatment; **red is not synonymous with “sell,” green is not synonymous with “buy.”** Never color all positive values teal/gold without secondary +/- icon/text/accessible label. Price provenance, tax estimate, statement freshness and unknown acquisition cost need distinct, repeatable chips. Chart series must have accessible names and differentiating line styles/patterns, not color only. Define light/dark category colors based on original `--bucket-*` values from `src/index.css`, verify contrast in both themes and preserve consistent category mapping everywhere.

### 6A.4 Exact foundation tokens and implementation choices

Initial compact-phone design baseline (adjust **only** when justified by screen evidence, device constraints or accessibility; token changes must be documented and re-snapshotted):

| Token family | Baseline proposal | Required behavior |
|---|---|---|
| Spacing | 4dp grid; `xs=4`, `sm=8`, `md=12`, `lg=16`, `xl=24`, `2xl=32`, `3xl=40` | Prefer 16–20dp screen edge gutters on compact handsets; no crowded forms. |
| Corner radius | chips 12dp; input 12–16dp; standard cards 16–20dp; hero cards 24dp; sheets 24dp top; pill full | Moderated curvature; do not round data tables into unreadability. |
| Elevation | mostly tonal surfaces and 1dp outline; subtle 1–3dp elevation for interactive surfaces | No huge shadows or glassmorphism; preserve contrast and low GPU cost. |
| Touch targets | minimum **48 × 48dp** for every actionable control | Include icon-only affordances, chart interactions, filters and dismissal controls. |
| Numeric formatting | tabular aligned digits; INR `₹`; Indian grouping `₹12,34,567.89` | Separate rounded overview from exact detail; no precision loss in underlying money. |
| Type hierarchy | display 30–34sp for hero wealth; screen title 24–28sp; card title 16–18sp; primary row 14–16sp; supporting 12–14sp | `sp` adapts to device font size; never fix height on rows containing money/labels. Verify 200% text; do not rely on ellipsizing critical values. |
| Typography | Licensed, legible contemporary sans. Start with Android system sans or compatible, bundled/approved `Noto Sans` for Hindi/Devanagari if required; avoid unlicensed premium fonts. | Consistent weights 400/500/600/700, readable numeral shapes, multiline support, sentence-case labels. |
| Motion | typical 120–220ms for non-financial microinteractions, strictly optional | Motion may clarify state but not animate fictional profits; honor reduced-motion / animator scale zero. |
| Iconography | cohesive Material Symbols / licensed vector icons, 20–24dp | No emoji as business icons, no mixture of line/filled icon sets without rule; accessible descriptions. |
| Imagery | official GrowUrDhan logo, simple native vector abstractions, charts tied to data | Never use stock photos, user PDFs or generated infographics as substitutes for financial records. |
| Charts | solid investment trend, clearly labeled legend, selectable time period, tooltip/date/₹, table equivalent | 0 values, missing dates, partial data and unknown cost have honest legends; chart color fixed by semantic/category identity. |
| State | `Loading`, `Empty`, `Ready`, `Partial/NeedsReview`, `Offline`, `Error`, `Locked` | Every destination must have a complete pattern; avoid fake dashboard placeholders or endless shimmer. |
| Layout | edge-to-edge handling, status/navigation bars, IME/insets, responsive `WindowSizeClass` | App content never hides behind the gesture area, keyboard or system bar. |

Use Material 3 `Scaffold`, `TopAppBar` variants, `NavigationBar`/`NavigationRail`, `Card`, `FilterChip`, `AssistChip`, `SnackbarHost`, `ModalBottomSheet`, `DatePicker`, text fields, dialog and progress indicators **where they suit the problem**. Use Compose immutable UI state and previewable components. A native Android financial interface must not be reconstructed in HTML, nor should it use a giant one-file composable for every screen. Keep large financial data displays virtualized (`LazyColumn`, stable keys, selective recomposition), and never expose real PAN/folios in previews. Use Android accessibility test APIs rather than assuming Material's built-in semantics cover custom charts.

**Reusable design-system components are mandatory**, at minimum:

- `GUDBrandMark`, `GUDBrandLockup`, `GUDPrivacyEyeToggle`, `GUDAppTopBar`, `GUDNavigation`, `GUDPageHeader`.
- `GUDNetWorthHero`, `GUDStatCard`, `GUDMoneyText`, `GUDDeltaLabel`, `GUDAssetLiabilityPair`, `GUDCategoryLegend`, `GUDAssetRow`, `GUDHoldingRow`.
- `GUDFamilyLensSelector`, `GUDAccountChip`, `GUDGoalProgress`, `GUDProvenanceChip`, `GUDStalenessBadge`, `GUDUnknownCostNotice`, `GUDDataHealthBanner`.
- `GUDTrendChart`, `GUDAllocationChart`, `GUDCashflowChart`, `GUDAccessibleChartTable` using tested financial projectors.
- `GUDFormField`, `GUDMoneyInput`, `GUDQuantityInput`, `GUDDateInput`, `GUDSearchField`, `GUDFilterBar`, `GUDReviewDiffRow`, `GUDConfirmedActionDialog`.
- `GUDEmptyState`, `GUDErrorState`, `GUDOfflineState`, `GUDLockedState`, `GUDLoadingState`, `GUDSectionHeader`, `GUDSecureSheet`.

Each component must have a documented contract (props/data types, semantic/accessibility meaning, default light/dark style, loading/empty/error states, sample Compose Preview, screenshot fixture and unit/UI test where appropriate). Domain computation belongs **outside** these components; composables render already-authorized, already-scaled data, not raw SQLite queries or price parsing.

### 6A.5 Navigation and information architecture: household-first, every old screen discoverable

**Recommended compact-phone top-level tabs (five):** **Home · Portfolio · Statements · Insights · Family**. Put `Accounts` in Portfolio as a clear first-class sub-tab/destination and in Family/owner drilldowns; `Goals` within Family and visible from Home/Insights; `Settings`, `Privacy`, `Backup`, `Gmail`, `Licence` and `Help` under an explicit toolbar profile/settings icon and/or a consistently labeled management section (not hidden behind an unexplained kebab menu). Original `Documents` functionality becomes Statements + Tax/Records in the proper destinations, while keeping **all** its old capabilities reachable in at most a logical short path. At M00 draw a route parity map of every legacy screen/modal and the exact new route: every existing action must remain discoverable and functional, not merely listed on paper.

- **Compact phones:** Material 3 bottom navigation for 4–5 high-priority destinations; selected destination labeled; scrollable main surface; no hidden horizontal tabs beyond reach; focused quick-add only where no destructive shortcut exists.
- **Tablets/foldables/landscape:** `NavigationSuiteScaffold` or equivalent adaptive Material 3 nav, appropriate rail or drawer; list-detail dual pane for holdings, accounts and statement review; no stretched 900dp-wide transaction forms. React to resizes without discarding unsaved, nonsensitive form work.
- **Owner lens:** Persistent (session-scoped, privacy-cleared) **All Family / individual / HUF** selector appears prominently on Home, Portfolio, Statements, Insights, Accounts and Goals. The same exact scope must be used by queries, totals, filters and exports; never recompute totals from an inconsistent set. Changing member clears detail views that no longer belong to the selected authorized scope.
- **Back/up behavior:** Correct Android Back, predictive back if supported, sheet dismissal without accidental money writes, nested destination return, external file picker cancellation, deep links only if authorized. All mutation previews require explicit review/confirm and live auth when needed.
- **Privacy:** Personal finance data hidden on the locked gate; optional hide-value eye icon toggles obfuscation consistently on dashboard, previews, switcher and exports. Do not leak through recent-app preview, notifications, title, crash UI, previews or screenshots. Screen-capture policy must be documented; if screenshots are restricted, have **test-only synthetic** controlled capture without bypassing production privacy.

### 6A.6 Canonical Home layout — immediate family net-worth clarity, not a generic stock feed

**Screen sketch (content order; adapt for compact vs expanded):**

```text
┌─────────────────────────────────────────┐
│ [approved logo] GrowUrDhan     [eye][⚙] │
│ Good morning                  [Family ▼] │
│ ┌─────────────────────────────────────┐ │
│ │ HOUSEHOLD NET WORTH   As of 12 Oct │ │
│ │ ₹1,24,56,780.00    [values hidden] │ │
│ │ Assets ₹…        Liabilities ₹…    │ │
│ │ [trend plot with explicit date]    │ │
│ └─────────────────────────────────────┘ │
│ [Allocation]    [Invested/Returns]      │
│ [Upcoming maturity/EMI]  [Goals]        │
│ [Portfolio health / review 2 items]    │
│ [Recorded holdings] [See all]          │
│ Home  Portfolio  Statements Insights Family│
└─────────────────────────────────────────┘
```

This is a **content schematic**, not copied competitor pixels. **Use test-only fictitious amounts; figures above are layout placeholders, never real reported data.** Primary: net worth with explicit **asset minus liability** derivation; `As of` date + source/freshness status if comparing observed valuations. Secondary: asset/category allocation, known invested value, gains and XIRR **only when supported by complete data**, family distribution and obligations. Third: actionable upcomings (FD/SGB maturity, EMI, statement review, missing backup) without fake urgency. Add a one-tap privacy eye; show explicit `Unknown cost` or `Estimated value` cards rather than making a zero/pseudo-profit. Financial hero values must never be fabricated from mock data in production. On tap a card navigates to a filtered ledger-/holding-backed detail; if data is absent or stale, use an explanatory empty/error state.

**Home states to design and screenshot:** first-run empty, zero holdings, multiple members, liabilities exceed assets, unknown cost, stale offline prices, large value (crores), zero/negative net worth, missing family owner, review pending, backup overdue, long names, values hidden, light/dark, text scaled 200%, tablet split screen. Avoid collapsing assets and liabilities into one green P&L signal. Optional dashboard-card rearrangement may be added once base hierarchy is flawless; do not let customization delay source parity.

### 6A.7 Complete screen-by-screen enterprise visual behavior

**A. Registration / unlock / recovery / terms / onboarding**
- Center approved official `logo-full` or `BrandLockup` appropriately with clear product identity and promise (“Private Indian Family Wealth & Records”), then one primary action per step; separate `create`, `unlock`, `recover`, `restore` journeys. No arbitrary cover photographs.
- Use progressive disclosure for legally sensitive identity fields; show why email/PAN/security factors may be requested and whether stored or masked; keyboard types, validation, privacy cues and password reveal control are native and accessible.
- Credential errors must not disclose internal cryptography or expose membership; cooldown and retry states are plain-language. The password and backup password have visibly distinct roles; after background lock, never flash prior balances.
- Onboarding introduces **offline vault**, **backup importance**, **family lens**, **CAS ownership review** and **uncertainty**, with skip where product policy allows; do not promise automatic Gmail or brokerage connection if unconfigured.

**B. Portfolio overview / all eight asset groups**
- Large readable assets/liabilities summary; family scope + category/owner/account/archived filters; search; sort by value, name, change or as-of date; view density switch only if supported safely.
- Category rows/cards for all **EQUITY, MUTUAL_FUND, GOLD_SGB, FIXED_INCOME, REAL_ESTATE, SPECIALISED, CASH_BANK, LIABILITY**, each with stable distinct semantic color, icon, count and value; liabilities never disguised as assets.
- Rows show instrument name, owner/account context, valuation, units if meaningful, known invested value or “Unknown cost”, absolute/percentage gain if computable, price source/date. Mask folio/account IDs by default. Preserve closed/archived history and quantity-zero assets.
- Use virtualized list, sticky optional headers, visible item count and deterministic search. Multi-asset holdings should not display nonsense placeholders (“0 units” for a property unless data model explicitly needs units).

**C. Holding details / asset-specific wizard / edit**
- Detail hero: name, class, owner, linked custody account, current value/source/as-of; segmented sections **Overview · History · Income/Tax · Details**, where applicable. A prominent `Add record` remains separate from metadata `Edit`; verified acquisition cost and estimated valuation look different. Exactly trace trades to immutable history with IDs available under secure drilldown.
- Wizard: category first → subcategory → identity/ownership → category-specific economic fields → initial value/acquisition known-vs-unknown → review/submit. Conditional forms for **SGB, physical/digital gold, MF direct/regular and IDCW/growth, demat vs folio, PPF/SSY/EPF, NPS Tier/CRA/PFM, FD/bond/SCSS, REIT/InvIT, AIF/PMS, real estate, bank/cash and loans**. Every original `HoldingDetails` field must be persisted, displayed and edit-safe. Do not show a 36-field undifferentiated form.
- When the user supplies purchase cost that conflicts with current value, show clear separate labels; missing purchase history remains `Unknown cost`. Archive/close actions require confirmation and a disclosure that ledger history is retained.

**D. Money event / transaction editor and timeline**
- Create BUY, SELL, OPENING_BALANCE, BONUS, SPLIT, DIVIDEND, INTEREST, REPAYMENT, VALUATION_UPDATE and CORRECTION with **type-specific fields**; `SNAPSHOT_ADJUSTMENT` is registrar-controlled. Show scaled quantity, INR price/charges/totals and computed preview without rounding away stored precision.
- Timeline groups chronologically with date, exact event type, signed effect, source (`manual`/statement), linked account and superseded/correction link. Distinguish dividend cash from buy/sell and valuation-only changes.
- Confirmed corrections are **new immutable ledger events**, never edits in place. Review includes impacts on holdings, FIFO lots and realized gains; fail closed on negative positions or cross-owner mismatches. No optimistic success display before DB commit.

**E. Accounts, custody, family, guardians, HUF and nominees**
- Account identity screen: institution/custodian, **masked** account/folio/DP/BO/PRAN, owner, holdings count, status and linked statements. Family screen: member avatar initials/relationship, ownership scope, personal assets/debts, HUF/entity labels, goals and nominees.
- Edit/nominee/guardian flows explicitly separate legally accountable owner from beneficiary and relationship. Never imply verified fractional joint ownership without an approved rule. Cross-owner attempt shows rejection and a safe next step; do not show unrelated member account metadata.
- Family context switch retains parent navigation but revalidates selection and suppresses unauthorized details.

**F. Statements, import, Gmail status and history**
- A native two-stage review-first experience: `Choose file` / `Gmail intake` → file metadata/issuer/owner → **parsed candidate summary** → grouped `New / Duplicate / Conflict / Incomplete` rows → explicit selected changes → auth/review confirmation → atomic outcome receipt.
- Show `File SHA prefix` as a privacy-safe identifier only if necessary, issuer/date/period, affected accounts, rows, unknown costs and warnings. **Never use “transaction imported” for a holding snapshot unless a real trade is proven**. Large statements use lazy lists; parse errors and cancelled pickers have clear retry flows; sensitive PDFs never become app-wide logs.
- Gmail view distinguishes **Connected / Permission needed / Syncing / Last success / Partial / Failed / Revoked** per mailbox and family link. Never imply sync is guaranteed exactly monthly; use manual sync and explicit status, not fake spinner success.

**G. Insights, tax, returns, cash flows and portfolio health**
- Insights hub has clear dated cards for **allocation, family/member wealth, XIRR, growth, income, gains, risk/concentration, goal progress, upcoming dues and capital-gains/financial-year reports**. Each figure has scope/time/financial method/source; detailed math stays in Kotlin domain and golden-tested.
- Charts: investment/value trend uses money-aware points; allocation donut or stacked bars with legends and readable remainder, plus an accessible table; risk warning reveals exact rationale; tax amount labeled **estimate** and fiscal-year rule version.
- Provide empty states for insufficient history; **no invented prior prices, return forecasts or “portfolio score 87/100”** unsupported by source and explainable methods. Downloads/print/tax dossier are privacy-gated and show redaction preview.

**H. Goals, maturity/EMI diary and household planning**
- Goal card displays owner, target and target date, documented current tracked allocation and progress; use exact integer projection only if financial model allows. `On track` is shown only with a testable defined methodology; otherwise `₹X of ₹Y tracked` and `₹Z remaining`.
- Upcoming list groups near-term event dates with correct handling of interest, SGB coupon, FD maturity and EMI; distinguish scheduled from recorded transactions and prevent double-posting.

**I. Settings, privacy, backups, diagnostics, licensing and help**
- Group settings into **Appearance**, **Security & login**, **Family & ownership**, **Backup & recovery**, **Statement sync**, **Prices & provenance**, **Diagnostics & privacy**, **Subscription & licence**, **Legal & help**. Make theme selector explicit `System / Light / Dark / optional Dynamic` with immediate previews.
- Backup/export UI must communicate encryption, destination, **last verified backup**, key separation, retention and an explicit restoration warning. Plaintext emergency dossier requires prominent consent and mask preview. Account/PAN masking defaults on.
- Trial/entitlement badges display verified information and purchase state; do not imply Play store recognition without trustworthy provider proof. Opt-in diagnostics show exactly what redacted fields leave the device, if anything, and can be turned off or cleared.

### 6A.8 Brand-safe graphics, data visualization, and motion behavior

1. **No false finance imagery:** every chart must map to recorded events or qualified observations. Unknown price dates create gaps/annotations; stale close/NAV never appears as live. Use reversible drilldown into underlying money records.
2. **Allocation:** eight base categories get source-derived category hues, consistent legend order, percentage and absolute value label, multi-member scope, accessible data table. When categories total 100% mathematically, distribute rounding remainder deterministically; no 99.9/100.1 misleading labels due to naive rounding. Separate debt from asset allocation or clearly explain signed treatment.
3. **Trend charts:** selectable 1M/3M/1Y/All only if actual data exists for that period. Label “portfolio value” vs “invested amount” vs “net worth” differently; do not use single line pretending the concepts are equal. Dates and INR are locale-aware; missing data gets an explicit gap.
4. **Return chips:** show +/− sign, time period, percentage and absolute amount when correct; no animated confetti for profits or alarming visual punishment for losses. Portfolio insights do not constitute buy/sell recommendations.
5. **Loading:** use neutral skeleton only for asynchronous UI, and avoid displaying fake balance values; prefer subtle progress indicator for parser/Gmail while offering cancel. Never shimmer in a way that reveals sensitive redacted rectangles during lock.
6. **Interaction polish:** smooth M3 screen transitions, responsive pressed/selected states, subtle and optional haptics after explicit user actions, in-place error messages anchored to invalid fields, meaningful confirmation receipts and cancellation without data loss.
7. **Motion/energy:** animation defaults short (often 120–220ms), disabled for accessibility preferences; charts do not continuously animate, rerender at network rate or induce rapid brightness/pulsing.
8. **Native quality:** no nonfunctional decorative buttons, no unlabeled icons, no dummy menu links, no forced landscape-only flow, no off-screen confirmation controls, no broken “Show more” pagination.

### 6A.9 Figma-equivalent source-controlled deliverables without requiring Figma

Grok may use a design tool if authorized, but the complete visual contract **must be reproducible from Git** and must not depend on the owner paying for Figma. Before broad implementation, commit:

1. `docs/design/BRAND-INVENTORY.md` and `docs/design/BRAND-APPROVAL.md` (original files/hex/SHA/usage and logo parity).
2. `docs/design/DESIGN-PRINCIPLES.md` (this 6A brief, selected inspirations, non-copying rationale and owner design decision log).
3. `docs/design/TOKENS.md` (light/dark palettes, semantic roles, type scales, spacing, corners, icons, content density, contrast measurements, motion).
4. `docs/design/NAVIGATION-SCREEN-MAP.md` (all original routes/modals/commands → native destination/sheet/action, plus five-tab proposal and tablet mapping).
5. `docs/design/SCREEN-SPECS.md` (all A–I screens above with hierarchy, content, empty/error/offline/locked states, fields, validation, navigation, as-of/provenance).
6. `docs/design/VISUAL-QA-MATRIX.csv` (screen × theme × viewport × font scale × state × screenshot path × test case × owner acceptance).
7. `android-native/core/designsystem/...` (actual reusable Compose source, theme preview, semantic chart palette, icon assets derived from originals).
8. `android-native/.../src/androidTest/...` UI screenshot, semantics, typography/inset tests and synthetic UI fixtures with deterministic fonts/clocks/prices/dates. Prefer existing-compatible tooling; document any screenshot test library decisions with licenses/versions.
9. `docs/implementation/STATUS.md` should contain **UI status separately from backend/financial feature status**, so a functioning repository class cannot be mistaken for a finished commercial screen.

**Do not create a fake static image of a screen and claim it is an interactive Compose implementation.** Every accepted screenshot must be captured from real Compose code (preview or running test), on a named test configuration, with synthetic amounts and a commit SHA.

### 6A.10 Required screenshot/visual regression and accessibility matrix — G14 gate

**Minimum screenshot matrix (synthetic data only):**

| Configuration | Essential screen/state coverage | Acceptance |
|---|---|---|
| Compact narrow phone (~360dp width), light | Login/restore, Home (full/empty), Portfolio/list/detail, asset wizard, trade review, Statements import review, Family, Insights, Settings | No clipping/overlap/horizontal scrolling, brand legible; touch targets work. |
| Typical phone (~412dp width), dark | All five tabs + full transaction/editor, account/folio, goals, loan/SGB/NPS detail, diagnostics and backup | Original logo legible; high contrast; correct money/status semantics. |
| Large font: **200%** | Login, hero net worth, holdings rows, long issuer names, form validation, transaction confirm, statement conflicts, modal and bottom nav | Financial values/critical controls never clipped; vertical scroll/reflow; TalkBack context correct. |
| Expanded tablet/foldable | Home, Portfolio list-detail, statement candidate-vs-diff, owner lens and rotation/multitasking | Rail/pane transitions maintain state and protect sensitive content. |
| Accessibility | TalkBack/keyboard/focus order, chart table, roles/semantics, high contrast, motion disabled | 48dp action targets, meaningful control names, color not sole status signal. |
| Privacy/offline/failure | Locked background, privacy eye hidden, no network prices, unknown cost, stale quotes, partial parse, cross-owner conflict, invalid backup and expired credential | No secret leak, no false success, actionable user guidance and preserved data. |
| Data scale | Crore-level values, negative net worth, 8 category series, 100+ holdings, repeated/duplicate events, 50-character fund names | Meaningful truncation/reflow and stable virtualization; no dropped rows/amounts. |

**G14 (`g14-design-visual-qa.yml`) acceptance actions:** Validate canonical logo resource hashes and approved derivations; run Compose screenshot baselines in **light and dark**, deterministic font/clock/value fixtures, a compact+expanded rendering set, accessibility UI tests (semantics, focus, touch targets, contrast audit including custom chart labels), image-diff thresholds plus owner-reviewed change summaries. The run reports exactly **which screenshot changed**, accepted baseline SHA, new source SHA and approval evidence. Screenshot pixel checks are indicators, not a license to approve visually poor layout. If visual baseline is updated, require documented reason, review and new owner acceptance; **never mass-update golden images merely to turn CI green**.

**Contrast objective:** WCAG-style text contrast **at least 4.5:1 for normal text and 3:1 for large text**; meaningful component boundaries/nontext graphics **at least 3:1** wherever applicable. Evaluate actual foreground/background pairs (including selected/disabled and alert/chips) with automated measurement; do not declare accessible only because M3 defaults are used. Color-blindness checks and TalkBack/manual test are separately required. Use at least 48dp touch targets, Android font scale 2.0, visible focus and no color-only chart meaning. Don't apply accessibility “fixes” by distorting the official logo; change its **surrounding surface** instead.

**Visual code-review rubric:** Score each area 0–5 with owner-review evidence and remediation for any score below 4: `Brand fidelity`, `Hierarchy and density`, `Navigation discoverability`, `Financial truthfulness`, `Dark/light finish`, `Forms and errors`, `Accessibility/legibility`, `Offline/privacy`, `Responsive adaptation`, `Motion/performance`. Scores do not replace mandatory objective pass/fail checks. Require **≥4/5 in each area** and **≥45/50 total** before owner design acceptance, unless owner explicitly approves a documented exception. Do not let an AI self-assigned score stand in for owner approval.

**Definition of UI done:** A screen passes **functional source parity + actual Compose interaction + correct authorized data + documented visual specification + screenshot pair and stress states + accessibility + G06/G14 CI on exact SHA + reviewed changes**. Device-specific owner visual signoff can be scheduled at M34, but the individual screen must be source/CI visual-reviewed during its development milestone. A polished picture with inert controls fails. A data-correct gray debug table with no design-system compliance also fails E01.

### 6A.11 Execution schedule, milestone integration and explicit memory reminder

- **M00:** Audit original brand/assets/CSS/screens; prepare approved logo exports and identity design; build both tokenized M3 themes and shared native preview components. Produce 2–3 **functional Compose** concept screens (Unlock, Home, Holding detail), with sample synthetic content and visual review before broadly cloning design across features.
- **M24/M25:** Reuse M3 forms and security language on account/recovery/backup screens. No generic unlabeled password screens; privacy/sensitive-state snapshots required.
- **M26/M27:** Complete family lens, account/custody UI and 8-category holding wizard/details/ledger with tokens and full review states; G14 scope covers those screens.
- **M28–M30:** Complete statement import/preview conflict UI, tax/XIRR/wealth visualization and price freshness; chart accessibility/integrity is mandatory.
- **M31:** Cross-app polish and theme/state/navigation audit, migrate any remaining legacy screens, resolve font/contrast/tablet issues and establish owner-approved screenshot baselines. M31 **does not** defer basic design work from earlier milestones.
- **M32/M33:** Style Gmail connection/consent, Play entitlement/terms appropriately; genuine provider state must be visible and cannot be mocked in production.
- **M34:** Run G06 + G14 + device visual acceptance and full accessibility/behavioral regression; do not authorize Play release with open brand, contrast, financial display or navigation P0 issues.

**SESSION REHYDRATION RULE FOR GROK:** Before starting any Compose screen, any visual bug diagnosis, any GitHub screenshot baseline update or any release-candidate test, open this **Section 6A**, verify the archived original `BrandLogo.tsx`/`public/brand` references and native `TOKENS.md`, list applicable screen-state acceptance checkboxes, then implement and verify. Re-read **Rule G14** each time, including “continue development” prompts. Copy the summary into the project-root `AGENTS.md` and keep its links current. **Do not allow later abbreviated milestone prompts to waive the approved logo, Material Design 3, high-quality redesign, the ten-reference originality rule or G14 visual verification.**

---

## 7. Detailed source-to-native domain migration contracts

Use this table to systematically port every original TypeScript financial/service module. Proposed native names are **illustrative**, not evidence of preexisting implementations.

| Source | Mandatory equivalent behavior / testing focus |
|---|---|
| `src/lib/money.ts` | scaled units, signed arithmetic, parsing and rounding, overflow, INR formatting |
| `src/lib/positions.ts` | event-ordered quantity/cost projection and corrections |
| `src/lib/lots.ts` | FIFO remaining parcels, fees, realized gains, split/bonus and partial sale |
| `src/lib/interest.ts` | EPF/PPF/SSY/FD/SGB/savings timing, leap dates, catch-up, published rates |
| `src/lib/emi.ts` | loan schedule, payment dates, pause/stop, exact remaining liability |
| `src/lib/taxRules.ts` | Indian FY and assessment-year rate tables, taxable classifications and grandfathering |
| `src/lib/returnMetrics.ts`, `returns.ts` | invested vs value, cash flows and XIRR, safe zero/negative edge cases |
| `src/lib/growth.ts` | cumulative/monthly trends, investment/earnings attribution and multi-member totals |
| `src/lib/lookthrough.ts` | wrapper exposure, equity/debt/hybrid slices that sum to exact totals |
| `src/lib/holdingForm.ts` | all 8 asset categories and specialized validation for SGB/NPS/REIT/AIF/property/loan |
| `src/lib/homeDashboard.ts` | book-health, source freshness, upcoming dates and concentration |
| `src/lib/costBasis.ts` | known vs unknown invested cost, immutable corrections |
| `src/lib/snapshot.ts`, `matchHolding.ts` | registrar observations, no guessed buys, chronology and duplicate detection |
| `src/lib/pan.ts`, `statementMail.ts` | verified owner/PAN/folio/PRAN match, ambiguous quarantine, sender-only evidence rejected |
| `src/lib/gmailSync.ts` | account connection, discovery, paging, cursors and revocation, no duplicate imports |
| `src/lib/eodPrices.ts` | dated official NSE/BSE/AMFI prices, symbol/ISIN mapping and offline cache |
| `src/lib/backup.ts`, `backupIo.ts`, `backupSchedule.ts` | authenticated exports, encrypted transfer, SAF, retention, failure warning, old format |
| `src/lib/auth.ts`, `autoLock.ts`, `sessionSecret.ts` | first install/auth/rotation/recovery/admission and secure session lifecycle |
| `src/lib/license.ts` | exact trial/legacy SKU paths, restore, revocation and subscription states |
| `src/lib/terms.ts`, `feedback.ts`, `onboarding.ts` | consent, review prompts, dismissed prompts and settings |
| `src/db/schema.ts`, `repository.ts` | entire schema-v9 contract, atomic DAOs, immutable ledger triggers and migration validation |
| `src/types.ts` | all immutable DTOs, holding details, events, family/account/goal types and import status |

**Golden test rules:** Freeze clock, timezone, tax-table revision, exchange rate (INR), price datasets and input fixtures. Require exact integer equality for money, quantities, dates, references and event counts. Floating analytical outputs (e.g. XIRR display) require a published tolerance and identical rounded UI result. Every unexpected mismatch is a bug or a separately approved design change; never quietly weaken reference fixtures.

---

## 8. Milestone plan, implementation sequence and exit conditions

Grok starts with legacy code rather than an existing native codebase. Execute **M00 foundation** and then the complete M24–M34 parity plan. You may divide large features into internal tasks, but a milestone is not accepted while its requirements remain unimplemented. Work across milestones as dependencies permit and batch expensive CI tests intentionally.

| Milestone | Required work | Exit evidence |
|---|---|---|
| **M00 — Legacy audit, freeze and native foundation** | Clone exact legacy baseline; inventory every screen/command/field; freeze parser hashes; create branch, `AGENTS.md`, this spec, parity CSV, Kotlin/Compose/SQLCipher/Chaquopy architecture, native scaffold, Section 6A brand inventory, exact approved-logo exports, Material 3 light/dark tokens and testable concept screens, G14 visual-test infrastructure | Source inventory + SHA, builds, basic Compose launch/lock screen, reviewed light/dark Compose previews, original-logo parity, dependency/security review and smoke tests |
| **M24 — Native schema and exact contracts** | Full legacy schema-v9 field map, SQLite encrypted native schema, accounts/holdings/events/snapshots/owner tables, source golden fixtures, reversible errors and migration strategy | DB migrations, trigger/foreign-key tests, exact type scaling and backup field retention golden suite |
| **M25 — Identity, vault, authentication and legacy restore** | App account create/login, password/recovery, OS gate, secure session, Keystore/SQLCipher, old `GUDVLT01/KOSHA1` import with identity and data verification, empty-target restore | Compiled native secure vault, tamper/rollback/replay tests, synthetic old backup restore and current-SHA emulator suite |
| **M26 — Family ownership, accounts, nominees and goals** | Household edit/delete, primary/HUF/minor/guardian, custodian/demat/folio/account, nominee, goals, owner lens across all destinations | Owner-scoped encrypted CRUD, backup roundtrip, navigation tests, no cross-owner mutation, ambiguity policy |
| **M27 — Full holdings and immutable money events** | Eight categories, all specialist forms/details, edit/archive/close, custom prices, all user ledger kinds and registrar-only adjustment, correction and FIFO lots | UI and persistence parity for all source holding fields, all transactions, zero/open/closed scenarios, golden + emulator |
| **M28 — Statements and import history** | CDSL/NSDL/CAMS/KFIN/NPS, PDF/CSV/TXT, parser adapter, ownership and chronology, preview/approve, historical batches, CAS/NPS account links | Frozen parser hash gates, synthetic contract tests, real ARM64 device test at final gate, idempotence and transactional recovery |
| **M29 — Financial engines, dashboard, tax and insights** | Complete source math port, interest/EMI, returns, gains and FY tax, look-through, growth, full home and insight UI | Complete TS-vs-Kotlin golden suite and mobile UI integration; no unexplained delta |
| **M30 — Official security/price catalog** | NSE/BSE/AMFI lookups and dated close/NAV, filters/overrides/freshness, offline cache | Mock HTTP/integration tests, recorded-source provenance, no stale-as-live display; real provider test when allowed |
| **M31 — Backup policy, settings, design, accessibility** | Scheduled encrypted SAF backups/restore, settings/diagnostics/privacy, emergency dossier, legal UX, approved logo, full Section 6A UI polish/theme, responsive navigation, accessibility, G14 screenshots | Restore retention tests, full 200% font/TalkBack navigation, contrast reports, owner-reviewed visual screenshot matrix/G14 exact-SHA pass and release-safe exports |
| **M32 — Gmail family sync** | Native multiple OAuth accounts, many-to-many member links, bounded scanning, WorkManager, staged review, errors/disconnect | Mock integration and credential lifecycle tests; real OAuth/consent test when owner supplies Google Cloud setup |
| **M33 — Play/licensing and commercial controls** | Trial/SKUs, current Billing, real entitlement decisions, terms/data safety, signed release pipeline, disclosure and legal docs | Billing test purchases, trust checks, signature/reproducibility report; owner production input required |
| **M34 — Final regression, AppSec, device and owner acceptance** | All P/E traceability, full exact-SHA CI, ARM64 genuine parser, new/old backups across phones, device/API matrix, offline/restart/low-storage, independent AppSec, legal/Play checks | Zero open P0, signed/minified AAB, actual physical-phone evidence, independent security findings disposition and owner release approval |

**Dependency rules:** M24 precedes secure data workflows; M25 precedes sensitive operations; M26 precedes owner-sensitive events and imports; M27 precedes complete analytics; M28 before Gmail automations; M29 before final dashboard; M31 and M25 precede release; M32/M33 require external owner credentials. Security/source checks run **throughout**, not just at M34. Owner may defer physical handset signoff to M34 but never omit it before commercialization.

---

## 9. Required GitHub branching, commit, review and run policy

1. Record baseline remote/branch/commit, e.g.:

   ```bash
   git clone https://github.com/shahvikask/kosha.git
   cd kosha
   git fetch --all --prune
   git switch --detach af96dcc8967ab595e1d9acee78e29e500cb99a9e
   git switch -c grok/native-redevelopment
   ```

   If branch already exists, fetch and verify its HEAD and permissions before using it. Never push to `GUD-Android-Gmail`, `GUD-Android` or the separate current native repository. Owner approval is needed before changing remote/branch protection or initiating destructive cleanup.

2. Save this document in the new branch under `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md`. Create the durable `AGENTS.md` pointer/instruction. Create one GitHub issue per milestone and one consolidated tracking issue. Use labels `milestone:Mxx`, `priority:P0/P1/P2`, `type:feature/bug/security`, and `gate:source/local/ci/device/owner`.
3. Use a **milestone integration branch or the agreed single Grok development branch**, not undocumented branches. WIP changes are freely staged locally; batch them into coherent commits. Prefer commit messages such as `feat(M27): complete holdings editor and immutable transaction coverage` or `fix(M25): bind vault restore to challenge revision`.
4. Perform AI-assisted `git diff` review **before** final milestone commit. Assess authorization bypass, source-field loss, insecure persistence, concurrency, integer overflows, errors, performance, user flows, labels and tests. AI review is an additional diagnostic aid, not a substitute for compiler/static/security tools or a human security audit.
5. Use local `git rebase -i` to squash/reorder only **unshared** WIP commits on a personal branch. Never rewrite the frozen source branch or shared protected commits. Do not squash away required milestone traceability or diagnostic evidence.
6. Create a PR/diff review with checked source paths, tests, screenshots, test matrix, open gaps and merge gate. Never merge red or unverified code by disabling workflow protection. Manual workarounds must be owner-approved and security-reviewed.
7. The exact source SHA tested by CI is the acceptance anchor. If subsequent source or test code changes, reopen the affected gate until a new exact-SHA successful run.

### 9.1 Recommended milestone commit checklist

- [ ] Open `AGENTS.md` and this document again; read milestone requirement IDs.
- [ ] Pin legacy ref SHA and verify frozen parser hashes.
- [ ] Identify and read all source screen/service/model/schema paths involved.
- [ ] Update feature parity CSV with precise source-to-native-to-test mapping.
- [ ] Implement all planned behavior; include error/empty/offline/cancel flows.
- [ ] Run local format/lint, Kotlin compile/JVM, reference TS golden and Python safety checks.
- [ ] Examine entire `git diff`, run AI/self review, fix severity HIGH/CRITICAL defects and untested path gaps.
- [ ] Commit logically; record the candidate HEAD SHA and exact tests invoked.
- [ ] Dispatch *only* required milestone GitHub workflows; monitor to terminal state.
- [ ] Investigate every failure, fix, rerun on new SHA and update reports. Do not declare green from an older SHA.
- [ ] Record any genuine external blocker and its owner action. Clearly distinguish automated acceptance from device/security/release acceptance.

---

## 10. GitHub Actions architecture — named, trackable workflows including visual acceptance

**Design goal:** Easy for the owner to see what passed, what failed, what was skipped, which milestone it covers and which **exact commit** was tested, while conserving GitHub Actions minutes. Maintain modular YAML under `.github/workflows/`. Reusable workflows (`workflow_call`) may be invoked by a single manually dispatched orchestration workflow; avoid costly duplicate jobs. Give every job and artifact a stable distinct gate ID.

### 10.1 Workflow catalog

| Gate | Recommended workflow file / UI title | Checks | Typical trigger and cost class |
|---|---|---|---|
| **G00** | `g00-source-guard.yml` — **G00 Frozen Sources** | Parser SHA baseline, legacy source branch integrity, protected paths, secrets in diff, reference manifest | Before source changes/PR/manual; LOW |
| **G01** | `g01-static-quality.yml` — **G01 Static & Compile** | Kotlin/Compose compiler, Kotlin formatting, Android Lint, Detekt where configured, Python Ruff/Bandit where appropriate, Gradle dependency sanity and source TypeScript `tsc --noEmit` | Local first; manual or lightweight PR; LOW/MED |
| **G02** | `g02-unit-tests.yml` — **G02 Domain Unit** | Kotlin JVM JUnit/MockK/coroutines tests, domain rules, auth policies, type adapters and original `npm test` / `npm run test:snap` / `npm run test:db` where runnable | Local + reusable manual; LOW/MED |
| **G03** | `g03-financial-parity.yml` — **G03 Financial Golden** | Deterministic TS-vs-Kotlin fixtures, exact integer outputs, XIRR tolerances, 11 event kinds, rates/FY, FIFO and source snapshots | Each financial milestone; MED |
| **G04** | `g04-vault-migration.yml` — **G04 Encrypted Storage** | SQLCipher open/close/rekey, schema migrations, triggers, foreign keys, tamper/rollback, source-v9 mapping, legacy locked backup fixtures | After data/auth changes; MED/HIGH emulator |
| **G05** | `g05-apk-aab-build.yml` — **G05 APK + AAB Compile** | Native debug APK, Android instrumentation APK, minified **unsigned or disposable-CI-key** release AAB, ABI/manifest, reproducible artifact metadata | Deliberate milestone gate; MED/HIGH |
| **G06** | `g06-compose-emulator.yml` — **G06 Compose Emulator** | API 35 representative Compose/navigation, auth/lock, holdings, family, backup, CAS review, UI smoke + screenshot regression | After UI integrated; HIGH |
| **G07** | `g07-parser-integrity.yml` — **G07 CAS Parser Contract** | Frozen parser hash, typed JSON adapter, malicious/synthetic fixtures, real ARM64 parser test if device runner exists (else **BLOCKED/NOT RUN**) | Parser changes or release gate; MED/HIGH |
| **G08** | `g08-appsec-supplychain.yml` — **G08 AppSec & Supply Chain** | Source authorization guards, vulnerability/dependency scan, SBOM, secret scanning, Android manifest/network/backup configuration, crypto/key invariants; preserve findings | Every security milestone and release; MED |
| **G09** | `g09-external-adapters.yml` — **G09 Market/Gmail/Play Contracts** | Mock HTTP/OAuth/Billing responses, retry/idempotence/entitlement policies and offline behavior; real external sandbox runs only when configured | Each external feature milestone; MED; provider real tests optionally BLOCKED |
| **G10** | `g10-compatibility-matrix.yml` — **G10 Android Matrix** | Android API 26, 30, 35, latest supported, ABI availability, process death, large data, storage pressure and restore on emulators; platform gaps listed | Scheduled/manual deliberate major releases; HIGH |
| **G11** | `g11-regression-release-candidate.yml` — **G11 Exact-SHA Full Regression** | Aggregate applicable G00–G10 **plus G14 for any UI scope** on exact `source_sha`; fail closed on missing mandatory gate; produce consolidated verified result. G13 reports downstream; G12 signing remains owner-protected and separate | **Manual dispatch** for final milestone candidate; HIGH |
| **G12** | `g12-production-signing.yml` — **G12 Owner-Approved Signed AAB** | Protected environment, exact owner-approved SHA, signer fingerprint check, versionCode/application ID, shrinker, SBOM, signed upload artifact, approval evidence; no silent Play publication | **Owner only/manual/protected**; HIGH |
| **G13** | `g13-report-index.yml` — **G13 Quality Dashboard** | Gather/publish redacted JUnit XML, code coverage, static findings, job/run URLs and acceptance JSON/Markdown; no credentials or PDFs | Reusable from gated runs; LOW |
| **G14** | `g14-design-visual-qa.yml` — **G14 Material 3 Visual & Accessibility** | Exact approved-logo preservation, Compose light/dark screenshot golden set, compact/expanded/200% text layouts, color contrast, TalkBack/semantics, design-review/owner signoff evidence (Section 6A) | Every UI milestone; lightweight local previews then deliberately dispatched screenshot/emulator stage; MED/HIGH |

**Practical implementation:** Use one `workflow_dispatch` entry `GrowUrDhan — Milestone Verification` with `source_sha`, `milestone` (M00/M24…M34), `test_level` (`quick`, `integrated`, `full`), optional `run_external` boolean (default false) and strict validation. This orchestrator calls the named reusable workflows for the appropriate scope. Keep separately visible individual workflow labels for troubleshooting, but do not run two copies of an expensive suite unnecessarily. A simple smoke stage must be available before a full emulator run.

### 10.2 Trigger policy and meaningful outcome labels

* **Local precommit:** lint/static, targeted unit and synthetic golden tests; no GitHub consumption.
* **Cheap PR checks:** `G00`, optionally lightweight portions of `G01/G02`, only after deliberate review (use paths filters to exclude unrelated docs). Do not auto-run emulator/AAB for each source push.
* **Manual milestone verification:** `workflow_dispatch` on requested source SHA; run targeted G00–G09 and **G14 for each UI milestone** with a clear milestone title (reusing G06 emulator capacity where practical).
* **Full integrated candidate:** manually dispatch G11 after all milestone code is committed; it must include G14 for UI-bearing scopes and fail if a required UI result is absent. G13 publishes the terminal summary; G12 production signing is a separate owner-approved step.
* **Nightly/weekly compatibility:** OPTIONAL and budget-controlled; if enabled, limit scheduled test suites and retain clearly marked SHA snapshots. Do not silently drain Actions credits.
* **Owner-signed release:** G12 only with owner-provided signing secrets and separate approval/environment protection; no auto-publish.

Every run should display an easily recognizable name, for example:  
`M27 • Holdings + Ledger • INTEGRATED • source 1a2b3c4`  
Job names: `G00 Source Guard`, `G01 Static & Compile`, …, `G13 Summary`, `G14 Material 3 Visual & Accessibility`.

GitHub workflow conclusions `success`, `failure`, `cancelled`, `timed_out`, `skipped`, `neutral` must be translated into a **truthful gate state** (`PASS`, `FAIL`, `CANCELLED`, `TIMEOUT`, `NOT_RUN`, `BLOCKED_EXTERNAL`). A dependent job skipped due to earlier failure is **not** PASS. The final summary should explicitly list all required jobs including ones never reached.

### 10.3 Exact source SHA enforcement

The workflow must verify that `source_sha` is a **full 40-hex commit ID**, exists in the approved repository and belongs to the authorized Grok development branch or reviewed PR history. Print and save `git rev-parse HEAD`, checked-out ref, Gradle lockfile hash, original reference SHA, parser-manifest SHA and test-run ID. Use `actions/checkout` with the exact `source_sha`; fail if `git rev-parse HEAD` differs. Do not test the drifting tip of a branch while labeling the results as an earlier candidate.

Tests are keyed by actual **tested SHA**, not by the commit that updated a workflow trigger marker. For security, evaluate untrusted pull-request code with restricted tokens and without production secrets. Never grant `pull_request_target` untrusted code execution with write credentials. Pin or version-control third-party GitHub Actions and review their privileges.

### 10.4 Artifacts and one-screen run dashboard

At minimum upload redacted, appropriately retained:

* `verification-summary.md` — milestone, exact SHA, overall PASS/FAIL/BLOCKED and **all G00–G14 statuses**, explicitly distinguishing `PASS`, `NOT_REQUIRED`, `NOT_RUN`, `BLOCKED`, and `FAIL`, with run/job URL; G12 signing must not falsely be scored as passing before owner signoff.
* `verification-manifest.json` — machine-readable `source_sha`, `legacy_source_sha`, `milestone`, timestamps, Android/Gradle versions, test counts, per-gate `status`, `artifact_sha256`, and declared external blockers.
* `junit-results-*.xml`, coverage reports, Android Lint/Detekt/security reports and SARIF when appropriate.
* `debug-apk-sha256.txt`, `release-aab-sha256.txt`, APK/AAB artifacts using disposable test keys only, proguard/mapping safely restricted as needed.
* `screenshots-synthetic/` from sanitized Compose previews/screenshot testing, with Section 6A G14 visual QA matrix, baseline/new exact SHA, light/dark/viewport/font cases and owner-review notes (never real statements or private balances).
* `parser-hash-manifest.txt`, dependency-lock/SBOM, no raw PDFs, PAN, secret values or auth tokens.

Configure artifact retention explicitly within organization policy (e.g. short retention for debug outputs and longer retention for approved release evidence). Release signer or owner secrets are never uploaded; protect `mapping.txt`, SBOM disclosures and security findings as appropriate. An **aggregate table must distinguish test run vs device signoff**.

### 10.5 Minimal workflow implementation contract (pseudocode)

```yaml
# Conceptual orchestrator — Grok must validate complete YAML and workflow_call wiring.
name: GrowUrDhan - Milestone Verification
run-name: "${{ inputs.milestone }} | ${{ inputs.test_level }} | ${{ inputs.source_sha }}"
on:
  workflow_dispatch:
    inputs:
      source_sha:
        description: 'Exact 40-character tested commit SHA'
        required: true
        type: string
      milestone:
        description: 'Milestone identifier (M00, M24 ... M34)'
        required: true
        type: string
      test_level:
        description: 'quick / integrated / full'
        type: choice
        options: [quick, integrated, full]
        default: integrated
      run_external:
        description: 'Owner-approved integration credentials/config available'
        type: boolean
        default: false
permissions:
  contents: read
  actions: read
concurrency:
  group: "gud-${{ inputs.milestone }}-${{ inputs.source_sha }}"
  cancel-in-progress: false
# Required implementation: validate SHA/branch, run applicable G00..G10
# and G14 for UI scopes, gather artifacts, fail closed on missing/failed
# mandatory jobs; then G13 writes machine-readable summary. G12 owner
# signing is independent. Do not just copy this
# pseudocode and claim a working workflow.
```

Grok must commit actual **working** workflows with defined reusable job names and dependency conditions, validate YAML, check triggers on GitHub, and ensure the summary runs with `if: always()` so failed jobs remain visible. The master summary must **fail** if any mandatory automation gate was skipped or lacks an attested result.

---

## 11. Grok autonomous CI diagnosis, fixes and terminal monitoring

This section is an executable working policy for **every** workflow run that Grok initiates. It is especially important when Grok accesses GitHub via its own plugin.

### 11.1 Monitoring state machine

1. **Preflight:** record candidate commit SHA, branch, milestone, workflows required, local test results, original baseline and parser hashes. Confirm it is a complete logical milestone candidate, not an unreviewed WIP.
2. **Dispatch:** trigger the correct manually selected GitHub Actions workflow with the **exact source SHA** and test level; record `run_id` and workflow URL immediately. If plugin cannot dispatch, use authenticated `gh workflow run` or clearly state the tool limitation; do not fabricate the run.
3. **Observe:** read workflow status `queued` → `in_progress` → terminal (`completed` with success/failure/cancelled/timed_out`). Query jobs/steps, test reports and uploaded artifacts. Track which expected gates were not reached. Waiting for GitHub requires actual rechecks, not a promise that it will eventually be inspected. Respect API rate limits and owner Actions budget.
4. **If SUCCESS:** verify *all required jobs* passed, absence of skipped mandatory suites, source SHA exactness, expected test totals, artifacts present, and successful build output matching requested package ID. Only then mark automated acceptance green.
5. **If FAILURE:** classify the **first real root cause** (source syntax/compile, bad Gradle/SDK tooling, test assertion, runtime crash, SQLCipher/Keystore migration, parser ABI/missing dependency, security finding, rate-limit/network flake, GitHub runner issue, unavailable external credentials, etc.). Download and inspect the failing step logs and JUnit XML. Identify smallest reproducible local case.
6. **Repair:** change production code when behavior is defective; change a test only after proving the test assumption is incorrect. Run the focused local test, plus related regressions; inspect the diff, lint and secure invariants. Make an explicit `fix(Mxx)` commit with issue/RCA reference. Compute **new SHA**.
7. **Verify:** dispatch a **new source-SHA-pinned** relevant workflow. Monitor until terminal. Rerun jobs on same SHA only for proven transient runner/network failures; a code fix must always be tested at the new SHA. Repeat steps 3–7 until the required automated suite is fully green.
8. **Blocker discipline:** if a real blocker is outside Grok's control (owner OAuth consent, Play secrets, signing, unavailable genuine phone or persistent GitHub service outage), publish `BLOCKED_EXTERNAL` with exact failure, evidence, owner action, and successfully tested unaffected scopes. **Never report a blocked suite as successful.** If repeated failures have no credible RCA, stop wasteful paid retries, escalate with logs and planned diagnostics instead of continuing an unbounded blind retry loop.
9. **Close milestone only with evidence:** update `STATUS.md`, parity CSV, milestone issue, tested SHA, workflow URL and tests. Record remaining device/external/release blockers separately. Do not claim product completion merely because the automated pipeline is green.

### 11.2 Failure diagnosis checklist by class

| Failure | What Grok must inspect before modifying code |
|---|---|
| Kotlin/Compose compile | Exact compiler diagnostics, nullability/smart-cast, source-set/module imports, dependency/AGP/Kotlin/Compose versions, generated Hilt/KSP code |
| Test failed | JUnit XML, stack trace, assertion expectation, synthetic fixture, identical local reproduction, any accidental test-only mismatch |
| SQLCipher/migration | Schema version, table/column constraints, cipher open key, migration transaction, foreign keys, triggers, rollback and cross-version backup vectors |
| Authentication/security | Keystore state, challenge revision, operation lifecycle, current credentials, app/user gate, cancellation, repository commit authorization and replay cases |
| CAS import | Frozen parser hashes, Chaquopy ABI/interpreter/libs, typed adapter, input bytes SHA, owner/folio/PRAN map, incomplete output, Android ARM64 integration |
| UI automation | Semantics tags, duplicate visible text, animation/idle state, lifecycle, density/font scale, keyboard/file chooser and accessibility focus |
| Pricing/Gmail/Play | Mock vs real source, token scopes, HTTP status, pagination, throttling, signature/entitlement trust, consent product configuration; no fake production claims |
| Packaging/signing | Manifest, bundle ABI, Chaquopy shared libs, Proguard/R8 keep rules, 16KiB compatibility, signing fingerprints, AAB packaging and tested SHA |
| CI environment | Runner image, Java/SDK version, cache integrity, workflow permissions, Actions minutes and external service outage; distinguish flaky infra from deterministic code defect |

### 11.3 Required failure report format

For every failed or blocked run, write a short record:

```text
MILESTONE: Mxx
EXPECTED GATES: G00, G01, ...
SOURCE SHA TESTED: <40 hex>
GITHUB RUN: <real URL and numeric run ID>
TERMINAL OUTCOME: FAILURE / CANCELLED / TIMEOUT / BLOCKED_EXTERNAL
FIRST ROOT CAUSE: <step, file, line, minimal repro and evidence>
AFFECTED SCOPE: <features/requirements>
FIX COMMIT: <new SHA> or NONE
LOCAL VERIFICATION: <commands and actual pass/fail counts>
NEXT GITHUB RUN: <new real URL> or NOT RUN
CURRENT ACCEPTANCE: <specific gates passed, failed, blocked, not run>
```

Do not use phrases such as “fixed, probably”, “everything works”, or “all regression tests passed” without links and actual exact-SHA evidence. An APK/AAB compilation success is **not** proof that finance, login, restore, CAS parser or device runtime works.

---

## 12. Required automated unit, static, integration and security tests

### 12.1 Unit/golden tests required on every relevant milestone

* Scaled integer parsing/rounding/overflow/negative cash flows and large values.
* BUY+BUY+SELL, fees, FIFO, partial disposal, dividends, interest, bonus, split, opening/corrections, realized/unrealized, unknown-cost warnings and stable historical ordering.
* Date edges: leap years, Indian FY boundaries, maturity, SGB coupon, EMI end dates, stale source dates and rate version changes.
* Multi-member same ISIN/different account and folio; HUF/guardian/nominee/goal changes and deliberately invalid cross-owner writes.
* Bank/savings/PPF/EPF/SSY/SCSS/NPS, SGB, FD, RBI bonds, loans, REIT/InvIT/AIF, property and cash with exact source-relevant calculations.
* Case-folded PAN, validated PRAN/folio/ISIN and ambiguous owner matches.
* Reimport same statement hash twice, older-than-last source snapshot, incomplete/zero units, false duplicate and rollback after each checkpoint.
* Source-v9 metadata/extended HoldingDetails fields, untouched ledger, import history, credentials, accounts, goals and price dates survive native backup roundtrip.
* XIRR, ROI, look-through weights, allocation totals, grandfathering date/FY and assessed tax disclosures.
* Authentication lock/unlock/rotation/recovery after process death, OS challenge changes, stale auth attempts and keyboard/input edge cases.
* Gmail sync cursors >20 messages, pagination, multiple accounts/shared inboxes, retries and disconnect/reconnect without cross-user import.
* Trial/entitlement states, purchase pending/acknowledged/refunded/unverified, offline grace and restore; no locally forged entitlement acceptance.
* SAF grant revocation/cancellation, slow backup encryption, corrupted/old encrypted file, incorrect password, Keystore invalidation and second-phone restore.

### 12.2 Static quality and AI-review checks

Run Kotlin formatter/ktlint or equivalent; Detekt and Android Lint; Gradle dependency verification and known-vulnerability checks; TypeScript `tsc --noEmit` and source regression tests for comparison; Python lint/type/security checks where applicable **without modifying frozen parser files**. Use SonarQube or equivalent only if configured and economical; it supplements, not replaces, Kotlin/Android-native analysis. Review `git diff --check`, API permissions, SQL queries, Android exported components, backup settings, unused code and licenses. Grok must perform a **documented AI-assisted diff review** on its own work, describe findings and fix them before the gate; an AI review comment alone is not testing evidence.

### 12.3 Integration and smoke tests

* First install to registration to unlock to Home; background/lock kills visibility and refuses old operations.
* Add family, account, zero-unit holding, BUY, SELL, corrected event and archive; reopen/restart and recompute exact book.
* CAS select PDF → typed parser result → validated staging → explicit review → atomic immutable commit → idempotent repeat.
* Multiple simultaneous owner/ISIN/folio accounts, NPS matching, ignored conflicting inputs.
* Backup document picker → encrypted export → app restart → empty-device restore → identical DB hashes/row counts/financial totals (where canonical hashes apply).
* Offline airplane mode: view/edit/compute/restore without server, quote freshness warning, no crash or silent destructive sync.
* Adaptive UI/navigation under Android Back, dark/light, screen rotation/process death, large fonts, TalkBack and low available storage.
* Staging Gmail results and tested OAuth revocation; mocked network errors never corrupt the book.
* Licence trial/purchase restore/lost network paths; no bypass or unverified Premium authorization.
* Production release build launches with R8/minify, embeds Chaquopy correctly, uses actual app ID and no debug/diagnostic backdoors.

### 12.4 Mandatory human/device/security gates

Automated tests cannot establish a finished commercial app. **M34 requires** physical ARM64 Android devices (minimum supported API and modern API), sample actual *consented or synthetic* PDF statements, installation and upgrade as applicable, lost phone/new phone restoration, Keystore failures, offline/low storage, real accessibility testing, signer acceptance, dependency risk review, independent OWASP MASVS-aligned application security testing and owner acceptance. Log security findings by severity, actual remediation commits and retests. Any unaccepted P0 defect blocks publication.

---

## 13. Gmail, official market data and Play commercial integrations

### 13.1 Gmail and household statement automation

Use actual Google OAuth on device, least-privilege scopes and securely maintained tokens; never request Gmail password. Recognize that Gmail restricted scopes may require Google verification and an external security assessment. Model `GmailAccount` separately from a `FamilyMember`; use many-to-many `MemberMailboxLink`. Implement one shared inbox serving multiple family members and one family member with multiple inboxes. Bound pagination, message/attachment size, failures and retries. Store durable per-mailbox cursors and SHA-256 attachment fingerprints locally. Classify file type and issuer but never trust email sender/filename alone for final investor matching. Verify the statement content's enrolled PAN/CAS ID/folio/PRAN/ISIN relationship. Quarantine ambiguous or incomplete rows for explicit human approval. Never auto-post guessed purchases. Offer `Sync now`, last status, disconnect/revoke, and WorkManager approximate monthly run with eventual catch-up; do not claim exact monthly wall-clock schedule guaranteed by Android.

### 13.2 Official closing prices and NAV

Integrate authorized NSE, BSE and AMFI interfaces as permitted by their terms. Cache dated close/NAV with source and identity; validate ISIN/symbol/AMFI mapping; distinguish exchange-listed, MF/NAV and SGB/physical gold valuation policies; handle unavailable/stale quotes offline. Never upload a family’s complete portfolio, PAN or user identifiers with quote retrieval. Verify current price/source and market data terms near implementation and release; do not invent endpoint stability or commercial reuse rights.

### 13.3 Google Play commercial behavior

Preserve original SKU semantics: `gud_lifetime`, `gud_gmail`, `gud_gmail_upgrade`, `gud_support_annual`; 30-day trial; licence, Gmail entitlement, upgrade and annual support behaviors. The September build document mentions earlier price examples; **do not hard-code historical price as current approved store price**. Confirm final prices, offerings, entitlement model and products with owner/Play Console before launch. Use current supported Google Play Billing library/requirements, proper purchase acknowledgment and reliable restore; handle pending, cancelled, refunded, revoked and signed-in account changes. Treat device-local purchase claims as untrusted: choose a security-reviewed verification design and document any serverless trust limitations. No fake Play production verification. Owner-approved legal/Privacy Policy/Data Safety and production-signed AAB are release blockers, not code-generated promises.

---

## 14. Acceptance artifacts, project documentation and governance

Grok must keep the following durable repository files current, not just explain them in chat:

```text
AGENTS.md

docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md

docs/implementation/SOURCE-INVENTORY.md

docs/implementation/parity-register.csv

docs/implementation/STATUS.md

docs/implementation/SESSION-HANDOFF.md

docs/implementation/DECISION-LOG.md

docs/implementation/SECURITY-THREAT-MODEL.md

docs/implementation/ARCHITECTURE-AS-BUILT.md

docs/implementation/DATA-MODEL-AND-MIGRATION.md

docs/implementation/API-AND-INTEGRATION-CONTRACTS.md

docs/implementation/TEST-MATRIX.md

docs/implementation/CI-RUNS.md

docs/implementation/RELEASE-GATES.md

.github/workflows/g00-source-guard.yml
.github/workflows/g01-static-quality.yml
... documented G02–G14 workflow files or correctly justified reusable equivalents ...
```

Status table must include every P/E feature and each Mxx milestone, owner, source path, native path, tests, status, last successful exact-SHA gate, pending external input, security risk, user-visible acceptance, and actual issue/PR links. Update it **after each diagnosis**, as well as after success. Never retroactively relabel failed old test runs green.

### 14.1 Definition of done per feature

A feature is *IMPLEMENTED* only when:

1. Its original behavior and additional requirements are mapped and reviewed.
2. The user-facing native flow (or explicit service-only flow) exists and is navigable with accessible error/loading/empty states.
3. Its data schema, read/write paths, backup/restore and owner-security scopes preserve the original fields.
4. Deterministic unit and relevant cross-module tests pass locally.
5. Current source SHA passes the applicable CI gates and artifacts are inspected. **Every user-visible screen must additionally meet Section 6A and pass G14 design/accessibility screenshot checks; owner visual acceptance is separately recorded.**
6. The only remaining stage, if any, is honestly identified: provider external acceptance, actual handset, independent AppSec, legal or owner signoff. No P0 source/CI gap is hidden under “done.”

A milestone is *automated-accepted* when all its P0/P1 source and tests pass on its exact candidate SHA. A milestone or product is *release-accepted* only after its stated device/external/security/owner criteria pass. M34 cannot be accepted from emulators alone.

### 14.2 Milestone report template (short form; Section 17.4 is authoritative)

```text
GrowUrDhan | Mxx | YYYY-MM-DD
Original frozen baseline: shahvikask/kosha@af96dcc8967ab595e1d9acee78e29e500cb99a9e
Grok branch: <name>
Implemented source SHA: <full SHA>
Requirement IDs covered: <Pxx / Exx>
Source differences vs original: <approved changes or NONE>
Files changed: <key paths>
Local tests: <commands, passing/failing counts>
AI code review: <what checked, findings and remediations>
GitHub workflow run(s): <actual URL, run ID, exact tested SHA>
Gates: G00=PASS, G01=PASS, ... G14=PASS (where UI scope applies); skipped/blocked explicitly labelled
APK/AAB: <exact artifact and SHA-256 or NOT BUILT>
Current CI result: PASS / FAIL / BLOCKED, explanation
Physical device result: TESTED / DEFERRED M34 / NOT RUN
Security signoff: ACCEPTED / PENDING
Known gaps and P0/P1 defects: <none or enumerated>
Owner decisions needed: <specific>
Next milestone allowed? <YES/NO and why>
```

---

## 15. Final release acceptance and zero-false-completion policy

Before declaring GrowUrDhan enterprise redevelopment ready, Grok must present:

* Complete P01–P39 and E01–E18 requirement matrix mapped to source code and tests, with no unsanctioned missing features.
* Approved native Kotlin/Compose Android app, local SQLCipher encrypted book, frozen Python parser, secure account/recovery, fully accessible navigation and complete 8-category family wealth workflows; **Section 6A Material 3 "Heritage Modern Wealth" light/dark redesign, exact original-logo fidelity, G14 visual/accessibility evidence and owner design acceptance**.
* Robust all-or-nothing ledger and statement import, lossless current backups and supported legacy encrypted backups, reliable offline operation, dated official quote provenance, honest tax/returns rules and scoped family ownership.
* Genuine Gmail OAuth and Play purchase verification when owners have supplied services and approvals. Otherwise the app is **not** commercial-feature-complete and those gates remain visibly BLOCKED.
* A **fully green G11 exact-SHA integrated automated suite**, debug APK + minified release candidate AAB and signer verification under G12 with actual secrets under protected owner control.
* Actual ARM64 Android device/Android-version matrix and cross-device restore checks, independent security assessment, legal/Play readiness and product-owner signoff.
* Readable as-built architecture, API, schema, reproducible build instructions, release gates, GitHub workflow dashboard and all known residual risks.

**Absolute rule:** If any required automated build fails, Grok must diagnose and repair it, then validate on the new exact SHA rather than declaring completion. If required external hardware, credentials, consent, policy review or signing is missing, Grok must report the precise unverified area and required owner action without fabricated success. Under no circumstances does Grok's own confidence, AI review, or a green old-commit workflow replace test evidence.

---

## 16. First command for Grok after receiving this document

**Execute this order, without asking the owner to restate the requirements:**

1. Read this entire specification, acknowledge Rules G01–G15 internally, including full Section 6A, and write their durable repository counterpart `AGENTS.md` in your own development branch.
2. Connect to and clone the **original** `shahvikask/kosha`; verify `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`, protected parser sources, legacy tests, full schema v9 and original screens.
3. Open your **own new development branch**; do not use, modify or depend on `vikaskunjbiharishah-ui/GUD` native `main`.
4. Establish the source-to-native P01–P39/E01–E18 register and identify any source behaviors not yet captured in this specification. Add them—never silently exclude them.
5. Review the Kotlin/Compose/Material 3/SQLCipher/Chaquopy architecture and Section 6A original branding against a current compatible toolchain, privacy and accessibility requirements. Document changes and why.
6. Implement M00 and the rest of the milestone sequence with each full scope checked, unit/static/AI diff reviewed and logically committed.
7. Add the GitHub Actions G00–G14 (or justified equivalent with all same gates), with manually source-SHA-pinned cost-controlled runs, truthful summary/report artifacts and no WIP push-triggered emulator spending.
8. **On every workflow failure: inspect real logs, locate root cause, fix and commit, dispatch a new exact-SHA run, observe terminal status, repeat until required checks green or a genuine external blocker is documented.**
9. Continue until all automated source requirements are accepted; then complete real Android device tests, independent AppSec, genuine service integration and owner-controlled release readiness. Never skip these instructions merely because the current task is “fix this one error.”

**The mandatory cross-session continuity and delivery-response contract follows in Section 17; the specification is not complete until G15 is followed.**

---

## 17. Mandatory cross-session continuity, Grok response contract, and owner next-prompt protocol (G15)

**Status: BINDING.** This section governs every interaction after initial upload, whether the user says “continue,” “next milestone,” “fix the error,” “review code,” “run a build,” or starts a completely new Grok conversation. It extends the milestone report in Section 14.2. **If the two report templates differ, use the more complete contract in this section.** The goal is to make progress reconstructible from committed repository evidence rather than relying on AI memory.

### 17.1 Rule G15 — Stop depending on conversational memory

1. **Before every development or diagnosis action**, Grok MUST read, using repository/file tools, all applicable instructions: repository-root `AGENTS.md`, `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md` (at least its binding rules, the applicable requirement/milestone sections, and Section 6A for UI), `docs/implementation/STATUS.md`, `docs/implementation/SESSION-HANDOFF.md`, `docs/implementation/DECISION-LOG.md`, and the relevant milestone issue/checklist. If these files are unavailable, say exactly which were inaccessible. Do not claim to have read them.
2. **Verify the live repository** using its real remote URL, branch name, `HEAD` full commit SHA, dirty/clean status, and latest accepted/tested source SHA. Never assume the previous answer's `HEAD` is current. For terminal/CI commands, verify `git remote -v`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short`, `git log -1`, and relevant `gh run` data where tooling permits. Use equivalent GitHub plugin calls when no shell exists.
3. **Print a short STARTUP COMPLIANCE RECEIPT** *before* beginning new code, fixing a failure, or claiming to resume work. It must identify the verified repository/branch/SHA, the actual instruction files read and their paths, current milestone and next approved task, most recent applicable CI status and exact tested SHA, and open blockers. If unverified, write `UNVERIFIED`, not `YES` or `PASS`.
4. **Load and respect the scope** from P01–P39, E01–E18, the current milestone and approved decision log. If later user shorthand conflicts with requirements, identify the conflict; do not silently ignore the requirement. A future explicit owner-approved decision may amend the specification, but must be captured in `DECISION-LOG.md` with date, rationale, affected requirements and tests. Do not let an ordinary “continue” waive tests or release gates.
5. **At the end of EVERY substantial working session**—successful, partial, failed, blocked, tool-interrupted, or quota-limited—update and commit (when repository writes are available) the status register, `SESSION-HANDOFF.md`, run evidence and decision log. A chat-only conclusion is insufficient evidence. If a commit is impossible, clearly identify uncommitted changes and provide content/patch to carry over; do not claim it was saved.
6. **One milestone at a time for acceptance.** If milestones are implemented together to economize, preserve separate per-milestone checklists and acceptance statuses. NEVER transition from Mxx to Mxx+1 on the assumption that the prior answer said “done”; check the evidence and owner authorization. If the owner specifically authorizes consecutive development, acceptance boundaries still apply.
7. **CI-monitoring requirement is conditional on active access.** When the owner has authorized paid CI and tools support polling, Grok must examine the actual run, job failures, logs, artifacts and test reports; fix and retest a *new exact source SHA* until mandatory gates pass or a genuine external blocker appears. Do not claim background monitoring persists after the Grok session ends. If the workflow remains running when tool access/session ends, record run ID/URL and `RUNNING—NOT VERIFIED`, and give the owner a ready-to-paste continuation prompt.
8. **Answer in the exact structured formats below.** The end-of-turn result cannot consist only of an enthusiastic summary, a plan, an unverified “success,” or a repository link without test evidence.

### 17.2 Durable file contract — repository is Grok's long-term memory

Create all of the following in the independent Grok development branch during M00 and keep them committed after every milestone, material diagnosis and integration checkpoint:

| File | Required durable content |
| --- | --- |
| `AGENTS.md` | Binding G01–G15 startup/check/report obligations; link to the full spec; immutable original and parser rules; CI budget controls; no fake pass; exact source SHA rule; Section 6A design rule. Explicitly say re-read this file before **every** prompt and repository write. |
| `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md` | This document, unchanged except owner-approved amendments that are logged and versioned. |
| `docs/implementation/STATUS.md` | Current milestone, all P/E requirement IDs and statuses, current branch/HEAD, last accepted SHA, CI table, device and external gates, blockers, next authorized work. Distinguish planned, source complete, locally tested, CI-tested, device-tested, owner-accepted. |
| `docs/implementation/SESSION-HANDOFF.md` | **Single freshest restart checkpoint**: UTC timestamp; remote; branch; full HEAD; whether working tree is clean; precise scope completed/not done; files changed; tested commands/results; workflow IDs/URLs and *tested* SHA; active failure; remaining checklist; exact next safe command/task; explicit owner decisions required. New sessions read this FIRST after `AGENTS.md`. |
| `docs/implementation/CI-RUNS.md` | One immutable historical row per Actions run: workflow, run ID/URL, branch, exact source SHA, conclusion, failed job/test, artifact/report location and fix commit if applicable. Do not overwrite older failed evidence. |
| `docs/implementation/DECISION-LOG.md` | Approved requirements/security/architecture deviations with date, owner approval, implications and regression gates. A Grok idea or assumption is not an owner decision. |
| `docs/implementation/parity-register.csv` | P01–P39 and E01–E18 mapped to original source, Kotlin source, tests and outstanding gates. |

**Important distinction:** `AGENTS.md` is repository-stored instruction material; it is **not guaranteed automatic long-term memory** in Grok. The startup prompt below forces a fresh read. The owner must provide Grok access to the branch and these files in every new environment.

### 17.3 Mandatory STARTUP COMPLIANCE RECEIPT (first visible section of each Grok work session)

Grok MUST respond with these exact labels before coding or CI repair. Fill values only from tool-observed evidence; if unobservable, use `UNVERIFIED` and say why:

```text
GROWURDHAN — SESSION STARTUP COMPLIANCE RECEIPT
Repository / remote: <verified URL>
Working branch: <branch>
Current HEAD SHA: <40-character SHA>
Working tree: CLEAN / DIRTY / UNVERIFIED
AGENTS.md: READ @ <path, SHA or version> / NOT ACCESSIBLE
Full redevelopment spec: READ @ <path, version> / NOT ACCESSIBLE
STATUS.md: READ @ <path, revision> / NOT ACCESSIBLE
SESSION-HANDOFF.md: READ @ <path, revision> / NOT ACCESSIBLE
Current milestone / requirements: <Mxx / Pxx / Exx>
Applicable design contract 6A: READ / NOT REQUIRED / NOT ACCESSIBLE
Last applicable CI: <workflow, run URL, TESTED SHA, PASS/FAIL/RUNNING/NOT RUN>
Outstanding blockers: <short factual list or NONE>
Today's bounded objective: <specific implementation/diagnosis scope>
Authorization to spend GitHub Actions credits: YES, explicit request / NO
Conflicts with persistent instructions: NONE / <explain and request owner decision>
```

**Missing-file recovery:** On the very first M00 session the files will not exist yet. Report `TO CREATE IN M00` and create them as part of the first commit; do not falsely report `READ`. In later sessions, a missing `AGENTS.md` or spec is an instruction-rehydration blocker, not permission to work from memory. Restore from the original uploaded specification or ask the owner for repository access.

### 17.4 Mandatory END-OF-SESSION DELIVERY REPORT (last visible section of every Grok work response)

Use the following full template, including when a failure is unresolved. Never replace the report with a generic “Completed successfully.” Use Markdown with working direct links when possible. Use `NOT RUN`, `NOT VERIFIED`, `BLOCKED`, `FAILED`, or `PENDING OWNER` rather than a dash where evidence is absent.

```text
GROWURDHAN — MILESTONE / DIAGNOSIS DELIVERY REPORT
A. SESSION ID: <UTC date/time; milestone; attempt number>
B. OUTCOME: ACCEPTED / SOURCE COMPLETE—TESTING PENDING / PARTIAL / FAILED / BLOCKED
C. ORIGINAL REFERENCE: shahvikask/kosha GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e
D. GROK REPOSITORY / BRANCH: <URL> / <branch>
E. CURRENT HEAD COMMIT SHA: <full 40-character SHA, or UNVERIFIED>
F. COMMITTED WORK: <commit URLs; major files and functioning user-visible features>
G. REQUIREMENTS TRACE: <Pxx / Exx / Mxx checkbox totals, completed and remaining>
H. AGENTS + SPEC COMPLIANCE: <files actually re-read, current version, deviations NONE or documented>
I. LOCAL VERIFICATION: <exact commands + PASS/FAIL/NOT RUN; counts and root cause>
J. GITHUB ACTIONS TABLE: <each required G00–G14 workflow: run ID, URL, exact TESTED source SHA, terminal conclusion; not-required explicitly labeled>
K. CI FAILURE / REPAIR: <failed job, real logs, root cause, commit fixing it, replacement run and result; or NONE>
L. BUILD ARTIFACTS: <APK/AAB URLs, signing status, SHA-256; or NOT BUILT>
M. UI/BRAND / ACCESSIBILITY: <G14 evidence, approved original logo fidelity, or NOT REQUIRED / NOT VERIFIED>
N. SECURITY / DATA / PARSER: <ledger invariants, migration and parser hash status; risks and test gaps>
O. REAL DEVICE / EXTERNAL SERVICES: <actual verification, deferred M34, Gmail/Play/owner blockers; NEVER implied passed>
P. REMAINING P0/P1 DEFECTS: <specific list with owners/issue links, or NONE>
Q. REPOSITORY HANDOFF FILES: <STATUS.md, SESSION-HANDOFF.md, CI-RUNS.md, DECISION-LOG.md updated? commit SHA or NOT WRITTEN>
R. MILESTONE ACCEPTANCE: YES / NO — <explicit justification; CI/source/device stages separately>
S. NEXT SAFE ACTION: <one bounded command or milestone task in dependency order>
T. OWNER APPROVAL NEEDED: <specific action, credential, design decision or CI budget; or NONE>
U. COPY/PASTE NEXT PROMPT: <one precise prompt starting with “Re-read AGENTS.md…” and referring to S, never auto-waiving gates>
```

**Do not assert `ACCEPTED` if current-commit mandatory automated gates have not actually passed.** `SOURCE COMPLETE—TESTING PENDING` is a valid and useful outcome. Keep physical-device and provider acceptance separate. When tool access fails, report exactly what is known; do not fabricate links or SHAs. The tested source SHA must be distinguished from GitHub's *workflow-trigger* commit SHA when the CI uses a source-SHA input.

### 17.5 The owner's STANDARD NEXT-MILESTONE PROMPT — copy/paste, changing only the milestone

This is the default prompt after any completed or partially completed development session. The first action is **read instructions**, not “start coding.” If Grok's previous report says a milestone remains unaccepted, `Continue or finish` that milestone rather than silently advancing.

```text
Continue GrowUrDhan independent native redevelopment from the ACTUAL GitHub repository state, not from your memory or your previous chat response.

MANDATORY FIRST: Open and re-read repository-root AGENTS.md; docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md (binding G01–G15 and applicable sections, including Section 6A for UI); docs/implementation/STATUS.md; docs/implementation/SESSION-HANDOFF.md; docs/implementation/CI-RUNS.md; docs/implementation/DECISION-LOG.md; and the current milestone requirements/parity register. Verify remote, development branch, git HEAD SHA, tree cleanliness, previous exact-SHA CI evidence and blockers using real GitHub/repository tools. Print the STARTUP COMPLIANCE RECEIPT from Section 17.3 before changing anything.

TASK: Continue or finish milestone <Mxx> according to its FULL P/E feature scope, including all associated unit, integration, security, data-preservation, and (if UI-facing) Material Design 3/G14 visual requirements. First close any previously recorded incomplete items or regressions; do not reimplement features already verified. Preserve frozen original references and CASPARSER. Work ONLY on Grok's isolated development branch.

Complete local tests, static checks, code review, and appropriate source updates. Do NOT spend paid GitHub Actions credits unless I expressly authorize a run in this prompt. When I authorize CI, pin the exact source SHA, watch each run to terminal, diagnose failures from logs, correct root causes, retest the new SHA, and record all run evidence. Never claim a test passed when it did not run.

Before ending, update/commit durable STATUS.md, SESSION-HANDOFF.md, CI-RUNS.md and decision records where applicable. Give me the complete Section 17.4 DELIVERY REPORT, including the exact next copy/paste prompt. Do not advance milestone acceptance without its required evidence.
```

### 17.6 The owner's CI BUILD / FAILURE-REPAIR PROMPT — use when explicitly ready to spend CI credits

```text
Continue GrowUrDhan CI verification and failure diagnosis on Grok's independent development branch.

FIRST re-read AGENTS.md, the full committed redevelopment specification's binding Rules G01–G15 and Sections 7–12/17, STATUS.md, SESSION-HANDOFF.md, CI-RUNS.md, decision log, test matrix and milestone requirements. Print Section 17.3 STARTUP COMPLIANCE RECEIPT with verified branch, latest HEAD SHA and last actual tested SHA.

I AUTHORIZE the appropriate milestone GitHub Actions verification for the exact committed source SHA you verify now, using the budget-controlled G00–G14 workflows described in the specification. Do not dispatch unrelated paid workflows, and never modify the protected original branch. First run affordable local/targeted checks where available.

Monitor the real workflow to terminal conclusion. If it fails, inspect failing jobs, stack traces, JUnit XML, Android instrumentation logs and CI artifacts. Reproduce and fix the actual defect; run relevant local tests; commit the correction; dispatch a NEW exact-SHA test for that corrected commit; continue while your tools/session permit until all mandatory checks pass or a genuine external blocker is documented. Do not declare success for queued/running, skipped, failing or older-SHA runs. Record EACH run in CI-RUNS.md.

Update and commit STATUS.md and SESSION-HANDOFF.md and return the full Section 17.4 DELIVERY REPORT with exact tested SHA, run links, passing counts, failures and next prompt. If the session ends before CI completes, state RUNNING—NOT VERIFIED and give me the run ID plus a continuation prompt rather than claiming future monitoring.
```

### 17.7 The owner's NEW-CHAT / LOST-CONTEXT RECOVERY PROMPT

Use this even if Grok says it “remembers” the application. It is specifically designed for a new chat, another model, a context-limit reset, or resuming after several days.

```text
This is a CONTINUATION of the GrowUrDhan independent Kotlin/Jetpack Compose native redevelopment, not a new project. Do not design or code from memory.

Connect to the Grok redevelopment GitHub repository and first locate/re-read the committed root AGENTS.md and docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md, including Rules G01–G15 and Section 17. Then read docs/implementation/SESSION-HANDOFF.md, STATUS.md, CI-RUNS.md, DECISION-LOG.md, parity-register.csv, and the active milestone issue. Verify remote/branch/HEAD and actual latest tested SHA. If these files are missing, stop before code changes and tell me what is missing; do not guess implementation history.

Print the Section 17.3 STARTUP COMPLIANCE RECEIPT. Resume exactly the single next safe task committed in SESSION-HANDOFF.md, unless I explicitly override it. Preserve original application branch, frozen parser and logo, design contracts and all P01–P39/E01–E18 feature requirements. Follow the full quality and CI policy, and end with the complete Section 17.4 DELIVERY REPORT plus a ready-to-paste next prompt. Never equate last chat's claimed completion with GitHub/test evidence.
```

### 17.8 The owner's RESPONSE-ONLY PROMPT — if Grok finishes without the required report

```text
Your last response did not satisfy the GrowUrDhan G15 handoff contract. Do not start any new coding or CI workflow yet. Re-read AGENTS.md and Section 17 of docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md; verify the actual repository state and produce the exact complete Section 17.4 DELIVERY REPORT. Provide real commit/run URLs and exact tested SHA, label any missing evidence NOT VERIFIED, update SESSION-HANDOFF.md if you have write access, and provide the copy/paste next prompt. Do not change milestone acceptance to PASS without tests.
```

### 17.9 Deterministic acceptance and next-prompt selection

Use the observed report result to decide the next instruction:

| Report result | What owner should do | Which prompt Grok must produce |
| --- | --- | --- |
| `ACCEPTED` with all required exact-SHA source/CI gates | Approve subsequent milestone (physical/device gate may remain assigned to M34) | Section 17.5 with next **Mxx**. |
| `SOURCE COMPLETE—TESTING PENDING` | Authorize one deliberate exact-SHA CI run once source review is satisfactory | Section 17.6. |
| `FAILED` due to a real code/test defect | Repair on same branch; check CI budget before dispatch | Section 17.6 if authorized; otherwise Section 17.5 scoped to defect, with no CI dispatch. |
| `PARTIAL` | Finish current milestone/checklist; do not skip outstanding features | Section 17.5 with SAME Mxx. |
| `BLOCKED` by credentials, Google/Play, owner choice or physical device | Supply exactly the stated prerequisite or authorize independent nonblocked work; keep affected acceptance blocked | Section 17.5 scoped to safe nonblocked work; no fabricated pass. |
| `RUNNING—NOT VERIFIED` workflow | Reopen the exact run and monitor its actual terminal conclusion; do not start a duplicate run without need | Section 17.6 modified to **inspect existing run first**, only rerun for a committed fix. |

**Nonnegotiable finish line:** A Grok response is not an acceptable milestone handoff unless (a) it begins with a real instruction-read receipt when work was performed, (b) it ends with the Section 17.4 delivery report, (c) the repository's status and session checkpoint are brought up to date or explicitly marked NOT WRITTEN, and (d) it provides a next prompt that requires reading `AGENTS.md` *again*. This guards against instruction drift; it does not grant Grok permanent memory, persistent background activity, GitHub permissions, or unlimited CI credits.

**End of GrowUrDhan Grok Specification v1.2. Binding requirements: G01–G15, P01–P39, E01–E18, Section 6A and Section 17.**
