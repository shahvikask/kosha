package `in`.growurdhan.model

import kotlin.math.pow

object Contrast {
    fun ratio(foreground: String, background: String): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(hex: String): Double {
        val clean = hex.removePrefix("#")
        require(clean.length == 6) { "expected RRGGBB, got $hex" }
        fun channel(start: Int): Double {
            val value = clean.substring(start, start + 2).toInt(16) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }
}
