package `in`.growurdhan.redevelopment

import android.content.Context
import java.io.File
import `in`.growurdhan.database.SchemaV9Catalog
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * M24 native SQLCipher file lifecycle. Never use android.database.sqlite.
 * User authentication, valid restore identity and unlocked UI are upstream
 * prerequisites; this class must not be wired to an automatically unlocked UI.
 *
 * No destructively "repair on corruption" handler is installed.
 * Missing files, unknown versions and incompatible schemas are fatal.
 */
internal class EncryptedVaultStore(private val context: Context) {
    private val keys = KeystoreWrappedVaultKey(context)
    private val file: File = context.getDatabasePath("growurdhan-v9.sqlcipher")
    // SQLCipher default corruption handler DELETES damaged files. Never use it.
    private val preserveOnCorruption = DatabaseErrorHandler { _, _ ->
        // SQLCipher Android 4.17 requires (database, SQLiteException)
        // parameters. Do not invoke the default destructive error handler.
        throw IllegalStateException("Encrypted vault corruption: preserve original file")
    }

    @Synchronized
    fun createFresh(legacyVaultPresent: Boolean): SQLiteDatabase =
        VaultLifecycleLock.exclusively(context) {
            createFreshUnderLock(legacyVaultPresent)
        }

    private fun createFreshUnderLock(legacyVaultPresent: Boolean): SQLiteDatabase {
        RestoreArtifactGuard.requireNoAbandonedRestore(context)
        check(!file.exists()) { "Encrypted vault already exists" }
        check(!keys.hasExistingEnvelope()) {
            "Wrapped key exists without database; refuse silent empty-vault recreation"
        }
        // The caller must also check external legacy-import locations before
        // authorizing a new vault. This method checks the known local stores.
        check(!File(context.filesDir, "vault.enc").exists())
        check(!File(context.noBackupFilesDir, "vault.enc").exists())
        check(!legacyVaultPresent) { "Existing legacy vault requires explicit import" }
        val pending = File(context.noBackupFilesDir, "restore-pending.v1.kosha")
        check(!pending.exists() && !File(pending.path + ".bak").exists() &&
            !File(pending.path + ".new").exists()) {
            "Pending encrypted restore must be explicitly reviewed before creating a vault"
        }
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val key = keys.getOrCreateForFreshInstall(legacyVaultPresent = false)
        val db = try {
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(
                file.absolutePath, key, null, SQLiteDatabase.CREATE_IF_NECESSARY,
                preserveOnCorruption, null
            )
        } finally { key.fill(0) }
        try {
            initialiseFreshV9(db)
            return db
        } catch (failure: Exception) {
            db.close()
            // Preserve failed encrypted files and wrapped keys for review.
            throw failure
        }
    }

    /**
     * Restore candidate ONLY. A caller must hold the exclusive lifecycle lock,
     * possess a DISTINCT candidate envelope, and have completed GCM account proof.
     * This never opens, creates, or updates the live vault path.
     */
    internal fun createNewRestoreCandidate(candidate: File, candidateKey: ByteArray): SQLiteDatabase {
        val live = file.absoluteFile.toPath().normalize()
        val path = candidate.absoluteFile.toPath().normalize()
        require(path.parent == live.parent && path != live &&
            path.fileName.toString().matches(
                Regex("growurdhan-v9\\.sqlcipher\\.restore-[0-9a-f-]{36}"))) {
            "Invalid isolated restore target filename"
        }
        check(!candidate.exists() &&
            !File(candidate.path + "-journal").exists() &&
            !File(candidate.path + "-wal").exists() &&
            !File(candidate.path + "-shm").exists()) {
            "Restore candidate already exists; manual recovery is required"
        }
        require(candidateKey.size == 32) { "Invalid new SQLCipher key length" }
        require(candidate.parentFile?.isDirectory == true)
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openDatabase(candidate.absolutePath, candidateKey,
            null, SQLiteDatabase.CREATE_IF_NECESSARY, preserveOnCorruption, null)
        try {
            initialiseFreshV9(db)
            return db
        } catch (failure: Exception) {
            db.close()
            // No auto-delete: preserve the candidate for explicit recovery.
            throw failure
        }
    }

