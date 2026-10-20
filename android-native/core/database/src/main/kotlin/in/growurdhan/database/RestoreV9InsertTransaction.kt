package `in`.growurdhan.database

import java.security.MessageDigest

/** Single-transaction, INSERT-only application of a prevalidated canonical v9 plan.
 * A production adapter MUST supply a fresh SQLCipher target and its lifecycle lock.
 * This component never opens or replaces an existing vault and never drops rows.
 */
object RestoreV9InsertTransaction {
    interface EmptyEncryptedTarget {
        /** Verify SQLCipher, ownership of an exclusive NEW target and absence of all user rows. */
        fun verifyNewEmptyCipherTarget()
        fun beginTransaction()
        /** Bind every value as parameter, especially Long amounts, never SQL text interpolation. */
        fun insert(table: String, columns: List<String>, parameters: List<Any?>)
        /** Verify FK constraints, append-only triggers and row counts before commit. */
        fun verifyTransactionIntegrity(expectedRows: Int)
        fun setTransactionSuccessful()
        /** Must commit only if marked successful; otherwise rollback. */
        fun endTransaction()
    }
    private val tables = setOf("vault_account", "entity", "account", "instrument",
        "holding", "ledger_event", "goal", "setting")
    private val column = Regex("[a-z][a-z0-9_]*")

    fun apply(plan: RestoreV9Plan.Plan, target: EmptyEncryptedTarget) {
        require(plan.rows.isNotEmpty() && plan.rows.size <= 1_500_001) {
            "Empty or oversized restore plan" }
        require(plan.originalJsonSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Missing authenticated source fingerprint" }
        val consentKeys = setOf("m25.terms.version", "m25.terms.text_id",
            "m25.terms.accepted_at")
        val providedConsent = plan.rows.filter { it.table == "setting" &&
            it.values.firstOrNull() in consentKeys }
        require(providedConsent.isEmpty() ||
            (providedConsent.size == 3 &&
                providedConsent.map { it.values.firstOrNull() }.toSet() == consentKeys &&
                providedConsent.all {
                    it.columns == listOf("key", "value") &&
                        it.values.size == 2 && it.values[1] is String
                })) {
            "Incomplete or duplicated restore Terms consent"
        }
        if (providedConsent.isNotEmpty()) {
            val evidence = providedConsent.associate {
                it.values[0] as String to (it.values[1] as String)
            }
            // Independently defend the storage boundary against fabricated
            // or stale terms rows even if a caller bypasses the UI publisher.
            require(evidence["m25.terms.version"] == "2026-10-06" &&
                evidence["m25.terms.text_id"] == "GUD-TOS-2026-10-06-v4" &&
                runCatching {
                    java.time.Instant.parse(evidence.getValue("m25.terms.accepted_at"))
                }.isSuccess) {
                "Non-current or malformed restore Terms consent"
            }
        }
        require(plan.rows.first().table == "vault_account" &&
            plan.rows.count { it.table == "vault_account" } == 1 &&
            plan.rows.any { it.table == "entity" } &&
            plan.rows.count { it.table == "setting" &&
                it.values.firstOrNull() == "m24.authenticated_frozen_original_json" } == 1 &&
            plan.rows.filter { it.table == "setting" }.all {
                it.values.firstOrNull() == "m24.authenticated_frozen_original_json" ||
                it.values.firstOrNull() in consentKeys ||
                (it.values.firstOrNull() as? String)?.startsWith(
                    "m24.frozen_average_cost_per_unit_minor.") == true
            }) {
            "Missing mandatory restored owner/login or duplicated raw source"
        }
        val original = plan.rows.last()
        require(original.table == "setting" &&
            original.columns == listOf("key", "value") &&
            original.values.firstOrNull() == "m24.authenticated_frozen_original_json") {
            "Unchanged encrypted source JSON must be retained"
        }
        val bytes = (original.values.getOrNull(1) as? String)
            ?.toByteArray(Charsets.UTF_8) ?: error("Missing encrypted-source record")
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(digest == plan.originalJsonSha256) {
            "Original authenticated source fingerprint differs from retained bytes"
        }
        for (row in plan.rows) {
            require(row.table in tables && row.columns.all(column::matches) &&
                row.columns.size == row.columns.toSet().size &&
                row.values.all { it == null || it is String || it is Long }) {
                "Unsafe restore SQL identifiers or parameter value types" }
        }
        // The production adapter MUST hold the exclusive lifecycle lock across
        // these calls. It must not point this interface at a populated vault.
        target.verifyNewEmptyCipherTarget()
        target.beginTransaction()
        try {
            for (row in plan.rows) target.insert(row.table, row.columns, row.values)
            target.verifyTransactionIntegrity(plan.rows.size)
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
        }
    }
}
