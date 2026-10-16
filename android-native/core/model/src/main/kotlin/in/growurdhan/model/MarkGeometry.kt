package `in`.growurdhan.model

/**
 * Numbers copied from public/brand/mark.svg. The Compose mark must use these
 * values. A test checks that the SVG file still contains them.
 */
object MarkGeometry {
    const val VIEWBOX = 100f
    const val TILE_X = 1.2f
    const val TILE_Y = 1.2f
    const val TILE_SIZE = 97.6f
    const val TILE_RX = 22f
    const val STROKE = 2.4f
    const val BAR1_X = 27f
    const val BAR1_Y = 31.2f
    const val BAR1_WIDTH = 46f
    const val BAR_HEIGHT = 5.4f
    const val BAR_RX = 1.15f
    const val BAR2_X = 27f
    const val BAR2_Y = 40.6f
    const val BAR2_WIDTH = 36f
    const val STEM_LEFT = 36.6f
    const val STEM_TOP = 23.2f
    const val STEM_RIGHT = 43.2f
    const val STEM_BOTTOM = 56.5f
    const val LEG_TOP = 54f
    const val LEG_RIGHT_X = 62.4f
    const val LEG_BOTTOM = 74.6f
    const val LEG_LEFT_X = 55.8f
    const val TIP_X = 50.06f
    const val TIP_Y = 67.42f
    val svgSnippets = listOf(
        "viewBox=\"0 0 100 100\"",
        "rx=\"22\"",
        "fill=\"#07131f\"",
        "stroke=\"#f5c14a\"",
        "stroke-width=\"2.4\"",
        "fill=\"#2dd4bf\"",
        "M36.6 23.2h6.6V56.5h-6.6z",
        "M50.06 67.42L55.8 74.6h6.6l-5.74-7.18z",
    )
}
