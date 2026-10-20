package `in`.growurdhan.redevelopment

import java.nio.ByteBuffer
import java.security.MessageDigest
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Read-only typed value witness across in-place, transactional v1..v9 migrations.
 * A schema upgrade may ADD fields/tables; it must not rewrite even an opaque
 * historic value in an existing column. Fingerprints never leave process memory.
 *
 * Requires the calling SQLCipher transaction for capture AND verification.
 * Never logs data, creates an unencrypted shadow, or silently repairs a mismatch.
 */
internal class HistoricalMigrationWitness private constructor(
    private val originals: List<TableWitness>,
    private val originalObjects: List<SchemaObject>
) {
    private data class TableWitness(
        val name: String,
        val existingColumns: List<String>,
        val originalColumnDeclarations: List<String>,
        val foreignKeys: List<String>,
        val rows: Long,
        val hash: ByteArray
    )
    // SQL objects may be added by later versions, but NONE of the original
    // index/trigger/view definitions may disappear or be silently replaced.
    private data class SchemaObject(
        val type: String, val name: String, val tableName: String,
        val sql: String?
    )
    companion object {
        private val identifier = Regex("[A-Za-z_][A-Za-z_0-9]*")
        private fun sqlName(value: String): String {
            require(identifier.matches(value)) { "Unexpected historic SQL identifier" }
            return "\"" + value + "\""
        }
        private fun writeInt(digest: MessageDigest, value: Int) {
            digest.update(ByteBuffer.allocate(4).putInt(value).array())
        }
        private fun feed(digest: MessageDigest, bytes: ByteArray) {
            writeInt(digest, bytes.size)
            digest.update(bytes)
        }
        private fun measure(db: SQLiteDatabase, table: String,
                            fields: List<String>): Pair<Long, ByteArray> {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            // Rowid-stable scan: the frozen migration scripts do not rebuild
            // tables, VACUUM, or rewrite historic records.
            val select = "SELECT rowid," + fields.joinToString(",") { sqlName(it) } +
                " FROM " + sqlName(table) +
                (if (table == "vault_meta") " WHERE key <> 'schema_version'" else "") +
                " ORDER BY rowid"
            db.rawQuery(select, null).use { cur ->
                while (cur.moveToNext()) {
                    count = Math.addExact(count, 1L)
                    digest.update(0x52.toByte()) // row boundary
                    // Preserve the actual original row identity as well as
                    // ordered values. Identical rows must not mask a rebuild.
                    feed(digest, ByteBuffer.allocate(8).putLong(cur.getLong(0)).array())
                    for (i in fields.indices) {
                        val index = i + 1
                        val type = cur.getType(index)
                        digest.update(type.toByte())
                        when (type) {
                            0 -> Unit // NULL
                            1 -> feed(digest, ByteBuffer.allocate(8)
                                .putLong(cur.getLong(index)).array()) // INTEGER
                            2 -> {
                                // Frozen v1-v8 columns may contain true SQLite
                                // REAL cells even where native finance now uses
                                // scaled integers. Witness their exact IEEE-754
                                // storage bit pattern; NEVER round, truncate
                                // or rewrite those historical cells.
                                val bits = java.lang.Double.doubleToRawLongBits(
                                    cur.getDouble(index))
                                feed(digest, ByteBuffer.allocate(8)
                                    .putLong(bits).array())
                            }
                            3 -> feed(digest, cur.getString(index)
                                .toByteArray(Charsets.UTF_8)) // TEXT
                            4 -> feed(digest, cur.getBlob(index)) // BLOB
                            else -> error("Unknown original SQLite storage type")
                        }
                    }
                }
            }
            return count to digest.digest()
        }
        private fun foreignKeys(db: SQLiteDatabase, table: String): List<String> {
            val keys = mutableListOf<String>()
            db.rawQuery("PRAGMA foreign_key_list(" + sqlName(table) + ")", null).use { c ->
                while (c.moveToNext()) {
                    // Preserve the full source FK contract, including every
                    // referenced table/column and ON UPDATE/DELETE behavior.
                    keys += (0 until c.columnCount).joinToString("\u0000") { i ->
                        if (c.isNull(i)) "<NULL>" else c.getString(i)
                    }
                }
            }
            return keys.sorted()
        }

        private fun schemaObjects(db: SQLiteDatabase): List<SchemaObject> {
            val objects = mutableListOf<SchemaObject>()
            db.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master " +
                "WHERE type IN ('index','trigger','view') ORDER BY type,name",
                null).use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0)
                    val name = c.getString(1)
                    val table = c.getString(2)
                    // Names/DDL are historic schema evidence. Capture even
                    // SQLite's implicit (SQL NULL) uniqueness indexes.
                    require(type in setOf("index","trigger","view"))
                    objects += SchemaObject(type, name, table,
                        if (c.isNull(3)) null else c.getString(3))
                }
            }
            return objects
        }

        fun capture(db: SQLiteDatabase): HistoricalMigrationWitness {
            val originals = mutableListOf<TableWitness>()
            val tables = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cur ->
                while (cur.moveToNext()) tables += cur.getString(0)
            }
            for (table in tables) {
                sqlName(table)
                val cols = mutableListOf<String>()
                val declarations = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info(" + sqlName(table) + ")", null).use { cur ->
                    while (cur.moveToNext()) {
                        cols += cur.getString(1)
                        declarations += listOf(cur.getString(1),
                            cur.getString(2), cur.getInt(3).toString(),
                            if (cur.isNull(4)) "<NULL>" else cur.getString(4),
                            cur.getInt(5).toString()).joinToString("\\u0000")
                    }
                }
                require(cols.isNotEmpty()) { "Historic table has no columns" }
                val (rows, digest) = measure(db, table, cols)
                originals += TableWitness(table, cols, declarations,
                    foreignKeys(db, table), rows, digest)
            }
            return HistoricalMigrationWitness(originals, schemaObjects(db))
        }
    }

    fun verifyUnmodified(db: SQLiteDatabase) {
        val currentObjects = schemaObjects(db).associateBy { it.type to it.name }
        require(originalObjects.all { old ->
            currentObjects[old.type to old.name] == old
        }) { "Historical index, trigger or view was removed or rewritten" }
        for (old in originals) {
            require(foreignKeys(db, old.name) == old.foreignKeys) {
                "Historic foreign-key contract changed during migration: ${old.name}"
            }
            val currentCols = mutableListOf<String>()
            val currentDeclarations = mutableListOf<String>()
            db.rawQuery("PRAGMA table_info(" + sqlName(old.name) + ")", null).use { cur ->
                while (cur.moveToNext()) {
                    currentCols += cur.getString(1)
                    currentDeclarations += listOf(cur.getString(1),
                        cur.getString(2), cur.getInt(3).toString(),
                        if (cur.isNull(4)) "<NULL>" else cur.getString(4),
                        cur.getInt(5).toString()).joinToString("\\u0000")
                }
            }
            require(currentCols.containsAll(old.existingColumns) &&
                old.existingColumns.indices.all { index ->
                    val currentIndex = currentCols.indexOf(old.existingColumns[index])
                    currentIndex >= 0 &&
                        currentDeclarations[currentIndex] == old.originalColumnDeclarations[index]
                }) {
                "Historic table/column disappeared during migration: ${old.name}"
            }
            val (currentRows, currentHash) = measure(db, old.name, old.existingColumns)
            require(currentRows == old.rows &&
                MessageDigest.isEqual(currentHash, old.hash)) {
                "Historic table content changed during migration: ${old.name}"
            }
        }
    }
}
