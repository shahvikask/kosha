# GrowUrDhan Enterprise Redevelopment — Master Plan

**Created:** 2026-10-09  
**Implementation branch:** `GUD-Android-ChatGPT` (the only branch to change)  
**Functional reference:** `GUD-Android-Gmail` (read-only)  
**Status:** Planning baseline; NOT a declaration of feature parity, commercial readiness, or security certification.

## Product charter

Deliver a polished, accessible, fully native Android family-wealth application with **no GrowUrDhan-operated financial-data backend**. Portfolio books, imported statements, ownership mappings, financial calculations, credentials and backups remain device-local except for explicitly user-directed exports. Third-party Gmail, market-data and Play services require network access, but must not receive the user's local wealth database from GrowUrDhan. The approved Gmail-branch logo and brand are the visual baseline; polish the design without replacing approved identity assets.

The working Gmail branch is the **minimum feature baseline**, not the target design quality. Never claim a native feature complete merely because a class or screen exists.

## Immutable boundaries

- Do not edit `GUD-Android-Gmail`, `GUD-Android` or frozen CASPARSER implementation and invocation scripts.
- Reuse the frozen on-device Python CASPARSER through Chaquopy and a typed, fail-closed adapter; do not reimplement its parsing algorithms.
- Preserve existing GrowUrDhan login behavior as a product requirement; current native OS-device-credential gate is **not** equivalent to the original app login. Design a compatible app-specific login and recovery model before declaring parity.
- No server-dependent login, portfolio computation, financial storage or recovery.
- Maintain append-only ledger, scaled-integer money, idempotent statement imports and explicit uncertainty for unknown cost.
- No automatic trade creation from a statement snapshot. No cross-member account or folio assignment based only on email address or fund name.
- Release is blocked until physical-device verification, signed production artifact verification and independent mobile security review.

## Confirmed source evidence

- `docs/native-migration/04-DOMAIN-PARITY-REGISTER.md` maps original TypeScript financial, portfolio, tax, analytics, statement, Gmail, pricing, backup, licensing and settings services to proposed native modules. Proposed paths are **not** proof of implementation.
- `android-native/README.md` documents native Compose/Hilt/SQLCipher foundation and outstanding financial UI, login, market and Play parity.
- `docs/native-migration/14-M5-RELEASE-HARDENING-AND-APPSEC-OPEN-GATES.md` records outstanding real ARM64 parser, authentication, security and release gates.
- `docs/native-migration/17-M7-CUSTODY-HOLDING-CAS-MAPPING-AS-BUILT.md` documents strict CAMS/KFINTECH identity enrollment, but not complete demat/NSDL/CDSL/NPS coverage.
- `docs/native-migration/16-M8-PORTABLE-RECOVERY-AND-CAS-BRIDGE.md` documents implemented portable recovery and parser boundary tests, while physical-device signoff remains open.
- Original `src/lib/gmailSync.ts` includes Gmail account discovery, attachment fetching, member enablement, statement classification and on-device parser invocation. The native equivalent requires a separate source and behavior audit.
- Native `GrowUrDhanTheme.kt` currently uses green Material 3 schemes and default typography; it is a starting point, **not** a finished enterprise design system.

## Delivery tracks and release gates

| Track | Required outcome | Acceptance evidence |
| --- | --- | --- |
| A. Inventory and parity | All Gmail-branch screens, routes, commands, schema tables, financial rules, Gmail, NPS, prices, licensing and settings mapped to native equivalents | Source-linked parity matrix with COMPLETE/PARTIAL/MISSING/REGRESSION/IMPROVED/NEW, owner, test and priority |
| B. Enterprise design | Approved logo retained; refined light/dark tokens, typography, spacing, reusable components, navigation and empty/error/loading states | Compose previews/screenshots, accessibility contrast/font scaling, navigation and device UI tests |
| C. Financial domain | Holdings, liabilities, interest, tax, FIFO, XIRR, growth, exposure, family analytics, statement snapshots | Golden tests against frozen TS fixtures, deterministic time/price and integer exactness |
| D. Statement import | Existing CASPARSER untouched; native CAMS/KFin/NSDL/CDSL/NPS mapping, review and safe commits | Real consented/synthetic ARM64 device acceptance, owner/folio matching, duplicate and conflict tests |
| E. Gmail Sync | Multiple OAuth accounts, shared and separate family inboxes, monthly sync, safe staged imports | Multi-account/owner integration tests, revoked permission, retries, duplicate suppression and background behavior |
| F. Security/recovery | App-specific login parity, local SQLCipher, Keystore, session authorization, portable encrypted backups | Threat model, device-loss and cross-device restore tests, OWASP MASVS assessment |
| G. Commercial | Play billing, policies, onboarding, accessibility, performance, release pipeline | Policy review, reproducible builds, production-signed AAB, device matrix and owner approval |

