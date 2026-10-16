package `in`.growurdhan.model

/**
 * Original brand values from Section 6A.1. These stay exact.
 * Semantic roles used for text may differ when an original candidate fails contrast.
 */
object BrandSourceTokens {
    const val MARK_NAVY = "#07131F"
    const val MARK_GOLD = "#F5C14A"
    const val MARK_TEAL = "#2DD4BF"
    const val DARK_BACKGROUND = "#07131F"
    const val DARK_CHROME = "#0C1C2C"
    const val DARK_CARD = "#102536"
    const val DARK_BORDER = "#1E3A4F"
    const val DARK_TEXT = "#F4F7FB"
    const val DARK_MUTED = "#9BB3C7"
    const val DARK_POSITIVE = "#34D399"
    const val DARK_NEGATIVE = "#FB7185"
    const val DARK_WARNING = "#FBBF24"
    const val LIGHT_BACKGROUND = "#F6EFE3"
    const val LIGHT_CHROME = "#FFFAF3"
    const val LIGHT_SURFACE = "#FFFFFF"
    const val LIGHT_BORDER = "#EADCC8"
    const val LIGHT_TEXT = "#1C1917"
    const val LIGHT_MUTED = "#6B5E52"
    const val LIGHT_AMBER_CANDIDATE = "#D97706"
    const val LIGHT_TEAL = "#0F766E"
    const val TAGLINE = "Private Indian Family Wealth & Records"
    const val MARK_SVG_SHA256 = "d612b12009911a06796e2fb2c75fa92247db87151a6344ae850acf04af823efc"
    const val MARK_GIT_BLOB = "52fd0aa1a9262e6d2ba8ab431c2955c6df5c417c"
}

/**
 * Text and control roles that meet the Section 6A contrast objective.
 * #D97706 on ivory or white is below 4.5:1, so it is not a text or button color.
 * Light primary controls use #9A3412 with white text. The logo gold stays #F5C14A.
 */
object SemanticTokens {
    const val LIGHT_PRIMARY_CONTAINER = "#9A3412"
    const val LIGHT_ON_PRIMARY = "#FFFFFF"
    const val DARK_PRIMARY = BrandSourceTokens.MARK_GOLD
    const val DARK_ON_PRIMARY = BrandSourceTokens.MARK_NAVY
}
