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
