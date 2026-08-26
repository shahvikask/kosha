/**
 * Physical schema. Implements SRS Annex A §A3.
 *
 * This DDL is platform-neutral and runs unchanged on:
 *   - wa-sqlite + OPFS   (browser)
 *   - rusqlite/SQLCipher (Tauri desktop)
 *   - SQLite + SQLCipher (Tauri Android)
 *
 * Money is INTEGER scaled 10,000. Quantity is INTEGER scaled 100,000,000.
 * No REAL columns anywhere — floating point is prohibited (SRS-LED-007).
 */

export const SCHEMA_VERSION = 1;

export const SCHEMA_V1 = `
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS vault_meta (
  key         TEXT PRIMARY KEY,
  value       TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entity (
  entity_id         TEXT PRIMARY KEY,
  entity_type       TEXT NOT NULL,
  display_name      TEXT NOT NULL,
  pan_masked        TEXT,
  date_of_majority  TEXT,
  guardian_id       TEXT REFERENCES entity(entity_id),
  is_active         INTEGER NOT NULL DEFAULT 1,
  created_at        TEXT NOT NULL,
  CONSTRAINT ck_entity__type CHECK (entity_type IN (
    'INDIVIDUAL','HUF','PROPRIETORSHIP','PRIVATE_TRUST','PARTNERSHIP_FIRM'))
);

CREATE TABLE IF NOT EXISTS account (
  account_id        TEXT PRIMARY KEY,
  entity_id         TEXT NOT NULL REFERENCES entity(entity_id),
  account_type      TEXT NOT NULL,
  custodian         TEXT NOT NULL,
  identifier_masked TEXT,
  base_currency     TEXT NOT NULL DEFAULT 'INR',
  nominee           TEXT,
  opened_on         TEXT,
  closed_on         TEXT,
  created_at        TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_account__entity ON account(entity_id);

CREATE TABLE IF NOT EXISTS instrument (
  instrument_id     TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  category          TEXT NOT NULL,
  sub_category      TEXT NOT NULL DEFAULT '',
  symbol_or_folio   TEXT,
  isin              TEXT,
  currency          TEXT NOT NULL DEFAULT 'INR',
  exposure_node     TEXT,
  l2_node           TEXT,
  wrapper           TEXT,
  allocation_split  TEXT,
  fmv_31jan2018_minor TEXT,
  is_user_created   INTEGER NOT NULL DEFAULT 1,
  created_at        TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_instrument__isin ON instrument(isin) WHERE isin IS NOT NULL;

CREATE TABLE IF NOT EXISTS holding (
  holding_id        TEXT PRIMARY KEY,
  instrument_id     TEXT NOT NULL REFERENCES instrument(instrument_id),
  entity_id         TEXT NOT NULL REFERENCES entity(entity_id),
  account_id        TEXT REFERENCES account(account_id),
  current_price_minor      INTEGER NOT NULL DEFAULT 0,
  benchmark_price_minor    INTEGER,
  sgb_valuation_mode       TEXT,
  last_price_updated       TEXT,
  price_source             TEXT,
  needs_review             INTEGER NOT NULL DEFAULT 0,
  review_reasons           TEXT,
  created_at        TEXT NOT NULL,
  CONSTRAINT ck_holding__price CHECK (current_price_minor >= 0)
);
CREATE INDEX IF NOT EXISTS ix_holding__entity ON holding(entity_id);

-- ===== THE LEDGER: append-only (SRS-LED-001) =====
CREATE TABLE IF NOT EXISTS ledger_event (
  ledger_event_id     TEXT PRIMARY KEY,
  entity_id           TEXT NOT NULL,
  holding_id          TEXT NOT NULL,
  holding_name        TEXT NOT NULL,
  account_id          TEXT,
  event_type          TEXT NOT NULL,
  trade_date          TEXT NOT NULL,
  settlement_date     TEXT,
  quantity_scaled     INTEGER NOT NULL DEFAULT 0,
  unit_price_minor    INTEGER NOT NULL DEFAULT 0,
  total_amount_minor  INTEGER NOT NULL DEFAULT 0,
  charges_minor       INTEGER NOT NULL DEFAULT 0,
  currency            TEXT NOT NULL DEFAULT 'INR',
  supersedes_event_id TEXT,
  import_batch_id     TEXT,
  source_ref          TEXT,
  notes               TEXT,
  entered_at          TEXT NOT NULL,
  entered_by          TEXT NOT NULL DEFAULT 'owner',
  CONSTRAINT ck_event__type CHECK (event_type IN (
    'OPENING_BALANCE','BUY','SELL','BONUS','SPLIT','DIVIDEND','INTEREST',
    'VALUATION_UPDATE','REPAYMENT','CORRECTION','SNAPSHOT_ADJUSTMENT')),
  CONSTRAINT ck_event__correction CHECK (
    (event_type = 'CORRECTION') = (supersedes_event_id IS NOT NULL)),
  CONSTRAINT ck_event__dates CHECK (
    settlement_date IS NULL OR settlement_date >= trade_date)
);

CREATE TRIGGER IF NOT EXISTS tg_ledger_event__no_update
BEFORE UPDATE ON ledger_event
BEGIN
  SELECT RAISE(ABORT, 'ledger_event is append-only: record a CORRECTION event');
END;

CREATE TRIGGER IF NOT EXISTS tg_ledger_event__no_delete
BEFORE DELETE ON ledger_event
BEGIN
  SELECT RAISE(ABORT, 'ledger_event is append-only: record a CORRECTION event');
END;

CREATE INDEX IF NOT EXISTS ix_event__scope
  ON ledger_event(entity_id, holding_id, trade_date);
CREATE INDEX IF NOT EXISTS ix_event__holding
  ON ledger_event(holding_id, trade_date);
CREATE INDEX IF NOT EXISTS ix_event__batch
  ON ledger_event(import_batch_id) WHERE import_batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_event__supersedes
  ON ledger_event(supersedes_event_id) WHERE supersedes_event_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_event__fingerprint
  ON ledger_event(entity_id, holding_id, trade_date, quantity_scaled, total_amount_minor);

CREATE TABLE IF NOT EXISTS price_observation (
  instrument_id  TEXT NOT NULL,
  price_date     TEXT NOT NULL,
  basis          TEXT NOT NULL,
  price_minor    INTEGER NOT NULL,
  currency       TEXT NOT NULL DEFAULT 'INR',
  source         TEXT NOT NULL,
  ingested_at    TEXT NOT NULL,
  PRIMARY KEY (instrument_id, price_date, basis, ingested_at)
);
CREATE INDEX IF NOT EXISTS ix_price__lookup
  ON price_observation(instrument_id, price_date DESC);

CREATE TABLE IF NOT EXISTS import_batch (
  import_batch_id     TEXT PRIMARY KEY,
  source_filename     TEXT NOT NULL,
  document_kind       TEXT NOT NULL,
  sha256              TEXT,
  statement_date      TEXT,
  rows_new            INTEGER NOT NULL DEFAULT 0,
  rows_duplicate      INTEGER NOT NULL DEFAULT 0,
  rows_conflict       INTEGER NOT NULL DEFAULT 0,
  committed_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tax_rule_set (
  assessment_year TEXT PRIMARY KEY,
  rules_json      TEXT NOT NULL,
  published_at    TEXT NOT NULL,
  installed_at    TEXT NOT NULL
);

CREATE TRIGGER IF NOT EXISTS tg_tax_rule_set__no_update
BEFORE UPDATE ON tax_rule_set
BEGIN
  SELECT RAISE(ABORT, 'tax rule sets are immutable once installed');
END;

CREATE TABLE IF NOT EXISTS liability (
  liability_id      TEXT PRIMARY KEY,
  entity_id         TEXT NOT NULL REFERENCES entity(entity_id),
  liability_type    TEXT NOT NULL,
  name              TEXT NOT NULL,
  lender            TEXT,
  principal_minor   INTEGER NOT NULL DEFAULT 0,
  outstanding_minor INTEGER NOT NULL DEFAULT 0,
  rate_scaled       INTEGER NOT NULL DEFAULT 0,
  emi_minor         INTEGER NOT NULL DEFAULT 0,
  start_date        TEXT,
  end_date          TEXT,
  linked_holding_id TEXT,
  created_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS goal (
  goal_id       TEXT PRIMARY KEY,
  entity_id     TEXT,
  name          TEXT NOT NULL,
  target_minor  INTEGER,
  target_date   TEXT,
  priority      TEXT,
  created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS setting (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS egress_log (
  egress_log_id  TEXT PRIMARY KEY,
  occurred_at    TEXT NOT NULL,
  destination    TEXT NOT NULL,
  purpose        TEXT NOT NULL,
  request_bytes  INTEGER NOT NULL DEFAULT 0,
  response_bytes INTEGER NOT NULL DEFAULT 0,
  outcome        TEXT NOT NULL,
  allow_listed   INTEGER NOT NULL DEFAULT 1
);
`;
