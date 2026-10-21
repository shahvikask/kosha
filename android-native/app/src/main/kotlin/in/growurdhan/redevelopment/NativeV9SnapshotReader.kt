package `in`.growurdhan.redevelopment

import android.database.Cursor
import `in`.growurdhan.database.NativeV9Archive
import `in`.growurdhan.database.SchemaV9Catalog
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * In-memory snapshot from a device-admitted, session-bound SQLCipher transaction.
 * All tables, columns, SQL schema objects and storage-class values are captured,
 * not a lossy legacy JSON projection. Never writes plaintext or financial logs.
 */
internal class NativeV9SnapshotReader(private val accounts: NativeAccountRepository) {
    fun createEncryptedArchive(email: String, backupPassword: CharArray,
        explicitOwnerApproval: Boolean): ByteArray {
        require(explicitOwnerApproval) { "Owner approval required for native backup" }
        require(backupPassword.size >= 12) { "Separate backup password required" }
        return accounts.withUnlockedVault(email) { db ->
            val source = capture(db)
            try { NativeV9Archive.encrypt(source, email, backupPassword) }
            finally { source.tables.forEach { table -> table.rows.forEach { row ->
                row.forEach { it.payload.fill(0) }
            } } }
        }
    }

    companion object {
        private val identifier = Regex("[A-Za-z_][A-Za-z_0-9]{0,127}")

        /** Caller MUST hold the exclusive authorized SQLCipher transaction/lock.
         * Also used by the isolated restore candidate verifier, never live import.
         */
        internal fun capture(db: SQLiteDatabase): NativeV9Archive.Snapshot {
            checkCipher(db)
            val objects = mutableListOf<NativeV9Archive.Schema>()
            db.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master " +
                "WHERE name NOT LIKE 'sqlite_%' ORDER BY type,name", null).use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0)
                    val name = c.getString(1)
                    val table = c.getString(2)
                    require(identifier.matches(name) && identifier.matches(table))
                    objects += NativeV9Archive.Schema(type, name, table,
                        if (c.isNull(3)) null else c.getString(3))
                }
            }
            val tables = mutableListOf<NativeV9Archive.Table>()
            for (obj in objects.filter { it.type == "table" }) {
                val name = obj.name
                val columns = mutableListOf<NativeV9Archive.Column>()
                db.rawQuery("PRAGMA table_info('$name')", null).use { c ->
                    while (c.moveToNext()) {
                        val colName = c.getString(1)
                        require(identifier.matches(colName))
                        columns += NativeV9Archive.Column(colName,
                            c.getString(2) ?: "", c.getInt(3) != 0,
                            c.getInt(5),
                            if (c.isNull(4)) null else c.getString(4))
                    }
                }
                require(columns.isNotEmpty())
                val rows = mutableListOf<List<NativeV9Archive.Cell>>()
                val select = columns.joinToString(",") { it.name }
                db.rawQuery("SELECT " + select + " FROM " + name + " ORDER BY rowid",
                    null).use { c ->
                    while (c.moveToNext()) {
                        require(rows.size < 200_000) { "Oversized native table" }
                        rows += (0 until c.columnCount).map { i -> when(c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> NativeV9Archive.Cell.nullValue()
                            Cursor.FIELD_TYPE_INTEGER ->
                                NativeV9Archive.Cell.integer(c.getLong(i))
                            Cursor.FIELD_TYPE_STRING ->
                                NativeV9Archive.Cell.text(c.getString(i))
                            Cursor.FIELD_TYPE_BLOB ->
                                NativeV9Archive.Cell.blob(c.getBlob(i))
                            Cursor.FIELD_TYPE_FLOAT ->
                                NativeV9Archive.Cell.real(c.getDouble(i))
                            else -> error("Unsupported encrypted SQLite storage class")
                        } }
                    }
                }
                tables += NativeV9Archive.Table(name, columns, rows)
            }
            require(tables.map { it.name }.toSet().containsAll(SchemaV9Catalog.tableNames))
            val snapshot = NativeV9Archive.Snapshot(objects, tables)
            // Materialize under the 64 MiB hard bound *before* any publication
            // can succeed, including opaque historical metadata and blob rows.
            val probe = NativeV9Archive.encodeInMemory(snapshot)
            probe.fill(0)
            return snapshot
        }

        internal fun checkCipher(db: SQLiteDatabase) {
            db.rawQuery("PRAGMA cipher_version", null).use { c ->
                require(c.moveToFirst() && !c.getString(0).isNullOrBlank())
            }
            db.rawQuery("PRAGMA user_version", null).use { c ->
                require(c.moveToFirst() && c.getLong(0) == 9L)
            }
            db.rawQuery("SELECT value FROM vault_meta WHERE key='schema_version'",
                null).use { c ->
                require(c.moveToFirst() && c.getString(0) == "9" &&
                    !c.moveToNext())
            }
            db.rawQuery("PRAGMA quick_check", null).use { c ->
                require(c.moveToFirst() && c.getString(0) == "ok" &&
                    !c.moveToNext()) { "Encrypted SQLite integrity failed" }
            }
            db.rawQuery("PRAGMA cipher_integrity_check", null).use { c ->
                require(!c.moveToFirst()) { "Encrypted SQLCipher HMAC invalid" }
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { c ->
                require(!c.moveToFirst()) { "Native owner reference invalid" }
            }
        }
    }
}
