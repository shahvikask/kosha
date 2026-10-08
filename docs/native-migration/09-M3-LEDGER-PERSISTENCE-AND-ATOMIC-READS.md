# GrowUrDhan — M3 Increment: Rich Ledger Persistence, Atomic Books and Pre-release v1 Upgrade

**Document date:** 2026-10-08  
**Development branch:** `GUD-Android-ChatGPT`  
**Status:** Committed implementation; **new CI verification required** after the final M3 increment commit.  
**Latest independently verified M3 base:** [Run #37796366761](https://github.com/shahvikask/kosha/actions/runs/37796366761), commit `e59bf821a65d622c007b129ef8906ab9c4544129`, **completed / success**, including Kotlin financial golden tests, M2 vault/Compose instrumented tests, APKs and reports. This previous pass **does not cover** the schema-v2 increment described below.

## 1. What changed

### Native schema v2: safe M2 database upgrade

`VaultSchema.VERSION = 2`. On a newly created vault, the original seven-table v1 DDL runs, followed by the six v2 migration statements **in one SQLite transaction**. On an existing initialized v1 SQLCipher database, only the six incremental statements and version bump run transactionally.

The v1 `ledger_event` table is never dropped, renamed, recreated or bulk-rewritten. Existing rows retain their original IDs, owners, holdings, event dates, money and quantities; UPDATE and DELETE denial triggers survive the upgrade.

**New ledger columns:**

| Column | Storage / guarantee |
| --- | --- |
| `financial_kind TEXT NOT NULL DEFAULT ''` | Explicit rich event identity; old rows retain `''` and are read using their old `kind` |
| `unit_price_scaled INTEGER NULL` | Optional historical 1e4-scaled price; NULL means not known, never silently imputed |
| `charges_scaled INTEGER NOT NULL DEFAULT 0` | 1e4-scaled charges, nonnegative |
| `supersedes_event_id TEXT NULL` | Append-only correction lineage; unique, must refer to an existing event belonging to the same holding and owner |

The legacy `kind` field is constrained by v1 to `BUY/SELL/DIVIDEND/INTEREST/OPENING_BALANCE/ADJUSTMENT/CORRECTION`. New `BONUS`, `SPLIT`, `SNAPSHOT_ADJUSTMENT`, `VALUATION_UPDATE`, `REPAYMENT` records use the **existing** `ADJUSTMENT` carrier and their real name in `financial_kind`. A database INSERT trigger rejects inconsistent carriers, corrections with missing ancestors, mismatched-family/holding correction ancestors, and supersession links on non-corrections. An additional unique index prevents correcting the same predecessor twice. Legacy `financial_kind=''` records are preserved as-is and are not given invented metadata.

No SQL column storing money or quantity uses `REAL`.

### Full-information financial event writer

`VaultRepository.appendFinancialEvents(List<FinancialEvent>)` commits an approved list of detailed events in one SQL transaction. It validates duplicate IDs, required explicit prices/ratios, nonnegative prices and charges, and quantity constraints. SQL constraints recheck ownership and correction links. **Any row failing validation or referential checks rolls back the entire batch.**

`VaultRepository.appendEvent(LedgerEvent)` remains supported for legacy callers, using v2 columns without discarding optional metadata.

The pure `domain/finance` engine still does **not** write or import statements automatically. An explicit user-confirmation and TOCTOU-safe import commit flow remains future work.

### Consistent financial reads

`VaultRepository.readBook(): VaultBook` performs **one `beginTransactionNonExclusive()` read transaction**, collecting family members, holdings and detailed ledger events under the repository mutex. The `CalculateFamilyPortfolio` and `PreviewStatementReconciliation` domain use cases now call `readBook()` once each instead of combining independent reads that could reflect different committed states. Unsupported v1 corrections without a supersession reference are **rejected**, not interpreted as a valid investment history.

### Portable encrypted backups

New encrypted exports contain logical **format 2, databaseVersion 2**, retaining the four extra ledger columns in their authenticated logical payload. Old encrypted `GUDNBACK` backup format 1 / schema 1 is accepted for intentional developer-data restoration into an **empty** v2 vault. Missing v1 metadata becomes SQL v2 defaults, not guessed historical prices or supersession. The existing AES-256-GCM/PBKDF2 protection and all-or-nothing restore transaction remain intact. Neither the plaintext financial database nor its device-only key is exported as a raw file.

This is voluntary pre-release data preservation, not a promise of an upgrade path for a nonexistent published Play user base.

## 2. Added verification coverage (committed, awaiting CI)

- **Host Python schema preflight** now reads the Kotlin production v1 DDL and v2 migration list. It checks interrupted migration ROLLBACK leaves no partial columns, upgraded v1 rows stay unchanged, fresh v2 installation, all seven tables, append-only UPDATE/DELETE denial, correction-link ownership, carrier/kind agreement, unique supersession, integrity and foreign keys.
- **Five additional real Android SQLCipher/Keystore instrumentation tests** in `NativeFinancialPersistenceInstrumentedTest.kt`:
  1. Open a manually constructed **encrypted v1 vault** with a real Keystore envelope and v1 ledger event, then verify in-place v2 upgrade, event preservation and reopen.
  2. Persist BUY/BONUS/SPLIT/SNAPSHOT_ADJUSTMENT/CORRECTION, explicit charges, and correction lineage; compare a single atomic `VaultBook` before and after close/reopen.
  3. Verify a failed multi-event transaction (missing ancestor) rolls back its successful earlier insert, and a cross-owner correction is blocked.
  4. Encrypt and restore a v2 backup, checking exact metadata round-trip and foreign keys.
  5. Restore a synthetic v1-format encrypted backup into an empty v2 vault; verify old values and safe missing-field defaults.
- **Four new JVM use-case tests** in `AtomicBookUseCaseTest.kt`: one book read per calculation/preview, no separate getter calls, no unreviewed event write, fail closed on pre-v2 correction lineage, and mapping of rich correction metadata.
- All **existing M2 instrumentation** and **M3 financial golden tests** are retained and must still pass.

Actual new v2 test results are **not claimed** in this file until the user supplies a completed GitHub Actions run and its reports have been inspected.

## 3. Security/integrity consequences

- Preserves the on-device Keystore-wrapped SQLCipher encryption and fail-closed vault open behavior.
- Avoids table-rewrite migrations for the append-only financial ledger.
- Fails closed on impossible correction lineage, wrong owner, duplicated supersession, non-positive split ratios and overflow-aware financial calculations.
- Read transactions prevent a portfolio view from mixing commits from different points in time.
- Backup compatibility does not make up missing transaction metadata.
- No CASPARSER engine/source/invocation/dependency changes, no raw user statements in fixtures, no remote GrowUrDhan financial-data server.

## 4. Still not complete in M3 / before Play release

1. Finish independently verified parity for remaining TS finance modules: `interest.ts`, `emi.ts`, `growth.ts`, `lookthrough.ts`, `returnMetrics.ts`, `costBasis.ts`, plus more liability and tax edge cases.
2. Provide atomic **review + accept + commit** for statement reconciliations with prevention of review/commit races; `SnapshotReconciler` remains read-only. Preserve CASPARSER as a black box during future Kotlin bridge work.
3. Complete the native holding-management and portfolio UI/validation, session lock/password/biometrics, safe portable backup UX and Android recovery.
4. Test backup/restore across physical devices, API 26–36, ABI and 16 KiB page sizes; verify release AAB signing, Play policies and Android security review.
5. Remove React/Tauri/Rust legacy finance code only **after** full native golden parity and approved cleanup gates. No production release has occurred.

## 5. CI owner handoff

For the **final new source commit** triggering `GrowUrDhan Native Android M3`, provide the complete commit SHA, native workflow run ID and link. Wait for the owner to report PASS/FAIL rather than consuming session time by repeated workflow polling. If failed, inspect native JVM, Python schema preflight and Android instrumentation reports and iterate on `GUD-Android-ChatGPT` only.
