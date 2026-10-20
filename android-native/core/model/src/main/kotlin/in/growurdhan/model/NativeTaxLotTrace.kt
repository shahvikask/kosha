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
