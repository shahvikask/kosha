package `in`.growurdhan.redevelopment

import android.database.Cursor
import `in`.growurdhan.database.RestoreV9Plan
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Exact, type-preserving financial-row check shared by pre-publication
 * verification and post-crash read-only forensics.
 *
 * SQLCipher HMAC/foreign key/trigger verification is performed by the caller.
 * This check rejects every omitted, rewritten, duplicate, additional or
 * type-coerced financial row, even when table counts and the authenticated
 * original-JSON fingerprint match.
 */
internal object RestoredRowsVerifier {
    private val tables = listOf("vault_account", "entity", "account",
        "instrument", "holding", "ledger_event", "goal", "setting")
    private val safeColumn = Regex("[a-z][a-z0-9_]*")

    fun verifyExactRows(db: SQLiteDatabase, plan: RestoreV9Plan.Plan): Int {
        require(plan.rows.isNotEmpty() &&
            plan.rows.all { it.table in tables }) { "Unexpected restored table" }

        for (table in tables) {
            val expected = plan.rows.filter { it.table == table }
            val columns = expected.firstOrNull()?.columns
            if (columns == null) {
                db.rawQuery("SELECT count(*) FROM $table", null).use { cur ->
                    require(cur.moveToFirst() && cur.getLong(0) == 0L &&
                        !cur.moveToNext()) { "Unexpected restored table rows" }
                }
                continue
            }
            require(columns.isNotEmpty() && columns.all(safeColumn::matches) &&
                columns.distinct().size == columns.size &&
                expected.all { it.columns == columns &&
                    it.values.size == columns.size }) {
                "Noncanonical restored column projection"
            }
            // Multiset equality, not a count, SQLite row order, or a
            // lossy string conversion. Every Long remains INTEGER, every
            // nullable text retains its exact UTF-8 string/NULL distinction.
            val remaining = expected.groupingBy { it.values }.eachCount().toMutableMap()
            val selection = columns.joinToString(",")
            db.rawQuery("SELECT $selection FROM $table", null).use { cur ->
                while (cur.moveToNext()) {
                    val actual = (0 until cur.columnCount).map { index ->
                        when (cur.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cur.getLong(index)
                            Cursor.FIELD_TYPE_STRING -> cur.getString(index)
                            else -> error("Unsupported restored financial value type")
                        }
                    }
                    val count = remaining[actual] ?: 0
                    require(count > 0) {
                        "Encrypted row differs from authenticated restore projection"
                    }
                    if (count == 1) remaining.remove(actual)
                    else remaining[actual] = count - 1
                }
            }
            require(remaining.isEmpty()) { "Authenticated financial row missing" }
        }
        return plan.rows.size
    }
}
