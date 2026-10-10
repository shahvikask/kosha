# GrowUrDhan — E0 Enterprise Feature Parity Matrix (Source-Audited Pass 1)

**Date:** 2026-10-09  
**Reference:** `GUD-Android-Gmail` (read-only)  
**Target:** `GUD-Android-ChatGPT` (only permitted write branch)  
**Evidence standard:** SOURCE VERIFIED = relevant code inspected; IMPLEMENTED ≠ tested; TESTED requires test evidence for current commit; RELEASE ACCEPTED requires device and security signoff. This is a *first-pass* source audit, not a completed exhaustive inventory.

## Audited feature register

| ID | User-visible requirement | Reference evidence | Native evidence | Status | Priority and next gate |
| --- | --- | --- | --- | --- | --- |
| F01 | Existing GrowUrDhan account/password login, lock, onboarding | `src/App.tsx` uses `AuthScreen`, `OnboardingScreen`, `AUTO_LOCK_MS`; `src/lib/auth.ts` mapped in `04-DOMAIN-PARITY-REGISTER.md` | `presentation/VaultSessionGate.kt`, `VaultScreen.kt`, `MainActivity.kt` use Android device credential; native README says app-specific auth incomplete | **PARTIAL / BEHAVIORAL REGRESSION** | P0: design app-password parity, preserve safe recovery and session gates |
| F02 | Household dashboard and net worth | `src/App.tsx` uses original `HomeScreen`; `src/lib/homeDashboard.ts` mapped in parity register | Native `screens/HomeScreen.kt` explicitly displays “Native portfolio screens still in development”; `PortfolioEngine.kt` exists | **MISSING user-facing dashboard** | P0: wire authenticated atomic book reads to real dashboard; no fake balances |
| F03 | Portfolio/holdings detail and transactions | Original `HoldingsScreen`, `AddHoldingModal`, `AddRecordModal`, `HoldingDetailModal` imported in `src/App.tsx` | Native custody enrollment `CustodyHoldingScreen.kt` and financial engines; no corresponding portfolio/transaction destination in inspected `GrowUrDhanShell.kt` main route enum | **PARTIAL** | P0: full holdings, transaction and detail workflows; golden ledger tests |
| F04 | Accounts and family management | Original `AccountsScreen`, `FamilyScreen` in `src/App.tsx` | Native `FamilyMemberScreen.kt`, `CustodyHoldingScreen.kt`, schema-v3 owner/ISIN/folio mapping | **PARTIAL** | P0: account types, multi-custodian/NPS/demat identities, family parity |
| F05 | Manual CAS import, statement history and document review | Original `DocumentsScreen`; `src/lib/statementMail.ts` classifies CAMS/KFINTECH/NSDL/CDSL/NPS | `StatementReviewScreen.kt`, `NativeCasPdfReader.kt`, frozen Python bridge; M7 limits enrolled identity to CAMS/KFINTECH | **PARTIAL** | P0: real ARM64 parser test, all issuer mappings, ownership and approval |
| F06 | NPS statement import and PRAN ownership | `src/lib/statementMail.ts` NPS filename and PRAN matching; `src/lib/gmailSync.ts` uses shared statement parsing | M7 document explicitly states NPS is unsupported in strict native identity registry | **MISSING native end-to-end** | P0: preserve existing parser; native NPS identity, review and import |
| F07 | Gmail account connection and statement sync | `src/lib/gmailSync.ts`: `gmailStatus`, `connectGmail`, `disconnectGmail`, `runGmailSync`; `src/App.tsx` triggers on eligible unlocked sessions | No Gmail route in inspected native `GrowUrDhanShell.kt`; no Gmail integration module in native file inventory from branch comparison | **MISSING native** | P1: OAuth, per-account cursor, multi-mailbox/member mapping, bounded downloads, WorkManager, staged approvals |
| F08 | Financial return/tax/interest/loan/exposure engines | `src/lib/{lots,interest,emi,taxRules,returnMetrics,returns,growth,lookthrough}.ts` enumerated in parity register | Native `domain/finance/{LotEngine,LoanEmiEngine,SavingsInterestEngine,ExposureSplitEngine,TaxAndReturns,PortfolioEngine}.kt`; domain tests exist | **PARTIAL, exact parity unverified** | P0: source-to-source golden vectors, all original edge cases |
| F09 | EOD prices and NAV refresh | `src/App.tsx` binds held ISINs; `src/lib/eodPrices.ts` mapped in parity register | No native price data module in native changed-file inventory | **MISSING native integration (provisional)** | P1: official-source network adapters, cache, dates, stale and offline UX |
| F10 | Play licensing, trial and Gmail entitlement | `src/lib/license.ts`: 30-day trial, `gud_lifetime`, `gud_gmail`, `gud_gmail_upgrade`, `gud_support_annual`; `src/App.tsx` licence gates | Native project inventory lacks a billing module and inspected shell has no licensing route | **MISSING native (provisional)** | P0 commercial: entitlement model, Play Billing, restore, tests |
| F11 | Backup, recovery and portability | `src/App.tsx` calls `maybeRunScheduledBackup`; parity register maps `backup.ts`, `backupIo.ts`, `backupSchedule.ts` | Native M8 `PortableRecoveryScreen.kt`, encrypted backup codec and SQLCipher recovery; physical acceptance not executed | **PARTIAL** | P0: legacy backup interoperability, schedule/retention, cross-device recovery |
| F12 | Terms, feedback and onboarding | `src/App.tsx` includes `TermsGate`, `FeedbackPrompt`, `LicenseScreen`, `OnboardingScreen` | Native shell routes Home/Vault/Statements/Privacy/Settings and secondary family/custody/recovery; no corresponding native routes observed | **MISSING / PARTIAL** | P1: consent versions, legal acceptance, onboarding and feedback |
| F13 | Settings and appearance | Reference settings/theme still need direct asset/CSS audit | Native `SettingsScreen.kt` implements system/light/dark and dynamic color; `GrowUrDhanTheme.kt` uses green Material3 palettes and default Typography | **PARTIAL** | P1: brand asset extraction, accessible design tokens, all reference settings |
| F14 | Enterprise navigation and visual polish | Original reference has header/sidebar/status bar and financial screens in `src/App.tsx` | Native `GrowUrDhanShell.kt` provides adaptive bottom bar/rail; Home is explicitly a placeholder | **PARTIAL** | P1: enterprise design system, real dashboard, charts, loading/error/empty states |
| F15 | Encryption and ledger integrity | Original offline SQLite WebAssembly + Android Keystore encrypted snapshot; append-only event triggers in as-built specification | Native SQLCipher, Keystore, append-only ledger, schema v3 and encrypted portable backups documented and tested on earlier baselines | **IMPROVED architectural foundation; release unverified** | P0: threat model, authorization audit, migration, physical devices and independent MASVS |
| F16 | No GrowUrDhan financial-data backend | Reference as-built states local book and only optional market/license/Gmail network | Native SQLCipher database local to device | **DESIGN PRESERVED, privacy audit pending** | P0: dependency/data-flow and network exfiltration audit |

