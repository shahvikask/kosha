package `in`.growurdhan.parser

import org.junit.Assert.assertFalse
import org.junit.Test

class ParserBoundaryTest {
    @Test
    fun moduleDoesNotLinkChaquopyOrCallTheParser() {
        assertFalse(ParserBoundary.CHAQUOPY_LINKED)
        assertFalse(ParserBoundary.CALLS_FROZEN_PARSER)
    }
}
