package `in`.growurdhan.data.vault

import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Native schema v3; upgrades v1/v2 in place without touching append-only events. */
internal object VaultSchema {
    const val VERSION = 3

    fun initializeOrValidate(db: SQLiteDatabase) {
        // SQLCipher returns a result row for secure_delete even when setting it.
        // execSQL routes to executeForChangedRowCount and rejects result-producing
        // PRAGMAs on Android. Execute it through a query cursor and check its result.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.rawQuery("PRAGMA foreign_keys", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                "SQLite foreign-key enforcement could not be enabled."
            }
        }
        db.rawQuery("PRAGMA secure_delete = ON", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                "SQLite secure deletion could not be enabled."
            }
        }
        val current = db.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "Missing SQLite user_version." }
            cursor.getInt(0)
        }
        when (current) {
            0, 1, 2 -> {
                // v1/v2 -> v3, and fresh installs, are one rollback-safe transaction.
                db.beginTransaction()
                try {
                    if (current == 0) for (sql in ddl) db.execSQL(sql)
                    if (current <= 1) for (sql in migrationToV2) db.execSQL(sql)
                    for (sql in migrationToV3) db.execSQL(sql)
                    db.execSQL("PRAGMA user_version = $VERSION")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            VERSION -> Unit
            else -> throw VaultUnavailable("Unsupported native vault schema: $current.")
        }
        // Fail closed if a forged or incomplete version number omits required columns.
        val columns = db.rawQuery("PRAGMA table_info(ledger_event)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        check(columns.containsAll(setOf(
            "financial_kind", "unit_price_scaled", "charges_scaled", "supersedes_event_id",
        ))) { "Native ledger schema v2 is incomplete." }
        val mappingColumns = db.rawQuery("PRAGMA table_info(holding_cas_identity)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        check(mappingColumns.containsAll(setOf(
            "holding_id", "owner_id", "account_id", "isin", "folio",
        ))) { "Native CAS identity table is missing or incomplete." }
        // A copied/forged user_version must never bypass immutable ledger or
        // enrolled-identity authorization triggers.
        val requiredTriggers = setOf(
            "deny_ledger_update", "deny_ledger_delete", "validate_rich_ledger_insert",
            "cas_holding_account_match", "deny_cas_mapping_update", "deny_cas_mapping_delete",
        )
        val triggerNames = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='trigger'", null,
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(triggerNames.containsAll(requiredTriggers)) {
            "Native vault safety triggers are incomplete."
        }
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use {
            if (it.moveToFirst()) it.getString(0) else "unavailable"
        }
        if (integrity != "ok") throw VaultUnavailable("Encrypted vault integrity verification failed.")
        val foreignKeys = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
        if (foreignKeys != 0) throw VaultUnavailable("Encrypted vault references are inconsistent.")
    }

    /**
     * Stored legacy kind remains constrained by v1 CHECK. Rich event kind is explicit,
     * with ADJUSTMENT as the legacy carrier for BONUS/SPLIT/other added event types.
     * This avoids rebuilding the append-only table and preserves old rows and triggers.
     * The insert validation trigger prevents inconsistent carriers or forged correction links.
     */
    internal val migrationToV2 = listOf(
        """ALTER TABLE ledger_event ADD COLUMN financial_kind TEXT NOT NULL DEFAULT ''
           CHECK(financial_kind IN ('', 'BUY','SELL','DIVIDEND','INTEREST',
               'OPENING_BALANCE','ADJUSTMENT','CORRECTION','BONUS','SPLIT',
               'SNAPSHOT_ADJUSTMENT','VALUATION_UPDATE','REPAYMENT'))""",
        "ALTER TABLE ledger_event ADD COLUMN unit_price_scaled INTEGER",
        "ALTER TABLE ledger_event ADD COLUMN charges_scaled INTEGER NOT NULL DEFAULT 0 CHECK(charges_scaled >= 0)",
        "ALTER TABLE ledger_event ADD COLUMN supersedes_event_id TEXT",
        "CREATE UNIQUE INDEX ledger_unique_supersession ON ledger_event(supersedes_event_id) WHERE supersedes_event_id IS NOT NULL",
        """
        CREATE TRIGGER validate_rich_ledger_insert BEFORE INSERT ON ledger_event
        WHEN NEW.financial_kind != '' OR NEW.supersedes_event_id IS NOT NULL
        BEGIN
          SELECT CASE WHEN
            (NEW.financial_kind IN ('BONUS','SPLIT','SNAPSHOT_ADJUSTMENT','VALUATION_UPDATE','REPAYMENT')
              AND NEW.kind != 'ADJUSTMENT') OR
            (NEW.financial_kind NOT IN ('BONUS','SPLIT','SNAPSHOT_ADJUSTMENT','VALUATION_UPDATE','REPAYMENT')
              AND NEW.financial_kind != '' AND NEW.kind != NEW.financial_kind)
            THEN RAISE(ABORT, 'Financial event kind does not match ledger carrier') END;
          SELECT CASE WHEN
            NEW.financial_kind = 'CORRECTION' AND NEW.supersedes_event_id IS NULL
            THEN RAISE(ABORT, 'A correction must supersede an existing event') END;
          SELECT CASE WHEN
            NEW.supersedes_event_id IS NOT NULL AND NEW.financial_kind != 'CORRECTION'
            THEN RAISE(ABORT, 'Only a correction can supersede an event') END;
          SELECT CASE WHEN
            NEW.supersedes_event_id IS NOT NULL AND NOT EXISTS (
              SELECT 1 FROM ledger_event AS prior
              WHERE prior.id = NEW.supersedes_event_id AND prior.owner_id = NEW.owner_id
                AND prior.holding_id = NEW.holding_id
            ) THEN RAISE(ABORT, 'Missing or cross-owner correction predecessor') END;
        END
        """.trimIndent(),
    )

    /**
     * The identity link is a separate table so old holding and ledger rows
     * migrate unchanged. Composite ownership FKs + insert trigger deny
     * mismatched account/owner/holding bindings.
     */
    internal val migrationToV3 = listOf(
        """
        CREATE TABLE holding_cas_identity (
          holding_id TEXT PRIMARY KEY NOT NULL,
          owner_id TEXT NOT NULL,
          account_id TEXT NOT NULL,
          isin TEXT NOT NULL CHECK(length(isin) = 12 AND isin = upper(isin)),
          folio TEXT NOT NULL CHECK(length(folio) BETWEEN 1 AND 64),
          UNIQUE(owner_id, isin, folio),
          FOREIGN KEY(holding_id, owner_id) REFERENCES holding(id, owner_id) ON DELETE RESTRICT,
          FOREIGN KEY(account_id, owner_id) REFERENCES custody_account(id, owner_id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TRIGGER cas_holding_account_match BEFORE INSERT ON holding_cas_identity
        BEGIN
          SELECT CASE WHEN NOT EXISTS (
            SELECT 1 FROM holding
            WHERE id=NEW.holding_id AND owner_id=NEW.owner_id
              AND account_id=NEW.account_id
              AND asset_category='MUTUAL_FUND'
          ) THEN RAISE(ABORT, 'CAS mapping requires matching account, owner and mutual fund') END;
          SELECT CASE WHEN NOT EXISTS (
            SELECT 1 FROM custody_account
            WHERE id=NEW.account_id AND owner_id=NEW.owner_id
              AND account_type IN ('CAMS','KFINTECH')
          ) THEN RAISE(ABORT, 'CAS mapping requires matching registrar account') END;
        END
        """.trimIndent(),
        """CREATE TRIGGER deny_cas_mapping_update BEFORE UPDATE ON holding_cas_identity
             BEGIN SELECT RAISE(ABORT, 'CAS identity is immutable'); END""",
        """CREATE TRIGGER deny_cas_mapping_delete BEFORE DELETE ON holding_cas_identity
             BEGIN SELECT RAISE(ABORT, 'CAS identity is immutable'); END""",
    )

    // Same immutable v1 DDL used by fresh initialization and v1 migration tests.
    internal val legacyV1Statements: List<String> get() = ddl

    private val ddl = listOf(
        """
        CREATE TABLE family_member (
          id TEXT PRIMARY KEY NOT NULL,
          display_name TEXT NOT NULL CHECK(length(display_name) BETWEEN 1 AND 200),
          relationship TEXT NOT NULL,
          is_primary INTEGER NOT NULL DEFAULT 0 CHECK(is_primary IN (0,1))
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX one_primary_member ON family_member(is_primary) WHERE is_primary = 1",
        """
        CREATE TABLE custody_account (
          id TEXT PRIMARY KEY NOT NULL,
          owner_id TEXT NOT NULL,
          account_type TEXT NOT NULL,
          identifier_masked TEXT NOT NULL,
          UNIQUE(id, owner_id),
          FOREIGN KEY(owner_id) REFERENCES family_member(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE holding (
          id TEXT PRIMARY KEY NOT NULL,
          owner_id TEXT NOT NULL,
          account_id TEXT,
          display_name TEXT NOT NULL,
          asset_category TEXT NOT NULL,
          quantity_scaled INTEGER NOT NULL DEFAULT 0 CHECK(quantity_scaled >= 0),
          price_scaled INTEGER NOT NULL DEFAULT 0 CHECK(price_scaled >= 0),
          UNIQUE(id, owner_id),
          FOREIGN KEY(owner_id) REFERENCES family_member(id) ON DELETE RESTRICT,
          FOREIGN KEY(account_id, owner_id) REFERENCES custody_account(id, owner_id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        "CREATE INDEX holding_by_owner ON holding(owner_id)",
        """
        CREATE TABLE ledger_event (
          id TEXT PRIMARY KEY NOT NULL,
          owner_id TEXT NOT NULL,
          holding_id TEXT NOT NULL,
          kind TEXT NOT NULL CHECK(kind IN
            ('BUY','SELL','DIVIDEND','INTEREST','OPENING_BALANCE','ADJUSTMENT','CORRECTION')),
          event_date TEXT NOT NULL,
          quantity_delta_scaled INTEGER NOT NULL,
          amount_scaled INTEGER NOT NULL,
          source_fingerprint TEXT,
          created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
          FOREIGN KEY(holding_id, owner_id) REFERENCES holding(id, owner_id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        "CREATE INDEX ledger_by_holding_date ON ledger_event(holding_id, event_date, id)",
        "CREATE UNIQUE INDEX ledger_event_source_uniq ON ledger_event(owner_id, source_fingerprint) WHERE source_fingerprint IS NOT NULL",
        """CREATE TRIGGER deny_ledger_update BEFORE UPDATE ON ledger_event
              BEGIN SELECT RAISE(ABORT, 'ledger_event is append only'); END""",
        """CREATE TRIGGER deny_ledger_delete BEFORE DELETE ON ledger_event
              BEGIN SELECT RAISE(ABORT, 'ledger_event is append only'); END""",
        """
        CREATE TABLE snapshot_import (
          id TEXT PRIMARY KEY NOT NULL,
          owner_id TEXT NOT NULL,
          source_sha256 TEXT NOT NULL UNIQUE,
          as_of TEXT NOT NULL,
          is_complete INTEGER NOT NULL CHECK(is_complete = 1),
          imported_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
          UNIQUE(id, owner_id),
          FOREIGN KEY(owner_id) REFERENCES family_member(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE statement_position (
          snapshot_id TEXT NOT NULL,
          owner_id TEXT NOT NULL,
          holding_id TEXT NOT NULL,
          quantity_scaled INTEGER NOT NULL CHECK(quantity_scaled >= 0),
          value_scaled INTEGER NOT NULL,
          PRIMARY KEY(snapshot_id, holding_id),
          FOREIGN KEY(snapshot_id, owner_id) REFERENCES snapshot_import(id, owner_id) ON DELETE RESTRICT,
          FOREIGN KEY(holding_id, owner_id) REFERENCES holding(id, owner_id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        "CREATE INDEX snapshot_owner ON snapshot_import(owner_id, as_of)",
        """
        CREATE TABLE vault_meta (
          key TEXT PRIMARY KEY NOT NULL,
          value TEXT NOT NULL
        )
        """.trimIndent()
    )
}
