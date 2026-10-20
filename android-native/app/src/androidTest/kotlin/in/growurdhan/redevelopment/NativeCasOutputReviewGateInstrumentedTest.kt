package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic ORIGINAL-SOURCE-FORMAT JSON produced according to frozen
 * scripts/cas_parse_server.py::_from_demat/_from_cams field contracts.
 * Not a real PDF, not evidence the Python/Android wheels execute, and contains
 * no actual investor/account/statement material.
 */
@RunWith(AndroidJUnit4::class)
class NativeCasOutputReviewGateInstrumentedTest {
    private fun source(): JSONObject = JSONObject()
        .put("ok", true).put("engine", "PYTHON_CASPARSER")
        .put("fileType", "NSDL").put("casType", "DEMAT")
        .put("investorPan", "ABCDE1234F")
        .put("periodFrom", "2025-02-01").put("periodTo", "2025-02-28")
        .put("skippedNilHoldings", 0)
        .put("accounts", JSONArray().put(JSONObject()
            .put("name", "Synthetic DP").put("type", "Equities")))
        .put("holdings", JSONArray().put(JSONObject()
            .put("kind", "equity").put("name", "Synthetic holding")
            .put("isin", "INE000000001").put("quantity", 10)
            .put("price", 100).put("value", 1000)))
        .put("warnings", JSONArray())
        .put("pdfTotals", JSONObject().put("demat", 1000)
            .put("mutualFund", 0).put("aif", 0).put("grand", 1000))
        .put("parsedTotals", JSONObject()
            .put("holdingsCount", 1).put("holdingsValue", 1000))
        .put("delta", 0).put("complete", true)

    @Test fun singleSyntheticOriginalFormatAccountPassesStrictPayloadGate() {
        val verified = NativeCasOutputReviewGate.verify(source(), "ABCDE1234F")
        assertEquals("NSDL", verified.issuer)
        assertEquals("2025-02-28", verified.date)
        assertEquals(1, verified.count)
        assertEquals(0, verified.warnings)
    }

    @Test fun multipleAccountsCannotAssumeFirstDematOwnerOwnsEverything() {
        val payload = source()
        payload.getJSONArray("accounts").put(JSONObject().put("name", "Second demat"))
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(payload, "ABCDE1234F")
        }
    }

    @Test fun reportIntegrityAndFullOwnerPanAreMandatoryBeforePreview() {
        for (modified in listOf(
            source().put("engine", "NOT_CASPARSER"),
            source().put("complete", false),
            source().put("skippedNilHoldings", 1),
            source().put("delta", 150),
            source().put("periodFrom", "2025-03-01"),
            source().put("fileType", "UNKNOWN"),
            source().put("parsedTotals", JSONObject()
                .put("holdingsCount", 2).put("holdingsValue", 1000)),
            source().put("pdfTotals", JSONObject().put("grand", 800))
        )) {
            assertThrows(Exception::class.java) {
                NativeCasOutputReviewGate.verify(modified, "ABCDE1234F")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(source(), "PQRST1234F")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(source(), "")
        }
    }
}
