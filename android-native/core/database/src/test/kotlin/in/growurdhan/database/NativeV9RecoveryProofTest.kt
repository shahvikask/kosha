package `in`.growurdhan.database

import `in`.growurdhan.model.NativeRegistrationProof
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.*
import org.junit.Test

class NativeV9RecoveryProofTest {
    private val owner = "owner@example.invalid"
    private val password = "Synthetic-login-password-2026!"
    private val pan = "ABCDE1234F"
    private val birthday = "1985-01-01"
    private val consent = NativeRegistrationProof.Consent(
        NativeRegistrationProof.TERMS_VERSION,
        NativeRegistrationProof.TERMS_TEXT_ID,
        "2026-10-21T10:00:00Z", true)

    private fun hash(value: String): Pair<String,String> {
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(value.toCharArray(), salt, 210_000, 256)
        val output = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return Base64.getEncoder().encodeToString(salt) to
            Base64.getEncoder().encodeToString(output)
    }
    private fun packed(answer: String): String {
        val (salt, digest) = hash(answer)
        return salt + ":" + digest + ":210000"
    }
    private val input = RestoreIdentityGate.Credentials(owner, password, pan,
        birthday, "city", "synthetic city", "pet", "synthetic pet")
    private fun snapshot(): NativeV9Archive.Snapshot {
        val (salt, digest) = hash(password)
        val q1 = packed("synthetic city")
        val q2 = packed("synthetic pet")
        val account = mapOf<String,Any>(
            "email" to owner,
            "password_salt" to salt,
            "password_hash" to digest,
            "password_iterations" to 210_000L,
            "pan_for_statements" to pan,
            "date_of_birth" to birthday,
            "q1_id" to "city",
            "q1_hash" to q1,
            "q2_id" to "pet",
            "q2_hash" to q2)
        val objects = SchemaV9Catalog.tables.map {
            NativeV9Archive.Schema("table", it.name, it.name, null)
        }
        val tables = SchemaV9Catalog.tables.map { table ->
            val columns = table.columns.map {
                NativeV9Archive.Column(it, "TEXT", false, 0, null)
            }
            fun row(map: Map<String,Any>): List<NativeV9Archive.Cell> =
                table.columns.map { name -> when (val v = map[name]) {
                    null -> NativeV9Archive.Cell.nullValue()
                    is Long -> NativeV9Archive.Cell.integer(v)
                    is String -> NativeV9Archive.Cell.text(v)
                    else -> error("Unknown synthetic type")
                } }
            val rows = when (table.name) {
                "vault_account" -> listOf(row(account))
                "entity" -> listOf(row(mapOf("is_primary" to 1L)))
                "vault_meta" -> listOf(row(mapOf("key" to "schema_version","value" to "9")))
                "setting" -> listOf(
                    row(mapOf("key" to "m25.terms.version",
                        "value" to consent.version)),
                    row(mapOf("key" to "m25.terms.text_id",
                        "value" to consent.textId)))
                else -> emptyList()
            }
            NativeV9Archive.Table(table.name, columns, rows)
        }
        return NativeV9Archive.Snapshot(objects, tables)
    }
    @Test fun ownerCredentialsTermsAndCanonicalSourceFingerprintRequired() {
        val source = snapshot()
        val first = NativeV9RecoveryProof.authenticate(source, input, consent)
        val second = NativeV9RecoveryProof.authenticate(source, input, consent)
        assertEquals(first, second)
        assertEquals(5, first.rowCount)
        assertTrue(first.snapshotSha256.matches(Regex("[0-9a-f]{64}")))
        val archiveA = NativeV9Archive.encrypt(source, owner,
            "Synthetic separate backup password".toCharArray())
        val archiveB = NativeV9Archive.encrypt(source, owner,
            "Synthetic separate backup password".toCharArray())
        assertFalse(archiveA.contentEquals(archiveB))
        assertEquals(first, NativeV9RecoveryProof.authenticate(
            NativeV9Archive.decrypt(archiveA, owner,
                "Synthetic separate backup password".toCharArray()), input, consent))
    }

    @Test fun wrongPanDobAccountPasswordAndEachRecoveryAnswerRefused() {
        val source = snapshot()
        val bad = listOf(
            input.copy(pan = "ABCDE1234Z"),
            input.copy(password = "Wrong synthetic login password"),
            input.copy(dateOfBirth = "1984-01-01"),
            input.copy(q1Answer = "wrong city"),
            input.copy(q2Answer = "wrong pet"),
            input.copy(email = "intruder@example.invalid"))
        for (credentials in bad) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9RecoveryProof.authenticate(source, credentials, consent)
            }
        }
    }

    @Test fun removedOrOutdatedTermsAndMissingAccountFailClosed() {
        val source = snapshot()
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9RecoveryProof.authenticate(source, input,
                consent.copy(version = "old"))
        }
        val noAccount = source.copy(tables = source.tables.map {
            if (it.name == "vault_account") it.copy(rows = emptyList()) else it
        })
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9RecoveryProof.authenticate(noAccount, input, consent)
        }
        val missingTerms = source.copy(tables = source.tables.map { t ->
            if (t.name == "setting") t.copy(rows = emptyList()) else t
        })
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9RecoveryProof.authenticate(missingTerms, input, consent)
        }
    }
}
