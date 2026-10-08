# GrowUrDhan — M4 Secure Review UI Slice (As Built)

**Branch:** `GUD-Android-ChatGPT`  
**Status:** Source implementation under CI validation; no commercial authentication claim.

The app uses Android's `KeyguardManager.createConfirmDeviceCredentialIntent` to require a **configured secure device screen lock** before issuing the native vault's OpenOrCreate intent. On success, a short-lived **in-memory** session grants up to five minutes of foreground access; approving a financial statement requires another credential confirmation (valid for at most 30 seconds at commit). Repeated failed/cancelled credential confirmation locks UI and closes the vault. `FLAG_SECURE` blocks Android screenshots/recents of sensitive pages. `MainActivity.onStop` locks unless stopped temporarily by the OS credential confirmation activity; the latter is a pre-release lifecycle caveat requiring deeper device tests.

The review route renders the actual `ReviewedStatement` plan held only in memory: date, affected positions, quantity deltas, owner conflict markers, warnings and proposed event count. Approval requires deliberate acknowledgement, no conflicts and a fresh device credential, and passes the unchanged receipt to the already verified repository transaction. The local device screen lock is **not** the GrowUrDhan vault password, and the repository is **not yet a globally authenticated authorization boundary**. A compromised in-process caller can still access its public methods. A stronger repository-enforced policy, biometric/credential lifecycle hardening, timeout invalidation, and independent MASVS tests are required before production.

The immutable Python CASPARSER bridge is **not connected** yet. No statement is fabricated or automatically approved; the native UI shows a disabled honest empty state until a separate validated adapter supplies source-bound `StatementLine` values. Its public Review ViewModel accepts only in-memory candidate data, and it does not verify PDF bytes by itself. The native app does not accept arbitrary text or SHA typed by a user as a substitute for an actual verified CAS input.

**Do not mark this increment complete until GitHub Kotlin/Compose test results are confirmed.** Frozen CASPARSER paths and all legacy React/Tauri code remain untouched. API/physical-device coverage, end-user authentication, statement parser provenance, legal tax parity, and signed AAB remain open.

## Implemented UI and automated verification gates

- `app/presentation/VaultSessionGate.kt` manages a five-minute session and a 30-second fresh-approval window using a monotonic clock. Explicit `lock()` clears all ephemeral session authority.
- `app/MainActivity.kt` uses `KeyguardManager.isDeviceSecure` and `createConfirmDeviceCredentialIntent`, with an ActivityResult callback that grants authority only on `RESULT_OK`; the app never collects Android screen-lock secrets. It also enables `WindowManager.LayoutParams.FLAG_SECURE`. A foreground watchdog invalidates expired sessions.
- `app/presentation/VaultViewModel.kt` refuses opening without a verified session, and no longer reports a still-open encrypted file as an authorized user session.
- `app/presentation/StatementReviewViewModel.kt` keeps the reviewed receipt only in volatile `StateFlow`, never `SavedStateHandle`; it cancels work on lock/cancel, only exposes confirmed result after backend success and refuses expired credentials. `RequestApproval` itself does not call the repository.
- `screens/StatementReviewScreen.kt` presents statement date, source-hash suffix, exact quantities, proposed differences, cost-basis warning, conflicts and receipt warnings. It requires a user acknowledgement checkbox, and conflict cases disable approval. There is deliberately no fake statement producer or raw user-entered SHA field.
- `GrowUrDhanShell.kt` now exposes a Statements destination. Vault, Home and Privacy copy describes the new and still incomplete security boundary accurately.
- New `VaultSessionGateTest` (**4**), `StatementReviewViewModelTest` (**4**) and additional `VaultViewModelTest` (**2**) cover authorization, expiry, cancellation, unauthorized access, and receipt revocation.
- Three new `NativeShellTest` Android Compose cases cover no-input empty state, acknowledgement-driven approval callback, and blocking of owner conflicts.

**CI gate:** New cases have been committed but their pass/fail must be read from GitHub Actions reports. Retain the prior verified 73/73 M3 baseline until that happens. Do not claim Android device credential confirmation itself is covered by automated emulator tests: the Compose tests bypass the OS credential activity, and real fingerprint/screen-lock device cases remain required.

**Known open issues:** No end-to-end PDF import until CASPARSER origin is validated and connected. Direct `VaultRepository` entry points still need an enforced authorization facade, and user identity/session hardening requires design review. A device's screen lock confirms device possession, **not** GrowUrDhan's owner identity or vault password. The app must also review background transitions while system credential UI is present; the old session is revoked but the already-open SQLCipher connection may be retained until result cancellation/completion.
