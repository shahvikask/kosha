# GrowUrDhan — M3 Financial Domain Engine: Implementation and Verification Gates

**Date:** 2026-10-08  
**Working branch:** `GUD-Android-ChatGPT`  
**Status:** M3 *source implementation in progress*; do not mark complete until the new native CI passes and the persistence/parity gaps below are resolved.  
**Immutable CASPARSER:** No changes to the parser engine, Python invocation or frozen dependencies.

## 1. Legacy code reviewed and ported as pure Kotlin

| TypeScript source | Kotlin source | Behavior |
| --- | --- | --- |
| `src/lib/money.ts`, `positions.ts` | `domain/finance/FinancialEvents.kt`, `PortfolioEngine.kt` | Checked 1e4-money / 1e8-quantity arithmetic, totals by owner, liabilities, unknown opening cost reporting, warning on orphan/mismatched events |
| `src/lib/lots.ts` | `LotEngine.kt` | Deterministic FIFO, long-term thresholds, purchase/sale lots, gain/loss, bonus, split, appended corrections with supersession, non-taxable downward snapshot adjustments, oversell warnings |
| `src/lib/snapshot.ts` | `SnapshotReconciler.kt` | As-of-date ledgers, 1e-6 unit tolerance, same-holding statement-line aggregation, opening instead of false BUY, delta-only adjustment, optional printed-investment cost rebase, no implicit closure of absent custodian holdings |
| `src/lib/taxRules.ts` | `TaxAndReturns.kt` | FY and AY labels, published year lookup, debt MF vs equity classification, SGB certificate exception, pre-2018 grandfathering, BPS calculations |
| `src/lib/returns.ts` | `TaxAndReturns.kt` | Dated cash flows and display-only Newton/bisection XIRR; returns null when inputs don't justify an IRR |
| `src/lib/positions.ts`, M2 `VaultRepository` | `FinancialUseCases.kt` | Read-only `CalculateFamilyPortfolio` and `PreviewStatementReconciliation` use cases |

**All financial calculations are deterministic and use scaled `Long` / checked `BigInteger` intermediates.** XIRR uses `Double` only to derive a displayed rate; it does not write floating money to the vault.

## 2. Automated golden cases

`domain/src/test/.../FinancialGoldenTest.kt` currently contains **17 JUnit5 test cases**, populated from synthetic scenario oracles in `src/lib/kosha.test.ts`, `src/lib/snapshot.test.ts`, `src/lib/lots.ts` and `src/lib/taxRules.ts`.

Key exact expected values:
- 100 units @ ₹2,450 + 50 units @ ₹2,600 − 40 units sold @ ₹2,750 -> **110 units, ₹2,77,000 remaining cost, ₹12,000 realized gain, ₹3,24,500 valuation @ ₹2,950**; first lot held **391 days**.
- Bonus + 2-for-1 split preserves invested cost.
- Downward snapshot adjustment is not a taxable SELL; overselling is flagged.
- CORRECTION supersedes opening balance without doubling quantity/cost.
- FY/AY boundary: 31 Mar 2025 -> FY2024-25; 1 Apr 2025 -> FY2025-26.
- Debt/gilt mutual fund tax classification, SGB demat vs certificate, 31 January 2018 grandfathering, 12.5% BPS rate.
- Per-member assets and liabilities; missing holdings, cross-member events and unknown invested cost never silently inflate totals.
- Same-holding statement lines coalesce; re-import yields MATCH; later file suggests **delta only**, e.g. 500 units.
- Missing custodian lines are **left untouched**; owner mismatch blocks acceptance.
- Contribution cost rebasing emits CORRECTION, never a new purchase.
- XIRR is null without positive and negative flows, and roughly 10% on a one-year 10% gain.
- Tiny fractional values truncate at the scaled integer boundary, matching the existing positions implementation.

**Verified M3 calculation baseline:** [run #37796366761](https://github.com/shahvikask/kosha/actions/runs/37796366761), commit `e59bf821`, **completed / success**, including the M3 golden JVM tests, native vault and Compose instrumentation, APK builds and CASPARSER guard. **The newer schema-v2 persistence increment still requires its own separate CI pass.**

## 3. Data compatibility and safety boundaries

M3 now implements **native SQLCipher schema v2**, adding rich ledger metadata without rebuilding the append-only v1 table, together with a transactional batch-writer, **atomic `VaultBook` reads** and portable backup **format 2**. An authenticated format-1 development backup may still be restored into an empty v2 vault. See [09-M3-LEDGER-PERSISTENCE-AND-ATOMIC-READS.md](09-M3-LEDGER-PERSISTENCE-AND-ATOMIC-READS.md) for exact DDL, migration strategy and new tests.

- Existing v1 ledger rows remain intact, with unknown unit prices and correction lineage explicitly unknown. A missing legacy correction ancestor is not guessed.
- M3 full-information events cover unit prices, charges, bonus/split/statement adjustments and append-only supersession, with owner-integrity and rollback checks.
- `CalculateFamilyPortfolio` and `PreviewStatementReconciliation` fetch one SQL-transactionally consistent family book; no separate member/holding/event read race.
- `SnapshotReconciler.review` remains **review-only**. Acceptance of a statement into persisted holdings requires explicit user confirmation and a TOCTOU-safe committed workflow.
- The new v2 increment must **pass its own CI** before its storage compatibility and encryption tests may be marked verified; the older M3 base run does not test v2.

## 4. Remaining M3 parity work before milestone closure

1. **Verify rich ledger persistence in the NEW run:** schema-v2 migration, encrypted v1 data survival, new metadata, atomic write/reads and both v1/v2 backup round trips. Implementation and regression tests are committed; actual v2 CI outcome pending.
2. **Commit a reviewed statement atomically:** verify book fingerprint and user acceptance in the same SQL transaction; current preview cannot safely commit after a stale review.
3. **Complete legacy financial coverage:** `interest.ts`, `emi.ts`, `growth.ts`, `lookthrough.ts`, `returnMetrics.ts`, `costBasis.ts` and liability edge cases need additional oracles; match the existing application and document intentional safety improvements.
4. **Ensure exact cross-runtime results:** Run TypeScript and Kotlin against identical input vectors, verify rounding and fiscal-year details before deleting legacy JS.
5. **Integrate with native UI carefully:** do not expose unchecked warnings as a trustworthy monetary summary; implement owner-verified error states and session gate.
6. **Run successful CI:** Kotlin domain tests, existing M2 emulator tests, CASPARSER immutable guard, and unchanged hybrid reference tests.

## 5. Verification workflow handoff

After the implementation commits, provide the exact final commit SHA and its Native Android GitHub workflow run ID to the owner. Do not wait/poll repeatedly for a long build. The owner will report PASS/FAIL, and any failure must be triaged from the actual CI logs before marking M3 done.

## 6. Cleanup policy

No working React/Tauri financial code may be deleted merely because these pure Kotlin tests compile. Keep all JS calculation and synthetic test fixtures as reference oracles until every required parity mapping and Android integration gate succeeds.
