# Kosha Phase 1 — CDSL Validation Round 1

Date: 2026-08-30

## Corpus

The supplied `output_unlocked.zip` contains 56 CDSL CAS PDFs spanning July 2021 through July 2026. The PDFs are anonymized and remain outside the repository.

## Round 1 objective

Validate the deterministic summary and holdings-column assumptions before replacing the existing monolithic CAS parser.

## Results

| Validation | Result |
|---|---:|
| CDSL PDFs inspected | 56 / 56 |
| Summary component reconciliation (`CDSL + MF = Total`) | **56 / 56** |
| CDSL demat holding rows structurally reconciled (`quantity × price ≈ value`) | **805** |
| CDSL demat holding arithmetic mismatches after column correction | **0** |
| MF holding rows structurally reconciled (`units × NAV ≈ valuation`) | **577** |
| MF holding arithmetic mismatches after folio/ARN/decimal corrections | **0** |

The corpus contains both ordinary holdings and edge layouts including pledged/frozen balances, ETF-like demat holdings, decimal unit balances, slash-bearing folios, ARN tokens, and wrapped security names.

## Defects discovered while implementing V2

1. Indian ISINs are 12 characters. The initial V2 model incorrectly used an 11-character expression; this was corrected before parser validation.
2. CDSL demat security descriptions can contain numbers such as `FV RS.2/-`. Numeric extraction must therefore use the market-value arithmetic to identify the current balance rather than treating the first number as quantity.
3. Pledged holdings can contain numeric pledge/free balances. Current balance is the balance that reconciles to the stated market value.
4. MF units may be written as `.08`; numeric parsing must preserve a leading decimal without converting it to `8`.
5. MF folio formats include `digits/digits` and short forms such as `1/0`. ARN numbers must never be interpreted as folios or units.
6. Cost basis is accepted only when the CAS explicitly supplies it. No percentage-based cost estimation is permitted.

## Three structural regression scenarios

The repository now contains regression tests for:

- standard equity + MF holdings;
- pledged/frozen demat balances;
- ETF/decimal holdings plus MF folio/ARN layouts.

These tests use reduced, anonymized structural extracts from the supplied corpus and contain no investor identity or account information.

## Important limitation

Round 1 validates the V2 parsing rules against text extracted from all 56 supplied PDFs and validates the reduced TypeScript fixtures. The 56 original PDF binaries are intentionally not committed to GitHub. Full browser `pdfjs-dist` execution and end-to-end Documents-tab import remain a separate validation gate and will be performed before the Phase 1 branch is considered ready for user testing.

## Next gate

Round 2 will cover source-specific transaction parsing and snapshot reconciliation. Round 3 will cover the complete Documents-tab flow, including family-member selection and automatic uppercase PAN password handling.
