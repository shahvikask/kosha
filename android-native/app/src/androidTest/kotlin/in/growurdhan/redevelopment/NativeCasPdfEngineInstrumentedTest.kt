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
