# GrowUrDhan — Domain Porting Register and Kotlin Golden-Test Contracts

**Status:** Planning inventory, 2026-10-08. No financial algorithms have been ported.  
**Branch:** `GUD-Android-ChatGPT`  
**Source:** `src/lib/**`, `src/db/**`, `src/types.ts`, React import flow and frozen CASPARSER interfaces.

A native UI is not a feature-complete replacement until the calculations below produce identical results from the same synthetic input and deterministic clock.

## 1. File-to-service mapping

| Existing source (observed line count) | Core responsibility | Native service/module | Verification |
| --- | --- | --- | --- |
| `src/lib/money.ts` (241) | Scaled money/quantity arithmetic | `domain/finance/Money.kt`, `Quantity.kt` | Precision, rounding, min/max, overflow, negative cash flows |
| `src/lib/positions.ts` (133) | Position derivation from events | `domain/positions/DerivePositions.kt` | Transaction ordering, corrections, opening balance |
| `src/lib/lots.ts` (256) | Lot folding, realized disposal, FY dates | `domain/portfolio/LotEngine.kt` | FIFO or actual existing choice, cost basis, FY boundary, partial sale |
| `src/lib/interest.ts` (587) | Small-savings rates, accrual, SGB/income timing | `domain/income/InterestEngine.kt` | Rate period, dates, leap years, catch-up idempotency |
| `src/lib/emi.ts` (116) | EMI catch-up and loan schedule | `domain/loans/EmiSchedule.kt` | Dates, status pause/resume, event totals |
| `src/lib/taxRules.ts` (146) | Gain classification and applicable rule sets | `domain/tax/TaxRuleEngine.kt` | Rule year, grandfathering, classification, signed rounding |
| `src/lib/returnMetrics.ts` (181) | Holding return and annualization hints | `domain/returns/HoldingReturn.kt` | Statement-only history, invested change, annualization |
| `src/lib/returns.ts` (113) | Cash flows, XIRR, percentage gains | `domain/returns/CashFlowEngine.kt` | Same cash-flow construction, time basis, tolerance |
| `src/lib/growth.ts` (334) | Growth model, income FY, monthly cumulative series | `domain/analytics/GrowthAnalytics.kt` | Time series, absolute/annual return, FY totals |
| `src/lib/lookthrough.ts` (190) | Exposure split and allocation tree | `domain/allocation/LookthroughEngine.kt` | Hybrid fund splits, weights summing to full position |
| `src/lib/holdingForm.ts` (368) | Asset-specific field rules, categorization, identifiers | `domain/holdings/HoldingRules.kt` | SGB/NPS/REIT/InvIT/AIF, demat ID/ISIN/PRAN rules |
| `src/lib/homeDashboard.ts` (452) | Books health, concentration, freshness, since-statement movement | `domain/dashboard/BooksHealth.kt` | Warnings, member totals, stale/unknown cost |
| `src/lib/costBasis.ts` (91) | Invested-cost recovery and corrections | `domain/portfolio/CostBasis.kt` | No invented invested cost, event correction behavior |
| `src/lib/snapshot.ts` (387) | Holdings snapshot reconciliation | `domain/statements/SnapshotReconciler.kt` | New/updated/closed holdings, historical order, no false BUY |
| `src/lib/matchHolding.ts` (150) | Stable instrument/folio match | `domain/holdings/HoldingMatcher.kt` | Similar names, same ISIN/different folio/member, collisions |
| `src/lib/pan.ts` (72) | Normalization and PAN ownership checks | `domain/identity/PanRules.kt` | Case normalization, null/ambiguous PAN, no wrong-owner writes |
| `src/lib/statementMail.ts` (283) | Email filename/date/owner resolution | `domain/statements/MailStatementClassifier.kt` | Impersonation, conflicts, reordering, uncertain sender evidence |
| `src/lib/gmailSync.ts` | Gmail sync state machine | `data/gmail/GmailSyncWorker.kt` + domain use case | Cursor pagination, idempotency, retry, staged import |
| `src/lib/eodPrices.ts` (162) | Official EOD price mapping, instrument search | `data/prices/EodRepository.kt` | No future prices, price date/source, exchange selection |
| `src/lib/backup.ts` (237) | Authenticated encrypted backup v2 + legacy | `data/backup/BackupCodec.kt` | `GUDVLT01`, `KOSHA1` golden vectors |
| `src/lib/backupIo.ts` (164), `backupSchedule.ts` (307) | SAF/periodic backups | `data/backup/BackupFiles.kt`, `BackupWorker.kt` | Cancel/revoke permissions, persist tree URI, retention |
| `src/lib/auth.ts` (184), `autoLock.ts` (57), `sessionSecret.ts` (24) | Password, recovery, session lock | `domain/security/VaultAuth.kt`, `data/security/SessionGuard.kt` | Existing hash validation, timeout, process kill |
| `src/lib/license.ts` (403) | Trials/entitlements/bridge | `data/billing/PlayBillingRepository.kt` | All legacy SKUs, Play restores, test vs production |
| `src/lib/terms.ts` (264), `feedback.ts` (196), `onboarding.ts` (82) | Policy acceptance, reviews and intro | `feature/settings`, `feature/onboarding` | Consent versions, snooze/review time rules |
| `src/db/schema.ts` (426), `repository.ts` (989) | SQLite schema and persistence APIs | `core/database`, DAO + transactional repositories | All tables, indexes, triggers, migration and ledger constraints |
| `src/types.ts` (480) | Finance/entity DTO definitions | `core/model` | JSON/binary compatibility and field-level parity |

