# Kosha CAS Ingestion Specification Baseline

## Scope

The CAS ingestion engine converts supported investment statements into a canonical normalized document without inventing financial facts.

## Supported-source architecture

Each source has an independent adapter:

```text
CAS / Statement Detector
        |
        +-- NSDL CAS Parser
        +-- CDSL CAS Parser
        +-- CAMS Parser (future)
        +-- KFintech Parser (future)
        |
        v
Canonical Normalized Document
```

The common contract must not contain source-specific assumptions.

## Canonical document

A normalized document should contain, where reported:

- source/provider
- statement period
- investor metadata
- accounts/demat identifiers
- mutual-fund folios
- holdings/positions
- transaction records
- corporate actions
- valuation fields
- source identifiers
- parser version
- validation results
- provenance references

## Extraction rules

1. Prefer deterministic extraction from the source layout.
2. Preserve source decimal values as strings at the parser boundary.
3. Do not guess a missing financial amount or quantity.
4. Do not convert a statement snapshot into a trade solely because a quantity differs from the ledger.
5. Preserve unknown fields as null with a validation/review flag.
6. Use ISIN as canonical security identity whenever available.
7. Preserve folio, DP ID/client ID and other source identifiers as account/provenance attributes.

## Validation

Every normalized position should be checked for:

- valid date formats
- valid numeric decimal representations
- non-negative/allowed quantity semantics
- ISIN shape where applicable
- internal value consistency where the source reports multiple related fields
- account/folio association
- duplicate source records

Validation must produce structured severity levels such as INFO, WARNING and ERROR.

## Confidence

Parser confidence is metadata for review prioritization, not permission to fabricate values. A low-confidence extraction must surface the affected field/record for review.

## Snapshot reconciliation

Statement positions are external snapshots. The reconciliation engine compares them with the ledger-derived state as of the statement date. Differences become reconciliation candidates and are classified only when evidence supports the classification.

## Source evolution

Parsers must be versioned. A change in a source PDF layout should create a parser regression fixture before release. Parser version is stored with each import batch so historical imports remain explainable.

## NSDL implementation priority

NSDL CAS is a first-class source and must have a dedicated parser and representative test corpus before Kosha claims production NSDL support. Do not infer NSDL structure from CDSL patterns.
