-- Generated exclusively from frozen src/db/schema.ts at GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e.
-- Source SHA256 8b98666f1d4954b1f3bb09e199dd504dd23b1155cf81becfcbd6fcbdbcb7e3a1
-- Original 210 CREATE-table columns; seven v8/v9 ALTER additions; final v9: 217.
-- SQL statements delimited by M24_END_STATEMENT to keep trigger BEGIN/END intact.
CREATE TABLE IF NOT EXISTS vault_meta (
key         TEXT PRIMARY KEY,
value       TEXT NOT NULL,
updated_at  TEXT NOT NULL
);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_account__entity ON account(entity_id);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_instrument__isin ON instrument(isin) WHERE isin IS NOT NULL;
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_holding__entity ON holding(entity_id);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE TRIGGER IF NOT EXISTS tg_ledger_event__no_update
BEFORE UPDATE ON ledger_event
BEGIN
SELECT RAISE(ABORT, 'ledger_event is append-only: record a CORRECTION event');
END;
-- M24_END_STATEMENT
CREATE TRIGGER IF NOT EXISTS tg_ledger_event__no_delete
BEFORE DELETE ON ledger_event
BEGIN
SELECT RAISE(ABORT, 'ledger_event is append-only: record a CORRECTION event');
END;
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_event__scope
ON ledger_event(entity_id, holding_id, trade_date);
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_event__holding
ON ledger_event(holding_id, trade_date);
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_event__batch
ON ledger_event(import_batch_id) WHERE import_batch_id IS NOT NULL;
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_event__supersedes
ON ledger_event(supersedes_event_id) WHERE supersedes_event_id IS NOT NULL;
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_event__fingerprint
ON ledger_event(entity_id, holding_id, trade_date, quantity_scaled, total_amount_minor);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_price__lookup
ON price_observation(instrument_id, price_date DESC);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE UNIQUE INDEX IF NOT EXISTS ux_source_document__sha
ON source_document(sha256);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE TABLE IF NOT EXISTS tax_rule_set (
assessment_year TEXT PRIMARY KEY,
rules_json      TEXT NOT NULL,
published_at    TEXT NOT NULL,
installed_at    TEXT NOT NULL
);
-- M24_END_STATEMENT
CREATE TRIGGER IF NOT EXISTS tg_tax_rule_set__no_update
BEFORE UPDATE ON tax_rule_set
BEGIN
SELECT RAISE(ABORT, 'tax rule sets are immutable once installed');
END;
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE TABLE IF NOT EXISTS goal (
goal_id       TEXT PRIMARY KEY,
entity_id     TEXT,
name          TEXT NOT NULL,
target_minor  INTEGER,
target_date   TEXT,
priority      TEXT,
created_at    TEXT NOT NULL
);
-- M24_END_STATEMENT
CREATE TABLE IF NOT EXISTS setting (
key   TEXT PRIMARY KEY,
value TEXT NOT NULL
);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_listed__symbol ON listed_security(symbol);
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_listed__name ON listed_security(name);
-- M24_END_STATEMENT
CREATE INDEX IF NOT EXISTS ix_listed__cat ON listed_security(category);
-- M24_END_STATEMENT
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
-- M24_END_STATEMENT
CREATE TABLE IF NOT EXISTS gmail_seen (
message_id     TEXT NOT NULL,
attachment_id  TEXT NOT NULL,
filename       TEXT,
entity_id      TEXT,
seen_at        TEXT NOT NULL,
PRIMARY KEY (message_id, attachment_id)
);
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN cas_id TEXT;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN pran TEXT;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN gmail_sync INTEGER NOT NULL DEFAULT 0;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN cas_id_source TEXT;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN pran_source TEXT;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN cas_sync_after TEXT;
-- M24_END_STATEMENT
ALTER TABLE entity ADD COLUMN pran_sync_after TEXT;
-- M24_END_STATEMENT
