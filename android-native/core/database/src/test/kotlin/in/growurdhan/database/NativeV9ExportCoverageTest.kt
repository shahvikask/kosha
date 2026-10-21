package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

/** Synthetic schema/count fixtures only; never user finances. */
class NativeV9ExportCoverageTest {
    private fun inventory() = SchemaV9Catalog.tables.map { t ->
        NativeV9ExportCoverage.Table(t.name,
            t.columns + (if (t.name == "entity")
                listOf("cas_id", "pran", "gmail_sync", "cas_id_source",
                    "pran_source", "cas_sync_after", "pran_sync_after")
                else emptyList()),
            when (t.name) {
                "vault_meta", "entity", "vault_account" -> 1L
                "setting" -> 4L
                else -> 0L
            })
    }
    private val settings = listOf("m24.authenticated_frozen_original_json",
        "m25.terms.version", "m25.terms.text_id", "m25.terms.accepted_at")

    private fun mutate(name: String,
        change: (NativeV9ExportCoverage.Table) -> NativeV9ExportCoverage.Table) =
        inventory().map { if (it.name == name) change(it) else it }

    @Test fun exactMinimalHistoricalInventoryIsOnlyAnAuditCandidate() {
        val receipt = NativeV9ExportCoverage.requireLosslessCandidate(
            inventory(), settings, listOf("schema_version"))
        assertEquals(6L, receipt.mappedRows)
        assertEquals(9, receipt.unsupportedTablesEmpty)
    }

    @Test fun everyUnrepresentedFinancialAndAuditTableMustBeEmpty() {
        val names = listOf("price_observation", "import_batch", "source_document",
            "statement_position", "tax_rule_set", "liability", "egress_log",
            "listed_security", "gmail_seen")
        for (name in names) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9ExportCoverage.requireLosslessCandidate(
                    mutate(name) { it.copy(rows = 1L) }, settings,
                    listOf("schema_version"))
            }
        }
    }

    @Test fun unknownOrMissingSchemaExtensionsAreNeverSilentlyDropped() {
        val invalid = listOf(
            mutate("holding") { it.copy(columns = it.columns + "opaque_rate") },
            mutate("ledger_event") { it.copy(columns = it.columns - "charges_minor") },
            mutate("entity") { it.copy(columns = it.columns - "cas_id") },
            mutate("setting") { it.copy(columns = it.columns + "historical_real") },
            inventory() + NativeV9ExportCoverage.Table("other", listOf("id"), 0L),
            inventory().dropLast(1),
            mutate("holding") { it.copy(rows = -1L) })
        for (rows in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9ExportCoverage.requireLosslessCandidate(
                    rows, settings, listOf("schema_version"))
            }
        }
    }

    @Test fun unrepresentedLocalSettingsOrExtraMetadataAreRejected() {
        for (keys in listOf(
            settings + "m25.auth.failure_state",
            settings + "m29.auto_backup_consent",
            settings + "unmapped_cost_minor",
            settings.drop(1),
            settings + settings.first())) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9ExportCoverage.requireLosslessCandidate(
                    mutate("setting") { it.copy(rows = keys.size.toLong()) },
                    keys, listOf("schema_version"))
            }
        }
        for (keys in listOf(listOf("schema_version", "custom_meta"),
                emptyList(), listOf("schema_version", "schema_version"))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9ExportCoverage.requireLosslessCandidate(
                    mutate("vault_meta") { it.copy(rows = keys.size.toLong()) },
                    settings, keys)
            }
        }
    }

    @Test fun missingOrOversizeHouseholdsCannotBeExported() {
        for (rows in listOf(
            mutate("entity") { it.copy(rows = 0L) },
            mutate("vault_account") { it.copy(rows = 2L) },
            mutate("holding") { it.copy(rows = 1_500_000L) })) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9ExportCoverage.requireLosslessCandidate(
                    rows, settings, listOf("schema_version"))
            }
        }
    }
}
