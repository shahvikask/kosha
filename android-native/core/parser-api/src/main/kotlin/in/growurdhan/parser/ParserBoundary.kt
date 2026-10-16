package `in`.growurdhan.parser

/**
 * M00 module boundary. The frozen CAS parser stays outside this module.
 * Chaquopy is not linked here, and this module does not call it.
 */
object ParserBoundary {
    const val CHAQUOPY_LINKED = false
    const val CALLS_FROZEN_PARSER = false
}
