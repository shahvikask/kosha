package `in`.growurdhan.redevelopment

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Partial P31/P32 — opt-in, device-local, redacted technical diagnostics.
 * No network requests, background collection, identifiers, PDF/statement
 * content, rates, amounts, personal data, account values or free-text logs.
 * The user may revoke consent and delete all collected diagnostics (none
 * are persisted other than the consent toggle). No export is automatic.
 */
internal class NativePrivateDiagnosticsRepository(
    private val accounts: NativeAccountRepository
) {
    data class Report(val schemaVersion: Int, val foreignKeysValid: Boolean,
        val encryptedLocalOnly: Boolean = true, val networkUpload: Boolean = false) {
        fun redactedText(): String = listOf(
            "GrowUrDhan technical health v1",
            "schema_version=$schemaVersion",
            "foreign_keys=" + if (foreignKeysValid) "valid" else "review_required",
            "vault=on_device_sqlcipher",
            "network_upload=disabled",
            "raw_diagnostic_logs_retained=false",
            "support_text=owner_initiated_copy_only",
            "identifiers_and_financial_values=excluded"
        ).joinToString("\n")
    }
    private val key = "native.privacy.diagnostics.enabled"

    private fun readEnabled(db: SQLiteDatabase): Boolean =
        db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) false else c.getString(0) == "1"
        }

    fun enabled(email: String): Boolean =
        accounts.withUnlockedVault(email) { readEnabled(it) }

    /** Stored encrypted. Revocation removes the consent marker entirely. */
    fun setEnabled(email: String, approved: Boolean) {
        accounts.withUnlockedVault(email) { db ->
            if (approved) db.execSQL(
                "INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(key, "1"))
            else db.execSQL("DELETE FROM setting WHERE key=?", arrayOf(key))
        }
    }

    /** A disabled session never probes sensitive database health tables. */
    fun localReport(email: String): Report? =
        accounts.withUnlockedVault(email) { db ->
            if (!readEnabled(db)) return@withUnlockedVault null
            val schema = db.rawQuery("PRAGMA user_version", null).use { c ->
                check(c.moveToFirst()); c.getInt(0)
            }
            // No detail rows (which may include a record identifier) leave
            // this function; convert solely to a strict boolean indicator.
            val integrity = db.rawQuery("PRAGMA foreign_key_check", null).use { c ->
                !c.moveToFirst()
            }
            Report(schema, integrity)
        }
}
