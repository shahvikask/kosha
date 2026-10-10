# GrowUrDhan — Native Kotlin Android

**Branch:** `GUD-Android-ChatGPT`  
**Status:** Active native development. The latest `GUD-Android-ChatGPT` changes are **not yet Gradle- or device-verified**. Historical M6 baseline `3a4dfa0790f` passed 102 JVM tests and 35 Android tests on API 35 (also 35 on each of APIs 26/30/36); those results **do not verify this branch**. Full legacy parity, physical-device acceptance, independent AppSec review and Play release remain OPEN.  
**Application ID:** `in.growurdhan.app` (pre-release development only). No Google Play version has been published.

## Modules and active implementation

- `app`: Kotlin, Compose Material 3 and Hilt, with device-credential-gated vault access, native household dashboard, family/custody onboarding, reviewed CAS PDF selection, portable backup/recovery and accessibility-aware navigation. Full financial UI parity and commercial authentication remain incomplete.
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

**Latest independently verified native M6 baseline:** [run #37888065112](https://github.com/shahvikask/kosha/actions/runs/37888065112), [API device matrix #37888065106](https://github.com/shahvikask/kosha/actions/runs/37888065106), implementation commit `3a4dfa0790f`. JVM 102/102; API 35 Android 35/35; APIs 26/30/36 also 35/35 each. See [M6 evidence](../docs/native-migration/16-M6-FAMILY-ONBOARDING-VERIFICATION.md). **M7 test results must be verified independently.**

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

## Native secure-device consent UI (M4 incremental slice — CI pending)

The native Compose shell now includes a **Statements** destination with a receipt-based, line-by-line review and an explicit acknowledgement before the financial approval button is enabled. The public UI **cannot load a statement or fabricate one** until a separate parser-origin validation adapter supplies authentic statement lines; a conflict blocks the entire approval.

The Android `MainActivity` requires the device's configured screen-lock confirmation before opening or creating a vault and before committing a reviewed statement. `VaultSessionGate` keeps an in-memory monotonic session, expires it after five minutes and requires a device credential confirmed within 30 seconds of approval. Leaving the foreground normally clears the staged receipt and closes the SQLCipher vault. A five-second foreground watchdog locks on session expiry, and `FLAG_SECURE` prevents OS screenshots/recents.

**Security limits:** The gate currently protects native UI/ViewModel operations, not every direct repository call; it is NOT a GrowUrDhan vault-password or full app-authentication system. The system credential confirmation Activity temporarily stops the app, so its old authorization is revoked pending fresh confirmation and a cancelled challenge locks the screen. OS-prompt lifecycle/device matrix needs additional physical-device tests. The immutable CASPARSER bridge is not connected, so approval UI is an honest empty state until validated source data is wired.

**Verification status:** This new slice has added **10 JVM tests** (vault gate, session expiration and review-state protection) and **3 Compose instrumentation tests** (empty state, deliberate consent, conflict blocking), plus all retained native financial/storage tests. **A new GitHub CI run has not yet been reported successful.** See [M4 native consent design](../docs/native-migration/13-M4-SECURE-REVIEW-UI-AS-BUILT.md).

## M5 release-hardening slice (new CI pending)

The next development slice introduces a guarded Android Storage Access Framework CAS PDF picker, explicit family-owner selection, transient masked PDF password, SHA-256 over actual bytes, a strict parser-to-finance adapter and an **ARM64 release-only** Chaquopy/PDFium packaging path (Android 26+). Only existing, unambiguous matching holdings can be reviewed; unknown/new ISIN/folio identities are refused pending a schema mapping layer. The frozen CASPARSER and Python wrappers have **not** changed.

Production `DefaultVaultRepository` now rejects financial reads without a volatile device credential session and rejects mutations unless an authorization check is still fresh. The source security preflight checks privacy, backup and signing protections; native CI smoke-builds an R8-minified AAB with a **disposable** CI key. A separate API 26/30/36 emulator matrix and a manual, protected owner-signed release workflow have been added.

**Not yet verified:** these new builds/tests, live ARM64 PDF parse, all issuer/password cases, 16 KiB native library compatibility, Play upload signature, real device matrix and independent OWASP MASVS review. No real user may rely on this build as a Play release. See [M5 release-hardening and security evidence](../docs/native-migration/14-M5-RELEASE-HARDENING-AND-APPSEC-OPEN-GATES.md).

## Family onboarding and CAS owner selection (M6 increment — CI pending)

The native **Vault** screen offers **Add first family member** after device-authenticated opening. The **Statements** route now shows **Add family member** when an unlocked vault contains no owners, leading directly to a family setup screen. On success the member can continue to Statements for fresh owner selection.

The first member becomes primary **Self**; additional members can be Spouse, Parent, Child, Sibling or Other. `VaultRepository.registerFamilyMember` validates display-name uniqueness and supported relationship, assigns a random UUID owner ID (never PAN or account number), checks primary owner invariants and inserts atomically in SQLCipher. No holdings, accounts, statements or ledger events are fabricated or deleted. The production repository requires a recent verified device credential, and the in-memory form requires a **new credential challenge after staging** before saving; a cancelled prompt discards the pending name.

Added tests: **6** pure Kotlin family-policy JVM cases, **7** ViewModel/session JVM cases, **4** SQLCipher Android device cases, and **4** Compose navigation/form cases; the existing production-authorization test now covers the new write API. Results remain **unverified until a new workflow finishes**.

**Important boundary:** Adding a member does not create a corresponding custody account or holding. CAS import still requires a supported ARM64 release build and correctly mapped existing holdings. Encrypted portable-backup UI, full registration/onboarding parity and Google Play release approval remain open. See [M6 family onboarding as-built](../docs/native-migration/15-M6-FAMILY-ONBOARDING-AS-BUILT.md).

## M7 custody account, CAS mutual-fund onboarding and identity mapping (CI pending)

The native **Vault → Manage accounts & holdings** flow now lets a previously authenticated family member add a CAMS/KFINTECH **nickname-based registrar account**, followed by an ISIN/folio-enrolled mutual fund. The family and Statements screens also route to setup. All registration is read-only until a separate successful Android device-credential challenge *after staging*. Atomic SQLCipher writes assign unrelated UUID IDs; new holdings have zero units and unknown historical cost, and produce no trades.

**SQLCipher schema v3** adds an owner/account-scoped, immutable `holding_cas_identity` table with composite foreign keys, uniqueness and triggers. The migration from v1/v2 is transactional and retains the original append-only ledger. Portable backup payload format 3 carries this identity mapping while logical formats 1/2 remain restorable without fabricated identities. The production Android PDF path now requires a strict identity registry and matches registrar+ISIN+folio+fund name before preparing a review; name-only mapping is not accepted there.

**New tests added, not yet independently verified:** 38 JVM scenarios (including the eight-case independent strict-CAS identity suite) and 11 Android scenarios, in addition to the retained native financial/SQLCipher/Compose regressions. The static authorization policy now covers 20 guarded repository entry points. See [M7 as-built and open release gates](../docs/native-migration/17-M7-CUSTODY-HOLDING-CAS-MAPPING-AS-BUILT.md) and [physical/recovery/signing acceptance protocol](../docs/native-migration/18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md).

**Still open:** independent PDF investor ownership verification, NSDL/CDSL/NPS strict mapping, physical ARM64 tests, full backup file-pickers and second-phone recovery, user account authentication beyond device credential, and owner-approved production signed AAB. Existing frozen CASPARSER files, pre-release developer vaults and legacy React/Tauri implementation must remain unchanged.

## M8 portable recovery and parser bridge (new CI pending)

**Vault → Backup & recovery** provides encrypted backup export for an authenticated open vault, and a **Recover from encrypted backup** entry point on a new device without an existing vault. Android SAF selects a private document, then the user supplies a separate 12+ character portable backup password and confirms a fresh device screen lock. Only encrypted bytes go through the chosen URI; size and header bounds are enforced, passwords are cleared on cancellation/destruction, and SQLCipher's all-or-nothing restore refuses a nonempty destination. Previously verified format 1/2 and latest format 3 logical backups remain covered by retained Android vault tests.

Five new Android instrumentation cases verify that synthetic frozen-parser-shaped JSON is bound to the **actual selected PDF byte SHA-256** and uniquely enrolled owner/registrar/ISIN/folio. Five JVM checks cover bounded backup input, one additional vault JVM regression preserves the uninitialized state across SAF handoff, two authorization JVM tests reject credentials older than file selection, and two Compose tests cover recovery navigation and consent (**15 additional checks total**). This is **not** proof that genuine CAS PDFs parse on ARM64 hardware; no live personal statement enters CI.

The protected Play-upload AAB workflow now additionally requires an owner-specified **exact approved 40-character Git commit SHA**, rejects `-dev` version names, and compares the **produced AAB signer** to the pinned upload certificate. Owner signing keys, Play listing, real-phone security tests, cross-phone backup recovery, ARM64/PDFium live acceptance and independent MASVS verification remain **NOT DONE**. See [M8 portable recovery and hardware/signing test plan](../docs/native-migration/16-M8-PORTABLE-RECOVERY-AND-CAS-BRIDGE.md). This increment requires independent CI JUnit/artifact verification before it can be marked tested.


## Household dashboard increment (October 2026; verification pending)

The native Home screen derives household net worth, assets, liabilities, member and
asset-category breakdowns from one authorized encrypted book snapshot. Member names
come from the same snapshot and are cleared with the financial UI when the session
locks. No live market-price claim or inferred historical purchase cost is made.
Unknown historical cost is disclosed separately from financial integrity warnings;
liabilities are kept separate from asset categories and reduce net worth.

The latest increment adds member-level assets/debt, per-category holding counts,
and dedicated unknown-cost messaging. Domain regression cases cover debt-only and
mixed household reconciliation; Compose tests cover unlocked member visibility and
locked-screen privacy. All tests use synthetic data.

**Not verified:** current-branch Gradle compilation, JVM/Compose test execution,
physical device behavior, complete native product parity, Play release readiness.
Earlier CI evidence in this README applies only to the specific historical commits
linked above, not these changes. The immutable CASPARSER files and reference
branches remain untouched.

## Native vault and CAS handoff hardening (October 2026; tests pending)

The production encrypted-vault repository now rechecks authorization **after**
asynchronous opening and private reads, not merely before them. If opening is
cancelled or credentials expire before opening completes, best-effort
non-cancellable SQLCipher connection cleanup runs before the failure returns.
The caller receives no successfully opened status or financial rows after
authorization has been revoked at the post-operation check.

The PDF picker now initiates **one** guarded vault opening instead of two
independent opens in the Activity and VaultViewModel. Successful opening
refreshes the vault UI, and backgrounding, cancellation, or locking invalidates
the outstanding import generation. Only the still-authorized, current import
may stage statement review or display parser failure. The provided PDF password
is wiped when input validation fails and after import processing.

Synthetic JVM regression tests cover denied opening, successful opening,
credential expiry mid-open, coroutine cancellation, and authorization expiry
mid-read. The existing CASPARSER and Python bridge are **unchanged**.

**Verification pending:** Gradle/JUnit/instrumentation runs and real ARM64 PDF
picker/credential lifecycle tests. There is no assertion that native
parity, Play signing, or independent MASVS assessment is complete.

The repository's session-bound opening and read paths additionally bind results
to the **same device-challenge revision** that authorized the call. Re-locking
and authenticating again while an older operation is suspended does not make
that older operation authorized. Added JVM tests for both mid-operation
credential replacement and challenge-revision progression.

The Activity's authenticated PDF reopen additionally waits for any previously
queued VaultViewModel close to finish. This avoids a pending picker-background
cleanup closing the just-reopened encrypted connection. A coroutine regression
test verifies that document import cannot pass this barrier while close is
suspended. The remaining physical-device picker lifecycle must still be tested.

## Native transaction authorization gate (October 2026; device verification pending)

The production `DefaultVaultRepository` now propagates its initial device
credential revision into financial mutations, including statement approvals
and encrypted backup restoration. The SQLCipher mutation transaction checks
the same revision and a still-recent credential immediately **before**
`setTransactionSuccessful()`; expired or replaced authorization aborts the
transaction, leaving SQLite to roll back. Isolated development repositories
retain their existing constructor behavior unless explicitly supplied an
authorization guard. Real SQLCipher instrumentation tests use synthetic
records to exercise rollback on expiry and credential replacement.

The production vault open/close boundary also uses a shared outer mutex:
a failure/cancellation cleanup cannot close a newly reopened connection
because the newer opening must wait for cleanup to complete. Added coroutine
tests for explicit close waiting and failed-open cleanup ordering.

**Not yet verified** on Android devices or with the full Gradle suite.

## Backup export authorization across slow encryption (October 2026; verification pending)

Portable backup export now rechecks the same **recent** device credential and
its challenge revision after the encrypted bytes have been generated. If the
credential expired or was replaced, the resulting ciphertext buffer is wiped
and never returned for document export. The Android SAF writer independently
checks the operation generation and recent credential immediately before
opening its output stream. Synthetic JVM regression tests cover expiry,
credential replacement and a successful unchanged approval.

Only encrypted bytes reach SAF; no network upload is introduced. Android
instrumentation and storage-provider lifecycle verification remain pending.

## CAS import action and credential binding (October 2026; verification pending)

A selected CAS PDF is now associated with a pure in-memory import ticket containing
the action generation and original device-challenge revision. Parser results cannot
be staged if the Activity locks, backgrounds, the user cancels review, starts
another PDF selection, or locks and reauthenticates during parsing. Cancellation
also wipes any staged mutable PDF password. Four synthetic JVM tests exercise
the ticket's cancellation, session-expiry, supersession and reauthentication
behavior. No CASPARSER source or runtime bridge was modified.

Still requires ARM64 hardware tests for real PDF parsing, external document
pickers and operating-system credential callbacks.

## Native recorded-holdings overview (October 2026; test verification pending)

The household Home screen now lists individual recorded asset/debt positions
derived from the same single authorized encrypted `readBook()` snapshot as the
household totals. It shows fund/asset name, registered owner display name,
recorded quantity, stored-price value, and gain only when historical cost is
known. Loan/debt positions are clearly liabilities and never presented as
investment gains or missing purchase cost. Account IDs, folios, holding IDs
and PAN do not appear in these display rows. The screen caps the rendered
holding list at 30 while retaining all recorded rows in the total, with a clear
count of additional positions. No network request or fabricated price is made.

JVM reconciliation/privacy regressions and a Compose holding-display regression
have been added but require full Gradle/instrumentation verification.

## Strict native household ownership and safe warnings (October 2026)

The native financial dashboard uses explicit enrolled-member ownership.
If the encrypted book contains holdings without any registered family member,
the portfolio screen excludes the orphan positions, leaves member/asset counts
at zero and presents an integrity warning. The general-purpose financial engine
retains its legacy inferred-owner behavior for explicitly nonproduction use
cases that omit `enforceKnownMembers`.

Raw internal ledger integrity errors can contain holding names and event IDs.
The dashboard now projects only safe warning categories rather than carrying
these private strings into the UI state. Unit tests cover missing ownership and
the absence of private identifiers in projected warnings. User-entered holding
names may still contain identifiers; users should choose privacy-safe nicknames.

## Recovery document-picker cleanup barrier (October 2026; tests pending)

The portable backup ViewModel now accepts a suspending vault cleanup barrier from
the Activity, just as CAS import already does. A queued close caused by the
document picker must complete before encrypted backup export or restore checks
vault status or opens SQLCipher. The original recently confirmed device
challenge and the operation generation are verified again **after** the wait.
Cancellation, revocation or supersession clears the passphrase in the ViewModel
without exposing a document URI or decrypted book.

Added synthetic coroutine tests verify that a suspended cleanup blocks SQLCipher
access and that expired credentials cause failure without opening the database.
