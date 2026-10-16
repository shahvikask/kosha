package `in`.growurdhan.model

/**
 * Signed scaled integers. INR money uses 10,000 minor units per rupee.
 * Quantities use 100,000,000 minor units per whole unit.
 * The stored minor count is the value. Display rounding must not replace it.
 */
data class ScaledAmount internal constructor(val minor: Long) {
    operator fun plus(other: ScaledAmount): ScaledAmount =
        ScaledAmount(Math.addExact(minor, other.minor))

    operator fun minus(other: ScaledAmount): ScaledAmount =
        ScaledAmount(Math.subtractExact(minor, other.minor))

    companion object {
        const val MONEY_SCALE: Long = 10_000L
        const val QUANTITY_SCALE: Long = 100_000_000L

        fun moneyMinor(minor: Long): ScaledAmount = ScaledAmount(minor)

        fun quantityMinor(minor: Long): ScaledAmount = ScaledAmount(minor)
    }
}
