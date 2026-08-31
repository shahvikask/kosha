# Kosha Phase 1B / 1C — CDSL Transactions and Documents Import

## Scope

Phase 1B adds source-specific CDSL transaction parsing without changing the canonical ledger model. Phase 1C activates the CDSL V2 pipeline through the existing Documents import entry point.

## 1B — Transaction parser

`src/lib/cdslV2Transactions.ts` parses two CDSL transaction families:

1. Demat transaction tables (`Opening / Credit / Debit / Closing / Stamp Duty`).
2. MF/RTA transaction tables (`Amount / NAV / Price / Units / Stamp Duty / Distribution / Withdrawal`).

Important rules:

- Transaction reference numbers are never treated as money or units.
- Blank `--` credit/debit columns are preserved while parsing.
- MF transaction amounts, NAV and units are read from their source columns.
- STP, lateral-shift and switch transactions are flagged as transfers and are not automatically treated as cash purchases/sales.
- Demat CAS transaction tables do not provide rupee trade consideration. Kosha therefore does **not** invent a transaction price or cost for these rows. Snapshot reconciliation remains the authoritative position mechanism.

## 1C — Documents tab integration

The existing `DocumentsScreen` continues to call `parseStatementFile`. The implementation behind that entry point now routes CDSL documents to the V2 parser while retaining the legacy parser for NSDL and CAMS/KFintech until their V2 parsers have passed their required corpus validation.

The selected family member's `panForStatements` value remains the password source. The parser normalizes the supplied password to uppercase before PDF decryption, so a stored lower-case PAN cannot prevent a valid CAS import.

The existing Documents workflow remains:

`Family member → PAN password → PDF decrypt → CDSL V2 parse → validation/reconciliation → import`

No CAS PDF bytes are persisted to GitHub or sent to an external parser.

## Regression coverage

- `cdslV2Transactions.test.ts` covers demat credits, corporate-action credit/debit rows, MF purchase rows, STP in and STP out rows, and transaction-reference handling.
- `cdslV2Parser.test.ts` covers canonical decimal normalization, pledged balances, ARN/folio contamination and decimal units.
- GitHub Actions runs TypeScript validation, model validation, CDSL holding regression tests and CDSL transaction regression tests.

## Known limitation carried deliberately into Phase 1

The current `StatementImportRecord` model only supports BUY/SELL/DIVIDEND/INTEREST. It cannot represent a demat transfer, bonus, split or lateral shift without semantic loss. Phase 1 therefore does not silently convert those source events into cash transactions. A dedicated corporate-action/transfer event model will be addressed before production-grade historical tax reconstruction.
