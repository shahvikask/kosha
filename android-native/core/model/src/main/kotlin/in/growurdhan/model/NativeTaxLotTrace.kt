package `in`.growurdhan.model

import java.time.LocalDate

/**
 * Authenticated tax-workpaper SOURCE FACTS from immutable corrected FIFO.
 * The frozen taxRules.ts warns its statutory rates require Finance Act review.
 * Never synthesize FMV, a tax rate, a person's income slab or an exemption.
 */
object NativeTaxLotTrace {
    data class Parcel(val ownerId: String, val holdingId: String,
        val purchaseEventId: String, val saleEventId: String,
        val acquired: String, val sold: String, val assessmentYear: String,
        val category: String, val quantityScaled: Long, val proceedsMinor: Long,
        val costMinor: Long?, val realizedGainMinor: Long?,
        val ledgerLongTerm: Boolean)
    data class Workpaper(val assessmentYear: String, val items: List<Parcel>,
        val fullyCostedGainMinor: Long?, val missingCost: Boolean,
        val needsTaxRuleReview: Boolean = true)

    /** Indian FY April–March; AY begins in the following April. */
    fun assessmentYear(sold: LocalDate): String {
        val fyStart = if (sold.monthValue >= 4) sold.year else sold.year - 1
        return "AY" + (fyStart + 1).toString().takeLast(2) + "-" +
            (fyStart + 2).toString().takeLast(2)
    }

    /**
     * Frozen taxRules.ts grandfatheredCostMinor source formula, SOURCE FACT ONLY.
     * The caller must supply the reviewed cutoff and actual historical FMV.
     * Never infer FMV, tax rates, exemptions, or alter original ledger cost.
     */
    fun reviewedGrandfatheredCostMinor(acquired: LocalDate,
        actualCostMinor: Long, proceedsMinor: Long,
        reviewedFmvMinor: Long?, reviewedGrandfatherDate: LocalDate?): Long {
        require(actualCostMinor >= 0L && proceedsMinor >= 0L &&
            (reviewedFmvMinor == null || reviewedFmvMinor >= 0L)) {
            "Invalid source cost, proceeds or FMV"
        }
        if (reviewedGrandfatherDate == null ||
            acquired.isAfter(reviewedGrandfatherDate) ||
            reviewedFmvMinor == null || reviewedFmvMinor == 0L) return actualCostMinor
        return maxOf(actualCostMinor, minOf(reviewedFmvMinor, proceedsMinor))
    }

    fun trace(events: List<NativePortfolioMath.Event>, category: String,
        targetYear: String): Workpaper {
        require(targetYear.matches(Regex("AY[0-9]{2}-[0-9]{2}")))
        require(category in NativeAssetTaxonomy.subcategories)
        val folded = NativePortfolioMath.fold(events, category)
        require(folded.errors.isEmpty()) { "Invalid FIFO financial history" }
        val origin = events.firstOrNull()
        val parcels = folded.disposals.filter {
            assessmentYear(LocalDate.parse(it.sold)) == targetYear
        }.map { disposal ->
            require(disposal.quantityScaled > 0L)
            Parcel(requireNotNull(origin).ownerId, origin.holdingId,
                disposal.purchaseId, disposal.saleId, disposal.acquired,
                disposal.sold, targetYear, category, disposal.quantityScaled,
                disposal.proceedsMinor, disposal.costMinor, disposal.gainMinor,
                disposal.longTerm)
        }
        val missing = parcels.any { it.costMinor == null || it.realizedGainMinor == null }
        val gain = if (missing) null else parcels.fold(0L) { sum, entry ->
            Math.addExact(sum, requireNotNull(entry.realizedGainMinor))
        }
        return Workpaper(targetYear, parcels, gain, missing)
    }
}
