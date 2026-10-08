package `in`.growurdhan.data.vault

import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whitelisted portable logical backup: no raw database keys, device tokens,
 * sqlite_master data or user-provided SQL. Monetary integers travel as decimal strings.
 */
internal object VaultBackupTables {
    private val columns: LinkedHashMap<String, List<String>> = linkedMapOf(
        "family_member" to listOf("id", "display_name", "relationship", "is_primary"),
        "custody_account" to listOf("id", "owner_id", "account_type", "identifier_masked"),
        "holding" to listOf(
            "id", "owner_id", "account_id", "display_name", "asset_category",
            "quantity_scaled", "price_scaled",
        ),
        "ledger_event" to listOf(
            "id", "owner_id", "holding_id", "kind", "event_date",
            "quantity_delta_scaled", "amount_scaled", "source_fingerprint",
        ),
        "snapshot_import" to listOf(
            "id", "owner_id", "source_sha256", "as_of", "is_complete",
        ),
        "statement_position" to listOf(
            "snapshot_id", "owner_id", "holding_id", "quantity_scaled", "value_scaled",
        ),
        "vault_meta" to listOf("key", "value"),
    )
    private val integers = setOf(
        "is_primary", "quantity_scaled", "price_scaled", "quantity_delta_scaled",
        "amount_scaled", "is_complete", "value_scaled",
    )
    private const val MAX_ROWS_PER_TABLE = 100_000

    fun export(db: SQLiteDatabase): ByteArray {
        val payload = JSONObject().put("format", 1).put("databaseVersion", VaultSchema.VERSION)
        for ((table, names) in columns) {
            val rows = JSONArray()
            db.rawQuery("SELECT ${names.joinToString(",")} FROM $table", null).use { cursor ->
                while (cursor.moveToNext()) {
                    check(rows.length() < MAX_ROWS_PER_TABLE) { "Backup has too many financial records." }
                    val row = JSONArray()
                    names.indices.forEach { index ->
                        row.put(
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                                else -> throw VaultUnavailable("Unsupported backup record field.")
                            }
                        )
                    }
                    rows.put(row)
                }
            }
            payload.put(table, rows)
        }
        return payload.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= VaultBackupCodec.MAX_PLAINTEXT) { "Backup export is too large." }
        }
    }

    fun restoreIntoEmpty(db: SQLiteDatabase, plain: ByteArray) {
        require(plain.size <= VaultBackupCodec.MAX_PLAINTEXT) { "Backup is oversized." }
        val payload = JSONObject(plain.toString(Charsets.UTF_8))
        require(payload.getInt("format") == 1 && payload.getInt("databaseVersion") == VaultSchema.VERSION) {
            "Unsupported native backup schema."
        }
        // Reject any attempt to combine backups or overwrite existing financial books.
        for (table in columns.keys) {
            db.rawQuery("SELECT 1 FROM $table LIMIT 1", null).use {
                check(!it.moveToFirst()) { "Backup restore requires an empty native vault." }
            }
        }
        for ((table, names) in columns) {
            val rows = payload.getJSONArray(table)
            require(rows.length() <= MAX_ROWS_PER_TABLE) { "Too many backup records." }
            val placeholders = names.joinToString(",") { "?" }
            val statement = "INSERT INTO $table (${names.joinToString(",")}) VALUES ($placeholders)"
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                require(row.length() == names.size) { "Backup column mismatch." }
                val values = arrayOfNulls<Any>(names.size)
                for (j in names.indices) {
                    if (!row.isNull(j)) {
                        val value = row.getString(j)
                        require(value.length <= 1_048_576) { "Backup field is oversized." }
                        values[j] = if (names[j] in integers) value.toLong() else value
                    }
                }
                db.execSQL(statement, values)
            }
        }
    }
}