## Specific functional observations

- **Gmail reference behavior:** `src/lib/statementMail.ts` treats sender and filename as preliminary classifiers, handles CDSL CAS ID and NPS PRAN patterns, and may download NSDL/CAMS/KFin statements for later owner resolution. `src/lib/gmailSync.ts` has explicit bounded paging/downloads and duplicate-state callbacks. Port the *validated behavior*; do not trust sender or filename alone for ownership.
- **Multi-account enterprise requirement:** support multiple OAuth Gmail accounts and many-to-many family-member mailbox links, including a single shared inbox. This is an explicit **new requirement**, not assumed already working in either branch.
- **Monthly schedule:** approximate WorkManager scheduling with catch-up and manual sync; never promise exact calendar-day background execution.
- **Visual baseline:** the approved logo and brand assets must be identified in the Gmail branch before any native redesign. Current native palette is not evidence of approved brand parity.
- **NPS:** source supports NPS discovery/classification, but real native parser-to-PRAN and persistence acceptance is still unverified.
- **Login:** device screen lock does not satisfy the user's instruction to retain original GrowUrDhan app login. Treat this as a P0 release blocker.
- **Security:** do not claim independent penetration testing or real-device ARM64 CAS acceptance from synthetic JVM/instrumented boundary tests.

## Immediate implementation backlog (dependency order)

1. **E0.1** Inventory original screens, settings, branding/logo assets and original native commands; fill exact file paths and feature behavior; add test references.
2. **E0.2** Inventory native screens, repositories and domain tests; verify each status against source and current-commit CI; separate provisional missing from proven missing.
3. **E1.1** Extract brand tokens and implement polished reusable Compose components without changing financial behavior; create visual regression and accessibility checks.
4. **E2.1** Native authenticated household dashboard and portfolio/transactions, preserving atomic book reads and no fabricated values.
5. **E2.2** App-specific login compatibility, all product/account identities and NPS/CAS review, migration/backup interoperability.
6. **E3.1** Multi-Gmail account OAuth, per-member mapping, bounded discovery, local statement staging, WorkManager and safe approval.
7. **E4.1** Play billing, complete policy/terms, end-to-end device tests, AppSec and signed release.

## Change-control and test policy

All work occurs on `GUD-Android-ChatGPT`. Original branch and frozen parser remain untouched. No completion percentage is asserted from file counts. A feature is accepted only when a user-visible workflow, financial invariants, current-commit CI and relevant real-device evidence agree.
