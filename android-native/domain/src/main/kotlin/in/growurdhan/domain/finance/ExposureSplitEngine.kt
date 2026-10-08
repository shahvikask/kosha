package \`in\`.growurdhan.domain.finance

import \`in\`.growurdhan.core.model.MoneyScaled
import java.math.BigInteger

/** TS lookthrough.ts. A hybrid holding is apportioned, never double-counted. */
enum class ExposureNode { EQUITY, DEBT, GOLD, PROPERTY, CASH, PRIVATE, PROTECTION, LIABILITY }
data class ExposureSlice(val exposure: ExposureNode, val basisPoints: Int) {
    init { require(basisPoints in 0..10_000) }
}
data class ExposureSplit(
    val slices: List<ExposureSlice>,
    val estimated: Boolean,
    val source: String,
) {
    init {
        require(slices.isNotEmpty() && slices.size <= 8)
        require(slices.sumOf { it.basisPoints } == 10_000) { "Exposure split must total 100%." }
        require(slices.map { it.exposure }.distinct().size == slices.size)
    }
}

object ExposureSplitEngine {
    /** Defaults are estimates, not disclosures of actual fund constituent assets. */
    fun resolve(
        name: String, subCategory: String, defaultExposure: ExposureNode,
        disclosedSplit: List<ExposureSlice>? = null,
    ): ExposureSplit {
        if (!disclosedSplit.isNullOrEmpty())
            return ExposureSplit(disclosedSplit.toList(), false, "User-specified split")
        val text = "$name $subCategory".lowercase()
        val patterns: List<Triple<List<String>, String, List<ExposureSlice>>> = listOf(
            Triple(listOf("aggressive hybrid"), "Aggressive hybrid: estimated 65/35",
                listOf(ExposureSlice(ExposureNode.EQUITY, 6500), ExposureSlice(ExposureNode.DEBT, 3500))),
            Triple(listOf("conservative hybrid"), "Conservative hybrid: estimated 75% debt",
                listOf(ExposureSlice(ExposureNode.DEBT, 7500), ExposureSlice(ExposureNode.EQUITY, 2500))),
            Triple(listOf("balanced advantage", "dynamic asset allocation"), "Balanced advantage: assumed 65/35",
                listOf(ExposureSlice(ExposureNode.EQUITY, 6500), ExposureSlice(ExposureNode.DEBT, 3500))),
            Triple(listOf("balanced hybrid"), "Balanced hybrid: assumed 50/50",
                listOf(ExposureSlice(ExposureNode.EQUITY, 5000), ExposureSlice(ExposureNode.DEBT, 5000))),
            Triple(listOf("multi asset", "multi-asset"), "Multi asset: assumed 50/30/20",
                listOf(ExposureSlice(ExposureNode.EQUITY, 5000), ExposureSlice(ExposureNode.DEBT, 3000),
                    ExposureSlice(ExposureNode.GOLD, 2000))),
            Triple(listOf("equity savings"), "Equity savings: assumed 35/65",
                listOf(ExposureSlice(ExposureNode.EQUITY, 3500), ExposureSlice(ExposureNode.DEBT, 6500))),
            Triple(listOf("nps tier", "nps pension"), "NPS: assumed 50/50 E/C",
                listOf(ExposureSlice(ExposureNode.EQUITY, 5000), ExposureSlice(ExposureNode.DEBT, 5000))),
            Triple(listOf("ulip"), "ULIP: assumed 60/40",
                listOf(ExposureSlice(ExposureNode.EQUITY, 6000), ExposureSlice(ExposureNode.DEBT, 4000))),
        )
        val preset = patterns.firstOrNull { pattern -> pattern.first.any { it in text } }
        return if (preset != null) ExposureSplit(preset.third, true, preset.second)
            else ExposureSplit(listOf(ExposureSlice(defaultExposure, 10_000)), false, "Single asset class")
    }

    /** Truncate each scaled share and assign the exact remainder to largest. */
    fun apportion(value: MoneyScaled, split: ExposureSplit): Map<ExposureNode, MoneyScaled> {
        require(value.raw >= 0)
        val allocations = linkedMapOf<ExposureNode, Long>()
        var assigned = 0L
        var largest = split.slices.first()
        for (slice in split.slices) {
            if (slice.basisPoints > largest.basisPoints) largest = slice
            val amount = (
                BigInteger.valueOf(value.raw) * BigInteger.valueOf(slice.basisPoints.toLong()) /
                    BigInteger.valueOf(10_000L)
                ).longValueExact()
            allocations[slice.exposure] = amount
            assigned = Math.addExact(assigned, amount)
        }
        allocations[largest.exposure] = Math.addExact(
            allocations.getValue(largest.exposure), Math.subtractExact(value.raw, assigned),
        )
        check(allocations.values.fold(0L) { sum, part -> Math.addExact(sum, part) } == value.raw)
        return allocations.mapValues { MoneyScaled(it.value) }
    }
}
