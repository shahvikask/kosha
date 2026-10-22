package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Source-boundary tests; no genuine statement, PAN, password, or PDF fixture is stored. */
@RunWith(AndroidJUnit4::class)
class NativeCasPdfEngineInstrumentedTest {
    private val engine get() = NativeCasPdfEngine(
        InstrumentationRegistry.getInstrumentation().targetContext)


    /**
     * Run as the sole instrumentation method in a clean app process. The
     * synthetic PDF header deliberately passes the older native preconditions
     * (length, magic and PAN), but its non-PDF extension must be refused by
     * ParserBoundary *before* Python.start or the frozen parser callback.
     */
    @Test fun malformedCasContainerNeverStartsChaquopyOnFreshProcess() {
        assertFalse("Test must begin in a clean process, not skip the proof",
            Python.isStarted())
        val headerOnly = ("%PDF-" + "synthetic-header-not-a-real-statement".repeat(2))
            .toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(headerOnly, "synthetic-password", "synthetic.txt", "ABCDE1234F")
        }
        assertFalse("Rejected CAS container must not initialize Chaquopy",
            Python.isStarted())
    }

    @Test fun arbitraryBytesNeverReachPythonPdfParser() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse("not-a-pdf".toByteArray(), "", "bad.pdf", "ABCDE1234F")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(ByteArray(32), "", "bad.pdf", "ABCDE1234F")
        }
    }

    @Test fun tooLargeOrMissingPdfNameIsRejectedBeforePasswordHandling() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(ByteArray(16 * 1024 * 1024 + 1), "", "large.pdf", "ABCDE1234F")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(("%PDF-" + "x".repeat(40)).toByteArray(), "", "", "ABCDE1234F")
        }
    }
    @Test fun explicitFullPanIsRequiredBeforePythonIsInvoked() {
        val fakePdf = ("%PDF-" + "synthetic-not-a-real-cas".repeat(2)).toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(fakePdf, "different-password", "synthetic.pdf", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.parse(fakePdf, "different-password", "synthetic.pdf", "INVALIDPAN")
        }
    }


    /**
     * Small generated, financial-data-free PDF. It must reach the real frozen
     * Python bridge, but can NEVER be treated as a genuine CAS statement.
     * No PDF bytes, parser response, PAN, or password are logged.
     */
    private fun syntheticOnePagePdf(): ByteArray {
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length 43 >>\nstream\nBT /F1 12 Tf 60 700 Td (TEST ONLY) Tj ET\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        )
        for ((i, body) in objects.withIndex()) {
            offsets += out.length
            out.append("${i + 1} 0 obj\n").append(body).append("\nendobj\n")
        }
        val xref = out.length
        out.append("xref\n0 6\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", it)) }
        out.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF\n")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    @Test fun realFrozenParserLoadsAndNonfinancialPdfCannotYieldImportableSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = syntheticOnePagePdf()
        assertTrue(pdf.size >= 32)
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val py = Python.getInstance().getModule("cas_android")
        val health = JSONObject(py.callAttr("health").toString())
        assertTrue("Frozen CAS and PDFium must actually load", health.getBoolean("ok"))
        val response = JSONObject(py.callAttr(
            "parse_b64",
            android.util.Base64.encodeToString(pdf, android.util.Base64.NO_WRAP),
            "", "synthetic-nonfinancial.pdf").toString())
        assertFalse("Nonfinancial PDF must not become an accepted CAS report",
            response.optBoolean("ok", false))
        assertThrows(Exception::class.java) {
            NativeCasOutputReviewGate.verify(response, "ABCDE1234F")
        }
        assertThrows(Exception::class.java) {
            engine.parse(pdf, "", "synthetic-nonfinancial.pdf", "ABCDE1234F")
        }
    }

    @Test fun packagedFrozenCasparserAndPdfiumActuallyLoadOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val json = Python.getInstance().getModule("cas_android")
            .callAttr("health").toString()
        val result = JSONObject(json)
        assertEquals("PYTHON_CASPARSER", result.getString("engine"))
        assertTrue("Frozen Python parser/PDFium must really load in Android",
            result.getBoolean("ok"))
        assertTrue(result.getString("casparser").isNotEmpty())
        assertTrue(result.getString("pypdfium2").isNotEmpty())
    }

}
