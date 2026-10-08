package `in`.growurdhan.data.vault

import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Fresh v1 native schema. No plaintext Room, no REAL money/quantity columns. */
internal object VaultSchema {
    const val VERSION = 1

    fun initializeOrValidate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("PRAGMA secure_delete = ON")
        val current = db.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "Missing SQLite user_version." }
            cursor.getInt(0)
        }
        when (current) {
            0 -> {
                db.beginTransaction()
                try {
                    for (sql in ddl) db.execSQL(sql)
                    db.execSQL("PRAGMA user_version = $VERSION")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            VERSION -> Unit
            else -> throw VaultUnavailable("Unsupported native vault schema: $current.")
        }
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use {
            if (it.moveToFirst()) it.getString(0) else "unavailable"
        }
        if (integrity != "ok") throw VaultUnavailable("Encrypted vault integrity verification failed.")
        val foreignKeys = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
        if (foreignKeys != 0) throw VaultUnavailable("Encrypted vault references are inconsistent.")
    }

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
          UNIQUE(id, owner_id),
          imported_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
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