    private fun initialiseFreshV9(db: SQLiteDatabase) {
        requireCipher(db)
        db.execSQL("PRAGMA foreign_keys=ON")
        db.beginTransaction()
        try {
            for ((_, statement) in readStatements("m24-frozen-v9.sql")) db.execSQL(statement)
            db.execSQL("PRAGMA user_version=9")
            db.execSQL("INSERT INTO vault_meta(key,value,updated_at) " +
                "VALUES ('schema_version','9','2026-10-16')")
            verifyVersion(db, 9)
            verifyOwnershipAndLedger(db)
            verifyIntegrity(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Existing native database ONLY. Never opens non-existent files or reset-to-empty. */
    @Synchronized
    fun openExisting(): SQLiteDatabase {
        check(file.isFile) { "Missing native encrypted vault; refusing implicit recreation" }
        val key = keys.unwrapExisting()
        val db = try {
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(file.absolutePath, key, null,
                SQLiteDatabase.OPEN_READWRITE, preserveOnCorruption, null)
        } finally { key.fill(0) }
        try {
            requireCipher(db)
            db.execSQL("PRAGMA foreign_keys=ON")
            verifyIntegrity(db)
            val version = singleLong(db, "PRAGMA user_version").toInt()
            require(version in 1..9) { "Unknown or unsupported schema version" }
            verifyVersion(db, version)
            verifyOwnershipAndLedger(db, schemaVersion = version)
            if (version < 9) migrateInsideTransaction(db, version)
            verifyVersion(db, 9)
            verifyOwnershipAndLedger(db)
            verifyIntegrity(db)
            return db
        } catch (failure: Exception) {
            db.close()
            throw failure
        }
    }

    private fun migrateInsideTransaction(db: SQLiteDatabase, oldVersion: Int) {
        // Asset is generated from the immutable frozen SCHEMA_V3..V9 symbols.
        // A single SQLCipher transaction protects every existing row and
        // version marker; no shadow plaintext DB or silent empty replacement.
        db.beginTransaction()
        try {
            for ((stepVersion, statement) in readStatements("m24-frozen-upgrades.sql")) {
                if (stepVersion == null || stepVersion <= oldVersion) continue
                if (statement.startsWith("ALTER TABLE", ignoreCase = true) &&
                    alreadyHasColumn(db, statement)) continue
                db.execSQL(statement)
            }
            db.execSQL("PRAGMA user_version=9")
            db.execSQL("UPDATE vault_meta SET value='9' WHERE key='schema_version'")
            verifyVersion(db, 9)
            verifyOwnershipAndLedger(db)
            verifyIntegrity(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun alreadyHasColumn(db: SQLiteDatabase, sql: String): Boolean {
        val m = Regex("""ALTER TABLE ([a-z_]+) ADD COLUMN ([a-z_]+) ([A-Z]+).*""",
            RegexOption.IGNORE_CASE).matchEntire(sql.trim().removeSuffix(";"))
            ?: error("Unexpected migration DDL")
        val table = m.groupValues[1]
        val column = m.groupValues[2]
        val type = m.groupValues[3]
        db.rawQuery("PRAGMA table_info('$table')", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    require(cursor.getString(2).uppercase() == type.uppercase()) {
                        "Incompatible prior migration field $table.$column"
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun requireCipher(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA cipher_version", null).use {
            require(it.moveToFirst() && !it.getString(0).isNullOrBlank()) {
                "Database driver lacks SQLCipher"
            }
        }
    }

    private fun verifyIntegrity(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA quick_check", null).use {
            require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) {
                "Encrypted vault integrity failed"
            }
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use {
            require(!it.moveToFirst()) { "Foreign-key violation in vault" }
        }
    }

    private fun verifyOwnershipAndLedger(db: SQLiteDatabase, schemaVersion: Int = 9) {
        // Never bless a truncated legacy database by advancing only user_version.
        // Frozen V1 contains 15 baseline tables; v5 adds listed_security,
        // v6 adds vault_account and v8 adds gmail_seen. Refuse absent tables
        // appropriate to EACH source version before changing any schema.
        val requiredTables = SchemaV9Catalog.tableNames.filter { table ->
            when (table) {
                "listed_security" -> schemaVersion >= 5
                "vault_account" -> schemaVersion >= 6
                "gmail_seen" -> schemaVersion >= 8
                else -> true
            }
        }
        for (table in requiredTables) {
            db.rawQuery("SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type='table' AND name=?", arrayOf(table)).use { cur ->
                require(cur.moveToFirst() && cur.getLong(0) == 1L) {
                    "Missing required legacy table $table"
                }
            }
            db.rawQuery("PRAGMA table_info('$table')", null).use { cur ->
                while (cur.moveToNext()) {
                    require(!cur.getString(2).equals("REAL", ignoreCase = true)) {
                        "Floating-point persistence field in $table"
                    }
                }
            }
        }
        if (schemaVersion == 9) {
            // Preserve unknown-but-valid physical extensions, while refusing
            // every missing canonical v9 field including the seven additions.
            for (table in SchemaV9Catalog.tables) {
                val required = table.columns.toSet() +
                    (if (table.name == "entity")
                        setOf("cas_id", "pran", "gmail_sync", "cas_id_source",
                            "pran_source", "cas_sync_after", "pran_sync_after")
                    else emptySet())
                db.rawQuery("PRAGMA table_info('${table.name}')", null).use { cur ->
                    val actual = mutableSetOf<String>()
                    while (cur.moveToNext()) actual.add(cur.getString(1))
                    require(actual.containsAll(required)) {
                        "Missing canonical v9 fields in ${table.name}: ${required - actual}"
                    }
                }
            }
        }
        for ((table, column, expected) in listOf(
            Triple("entity", "entity_id", "TEXT"),
            Triple("holding", "entity_id", "TEXT"),
            Triple("holding", "current_price_minor", "INTEGER"),
            Triple("ledger_event", "quantity_scaled", "INTEGER"),
            Triple("ledger_event", "total_amount_minor", "INTEGER"),
            Triple("ledger_event", "charges_minor", "INTEGER")
        )) {
            db.rawQuery("PRAGMA table_info('$table')", null).use { cur ->
                var valid = false
                while (cur.moveToNext()) {
                    if (cur.getString(1) == column &&
                        cur.getString(2).equals(expected, ignoreCase = true)) valid = true
                }
                require(valid) { "Missing/incompatible persisted field $table.$column" }
            }
        }
        db.rawQuery("PRAGMA foreign_key_list('account')", null).use { cur ->
            var found = false
            while (cur.moveToNext()) {
                if (cur.getString(2) == "entity" &&
                    cur.getString(3) == "entity_id" && cur.getString(4) == "entity_id"
                ) found = true
            }
            require(found) { "Missing account ownership foreign key" }
        }
        for ((name, operation) in listOf(
            "tg_ledger_event__no_update" to "BEFORE UPDATE",
            "tg_ledger_event__no_delete" to "BEFORE DELETE"
        )) {
            db.rawQuery("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?",
                arrayOf(name)).use { cur ->
                require(cur.moveToFirst()) { "Missing immutable ledger trigger $name" }
                val ddl = cur.getString(0).uppercase()
                require(ddl.contains(operation) &&
                    ddl.contains("ON LEDGER_EVENT") && ddl.contains("RAISE(ABORT")) {
                    "Invalid immutable ledger trigger $name"
                }
            }
        }
    }

    private fun verifyVersion(db: SQLiteDatabase, expected: Int) {
        require(singleLong(db, "PRAGMA user_version") == expected.toLong()) {
            "Vault schema version mismatch"
        }
        db.rawQuery("SELECT value FROM vault_meta WHERE key='schema_version'", null).use {
            require(it.moveToFirst() && it.getString(0) == expected.toString()
                && !it.moveToNext()) { "Missing or incompatible vault metadata version" }
        }
    }

    private fun singleLong(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use {
            check(it.moveToFirst())
            it.getLong(0)
        }

    /** Scripts consist ONLY of checked-in frozen SQL with explicit boundaries. */
    private fun readStatements(asset: String): List<Pair<Int?, String>> {
        val text = context.assets.open(asset).bufferedReader().use { it.readText() }
        return text.split("-- M24_END_STATEMENT").mapNotNull { block ->
            val version = Regex("""-- M24_VERSION_(\d+)""").find(block)
                ?.groupValues?.get(1)?.toInt()
            val sql = block.lineSequence()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n").trim()
            if (sql.isBlank()) null else version to sql
        }
    }
}
