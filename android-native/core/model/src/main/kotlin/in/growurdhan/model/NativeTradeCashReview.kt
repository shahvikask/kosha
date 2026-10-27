package `in`.growurdhan.model

import java.math.BigInteger

/**
 * P13/P14 non-posting purchase/sale review matched to frozen src/lib/returns.ts
 * buildCashFlows: recorded gross wins; only when zero does the source model
 * derive a theoretical price-times-units value. Never call that a receipt.
 *
 * This is a review projection, NOT a ledger write or tax advice.
 */
object NativeTradeCashReview {
    data class Preview(
        val computedGrossMinor: Long,
        val recordedGrossMinor: Long?,
        val chargesMinor: Long,
        val modeledNetMinor: Long,
        val recordedSignedCashMinor: Long?,
        val grossDifferenceMinor: Long?
    )

    fun preview(kind: String, quantityScaled: Long, unitPriceMinor: Long,
        recordedTotalMinor: Long, chargesMinor: Long): Preview? {
        if (kind != "BUY" && kind != "SELL") return null
        require(quantityScaled != 0L && quantityScaled != Long.MIN_VALUE)
        require(unitPriceMinor >= 0L && recordedTotalMinor >= 0L && chargesMinor >= 0L)
        val qty = BigInteger.valueOf(quantityScaled).abs()
        val computed = qty.multiply(BigInteger.valueOf(unitPriceMinor))
            .divide(BigInteger.valueOf(NativePortfolioMath.QTY_SCALE)).longValueExact()
        val signed = if (kind == "BUY") -1 else 1
        // Positive recorded gross is the only owner-entered gross amount.
        val entered = recordedTotalMinor.takeIf { it > 0L }
        val modeled = if (signed < 0) Math.negateExact(Math.addExact(computed, chargesMinor))
            else Math.subtractExact(computed, chargesMinor)
        val recorded = entered?.let {
            if (signed < 0) Math.negateExact(Math.addExact(it, chargesMinor))
            else Math.subtractExact(it, chargesMinor)
        }
        return Preview(computed, entered, chargesMinor, modeled, recorded,
            entered?.let { Math.subtractExact(it, computed) })
    }
}
