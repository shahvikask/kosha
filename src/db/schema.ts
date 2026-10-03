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
 *
 * v3 adds family-profile columns and CAS import fields so every value
 * casparser emits (CDSL, NSDL, CAMS, KFin) has a home.
 */

export const SCHEMA_VERSION = 9;

export const SCHEMA_V1 = `
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS vault_meta (
  key         TEXT PRIMARY KEY,
  value       TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entity (
  entity_id          TEXT PRIMARY KEY,
  entity_type        TEXT NOT NULL,
  display_name       TEXT NOT NULL,
  pan_masked         TEXT,
  pan_for_statements TEXT,
  date_of_majority   TEXT,
  date_of_birth      TEXT,
  guardian_id        TEXT REFERENCES entity(entity_id),
  relation           TEXT NOT NULL DEFAULT 'Self',
  color              TEXT,
  notes              TEXT,
  is_primary         INTEGER NOT NULL DEFAULT 0,
  is_active          INTEGER NOT NULL DEFAULT 1,
  created_at         TEXT NOT NULL,
  CONSTRAINT ck_entity__type CHECK (entity_type IN (
    'INDIVIDUAL','HUF','PROPRIETORSHIP','PRIVATE_TRUST','PARTNERSHIP_FIRM')),
  CONSTRAINT ck_entity__relation CHECK (relation IN (
    'Self','Spouse','Parent','Child','HUF','Joint'))
);

CREATE TABLE IF NOT EXISTS account (
  account_id        TEXT PRIMARY KEY,
  entity_id         TEXT NOT NULL REFERENCES entity(entity_id),
  account_type      TEXT NOT NULL,
  custodian         TEXT NOT NULL,
  identifier_masked TEXT,
  dp_id             TEXT,
  client_id         TEXT,
  bo_id             TEXT,
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
  amc               TEXT,
  rta               TEXT,
  amfi_code         TEXT,
  advisor           TEXT,
  scheme_type       TEXT,
  ucc               TEXT,
  exchange          TEXT,
  dp_name           TEXT,
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
  issue_price_minor        INTEGER,
  sgb_valuation_mode       TEXT,
  last_price_updated       TEXT,
  price_source             TEXT,
  needs_review             INTEGER NOT NULL DEFAULT 0,
  review_reasons           TEXT,
  institution_name         TEXT,
  account_number_masked    TEXT,
  notes                    TEXT,
  location                 TEXT,
  tag                      TEXT,
  tags_json                TEXT,
  coupon_rate_bps          INTEGER,
  interest_rate_bps        INTEGER,
  maturity_date            TEXT,
  next_coupon_date         TEXT,
  monthly_emi_minor        INTEGER,
  emi_status               TEXT,
  remaining_emi_count      INTEGER,
  acquired_on              TEXT,
  details_json             TEXT,
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
  unit_balance_after  INTEGER,
  description         TEXT,
  notes               TEXT,
  entered_at          TEXT NOT NULL,
  entered_by          TEXT NOT NULL DEFAULT 'owner',
  CONSTRAINT ck_event__type CHECK (event_type IN (
    'OPENING_BALANCE','SNAPSHOT_ADJUSTMENT','BUY','SELL','BONUS','SPLIT','DIVIDEND','INTEREST',
    'VALUATION_UPDATE','REPAYMENT','CORRECTION')),
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
  period_from         TEXT,
  period_to           TEXT,
  investor_name       TEXT,
  investor_pan_masked TEXT,
  investor_email      TEXT,
  cas_id              TEXT,
  statement_type      TEXT,
  rows_new            INTEGER NOT NULL DEFAULT 0,
  rows_duplicate      INTEGER NOT NULL DEFAULT 0,
  rows_conflict       INTEGER NOT NULL DEFAULT 0,
  committed_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS source_document (
  source_document_id TEXT PRIMARY KEY,
  import_batch_id    TEXT NOT NULL,
  original_filename  TEXT NOT NULL,
  document_kind      TEXT NOT NULL,
  sha256             TEXT NOT NULL,
  byte_length        INTEGER NOT NULL,
  page_count         INTEGER,
  imported_at        TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_source_document__sha
  ON source_document(sha256);

CREATE TABLE IF NOT EXISTS statement_position (
  import_batch_id      TEXT NOT NULL,
  holding_id           TEXT NOT NULL,
  statement_date       TEXT NOT NULL,
  quantity_scaled      INTEGER NOT NULL,
  unit_price_minor     INTEGER NOT NULL,
  invested_minor       INTEGER,
  isin                 TEXT,
  folio                TEXT,
  current_value_minor  INTEGER,
  amc                  TEXT,
  scheme_type          TEXT,
  source_section       TEXT,
  PRIMARY KEY (import_batch_id, holding_id)
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

/** Recreated after a permitted family-member cascade delete. */
export const LEDGER_NO_DELETE_TRIGGER = `
CREATE TRIGGER IF NOT EXISTS tg_ledger_event__no_delete
BEFORE DELETE ON ledger_event
BEGIN
  SELECT RAISE(ABORT, 'ledger_event is append-only: record a CORRECTION event');
