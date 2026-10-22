package `in`.growurdhan.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParserBoundaryTest {
    @Test fun rejectsMalformedPdfBeforeCallback() {
        var invoked = 0
        val invalid = listOf(
            "not-pdf".toByteArray(), byteArrayOf(), byteArrayOf(0, 1, 2)
        )
        for (bytes in invalid) {
            try {
                ParserBoundary.withAdmittedPdf("source.pdf", bytes) { invoked++ }
                org.junit.Assert.fail("Malformed CAS entered callback")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, invoked)
            }
        }
    }

    @Test fun validPdfInvokesCallbackOnce() {
        var invoked = 0
        val answer = ParserBoundary.withAdmittedPdf("source.PDF",
            "%PDF-1.7".toByteArray()) { invoked++; "parsed" }
        assertEquals("parsed", answer)
        assertEquals(1, invoked)
    }

    @Test fun moduleDoesNotLinkChaquopyOrCallTheParser() {
        assertFalse(ParserBoundary.CHAQUOPY_LINKED)
        assertFalse(ParserBoundary.CALLS_FROZEN_PARSER)
    }

    @Test fun admitsOnlyPdfHeaderForPdfExtension() {
        assertEquals(ParserBoundary.Admission.Allowed(ParserBoundary.Format.PDF),
            ParserBoundary.admit("cas.PDF", "%PDF-1.7".toByteArray()))
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.BAD_SIGNATURE),
            ParserBoundary.admit("cas.pdf", "not a pdf".toByteArray()))
    }

    @Test fun rejectsNonTextCsvWithoutModifyingOriginal() {
        val raw = byteArrayOf(65, 44, -1)
        val before = raw.clone()
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.BINARY_CSV),
            ParserBoundary.admit("cas.csv", raw))
        assertEquals(before.toList(), raw.toList())
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.BINARY_CSV),
            ParserBoundary.admit("cas.csv", byteArrayOf(65, 0, 66)))
    }

    @Test fun acceptsUtf8CsvButNeverParsesItsFinancialValues() {
        assertEquals(ParserBoundary.Admission.Allowed(ParserBoundary.Format.CSV),
            ParserBoundary.admit("statement.CsV", "folio,amount\n123,₹50".toByteArray(Charsets.UTF_8)))
    }

    @Test fun failsClosedOnEmptyOversizeAndUnsupportedInput() {
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.EMPTY),
            ParserBoundary.admit("cas.csv", byteArrayOf()))
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.TOO_LARGE),
            ParserBoundary.admit("cas.csv", ByteArray(ParserBoundary.MAX_INPUT_BYTES + 1)))
        assertEquals(ParserBoundary.Admission.Rejected(ParserBoundary.Refusal.UNSUPPORTED_FORMAT),
            ParserBoundary.admit("cas.json", "{}".toByteArray()))
    }
}
