# Kosha Phase 1 — CDSL Validation Round 1

Date: 2026-09-01

## Corpus

The supplied `output_unlocked.zip` contains 56 anonymized CDSL CAS PDFs spanning July 2021 through July 2026. The PDFs remain outside the repository.

## Round 1 objective

Validate the deterministic summary and holdings-column assumptions before replacing the existing monolithic CAS parser.

## Baseline findings from the original implementation

The original parser was not safe for this corpus. It could double-count summary values, contaminate security names from adjacent rows, interpret ARN/folio numbers as financial values, and fall back to estimated cost values. These were treated as blocking defects rather than patched with heuristics.

## User-reported Sep-2024 failure

File: `SEP2024_AA07970379_TXN_unlocked.pdf`

The PDF summary states:

- CDSL Demat Accounts: `87,891.15`
- Mutual Fund Folios: `1,50,46,788.14`
- Total Portfolio Value: `1,51,34,679.29`

The user-imported result was materially wrong: several MF names were concatenated with neighbouring rows, multiple holdings showed `0 units` / `₹0` valuation, and the resulting portfolio was approximately `₹64.32 lakh` rather than the statement total of `₹1.513 crore`.

The failure is reproducible from the supplied PDF and is therefore a blocking Phase 1 defect.

## Root causes identified in the V2 implementation

1. **ISIN detector too permissive.** A generic 12-character alphanumeric expression could treat words such as `TRANSACTIONS` as ISIN-like tokens. V2 now requires the final ISIN character to be numeric.
2. **Sparse demat rows.** CDSL PDF text extraction removes `--` cells. A valid demat row can therefore contain only four numeric values: current balance, market price, and market value plus another balance. Requiring seven numeric values dropped legitimate holdings.
3. **Multi-page MF table termination.** CDSL repeats `About CDSL` and column headers on subsequent pages. Treating those headers as the end of the MF section caused later MF rows to disappear. V2 now stops at the actual end of the table (`Grand Total` / unclaimed-redemption section).
4. **Security-name contamination.** The previous `nearestName()` implementation looked only backwards from the ISIN. For wrapped rows and repeated schemes, this attached the previous scheme's continuation text to the current security. V2 now prefers the text immediately before the ISIN and uses the following continuation line only when it is not another row.
5. **Duplicate scheme/folio rows.** Some CDSL CAS statements print multiple rows for the same ISIN/folio. V2 aggregates exact ISIN+folio duplicates so the same snapshot is not reconciled twice against one holding.
6. **No financial estimation.** V2 continues to reject the old practice of inventing cost basis when the CAS does not provide it.

## Sep-2024 expected V2 extraction

For the supplied Sep-2024 PDF, the deterministic source-table extraction now identifies:

- 7 demat holding rows (6 equity positions + 1 ETF-like demat position)
- 33 raw MF rows
- 13 aggregated MF ISIN/folio positions
- MF valuation sum: `1,50,46,788.14`
- CDSL demat valuation sum: `87,891.15`
- Grand total: `1,51,34,679.29`

These values are obtained directly from the PDF table columns; no estimated values are used.

## 56-file status after the latest parser corrections

The latest local corpus replay shows the Sep-2024 file reconciles exactly. However, the same deterministic row rules still have **22 format-specific summary mismatches across the other 55 files**. Those are not being hidden or treated as passes. They are the next CDSL parser-fix queue and must be resolved before the 56-file acceptance gate.

## Regression tests

The repository contains reduced, anonymized structural tests for:

- standard equity + MF holdings;
- pledged/frozen demat balances;
- decimal MF units and ARN/folio layouts;
- Sep-2024 CDSL row structure, including multi-page MF behaviour.

## Current CI status

TypeScript validation and the CAS V2 model validation are passing on the latest commit. The latest CDSL parser regression run is still in progress at the time of this update; the branch is not considered green until GitHub reports a successful run.

## Acceptance gates still outstanding

1. Resolve the remaining 22 format-specific mismatches in the 56-file CDSL corpus.
2. Complete three independent validation rounds against the corpus.
3. Validate browser `pdfjs-dist` extraction, not only command-line text extraction.
4. Validate the complete Documents-tab import path.
5. Validate family-member selection and automatic uppercase PAN password handling.
6. Only after these gates pass should Phase 1 be offered for Google AI Studio user testing.
