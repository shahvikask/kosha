package `in`.growurdhan.model

import java.math.BigInteger

/**
 * Exact source-lookthrough arithmetic (src/lib/lookthrough.ts::apportion).
 * Pure preview only: neither a price source, an acquisition, nor a persisted
 * allocation. The caller supplies reviewed or explicitly labelled ESTIMATED
 * basis-point splits and known dated values; missing values stay excluded.
 */
object NativeExposureAllocation {
    private val order = listOf(
        "EQUITY", "DEBT", "GOLD", "PROPERTY",
        "CASH", "PRIVATE", "PROTECTION", "LIABILITY")
    private val allowed = order.toSet()
    private val scale = BigInteger.valueOf(10_000L)

    data class Slice(val exposure: String, val basisPoints: Int)
    data class Position(
        val holdingId: String,
        val ownerId: String,
        val valueMinor: BigInteger?,
        val slices: List<Slice>,
        val estimated: Boolean,
        val source: String
    )
    data class Summary(
        val totals: Map<String, BigInteger>,
        val knownValueMinor: BigInteger,
        val estimatedValueMinor: BigInteger,
        val excludedUnpriced: Int,
        val listedHoldingIds: List<String>
    )

    fun apportion(valueMinor: BigInteger, slices: List<Slice>): Map<String, BigInteger> {
        require(valueMinor.signum() >= 0) { "Negative asset allocation" }
        require(slices.isNotEmpty() && slices.size <= allowed.size &&
            slices.all { it.exposure in allowed && it.basisPoints > 0 } &&
            slices.map { it.exposure }.toSet().size == slices.size &&
            slices.sumOf { it.basisPoints.toLong() } == 10_000L) {
            "Explicit source split must total exactly 10000 basis points"
        }
        val out = linkedMapOf<String, BigInteger>()
        var allocated = BigInteger.ZERO
        val largest = slices.maxBy { it.basisPoints }.exposure
        for (slice in slices) {
            val part = valueMinor.multiply(BigInteger.valueOf(slice.basisPoints.toLong()))
                .divide(scale)
            out[slice.exposure] = part
            allocated += part
        }
        out[largest] = out.getValue(largest) + (valueMinor - allocated)
        return out
    }

    fun summarize(positions: List<Position>, ownerId: String): Summary {
        val scoped = positions.filter { ownerId.isEmpty() || it.ownerId == ownerId }
        require(scoped.all { it.holdingId.isNotBlank() && it.ownerId.isNotBlank() } &&
            scoped.map { it.holdingId }.toSet().size == scoped.size) {
            "Missing or duplicate holding identity"
        }
        val totals = linkedMapOf<String, BigInteger>()
        var known = BigInteger.ZERO
        var estimated = BigInteger.ZERO
        var unpriced = 0
        for (row in scoped) {
            val value = row.valueMinor
            if (value == null) {
                unpriced++
                continue
            }
            require(row.source.isNotBlank()) { "No reviewed source or estimate label" }
            val split = apportion(value, row.slices)
            known += value
            if (row.estimated) estimated += value
            for ((exposure, part) in split) {
                totals[exposure] = (totals[exposure] ?: BigInteger.ZERO) + part
            }
        }
        require(totals.values.fold(BigInteger.ZERO, BigInteger::add) == known) {
            "Exposure must equal known asset value without duplication"
        }
        return Summary(order.filter { it in totals }.associateWith { totals.getValue(it) },
            known, estimated, unpriced, scoped.map { it.holdingId })
    }
}
