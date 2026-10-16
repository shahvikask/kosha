-- Regenerated from frozen src/db/schema.ts, sha256 8b98666f1d4954b1f3bb09e199dd504dd23b1155cf81becfcbd6fcbdbcb7e3a1
-- Every step is its original SQL; migrations must run in one transaction and fail closed.
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN pan_for_statements TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN date_of_birth TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN relation TEXT NOT NULL DEFAULT 'Self';
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN color TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN notes TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE entity ADD COLUMN is_primary INTEGER NOT NULL DEFAULT 0;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE account ADD COLUMN dp_id TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE account ADD COLUMN client_id TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE account ADD COLUMN bo_id TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE instrument ADD COLUMN scheme_type TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE instrument ADD COLUMN ucc TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE instrument ADD COLUMN exchange TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE instrument ADD COLUMN dp_name TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN issue_price_minor INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN institution_name TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN account_number_masked TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN notes TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN location TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN tag TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN tags_json TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN coupon_rate_bps INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN interest_rate_bps INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN maturity_date TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN next_coupon_date TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE holding ADD COLUMN monthly_emi_minor INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE import_batch ADD COLUMN investor_pan_masked TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE import_batch ADD COLUMN investor_email TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE import_batch ADD COLUMN cas_id TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN isin TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN folio TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN current_value_minor INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN amc TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN scheme_type TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_3
ALTER TABLE statement_position ADD COLUMN source_section TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_4
ALTER TABLE holding ADD COLUMN emi_status TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_4
ALTER TABLE holding ADD COLUMN remaining_emi_count INTEGER;
-- M24_END_STATEMENT
-- M24_VERSION_5
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
-- M24_VERSION_5
CREATE INDEX IF NOT EXISTS ix_listed__symbol ON listed_security(symbol);
-- M24_END_STATEMENT
-- M24_VERSION_5
CREATE INDEX IF NOT EXISTS ix_listed__name ON listed_security(name);
-- M24_END_STATEMENT
-- M24_VERSION_5
CREATE INDEX IF NOT EXISTS ix_listed__cat ON listed_security(category);
-- M24_END_STATEMENT
-- M24_VERSION_6
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
-- M24_VERSION_7
ALTER TABLE holding ADD COLUMN acquired_on TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_7
ALTER TABLE holding ADD COLUMN details_json TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_8
ALTER TABLE entity ADD COLUMN cas_id TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_8
ALTER TABLE entity ADD COLUMN pran TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_8
ALTER TABLE entity ADD COLUMN gmail_sync INTEGER NOT NULL DEFAULT 0;
-- M24_END_STATEMENT
-- M24_VERSION_8
CREATE TABLE IF NOT EXISTS gmail_seen (
message_id     TEXT NOT NULL,
attachment_id  TEXT NOT NULL,
filename       TEXT,
entity_id      TEXT,
seen_at        TEXT NOT NULL,
PRIMARY KEY (message_id, attachment_id)
);
-- M24_END_STATEMENT
-- M24_VERSION_9
ALTER TABLE entity ADD COLUMN cas_id_source TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_9
ALTER TABLE entity ADD COLUMN pran_source TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_9
ALTER TABLE entity ADD COLUMN cas_sync_after TEXT;
-- M24_END_STATEMENT
-- M24_VERSION_9
ALTER TABLE entity ADD COLUMN pran_sync_after TEXT;
-- M24_END_STATEMENT
