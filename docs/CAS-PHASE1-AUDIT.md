# Kosha Phase 1 — CAS Ingestion Engine V2 Audit

**Status:** Baseline audit complete; application code has not yet been modified on this branch.

## 1. Test corpus

- 56 unlocked CDSL CAS PDFs supplied by the project owner.
- Corpus spans Jul-2021 through Jul-2026.
- All 56 PDFs passed independent `pdfinfo` validation.
- Page count range: 10–52 pages; average 19.05 pages.
- Two pairs are duplicate textual content with alternate filenames (Jun-2024 and Jul-2024); all 56 files remain distinct files in the supplied corpus.
- The supplied PDFs are treated as source evidence/ground truth, not as an application database.
- The supplied files still contain some statement metadata beyond the fields the owner intended to anonymize. No such personal values are copied into this report or into test fixtures.

## 2. Round 1 — Summary control reconciliation (56/56)

The first pass replayed the current CDSL summary extraction logic against all 56 PDFs and compared it with the CDSL summary table in each PDF.

### Findings

1. **CDSL demat valuation is systematically double-counted.** The current implementation scans every line containing `CDSL Demat Account|NSDL Demat Account` and adds the last numeric token. In the supplied corpus, this causes the summary-row valuation and the detailed account-row valuation to be added together. Result: 0/56 exact demat-summary matches.
2. **MF summary extraction is mostly correct but structurally fragile.** 51/56 files matched the PDF MF valuation exactly in the replay; 5/56 2024 files selected the folio count rather than the valuation in the first matching text occurrence.
3. **Grand Total extraction is the strongest current control.** The current `Grand Total` regex matched the PDF total in the corpus when the PDF text layout exposed the expected row.
4. **The parser currently returns a grand total even when component extraction is wrong.** This can hide component-level errors; component reconciliation must therefore be a hard validation step rather than relying on grand total alone.

## 3. Round 2 — Detailed holding extraction replay

Three representative scenarios were used before code modification:

### Scenario A — Early CDSL layout (Apr-2022)

- PDF contains CDSL equity holdings plus MF sections.
- Current holding heuristic extracted 6 demat rows from the visible holding section.
- The extracted demat valuation was substantially below the PDF CDSL demat summary.
- The parser also selected an incorrect market-price candidate for at least one row because the heuristic searches combinations of numeric tokens instead of binding to column positions.

### Scenario B — Mid-period layout with richer MF data (Sep-2024)

- PDF contains CDSL holdings, MF/RTA holdings and transaction tables.
- Current heuristic produced only 6 INE demat rows and 19 INF MF rows in the replay, while the PDF summary reports a materially larger MF portfolio.
- A severe false-positive was observed in MF parsing: folio numbers can be interpreted as unit quantities or other numeric fields. In the replay, this produced an implausible valuation in excess of the PDF portfolio value by many orders of magnitude.
- This is a blocking financial-integrity defect.

### Scenario C — Current layout with NPS/AIF/ETF content (Jul-2026)

- PDF contains multiple modern sections including MF, ETF/liquid-fund style holdings and AIF-related material.
- Current heuristic again produced implausible MF quantities when long folio identifiers appeared on the same line as an ISIN.
- AIF extraction contains hard-coded fallback quantity/price values in the current implementation; these are unacceptable for financial data ingestion.

## 4. Round 3 — Transaction extraction / import-flow audit

The third pass reviewed CDSL transaction rows and the current transaction parsing logic.

### Findings

1. **Demat CDSL transaction rows are not column-bound.** CDSL transaction rows are commonly laid out with ISIN/security first and date later, while the current transaction parser only accepts lines whose first token is a date. This means many demat transactions are not imported through the current transaction path.
2. **MF transaction rows can contain reference numbers before the amount.** Example structures include transaction descriptions such as STP entries followed by a numeric reference before the actual amount. The current parser takes the first numeric token as `amount`, so reference numbers can become monetary amounts.
3. **The current MF parser uses absolute values for amounts.** This destroys the original sign information in statement rows. Transaction semantics must be derived from the transaction type and the source columns, not by blindly applying `Math.abs()`.
4. **`ParsedCASScheme.transactions` is defined but the CDSL transaction loop does not populate scheme transaction arrays.** Transactions are pushed directly to `importRecords`, leaving the normalized scheme object incomplete.
5. **Current duplicate detection is name/date based in several parser paths.** This is insufficient for financial imports. ISIN + folio/account + date + transaction fingerprint should be used where available.

## 5. Documents-tab / family-member audit

The existing UI already has the desired basic UX direction:

- Target family member is selectable.
- Changing the target member clears staged import state.
- The member's `panForStatements` is loaded into the password state.
- The file picker attempts automatic parsing when a stored PAN exists.
- Settings uppercases the stored statement PAN before persistence.

However, Phase 1 must validate the complete runtime chain rather than relying on state inspection:

`Family member → stored statement PAN → uppercase password → PDF decryption → text extraction → CAS detection → parser → reconciliation → import`

The UI acceptance test must be performed later by the project owner in Google AI Studio as specified in the Phase 1 rules.

## 6. Import/reconciliation finding outside the parser

The current `importStatementRecords` implementation constructs `acceptedIds` from **all** snapshot lines immediately before calling `eventsToCommit()`. This means the function does not appear to honor the UI's `acceptedHoldingIds` state when committing snapshot reconciliation results. This must be reviewed and corrected as part of the import-flow hardening if confirmed by the existing snapshot implementation.

## 7. Existing test architecture

The repository has a useful existing unit-test structure and synthetic CAS fixtures. The fixtures intentionally model a three-statement time series and test re-import behavior. They are useful regression tests but cannot substitute for the 56 real CDSL PDFs supplied for this project.

The Phase 1 test strategy will retain synthetic fixtures for deterministic unit tests and add anonymized corpus-derived regression cases without storing personal data in source control.

## 8. Immediate design conclusions

The current parser should **not** be patched with more numeric heuristics. The audit shows that the primary defect is structural: PDF text extraction is being interpreted as free-form numbers rather than as a document with sections, headers, rows, columns and source-specific semantics.

The V2 design should therefore use:

1. Source/document fingerprinting.
2. Page/section segmentation.
3. Column/header detection.
4. Row reconstruction using PDF coordinates where available.
5. Separate CDSL demat, CDSL MF/RTA, AIF and transaction parsers.
6. Explicit typed normalized records.
7. Strict numeric validation; never invent values.
8. Cross-checks against PDF summary controls.
9. ISIN/folio/account based identity and idempotency.
10. A validation report that records every discrepancy.
11. No inferred invested/cost values when the statement does not provide them.
12. Snapshot reconciliation only after parsing passes validation.

## 9. Phase 1 acceptance gates

The implementation is not complete until:

- 56/56 supplied CDSL PDFs can be opened and parsed without personal data leaving the device.
- Each test records PDF-vs-import comparisons.
- Three validation rounds are completed and findings are documented.
- No fabricated NAV, quantity, valuation or invested amount is introduced.
- Summary valuation components reconcile to the PDF within documented rounding tolerance.
- Transaction rows preserve source semantics and signs.
- Re-import is idempotent.
- Documents-tab family-member selection automatically supplies the normalized uppercase statement PAN.
- The project owner successfully performs the real Documents-tab import in Google AI Studio.
- Regression tests cover every defect discovered during this audit.

**No production application code was changed while producing this audit.**