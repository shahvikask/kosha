package `in`.growurdhan.app.cas

import `in`.growurdhan.domain.finance.CasImportContract
import `in`.growurdhan.domain.finance.CasValidationException
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

/** JVM hostility tests for the PDF content-provider boundary, no real PDFs. */
class CasPdfBytesTest {
    private val header = "%PDF-1.7\n".toByteArray(Charsets.US_ASCII)

    @Test fun validHeaderIsAcceptedWithoutPersistingData() {
        val pdf = header + ByteArray(128) { 65 }
        assertArrayEquals(pdf, CasPdfBytes.read(ByteArrayInputStream(pdf)))
    }

    @Test fun htmlOrZipPayloadCannotBeAcceptedAsPdf() {
        for (candidate in listOf(
            "<html>not pdf</html>".toByteArray(),
            byteArrayOf(80, 75, 3, 4, 0, 0, 0, 0),
        )) {
            assertThrows(CasValidationException::class.java) {
                CasPdfBytes.read(ByteArrayInputStream(candidate))
            }
        }
    }

    @Test fun emptyOrTruncatedDocumentsFailClosed() {
        assertThrows(CasValidationException::class.java) {
            CasPdfBytes.read(ByteArrayInputStream(ByteArray(0)))
        }
        assertThrows(CasValidationException::class.java) {
            CasPdfBytes.read(ByteArrayInputStream("%PDF-".toByteArray()))
        }
    }

    @Test fun exactThirtyMiBBoundaryIsAcceptedAndOneMoreByteIsRejected() {
        val exact = ByteArray(CasImportContract.MAX_PDF_BYTES)
        header.copyInto(exact)
        assertEquals(exact.size, CasPdfBytes.read(ByteArrayInputStream(exact)).size)
        assertThrows(CasValidationException::class.java) {
            CasPdfBytes.read(ByteArrayInputStream(exact + byteArrayOf(1)))
        }
    }

    @Test fun maliciousContentProviderCannotLoopIndefinitelyWithZeroReads() {
        val stream = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertThrows(CasValidationException::class.java) {
            CasPdfBytes.read(stream)
        }
    }
}