**Important:** The mapping names are proposed Kotlin destinations. They do not assert that the listed Kotlin files already exist.

## 2. Exact parity policy

- Baseline corpus comes from TS tests, anonymized/synthetic fixtures and deterministic exports. Reuse the existing test helpers `src/lib/casFixtures.ts`, `src/lib/kosha.test.ts`, `src/lib/snapshot.test.ts` and `src/db/db.test.ts` as the initial comparator source, supplemented by new synthetic edge cases.
- For any integer-scale money, quantity or persisted identifier: **exact equality** required, not floating epsilon.
- For XIRR or other floating-point-display-only numerical routines: define a documented numerical tolerance and verify matching rounded UI displays; never use approximate comparisons for cash amounts.
- Date calculations must use explicit India-relevant fiscal-year rules where the current source requires them; locale and device timezone must not silently change historical calculations.
- Freeze time and price-data inputs; never compare a cached legacy price with a freshly downloaded native price.
- Any intended behavior change (e.g. secure rejection of an incomplete snapshot) requires an explicit change request, security test and approved acceptance delta; do not conceal intentional corrections as parity.
- Keep append-only ledger corrections as new events. Verify UI state only publishes after successful DB commit.
- Only delete old TS calculation code after each mapped Kotlin use case has golden parity tests, code review and compatibility sign-off.

## 3. Financial regression scenario catalog

| Scenario | Required output comparison |
| --- | --- |
| Manual holding with no transactions | Total value, invested state and health findings |
| BUY + BUY + SELL | Position, cost basis, realized gains, XIRR |
| Bonus + split + correction | Quantity and immutable ledger history |
| Multi-account same ISIN | Distinct custody/folio/account ownership, no merging |
| Family member removal | Defined allowed cascade with no orphan rows |
| Re-import same CAS | Exactly one snapshot adjustment effect |
| Statement older/newer than current | Chronological acceptance/rejection and correct explanation |
| Zero-value/closed account | Legitimate closure versus incomplete parse |
| SGB coupon and maturity | Rate, due dates and cash flows |
| PPF/SSY/EPF/NPS cases | Product rate and valuation logic as currently implemented |
| Fixed deposit and loan EMI | Interest schedule and catch-up idempotence |
| REIT / InvIT monthly income | Correct scheduled event dates |
| Tax year boundary / grandfathering | Gain classification and tax rule selection |
| Hybrid fund and wrapper exposure | Normalized allocation/segment totals |
| Cache-only price refresh | Existing book and stale-state display retained |
| Backup export/restoration | Cross-implementation roundtrip without loss |

## 4. UI feature-by-feature handoff contract

For each screen port:
1. Record existing screenshots and complete interaction list, including accessibility labels and error states.
2. Expose immutable `UiState` from a ViewModel; keep finance logic in domain use cases.
3. Compare resulting SQL/ledger state after a user action, not just rendered text.
4. Test navigation Back, process death, long-running tasks, keyboard, dynamic theme, large fonts and offline.
5. Confirm user-facing consent/terms/licence behavior is unchanged unless deliberately approved.
6. Implement polished Material3 transitions and haptics **after** functional and data parity pass.
7. Record replacement path and deletion gate in `03-TESTS-CLEANUP-GATES.md`.

## 5. Protected parser acceptance

The CASPARSER's Python files and its result-generation algorithms must remain byte-identical. For every import:
- unchanged engine receives original PDF/password/filename semantics;
- Kotlin adapter validates the existing JSON contract with typed nullability and error codes;
- validator checks completeness, owner, total tolerance and chronology;
- persistence layer performs all-or-nothing commit;
- parsers' generated holdings are never reinterpreted as fresh BUY transactions solely because an intermediate DTO uses that label.

If a golden parser fixture fails, stop the migration and investigate **outside** the parser first.

## 6. First native implementation task ownership

M1 should add only:
- `android-native/` build system with source-controlled manifests and compatible dependencies;
- Material3 theme + light/dark + dynamic color;
- accessible native landing/navigation shell;
- Hilt injection baseline and strict `StateFlow`/UDF ViewModel;
- test fixtures, JUnit tests and Compose UI smoke test;
- CI job proving Kotlin Android compilation and tests.

No live vault write, schema conversion, CASPARSER edits or removal of legacy TS code in M1. That work follows proven data migration contracts.
