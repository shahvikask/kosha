# GrowUrDhan — M6 Secure Family Member Onboarding (As-Built)

**Date:** 2026-10-08  
**Branch:** `GUD-Android-ChatGPT`  
**Status:** Implementation committed; JVM and Android instrumentation **must be independently verified** from upcoming GitHub Actions.  
**Prior verified baseline:** `076a7c68ee9c45bbb9c9d6f31c66f4570e53fc14`; Native M3 #37875317806, device matrix #37875317880, Kosha CI #37875317825 all successful. This document does not assert the new onboarding tests passed.

## 1. What this development increment fixes

Real-device screenshots showed an empty encrypted vault, a working native unlock prompt and a CAS import dead end: "Add a family member to the vault before reviewing a statement." Prior versions had no screen to satisfy this prerequisite. The new screen allows an explicitly authenticated user to add a family owner, then return to Statements. It does **not** create a fictitious holding, skip CAS-to-owner/ISIN mapping or turn a missing PDF bridge into a success.

- Vault screen: **Add first family member** (or **Manage family members**) only in the opened-vault state.
- Statements screen: a typed `StatementReviewUiState.MissingFamilyMember`, with direct **Add family member** navigation and cancel; no brittle text matching.
- Secondary Compose route `family`: does **not** add a sixth bottom-navigation tab. When opened, it refreshes family members only if the volatile device session is unlocked.
- First record: role `Self`, automatically marked `isPrimary = true`. Additional records: Spouse, Parent, Child, Sibling or Other; never another primary.
- On-screen form: required name, relationship selection, explicit **Continue and authenticate**. Names are not included in owner IDs, logs, saved UI state, file names or network requests.
- On success: **Continue to Statements**, which invokes `LoadOwners` again to obtain fresh encrypted book state. The vault health/member count is also refreshed.

## 2. Persistence and security boundary

The new `VaultRepository.registerFamilyMember(displayName, relationship)` is implemented on the actual SQLCipher repository as one `mutate` SQL transaction:

1. Read existing `family_member` rows **inside that same repository lock and SQL transaction**.
2. Normalize whitespace, constrain names to 1–100 characters, reject control characters and case-insensitive duplicate display names. Restrict the family to a maximum of 30 records.
3. A completely empty vault requires a new `Self` primary. For later inserts require exactly one existing primary and a supported non-`Self` relationship. An inconsistent legacy vault fails closed; it is **not silently repaired**.
4. Assign `OwnerId(UUID.randomUUID().toString())` unrelated to PAN, account or names, and INSERT one row. The existing SQLite unique index also forbids a second primary. Any failure rolls back the transaction; existing holdings, statements and ledger rows are untouched.

The production `DefaultVaultRepository` wrapper calls `authorization.requireRecentApproval()` before the new write. The UI `FamilyMemberViewModel` stages only an in-memory request and records the current `VaultSessionGate.credentialEpoch()`. The Activity launches Android's secure device-credential challenge. Only a successful result advances the gate's epoch, and `SaveAfterCredential` requires both a newer epoch **and** the existing 30-second recent-authentication gate. This prevents a UI call from reusing the original vault-unlock challenge to save a subsequently staged member. The repository itself still enforces the recent-approval gate.

Cancel, credential failure, background lock, process recreation or logout discard the staged name and return the form to Locked. No `SavedStateHandle` is used, no network or telemetry is involved, and `FLAG_SECURE` remains active. Form or repository failures show safe generic errors, not SQL paths or personal identifiers.

**Limitations:** The OS credential establishes possession of a secured device, **not** a GrowUrDhan-specific identity/password. A compromised same-process caller can bypass UI staging if it already holds a valid repository-wide recent-approval session. This increment does not claim full OWASP MASVS readiness, real-device/multi-API behavioral testing, or app account recovery. Existing imported backups and legacy pre-release owner records are preserved.

## 3. Tests added or extended

| Scope | Test cases | Coverage |
| --- | ---: | --- |
| `FamilyMemberOnboardingTest` (domain JVM) | 6 | First Self/primary, no second Self, supported relationships, limits/control characters, duplicate name/ID, inconsistent primary |
| `FamilyMemberViewModelTest` (app JVM) | 7 | Locked read denial, read-only form entry, new credential epoch needed, exactly-once save, invalid inputs, cancel, backend failure |
| `NativeFamilyOnboardingInstrumentedTest` (encrypted vault device) | 4 | Primary+dependent persisted across reopen; invalid/duplicate rollback; invalid first role; legacy no-primary fail-closed |
| `FamilyOnboardingShellTest` (Compose emulator) | 4 | Statements recovery route, required name before staging, return to owner selection, vault shortcut |
| Existing `NativeAuthorizationInstrumentedTest` | Extended | New member-registration API requires repository recent-approval gate |

**Total newly added:** 21 test cases (13 JVM and 8 Android). Existing M5 golden, SQLCipher, approval, Compose and CAS contract tests must also pass. Do not treat counts as passing test results before inspecting downloaded archived JUnit reports.

## 4. Explicitly unfinished

- Native creation/import of holdings and custody accounts with reliable CAS ISIN/folio identity matching; adding a family owner alone cannot make unknown CAS positions importable.
- Encrypted backup file picker and secure restore onboarding.
- Full app-specific identity/password/biometric security design, physical-device and adversarial AppSec tests.
- Complete financial product parity and signed production AAB release approval.

**Change protection:** No edits to frozen `src/casparser-main/**` or protected Python scripts; no edits to the reference React/Tauri application. All source code and tests are kept solely on `GUD-Android-ChatGPT`.
