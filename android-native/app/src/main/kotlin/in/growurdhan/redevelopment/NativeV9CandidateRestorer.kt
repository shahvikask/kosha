package `in`.growurdhan.redevelopment

import `in`.growurdhan.database.NativeV9Archive
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Native GUDNAT01 reverse-restoration primitive, never a live-vault merge.
 *
 * Caller MUST create an unpublished, independent-key SQLCipher v9 candidate,
 * own VaultLifecycleLock + once-only restore claim, verify owner identity,
 * recovery evidence, current Terms, and complete file/key no-overwrite
 * publication. No activity/SAF path calls this until those gates are wired.
 *
 * The code explicitly rejects any source schema or extension that the fresh
 * target cannot faithfully represent. Original full tables/typed cells are
 * independently re-read and compared BEFORE transaction commit.
 */
internal object NativeV9CandidateRestorer {
    // Parent/child tables before dependents; deferred SQLite FK checks cover
    // guardian links and any cross-row references within a table.
    private val order = listOf("vault_meta", "vault_account", "entity",
        "account", "instrument", "import_batch", "holding", "ledger_event",
        "price_observation", "source_document", "statement_position",
        "tax_rule_set", "liability", "goal", "setting", "egress_log",
        "listed_security", "gmail_seen")
    private val identifier = Regex("[A-Za-z_][A-Za-z_0-9]{0,127}")

    /** Does not open, create or publish a vault. Bound to a candidate supplied by caller. */
    fun restoreIntoFreshCandidate(db: SQLiteDatabase, encrypted: ByteArray,
        email: String, backupPassword: CharArray,
        explicitOwnerApproval: Boolean): Int {
        require(explicitOwnerApproval) { "Native restore needs explicit owner approval" }
        val source = NativeV9Archive.decrypt(encrypted, email, backupPassword)
        try {
            require(!db.inTransaction()) { "Native restore target transaction already active" }
            val empty = NativeV9SnapshotReader.capture(db)
            require(source.objects == empty.objects &&
                source.tables.map { it.name to it.columns } ==
                empty.tables.map { it.name to it.columns }) {
                "Native archive schema differs from fresh encrypted candidate"
            }
            require(source.tables.map { it.name }.toSet() == order.toSet())
            require(empty.tables.all { table ->
                if (table.name != "vault_meta") table.rows.isEmpty()
                else table.rows.size == 1 &&
                    table.rows[0][0].boundValue() == "schema_version" &&
                    table.rows[0][1].boundValue() == "9"
            }) { "Native restore target must be an unused initialized candidate" }
            require(source.tables.single { it.name == "vault_meta" }.rows.any {
                it[0].boundValue() == "schema_version" &&
                    it[1].boundValue() == "9"
            })
            val byName = source.tables.associateBy { it.name }
            var inserted = 0
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA defer_foreign_keys=ON")
                db.execSQL("DELETE FROM vault_meta")
                for (table in order) {
                    val t = byName.getValue(table)
                    require(identifier.matches(t.name) &&
                        t.columns.isNotEmpty() &&
                        t.columns.all { identifier.matches(it.name) })
                    val fields = t.columns.joinToString(",") { it.name }
                    val places = List(t.columns.size) { "?" }.joinToString(",")
                    val insert = "INSERT INTO " + t.name + " (" + fields +
                        ") VALUES (" + places + ")"
                    for (row in t.rows) {
                        db.execSQL(insert, row.map { it.boundValue() }.toTypedArray())
                        inserted = Math.addExact(inserted, 1)
                    }
                }
                // Captures every table and native-only row with the original
                // storage class; NOT a row-count-only or sampled comparison.
                val recovered = NativeV9SnapshotReader.capture(db)
                require(recovered == source) {
                    "Native candidate cannot reconstruct exact source cells"
                }
                require(db.rawQuery("PRAGMA foreign_key_check", null).use {
                    !it.moveToFirst()
                }) { "Native candidate contains dangling references" }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            return inserted
        } finally {
            // Plaintext native finance is process memory only.
            source.tables.forEach { t -> t.rows.forEach { r ->
                r.forEach { it.payload.fill(0) }
            } }
        }
    }
}
