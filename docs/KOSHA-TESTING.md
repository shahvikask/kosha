# Kosha Testing Baseline

## Testing principle

Financial correctness and privacy are release-blocking concerns. UI polish is not a substitute for accounting correctness.

## Required test layers

### Unit tests

Cover:

- fixed-point money arithmetic
- fixed-point quantity arithmetic
- XIRR and return calculations
- transaction classification
- corporate-action calculations
- source parsing helpers
- identifier normalization
- validation rules

### Parser golden tests

Each supported source needs real or legally usable representative statement fixtures with expected normalized output.

Minimum structure:

```text
fixture PDF
    -> parser
    -> normalized JSON
    -> deep comparison with expected JSON
```

Synthetic fixtures remain useful for edge cases but must not be the only parser evidence.

### Reconciliation tests

Test:

- exact match
- new position
- quantity increase
- quantity decrease
- closed position
- unexplained conflict
- partial sale
- corporate action
- repeated import
- out-of-order statement imports
- multiple accounts containing the same instrument

### Database tests

Test append-only enforcement, schema constraints, migrations, duplicate protection, historical price persistence and recovery after interrupted import.

### Privacy tests

Verify that:

- CAS content is not sent over the network during local import
- sensitive values are absent from logs
- temporary documents are released after import
- PAN/password values are not persisted accidentally
- exports are explicitly user initiated

## CI quality gates

A pull request that changes accounting, parsing, reconciliation, schema or security code must run the relevant automated tests before merge.

Future CI should include:

- type checking
- unit tests
- parser golden tests
- database tests
- build
- dependency/security audit where practical

## Regression policy

Every production bug involving incorrect financial data must produce a regression test before the fix is considered complete.

## Release confidence

The application should not claim a statement source is production-supported until its parser has a representative corpus covering normal layouts and known layout variations.