## Gmail Sync v1 acceptance contract

1. Model `GmailAccount` separately from `FamilyMember`, with a many-to-many `MemberMailboxLink`; one mailbox can serve many people and one person can connect multiple mailboxes.
2. Use Google OAuth authorization on the device and minimum necessary Gmail API scopes. Gmail read access can require restricted-scope verification and an independent security assessment; investigate Google requirements before promising production availability. Never collect Gmail passwords.
3. Discover eligible CAS/NPS messages and attachments with bounded pagination/download size; retain per-account cursor, sync time, per-attachment fingerprint and sanitized processing status locally. Never log message bodies, PAN, attachments or OAuth secrets.
4. Sender, filename and email recipient are **hints only**. Resolve statement ownership using verified document and enrolled financial account identifiers; quarantine ambiguous/mismatched results for manual review.
5. Preserve the existing CASPARSER unchanged. Reuse manual PDF import's typed validation, explicit uncertainty and atomic, idempotent reconciliation. NPS integration needs independent format and account-identity verification.
6. Schedule approximate monthly WorkManager synchronization with connectivity constraints, exponential retry and catch-up on next app launch; Android does **not** guarantee an exact monthly execution time. Provide per-account `Sync now`, last-success, last-error, revoke and disconnect controls.
7. Define a security-approved auto-approval policy separately. Default to **staging** new observations; automatically commit only if explicit user opt-in and all ownership, chronology, completeness and conflict gates pass. Never auto-post inferred purchases.
8. Provide OAuth revoke/disconnect and local metadata deletion policies without deleting historical ledger entries.

## Enterprise UI/UX direction

- Preserve approved logo assets. Extract and document original brand colors before finalizing tokens; do not silently replace approved brand identity.
- Build a design-token layer for brand, semantic success/warning/error, backgrounds, elevations, corner radii, spacing, type scale, chart palettes and light/dark contrast.
- Prioritize Home (household net worth, allocation, freshness, next action), Portfolio (family/owner/product drilldowns), Statements (manual/Gmail intake, review and history), Insights (XIRR, gains, income, tax, liabilities), and Settings (identity, security, backup, licences).
- Never render a guessed cost, stale quote or uncertain ownership as an authoritative number. Always label as-of dates and source; ensure INR grouping, screen-reader descriptions, 200% font scaling, 48dp touch targets, offline/empty/error/loading states and reduced-motion behavior.
- Use real, anonymized fixtures for UI review; no customer PAN, account number or PDF in screenshots, CI artifacts or issue comments.
- Evaluate competitor capability categories (INDmoney, Kuvera, Dezerv, MProfit) using dated official public documentation; no unverified claims about competitors and no imitation of proprietary UI.

## Execution sequence

**E0 — Source audit (first):** enumerate reference screens, original Gmail and NPS commands, assets, theme and financial tests; inspect native screens and modules; publish a source-linked feature register. Record evidence and unknowns, not guessed completion percentages.

**E1 — Design system and navigation:** document original brand assets; implement reusable Compose components and professional Home/Portfolio/Statements/Insights/Settings navigation, while preserving current protected workflows. Test screen scaling and accessibility.

**E2 — Critical functional parity:** implement missing original financial use cases, app login, account/folio identity, manual CAS/NPS workflows and data migration; require exact financial golden tests.

**E3 — Gmail Sync:** implement multi-account OAuth, account-to-member mapping, bounded statement discovery, WorkManager sync, review/approval and revocation; require device and Google consent tests.

**E4 — Commercialization:** Play billing, policies, offline/backup recovery, performance and complete release/security acceptance. No Play release before every P0/P1 gate passes.

Each feature moves through: SOURCE VERIFIED → DESIGN APPROVED → IMPLEMENTED → JVM TESTED → ANDROID TESTED → DEVICE VERIFIED → RELEASE ACCEPTED. CI passing on an older commit does not verify a later increment.

## Next implementation task

Create `docs/native-migration/20-ENTERPRISE-FEATURE-PARITY-MATRIX.md` from actual source inspections, beginning with Gmail/NPS, portfolio dashboard, login, market prices, licences, settings and approved visual assets. Add path-level evidence and tests for each feature. Do not edit frozen parser or baseline branch.
