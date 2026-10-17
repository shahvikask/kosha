package `in`.growurdhan.redevelopment

import `in`.growurdhan.database.RestoreV9InsertTransaction
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * SQLCipher implementation for an ALREADY OPEN, NEW, EMPTY v9 candidate.
 *
 * NEVER construct with the live vault or invoke before backup GCM + account
 * recovery + original-byte identity + typed row-preflight. The caller owns the
 * exclusive restore lock, Keystore-wrapped candidate key, journal and final
 * no-overwrite publication. This adapter does not expose a restore action.
 */
internal class AndroidRestoreV9SqlTarget(private val db: SQLiteDatabase) :
    RestoreV9InsertTransaction.EmptyEncryptedTarget {

    private val importTables = listOf("vault_account", "entity", "account",
        "instrument", "holding", "ledger_event", "goal", "setting")

    override fun verifyNewEmptyCipherTarget() {
        db.rawQuery("PRAGMA cipher_version", null).use { cur ->
            require(cur.moveToFirst() && !cur.getString(0).isNullOrBlank()) {
                "Restore target is not SQLCipher"
            }
        }
        db.rawQuery("PRAGMA user_version", null).use { cur ->
            require(cur.moveToFirst() && cur.getLong(0) == 9L) {
                "Restore candidate has unexpected schema version"
            }
        }
        db.rawQuery("SELECT value FROM vault_meta WHERE key='schema_version'", null).use { cur ->
            require(cur.moveToFirst() && cur.getString(0) == "9" && !cur.moveToNext()) {
                "Restore candidate metadata is incompatible"
            }
        }
        // This guards against accidentally importing into populated encrypted
        // books. Exclusive fresh-target identity and filesystem lock remain
        // obligations of the caller, not inferred from these empty counts.
        for (table in importTables) {
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cur ->
                require(cur.moveToFirst() && cur.getLong(0) == 0L) {
                    "Encrypted restore target already has user data"
                }
            }
        }
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    override fun beginTransaction() = db.beginTransaction()

    override fun insert(table: String, columns: List<String>, parameters: List<Any?>) {
        require(table in importTables && columns.isNotEmpty() &&
            columns.all { it.matches(Regex("[a-z][a-z0-9_]*")) }) {
            "Unsupported import SQL identifiers"
        }
        val holders = List(columns.size) { "?" }.joinToString(",")
        val sql = "INSERT INTO $table (" + columns.joinToString(",") +
            ") VALUES ($holders)"
        // Values are bound using their exact String/Long/null type; never
        // concatenate amounts or account content into SQL text.
        db.execSQL(sql, parameters.toTypedArray())
    }

    override fun verifyTransactionIntegrity(expectedRows: Int) {
        var inserted = 0L
        for (table in importTables) {
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cur ->
                require(cur.moveToFirst())
                inserted += cur.getLong(0)
            }
        }
        require(inserted == expectedRows.toLong()) {
            "Restore SQLCipher row count differs from authenticated plan"
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use {
            require(!it.moveToFirst()) { "Foreign-key violation during restore" }
        }
        // The append-only guards must remain installed even while performing
        // a one-time insertion into the new encrypted target.
        for (trigger in listOf("tg_ledger_event__no_update",
                "tg_ledger_event__no_delete")) {
            db.rawQuery("SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type='trigger' AND name=?", arrayOf(trigger)).use { cur ->
                require(cur.moveToFirst() && cur.getLong(0) == 1L) {
                    "Restore target lost immutable ledger protection"
                }
            }
        }
        db.rawQuery("PRAGMA quick_check", null).use { cur ->
            require(cur.moveToFirst() && cur.getString(0) == "ok" && !cur.moveToNext()) {
                "Restore target integrity failed"
            }
        }
    }

    override fun setTransactionSuccessful() = db.setTransactionSuccessful()

    override fun endTransaction() = db.endTransaction()
}
