package `in`.growurdhan.model

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Real-estate owner-share disclosure using an EXPLICIT recorded percent only.
 * The source HoldingDetails.ownershipPercent is metadata, not proof of beneficial
 * title, a new owner ledger, a cash receipt, a tax allocation or authority to
 * reduce another family member's position.
 */
object NativeOwnershipEconomicShare {
    data class Share(val fractionPercent: String, val grossLastPriceMinor: Long,
        val indicativeOwnerValueMinor: Long)

    fun preview(category: String, ownership: String?, percent: String?,
        quantityScaled: Long, lastUnitPriceMinor: Long): Share? {
        if (category != "REAL_ESTATE" || percent.isNullOrBlank()) return null
        require(ownership in setOf("SELF", "JOINT", "HUF", "SPOUSE")) {
            "Unverified ownership classification"
        }
        val share = BigDecimal(percent)
        require(share >= BigDecimal.ZERO && share <= BigDecimal("100")) {
            "Ownership percent outside 0..100"
        }
        require(quantityScaled >= 0L && lastUnitPriceMinor >= 0L)
        if (quantityScaled == 0L || lastUnitPriceMinor == 0L) return null
        val gross = BigInteger.valueOf(quantityScaled)
            .multiply(BigInteger.valueOf(lastUnitPriceMinor))
            .divide(BigInteger.valueOf(NativePortfolioMath.QTY_SCALE)).longValueExact()
        val allocation = BigDecimal.valueOf(gross)
            .multiply(share).divideToIntegralValue(BigDecimal("100")).longValueExact()
        return Share(share.stripTrailingZeros().toPlainString(), gross, allocation)
    }
}
