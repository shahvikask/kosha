package `in`.growurdhan.database

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** This verifies the INSERT-ONLY boundary, independently of the UI publisher. */
class RestoreTermsInsertionContractTest {
    private class TrackingTarget : RestoreV9InsertTransaction.EmptyEncryptedTarget {
        var touched = false
        var rows = 0
        var successful = false
        override fun verifyNewEmptyCipherTarget() { touched = true }
        override fun beginTransaction() { touched = true }
        override fun insert(table: String, columns: List<String>, parameters: List<Any?>) {
            touched = true; rows++
        }
        override fun verifyTransactionIntegrity(expectedRows: Int) {
            assertEquals(expectedRows, rows)
        }
        override fun setTransactionSuccessful() { successful = true }
        override fun endTransaction() { }
    }

    private val raw = """{"synthetic":"restore-proof"}"""
    private fun original() = RestoreV9Plan.Row("setting", listOf("key", "value"),
        listOf("m24.authenticated_frozen_original_json", raw))
    private fun evidence(key: String, value: String) = RestoreV9Plan.Row(
        "setting", listOf("key", "value"), listOf(key, value))
    private fun plan(consent: List<RestoreV9Plan.Row>): RestoreV9Plan.Plan {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return RestoreV9Plan.Plan(
            listOf(
                RestoreV9Plan.Row("vault_account", listOf("id"), listOf("test-account")),
                RestoreV9Plan.Row("entity", listOf("entity_id"), listOf("test-owner"))
            ) + consent + original(), fingerprint)
    }
    private fun valid() = listOf(
        evidence("m25.terms.version", "2026-10-06"),
        evidence("m25.terms.text_id", "GUD-TOS-2026-10-06-v4"),
        evidence("m25.terms.accepted_at", "2026-10-19T10:00:00Z")
    )
    @Test fun currentVersionAcceptedOnlyWithExactThreeKeysAndAuthenticatedOriginal() {
        val target = TrackingTarget()
        RestoreV9InsertTransaction.apply(plan(valid()), target)
        assertTrue(target.touched)
        assertTrue(target.successful)
        assertEquals(6, target.rows)
    }
    @Test fun invalidOrStaleConsentCannotTouchEncryptedDestination() {
        val invalid = listOf(
            valid().dropLast(1),
            valid() + valid().first(),
            valid().map {
                if (it.values.first() == "m25.terms.version")
                    evidence("m25.terms.version", "2026-01-01") else it
            },
            valid().map {
                if (it.values.first() == "m25.terms.text_id")
                    evidence("m25.terms.text_id", "unapproved-text") else it
            },
            valid().map {
                if (it.values.first() == "m25.terms.accepted_at")
                    evidence("m25.terms.accepted_at", "not-an-instant") else it
            }
        )
        invalid.forEach { rows ->
            val target = TrackingTarget()
            assertThrows(IllegalArgumentException::class.java) {
                RestoreV9InsertTransaction.apply(plan(rows), target)
            }
            assertFalse(target.touched)
            assertFalse(target.successful)
        }
    }
}