END;
`;

/**
 * Columns added in schema v3. Applied via ALTER TABLE on existing vaults
 * (CREATE IF NOT EXISTS will not reshape already-created tables).
 * Format: [table, column, "column-name TYPE [DEFAULT …]"]
 */
export const SCHEMA_V3_COLUMNS: [string, string, string][] = [
  ['entity', 'pan_for_statements', 'pan_for_statements TEXT'],
  ['entity', 'date_of_birth', 'date_of_birth TEXT'],
  ['entity', 'relation', "relation TEXT NOT NULL DEFAULT 'Self'"],
  ['entity', 'color', 'color TEXT'],
  ['entity', 'notes', 'notes TEXT'],
  ['entity', 'is_primary', 'is_primary INTEGER NOT NULL DEFAULT 0'],
  ['account', 'dp_id', 'dp_id TEXT'],
  ['account', 'client_id', 'client_id TEXT'],
  ['account', 'bo_id', 'bo_id TEXT'],
  ['instrument', 'scheme_type', 'scheme_type TEXT'],
  ['instrument', 'ucc', 'ucc TEXT'],
  ['instrument', 'exchange', 'exchange TEXT'],
  ['instrument', 'dp_name', 'dp_name TEXT'],
  ['holding', 'issue_price_minor', 'issue_price_minor INTEGER'],
  ['holding', 'institution_name', 'institution_name TEXT'],
  ['holding', 'account_number_masked', 'account_number_masked TEXT'],
  ['holding', 'notes', 'notes TEXT'],
  ['holding', 'location', 'location TEXT'],
  ['holding', 'tag', 'tag TEXT'],
  ['holding', 'tags_json', 'tags_json TEXT'],
  ['holding', 'coupon_rate_bps', 'coupon_rate_bps INTEGER'],
  ['holding', 'interest_rate_bps', 'interest_rate_bps INTEGER'],
  ['holding', 'maturity_date', 'maturity_date TEXT'],
  ['holding', 'next_coupon_date', 'next_coupon_date TEXT'],
  ['holding', 'monthly_emi_minor', 'monthly_emi_minor INTEGER'],
  ['import_batch', 'investor_pan_masked', 'investor_pan_masked TEXT'],
  ['import_batch', 'investor_email', 'investor_email TEXT'],
  ['import_batch', 'cas_id', 'cas_id TEXT'],
  ['statement_position', 'isin', 'isin TEXT'],
  ['statement_position', 'folio', 'folio TEXT'],
  ['statement_position', 'current_value_minor', 'current_value_minor INTEGER'],
  ['statement_position', 'amc', 'amc TEXT'],
  ['statement_position', 'scheme_type', 'scheme_type TEXT'],
  ['statement_position', 'source_section', 'source_section TEXT'],
];

/** Columns added in schema v4 (loan EMI schedule on holding). */
export const SCHEMA_V4_COLUMNS: [string, string, string][] = [
  ['holding', 'emi_status', 'emi_status TEXT'],
  ['holding', 'remaining_emi_count', 'remaining_emi_count INTEGER'],
];

/** Listed NSE/BSE/AMFI universe for picker + close-only CMP. */
export const SCHEMA_V5_SQL = `
CREATE TABLE IF NOT EXISTS listed_security (
  isin          TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  symbol        TEXT,
  nse_symbol    TEXT,
  bse_symbol    TEXT,
  amfi_code     TEXT,
  category      TEXT NOT NULL,
  close_minor   INTEGER NOT NULL DEFAULT 0,
  price_date    TEXT NOT NULL,
  source        TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_listed__symbol ON listed_security(symbol);
CREATE INDEX IF NOT EXISTS ix_listed__name ON listed_security(name);
CREATE INDEX IF NOT EXISTS ix_listed__cat ON listed_security(category);
`;

/** Local login (one owner). Password stored as PBKDF2 hash only. */
export const SCHEMA_V6_SQL = `
CREATE TABLE IF NOT EXISTS vault_account (
  id                    TEXT PRIMARY KEY,
  email                 TEXT NOT NULL UNIQUE,
  first_name            TEXT NOT NULL,
  last_name             TEXT NOT NULL,
  password_salt         TEXT NOT NULL,
  password_hash         TEXT NOT NULL,
  password_iterations   INTEGER NOT NULL,
  password_changed_at   TEXT NOT NULL,
  pan_masked            TEXT,
  pan_for_statements    TEXT,
  date_of_birth         TEXT,
  q1_id                 TEXT NOT NULL,
  q1_hash               TEXT NOT NULL,
  q2_id                 TEXT NOT NULL,
  q2_hash               TEXT NOT NULL,
  created_at            TEXT NOT NULL
);
`;

/** Subcategory facts on a holding (SGB hold-mode, PRAN extras, AIF commitment, …). */
export const SCHEMA_V7_COLUMNS: [string, string, string][] = [
  ['holding', 'acquired_on', 'acquired_on TEXT'],
  ['holding', 'details_json', 'details_json TEXT'],
];

/** Gmail statement match keys. Hidden on the profile form. */
export const SCHEMA_V8_COLUMNS: [string, string, string][] = [
  ['entity', 'cas_id', 'cas_id TEXT'],
  ['entity', 'pran', 'pran TEXT'],
  ['entity', 'gmail_sync', 'gmail_sync INTEGER NOT NULL DEFAULT 0'],
];


/** How a CAS id or PRAN was recorded, and the hand-import cutoff. */
export const SCHEMA_V9_COLUMNS: [string, string, string][] = [
  ['entity', 'cas_id_source', 'cas_id_source TEXT'],
  ['entity', 'pran_source', 'pran_source TEXT'],
  ['entity', 'cas_sync_after', 'cas_sync_after TEXT'],
  ['entity', 'pran_sync_after', 'pran_sync_after TEXT'],
];
export const SCHEMA_V8_SQL = `
CREATE TABLE IF NOT EXISTS gmail_seen (
  message_id     TEXT NOT NULL,
  attachment_id  TEXT NOT NULL,
  filename       TEXT,
  entity_id      TEXT,
  seen_at        TEXT NOT NULL,
  PRIMARY KEY (message_id, attachment_id)
);
`;
