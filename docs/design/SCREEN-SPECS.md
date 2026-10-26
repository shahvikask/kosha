# Concept screen specs

> **GrowUrDhan first market release — owner decision D57 (2026-10-26):** Financial vault backup/export, automated SAF backup, backup-file import and financial-data recovery/restore are **not available in the initial release**. They belong to the next upgrade. This note supersedes any older proposed screens, flows, requirements or tests in this document; those descriptions are retained only as legacy/future-upgrade references. Offline account-password recovery and standard CAS/CSV/PDF statement imports remain in scope. No backup or recovery must be advertised as active. See [base-release scope](../implementation/BASE-RELEASE-SCOPE.md) and spec v1.3.

These three screens are Compose source in `ConceptScreens.kt`. They use synthetic content. They are not the finished product screens.

## Unlock

- Mark, wordmark, and tagline.
- A lock banner: the vault remains locked and no book is open.
- States that there is no password field because this build has no vault.
- One control, “View sample layout”, at least 48dp tall. It does not call an unlock method.
- Sample home and sample holding keep the same lock banner and can return to the locked screen.

## Home

- Same lockup.
- One hero amount, `₹12,34,567.89`, labeled as a sample and not a ledger balance.
- One control, “Open sample holding”.
- No chart, no family lens, and no bottom navigation.

## Holding detail

- Back control returns to the sample home.
- Title “Sample listed equity”.
- Notice: acquisition cost is unknown and is not invented.
- No edit, sell, or ledger action.

## Not specified here

Portfolio, statements, family, insights, forms, and settings still need their Section 6A specs in the milestone that implements them.
