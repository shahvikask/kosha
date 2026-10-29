package `in`.growurdhan.redevelopment

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Native data-handling disclosure pending owner/legal approval, NOT a signed
 * external Privacy Policy. Revision changes always invalidate prior consent.
 */
internal object NativePrivacyNotice {
    const val VERSION = "native-privacy-notice-2026-10-29-v1"
    const val TEXT_ID = "GUD-NATIVE-DATA-NOTICE-V1"
    val TEXT = listOf(
        "GrowUrDhan • Native data-handling notice ($VERSION)",
        "Your financial information is stored on this device in an encrypted vault. " +
            "Opening the vault requires account and device authentication.",
        "No bank transaction or financial statement is sent to Google merely by viewing this notice. " +
            "Gmail connectivity requires separate Google authorization and an approved family-member mapping; " +
            "this release does not have a configured Gmail OAuth provider.",
        "Google Play purchases and trial eligibility cannot be confirmed until a trusted Play verifier " +
            "is connected. A local purchase flag never grants access.",
        "Backup, recovery of financial archives and cross-device restore are unavailable in this " +
            "base release. Uninstalling or losing the device can permanently erase financial data.",
        "This notice describes the current on-device behavior and is not a substitute for " +
            "a legally reviewed Privacy Policy. You may decline; the notice will remain unaccepted."
    ).joinToString("\n\n")
}

internal class NativePrivacyConsentRepository(private val accounts: NativeAccountRepository) {
    private val versionKey = "native.privacy.consent.version"
    private val textKey = "native.privacy.consent.text_id"
    private val timeKey = "native.privacy.consent.accepted_at"
    data class Evidence(val version: String, val textId: String, val acceptedAtMillis: Long)
    data class Review(val textId: String, val reachedEnd: Boolean,
        val explicitlyAcknowledged: Boolean, val acceptedAtMillis: Long)

    private fun readValue(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun accepted(email: String): Evidence? = accounts.withUnlockedVault(email) { db ->
        val v = readValue(db, versionKey) ?: return@withUnlockedVault null
        val id = readValue(db, textKey) ?: return@withUnlockedVault null
        val at = readValue(db, timeKey)?.toLongOrNull() ?: return@withUnlockedVault null
        if (v == NativePrivacyNotice.VERSION && id == NativePrivacyNotice.TEXT_ID && at >= 0)
            Evidence(v, id, at) else null
    }

    fun accept(email: String, review: Review): Evidence =
        accounts.withUnlockedVault(email) { db ->
            require(review.reachedEnd && review.explicitlyAcknowledged) {
                "Data notice must be read and explicitly acknowledged"
            }
            require(review.textId == NativePrivacyNotice.TEXT_ID &&
                review.acceptedAtMillis > 0L) { "Stale or invalid notice version" }
            val previous = readValue(db, timeKey)?.toLongOrNull()
            require(previous == null || review.acceptedAtMillis >= previous) {
                "Privacy acceptance clock rollback"
            }
            db.beginTransaction()
            try {
                for ((key, value) in listOf(
                    versionKey to NativePrivacyNotice.VERSION,
                    textKey to NativePrivacyNotice.TEXT_ID,
                    timeKey to review.acceptedAtMillis.toString()
                )) db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                    arrayOf<Any>(key, value))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            Evidence(NativePrivacyNotice.VERSION, NativePrivacyNotice.TEXT_ID,
                review.acceptedAtMillis)
        }
}
