package `in`.growurdhan.model

/**
 * Indian digit grouping for display only.
 * [formatExact] keeps all four minor digits. [formatOverview] shows two
 * decimal places and does not change the stored minor amount.
 */
object InrFormat {
    fun formatExact(minor: Long): String = format(minor, fractionDigits = 4)

    fun formatOverview(minor: Long): String {
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) throw ArithmeticException("overflow") else kotlin.math.abs(minor)
        val rounded = (abs + 50L) / 100L
        val rupees = rounded / 100L
        val paise = rounded % 100L
        val body = group(rupees) + "." + paise.toString().padStart(2, '0')
        return (if (negative) "\u2212" else "") + "\u20B9" + body
    }

    private fun format(minor: Long, fractionDigits: Int): String {
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) throw ArithmeticException("overflow") else kotlin.math.abs(minor)
        val scale = ScaledAmount.MONEY_SCALE
        val whole = abs / scale
        val frac = abs % scale
        val digits = frac.toString().padStart(4, '0').take(fractionDigits)
        val body = group(whole) + "." + digits
        return (if (negative) "\u2212" else "") + "\u20B9" + body
    }

    private fun group(rupees: Long): String {
        val text = rupees.toString()
        if (text.length <= 3) return text
        val head = text.dropLast(3)
        val tail = text.takeLast(3)
        val grouped = head.reversed().chunked(2).joinToString(",").reversed()
        return "$grouped,$tail"
    }
}
