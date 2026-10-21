package `in`.growurdhan.database

/**
 * Fail-closed P03/P04/P28 coverage preflight. Legacy GUDVAULT-v2 does not
 * represent all native v9 tables, so a projection must never silently drop
 * financial, security, or opaque historic records.
 *
 * Necessary but NOT sufficient for export: separately verify every typed cell
 * against authenticated source, password/session, and cross-phone restore.
 * No bytes are serialized or published by this object.
 */
object NativeV9ExportCoverage {
    data class Table(val name: String, val columns: List<String>, val rows: Long)
    data class Receipt(val mappedRows: Long, val unsupportedTablesEmpty: Int)

    private val projectable = setOf("vault_account", "entity", "account",
        "instrument", "holding", "ledger_event", "goal", "setting")
    private val entityExtras = setOf("cas_id", "pran", "gmail_sync",
        "cas_id_source", "pran_source", "cas_sync_after", "pran_sync_after")
    private val originalKey = "m24.authenticated_frozen_original_json"
    private val consent = setOf("m25.terms.version", "m25.terms.text_id",
        "m25.terms.accepted_at")
    private const val COST = "m24.frozen_average_cost_per_unit_minor."
    private val identifier = Regex("[a-z][a-z0-9_]*")

    fun requireLosslessCandidate(inventory: List<Table>,
        settingKeys: List<String>, metadataKeys: List<String>): Receipt {
        val expected = SchemaV9Catalog.tables.associateBy { it.name }
        require(inventory.size == expected.size &&
            inventory.map { it.name }.toSet() == expected.keys &&
            inventory.map { it.name }.distinct().size == inventory.size) {
            "Missing or unrecognized v9 table prevents lossless export"
        }
        for (table in inventory) {
            val cols = expected.getValue(table.name).columns.toSet() +
                (if (table.name == "entity") entityExtras else emptySet())
            require(table.rows >= 0 && table.columns.size == cols.size &&
                table.columns.all(identifier::matches) && table.columns.toSet() == cols) {
                "Unknown or missing financial schema column"
            }
            if (table.name !in projectable && table.name != "vault_meta") {
                require(table.rows == 0L) {
                    "Native records not expressible in historical backup"
                }
            }
        }
        require(inventory.single { it.name == "vault_meta" }.rows == 1L &&
            metadataKeys == listOf("schema_version")) {
            "Unrepresented vault metadata prevents export"
        }
        val settingsCount = inventory.single { it.name == "setting" }.rows
        require(settingsCount == settingKeys.size.toLong() &&
            settingKeys.size <= 250_000 &&
            settingKeys.toSet().size == settingKeys.size &&
            originalKey in settingKeys &&
            settingKeys.all { key ->
                key == originalKey || key in consent ||
                    (key.startsWith(COST) && key.length > COST.length &&
                        key.length <= 256)
            }) {
            "Unrepresented native setting prevents lossless export"
        }
        require(inventory.single { it.name == "vault_account" }.rows == 1L &&
            inventory.single { it.name == "entity" }.rows in 1L..250_000L) {
            "Missing or ambiguous original owner"
        }
        val count = inventory.filter { it.name in projectable }
            .fold(0L) { sum, item -> Math.addExact(sum, item.rows) }
        require(count <= 1_500_001L) {
            "Native export exceeds bounded historical restore plan"
        }
        return Receipt(count,
            inventory.count { it.name !in projectable && it.name != "vault_meta" })
    }
}
