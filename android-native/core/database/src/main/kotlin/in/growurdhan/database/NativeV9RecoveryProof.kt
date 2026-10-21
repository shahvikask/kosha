package `in`.growurdhan.database

import `in`.growurdhan.model.NativeRegistrationProof
import java.security.MessageDigest

/**
 * Separate native-v9 recovery proof: GCM backup password alone is NOT
 * new-device identity. Pure, no SQL or archive publication side effects.
 */
object NativeV9RecoveryProof {
    data class Receipt(val snapshotSha256: String, val rowCount: Int)
    fun authenticate(snapshot: NativeV9Archive.Snapshot,
        credentials: RestoreIdentityGate.Credentials,
        consent: NativeRegistrationProof.Consent): Receipt {
        NativeRegistrationProof.requireValidConsent(consent)
        val accountTable = snapshot.tables.single { it.name == "vault_account" }
        require(accountTable.rows.size == 1) { "Ambiguous recovered account" }
        fun value(table: NativeV9Archive.Table, row: List<NativeV9Archive.Cell>,
            name: String): String? {
            val at = table.columns.indexOfFirst { it.name == name }
            require(at >= 0) { "Missing recovery identity field" }
            val cell = row[at]
            require(cell.tag == 0 || cell.tag == 2) { "Recovery identity storage type invalid" }
            return cell.boundValue() as? String
        }
        val row = accountTable.rows.single()
        val roundsAt = accountTable.columns.indexOfFirst { it.name == "password_iterations" }
        require(roundsAt >= 0 && row[roundsAt].tag == 1)
        val rounds = row[roundsAt].boundValue() as Long
        require(rounds in 100_000L..1_000_000L)
        val stored = RestoreIdentityGate.Account(
            value(accountTable,row,"email") ?: error("No account email"),
            value(accountTable,row,"password_salt") ?: error("No account salt"),
            value(accountTable,row,"password_hash") ?: error("No account hash"),
            rounds.toInt(),
            value(accountTable,row,"pan_for_statements"),
            value(accountTable,row,"pan_masked"),
            value(accountTable,row,"date_of_birth"),
            value(accountTable,row,"q1_id") ?: error("Missing recovery answer identifier"),
            value(accountTable,row,"q1_hash") ?: error("Missing recovery proof"),
            value(accountTable,row,"q2_id") ?: error("Missing recovery answer identifier"),
            value(accountTable,row,"q2_hash") ?: error("Missing recovery proof"))
        RestoreIdentityGate.verifyForNewDevice(stored, credentials)
        val entities = snapshot.tables.single { it.name == "entity" }
        val primary = entities.columns.indexOfFirst { it.name == "is_primary" }
        require(primary >= 0 && entities.rows.count {
            it[primary].tag == 1 && it[primary].boundValue() == 1L
        } == 1) { "Ambiguous household primary owner" }
        val meta = snapshot.tables.single { it.name == "vault_meta" }
        require(meta.rows.size == 1 && value(meta,meta.rows.single(),"key") ==
            "schema_version" && value(meta,meta.rows.single(),"value") == "9") {
            "Invalid encrypted vault metadata"
        }
        val settings = snapshot.tables.single { it.name == "setting" }
        val accepted = settings.rows.associate { r ->
            (value(settings,r,"key") ?: error("Missing setting key")) to
                (value(settings,r,"value") ?: error("Missing setting value"))
        }
        require(accepted.size == settings.rows.size &&
            accepted["m25.terms.version"] == consent.version &&
            accepted["m25.terms.text_id"] == consent.textId) {
            "Archived Terms are incompatible with current acceptance"
        }
        val plain = NativeV9Archive.encodeInMemory(snapshot)
        try {
            val hash = MessageDigest.getInstance("SHA-256").digest(plain)
                .joinToString("") { "%02x".format(it) }
            return Receipt(hash, snapshot.tables.sumOf { it.rows.size })
        } finally { plain.fill(0) }
    }
}
