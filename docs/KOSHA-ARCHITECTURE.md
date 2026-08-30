# Kosha Architecture Baseline

**Status:** Baseline proposal — Phase 0
**Purpose:** Source of truth for the architecture and engineering direction of Kosha.

## 1. Product principle

Kosha is a **local-first, privacy-first personal portfolio tracking application**.

The default product promise is:

> Financial data should not leave the user's device.

Portfolio holdings, transactions, CAS documents, personally identifiable financial information, derived analytics, and the local accounting ledger are therefore processed and stored on-device by default.

Kosha must work offline for core portfolio functions. Internet connectivity may be used only for explicitly enabled functions such as market-data updates, reference-data updates, application updates, or an optional future user-controlled backup/sync feature.

## 2. Data boundary

### Default

- No mandatory cloud database.
- No mandatory cloud portfolio API.
- No server-side CAS processing.
- No automatic upload of CAS PDFs.
- No third-party analytics that receives financial portfolio data.
- No raw PAN in ordinary logs, telemetry, URLs, or error reports.

### Future optional services

Any backup/sync service must be explicitly opt-in, clearly disclosed, independently encrypted, and designed so that the user controls the encryption key. It must never silently become the default data path.

## 3. Logical architecture

```text
UI (Web/PWA or Android)
        |
        v
Application Services
        |
        +--> Portfolio Service
        +--> Import Service
        +--> Analytics Service
        +--> Document Service
        |
        v
Domain / Financial Engine
        |
        +--> Ledger
        +--> Reconciliation
        +--> Valuation
        +--> Return Engine
        +--> Corporate Actions
        +--> Security Master
        |
        v
Local Data Repository
        |
        +--> Encrypted SQLite / SQLCipher
        +--> Local document storage (optional)
```

The UI must not implement accounting rules directly. Financial rules belong in the domain/application layers so that web and Android clients can share the same behavior/specification.

## 4. Core domain boundaries

Kosha must keep these concepts distinct:

- **Instrument:** the financial security itself, identified primarily by ISIN when available.
- **Account:** the place where an instrument is held (demat account, mutual-fund folio, etc.).
- **Position:** quantity of an instrument held by an owner through an account at a point in time.
- **Ledger event:** an accounting event explaining how a position changed.
- **Statement position:** an externally reported snapshot from a source document.
- **Import batch:** one ingestion operation with provenance and idempotency metadata.

## 5. Ledger rules

1. The financial ledger is append-only.
2. Existing ledger events must not be updated or deleted through application APIs.
3. Corrections are represented by new compensating events.
4. CAS holdings are snapshots, not BUY transactions.
5. Imported snapshots must reconcile against the ledger rather than being blindly added to it.
6. Every imported event must retain source/provenance information.
7. Re-importing the same source must be idempotent.

## 6. Financial precision

- Never use JavaScript floating-point values as the canonical representation of money or quantity.
- Financial import DTOs should preserve decimal values as strings until conversion into fixed-point/scaled integer representation.
- Money and quantity scales must be explicit and centralized.
- No fabricated or guessed financial values may enter the ledger.
- Missing source values remain null/unknown and must be surfaced through validation/review.

## 7. Import pipeline

```text
Document
  -> Source detection
  -> Source-specific parser
  -> Canonical normalized document
  -> Validation
  -> Security Master resolution
  -> Snapshot reconciliation
  -> User review where required
  -> Ledger events
  -> Derived positions / analytics
```

Source-specific parsers must remain separate. At minimum the architecture must support independent NSDL and CDSL CAS parsers, with future adapters for CAMS, KFintech and other supported statement sources.

## 8. Security Master

ISIN is the primary security identity whenever available. A Security Master maps source identifiers to a canonical instrument and can contain:

- ISIN
- security name
- exchange symbol(s)
- asset class
- sub-classification
- AMC/RTA
- AMFI code where applicable
- issuer
- sector/taxonomy
- wrapper/accounting classification

Security identity must not depend on a display name because names can change and source formatting differs.

## 9. Offline-first behavior

The following must work without Internet access:

- viewing portfolio
- viewing holdings and transactions
- importing supported CAS documents
- reconciliation
- XIRR and supported analytics
- historical portfolio views
- searching local records
- exporting user data

Network access is optional for external reference/market data and application maintenance.

## 10. Web-to-Android strategy

The web application is the current product-development environment. The long-term product is a commercial Android application.

The domain model, financial rules, import contract, validation rules and database semantics must therefore remain client-independent. Android should be a new presentation/runtime client over the same logical domain architecture, not a second implementation of accounting rules.

## 11. Engineering principles

- Prefer deterministic parsing over LLM extraction for financial facts.
- AI may assist with ambiguous extraction only as a bounded fallback, never as an unchecked source of financial truth.
- Every parser change must have regression tests.
- Critical calculations require independent test vectors.
- Schema migrations must preserve historical data.
- Privacy failures are treated as release-blocking defects.
- `main` must remain stable; feature work is performed on branches and merged only through reviewed pull requests.
