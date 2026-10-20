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
    private val originals: List<TableWitness>
) {
    private data class TableWitness(
        val name: String,
        val existingColumns: List<String>,
        val rows: Long,
        val hash: ByteArray
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
            val select = "SELECT " + fields.joinToString(",") { sqlName(it) } +
                " FROM " + sqlName(table) +
                (if (table == "vault_meta") " WHERE key <> 'schema_version'" else "") +
                " ORDER BY rowid"
            db.rawQuery(select, null).use { cur ->
                while (cur.moveToNext()) {
                    count = Math.addExact(count, 1L)
                    digest.update(0x52.toByte()) // row boundary
                    for (i in fields.indices) {
                        val type = cur.getType(i)
                        digest.update(type.toByte())
                        when (type) {
                            0 -> Unit // NULL
                            1 -> feed(digest, ByteBuffer.allocate(8)
                                .putLong(cur.getLong(i)).array()) // INTEGER
                            3 -> feed(digest, cur.getString(i)
                                .toByteArray(Charsets.UTF_8)) // TEXT
                            4 -> feed(digest, cur.getBlob(i)) // BLOB
                            else -> error("Unexpected floating-point/unknown legacy value type")
                        }
                    }
                }
            }
            return count to digest.digest()
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
                db.rawQuery("PRAGMA table_info(" + sqlName(table) + ")", null).use { cur ->
                    while (cur.moveToNext()) cols += cur.getString(1)
                }
                require(cols.isNotEmpty()) { "Historic table has no columns" }
                val (rows, digest) = measure(db, table, cols)
                originals += TableWitness(table, cols, rows, digest)
            }
            return HistoricalMigrationWitness(originals)
        }
    }

    fun verifyUnmodified(db: SQLiteDatabase) {
        for (old in originals) {
            val currentCols = mutableListOf<String>()
            db.rawQuery("PRAGMA table_info(" + sqlName(old.name) + ")", null).use { cur ->
                while (cur.moveToNext()) currentCols += cur.getString(1)
            }
            require(currentCols.containsAll(old.existingColumns)) {
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
