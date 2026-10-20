package `in`.growurdhan.redevelopment

import android.database.Cursor
import `in`.growurdhan.database.RestoreV9Plan
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Compare EVERY restored SQLCipher financial cell against an authenticated
 * projection, including columns omitted from INSERT and filled by a frozen
 * schema default. Only the immutable, allow-listed restore tables are read.
 *
 * This is shared by pre-publication verification and read-only crash forensics.
 * SQLCipher HMAC, source JSON hash, FKs and trigger checks are caller duties.
 */
internal object RestoredRowsVerifier {
    private val tables = listOf("vault_account", "entity", "account",
        "instrument", "holding", "ledger_event", "goal", "setting")
    private val identifier = Regex("[a-z][a-z0-9_]*")
    private val integerDefault = Regex("-?(0|[1-9][0-9]*)")
    private val quotedDefault = Regex("'(?:[^']|'')*'")

    private data class Column(val name: String, val defaultValue: Any?)

    private fun defaultFromSql(sql: String?): Any? = when {
        sql == null || sql.equals("NULL", ignoreCase = true) -> null
        integerDefault.matches(sql) -> sql.toLongOrNull()
            ?: error("Unsafe integer default in encrypted restore table")
        quotedDefault.matches(sql) ->
            sql.substring(1, sql.length - 1).replace("''", "'")
        else -> error("Unsupported SQL default in encrypted restore table")
    }

    private fun columns(db: SQLiteDatabase, table: String): List<Column> {
        require(table in tables)
        val schema = mutableListOf<Column>()
        db.rawQuery("PRAGMA table_info('$table')", null).use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1)
                require(identifier.matches(name)) { "Unexpected restored SQL column" }
                schema += Column(name, defaultFromSql(
                    if (cur.isNull(4)) null else cur.getString(4)))
            }
        }
        require(schema.isNotEmpty() &&
            schema.map { it.name }.distinct().size == schema.size) {
            "Missing or ambiguous restored SQL schema"
        }
        return schema
    }

    private fun typedActual(cur: Cursor, index: Int): Any? =
        when (cur.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cur.getLong(index)
            Cursor.FIELD_TYPE_STRING -> cur.getString(index)
            else -> error("Unsupported restored financial SQLite value type")
        }

    fun verifyExactRows(db: SQLiteDatabase, plan: RestoreV9Plan.Plan): Int {
        require(plan.rows.isNotEmpty() &&
            plan.rows.all { it.table in tables }) {
            "Unexpected restored table"
        }
        for (table in tables) {
            val schema = columns(db, table)
            val byTable = plan.rows.filter { it.table == table }
            val specified = byTable.firstOrNull()?.columns
            if (specified == null) {
                db.rawQuery("SELECT count(*) FROM $table", null).use { cur ->
                    require(cur.moveToFirst() && cur.getLong(0) == 0L &&
                        !cur.moveToNext()) { "Unexpected restored financial table rows" }
                }
                continue
            }
            require(specified.isNotEmpty() &&
                specified.distinct().size == specified.size &&
                specified.all { it in schema.map(Column::name) } &&
                byTable.all { it.columns == specified &&
                    it.values.size == specified.size &&
                    it.values.all { v -> v == null || v is String || v is Long } }) {
                "Invalid restored financial column projection"
            }
            val projectionIndices = specified.withIndex().associate {
                it.value to it.index
            }
            // An omitted financial field must remain precisely its frozen
            // schema default (including NULL), not an arbitrary extra value.
            val multiset = byTable.map { row ->
                schema.map { col ->
                    val index = projectionIndices[col.name]
                    if (index == null) col.defaultValue else row.values[index]
                }
            }.groupingBy { it }.eachCount().toMutableMap()
            // Read the FULL physical record with SQLite's actual cell types,
            // not just the columns present in the authenticated source plan.
            val select = schema.joinToString(",") { "${it.name}" }
            db.rawQuery("SELECT $select FROM $table", null).use { cur ->
                while (cur.moveToNext()) {
                    val actual = (0 until cur.columnCount).map {
                        typedActual(cur, it)
                    }
                    val count = multiset[actual] ?: 0
                    require(count > 0) {
                        "Restored field or default differs from authenticated projection"
                    }
                    if (count == 1) multiset.remove(actual)
                    else multiset[actual] = count - 1
                }
            }
            require(multiset.isEmpty()) {
                "Authenticated financial row missing"
            }
        }
        return plan.rows.size
    }
}
