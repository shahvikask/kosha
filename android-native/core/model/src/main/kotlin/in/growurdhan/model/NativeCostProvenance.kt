package `in`.growurdhan.model

/**
 * A registrar position/market quote is NOT an acquisition cost.
 * Unmarked historical SNAPSHOT_ADJUSTMENT rows must remain unknown even
 * when a legacy statement stores a nonzero quote in its unit-price column.
 * Only the separately reviewed, explicit KNOWN marker authorizes a basis.
 * Used identically by Holdings and owner-reviewed statement reconciliation.
 */
object NativeCostProvenance {
    fun knownCost(kind: String, unitPriceMinor: Long, amountMinor: Long,
        notes: String, recorded: String?): Boolean = when (recorded) {
        "KNOWN" -> true
        "UNKNOWN" -> false
        null -> when (kind) {
            "SNAPSHOT_ADJUSTMENT" -> false
            "OPENING_BALANCE" -> !(unitPriceMinor == 0L && amountMinor == 0L ||
                notes.contains("unknown", ignoreCase = true))
            else -> true
        }
        else -> throw IllegalArgumentException("Unrecognized acquisition cost provenance")
    }
}
