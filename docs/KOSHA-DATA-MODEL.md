# Kosha Data Model Baseline

## Purpose

Define the canonical financial-domain model independently of UI and storage implementation.

## Core entities

### Entity / Investor

Represents a person or legal owner whose portfolio data is tracked. Family portfolios may contain multiple entities.

### Account

Represents the custody/account container: NSDL demat, CDSL demat, mutual-fund folio, broker account, etc.

### Instrument

Represents the canonical security. ISIN is the primary identifier whenever available. Display names and symbols are attributes, not identity.

### Position

Represents the quantity of an instrument owned by an entity through an account at a point in time. Positions are derived from ledger state and/or reconciled external snapshots; they are not the accounting source of truth.

### Ledger Event

An immutable accounting event that changes a position or records an economically relevant event. Corrections use compensating events.

### Statement Position

A source-reported snapshot from a CAS or other statement. A statement position must never automatically become a BUY/SELL transaction merely because it appears in a statement.

### Import Batch

One document ingestion operation. It records source, document period, parser version, status, and provenance.

### Source Document

Metadata about the imported document. Store only what the privacy policy permits; raw documents should not be retained by default.

### Price Observation

A dated external market/reference price observation. Historical observations are immutable facts from a named source.

## Relationship model

```text
Entity
  |
  +-- Account
  |     |
  |     +-- Position -> Instrument
  |
  +-- Ledger Events -> Account + Instrument
  |
  +-- Import Batches

Import Batch
  |
  +-- Statement Positions -> Account + Instrument
  |
  +-- normalized source records
```

## Canonical identity rules

1. Prefer ISIN for listed securities, ETFs, bonds, REITs/InvITs and other securities where available.
2. Mutual funds may additionally require AMFI scheme code, folio, AMC/RTA and plan/option attributes.
3. Source-specific identifiers must be retained as provenance but must not become canonical identity when a stronger identifier exists.
4. Instrument names are mutable display attributes.

## Monetary and quantity rules

- Canonical persisted money uses fixed-point/scaled integer representation.
- Canonical persisted quantity uses fixed-point/scaled integer representation.
- Import boundaries preserve source decimal values as strings until validated and converted.
- Never silently round a source amount without recording the rule.
- Null means unknown/not reported; zero means explicitly zero.

## Provenance

Every imported fact that affects portfolio state should be traceable to:

- source provider
- source document/import batch
- statement period/date
- source account/folio
- parser version
- source record fingerprint where applicable

## Idempotency

Repeated import of the same document or equivalent source record must not create duplicate economic events. File hashes protect document-level idempotency; deterministic source-record fingerprints protect record-level idempotency.

## Snapshot reconciliation

For each statement position:

```text
External snapshot quantity
        -
Ledger-derived quantity as of snapshot date
        =
Reconciliation delta
```

The delta is not automatically classified as a trade. Classification requires transaction evidence or an explicit reconciliation policy. Unexplained deltas remain reviewable adjustments.

## Derived data

Portfolio market value, allocation, gain/loss, XIRR, CAGR and similar analytics are derived views. They must be recomputable from canonical data and must not replace the ledger.
