package `in`.growurdhan.redevelopment

import `in`.growurdhan.database.RestoreV9Plan
import `in`.growurdhan.model.NativeRegistrationProof
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Same candidate projection for first-time restore publication and read-only
 * crash forensics. Source JSON is authenticated independently; these three
 * NEW-device consent rows are validated local evidence, not archive contents.
 */
internal object NativeRestoreTermsRows {
    private val keys = setOf("m25.terms.version", "m25.terms.text_id",
        "m25.terms.accepted_at")

    fun append(source: RestoreV9Plan.Plan,
        consent: NativeRegistrationProof.Consent): RestoreV9Plan.Plan {
        NativeRegistrationProof.requireValidConsent(consent)
        val last = source.rows.lastOrNull()
        require(last != null && last.table == "setting" &&
            last.columns == listOf("key", "value") &&
            last.values.firstOrNull() == "m24.authenticated_frozen_original_json") {
            "The authenticated original JSON must remain the final restore row"
        }
        require(source.rows.none { it.table == "setting" &&
            it.values.firstOrNull() in keys }) {
            "Conflicting source consent must not override new-device acceptance"
        }
        val evidence = listOf(
            "m25.terms.version" to consent.version,
            "m25.terms.text_id" to consent.textId,
            "m25.terms.accepted_at" to consent.acceptedAt
        ).map { (key, value) ->
            RestoreV9Plan.Row("setting", listOf("key", "value"),
                listOf(key, value))
        }
        return source.copy(rows = source.rows.dropLast(1) + evidence + last)
    }

    /**
     * Inspect only the three consent keys in an already-open READ-ONLY
     * SQLCipher candidate after its integrity checks. Strictly reject missing,
     * old, malformed or extra acceptance evidence before financial-row
     * comparison. Do not change any row or grant publication permission.
     */
    fun projectForensicCandidate(source: RestoreV9Plan.Plan,
        db: SQLiteDatabase): RestoreV9Plan.Plan {
        val found = linkedMapOf<String, String>()
        db.rawQuery("SELECT key,value FROM setting WHERE key IN (?,?,?)",
            keys.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                require(key in keys && !found.containsKey(key) && !cursor.isNull(1)) {
                    "Ambiguous restore consent evidence"
                }
                found[key] = cursor.getString(1)
            }
        }
        require(found.keys == keys) { "Missing new-device restore acceptance" }
        return append(source, NativeRegistrationProof.Consent(
            found.getValue("m25.terms.version"),
            found.getValue("m25.terms.text_id"),
            found.getValue("m25.terms.accepted_at"), scrolledToEnd = true))
    }
}
