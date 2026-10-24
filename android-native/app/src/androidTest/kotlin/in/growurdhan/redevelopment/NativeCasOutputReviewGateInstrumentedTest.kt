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
            .put("name", "Synthetic DP").put("type", "Equities")
            .put("dpId", "DP000001").put("clientId", "CLIENT01")))
        .put("holdings", JSONArray().put(JSONObject()
            .put("kind", "equity").put("name", "Synthetic holding")
            .put("isin", "INE000000001")
            .put("dpId", "DP000001").put("clientId", "CLIENT01")
            .put("quantity", 10)
            .put("price", 100).put("value", 1000)))
        .put("warnings", JSONArray())
        .put("pdfTotals", JSONObject().put("demat", 1000)
            .put("mutualFund", 0).put("aif", 0).put("grand", 1000))
        .put("parsedTotals", JSONObject()
            .put("holdingsCount", 1).put("holdingsValue", 1000))
        .put("delta", 0).put("complete", true)


    @Test fun forgedCompleteReportsNeverPassImportReviewGate() {
        val cases = listOf(
            source().put("ok", false),
            source().put("engine", "SIMULATED"),
            source().put("complete", false),
            source().put("investorPan", "PQRST1234F"),
            source().put("holdings", JSONArray()),
            source().put("parsedTotals", JSONObject()
                .put("holdingsCount", 1).put("holdingsValue", 999)),
            source().put("pdfTotals", JSONObject().put("grand", 999)),
            source().put("skippedNilHoldings", 1)
        )
        for (forged in cases) {
            assertThrows(Exception::class.java) {
                NativeCasOutputReviewGate.verify(forged, "ABCDE1234F")
            }
        }
    }

    @Test fun singleSyntheticOriginalFormatAccountPassesStrictPayloadGate() {
        val verified = NativeCasOutputReviewGate.verify(source(), "ABCDE1234F")
        assertEquals("NSDL", verified.issuer)
        assertEquals("2025-02-28", verified.date)
        assertEquals(1, verified.count)
        assertEquals(0, verified.warnings)
    }

    @Test fun dematRowsMustCarryExactDpAndClientProvenance() {
        for (field in listOf("dpId", "clientId")) {
            val omitted = source()
            omitted.getJSONArray("holdings").getJSONObject(0).remove(field)
            assertThrows(IllegalArgumentException::class.java) {
                NativeCasOutputReviewGate.verify(omitted, "ABCDE1234F")
            }
            val altered = source()
            altered.getJSONArray("holdings").getJSONObject(0).put(field, "OTHER999")
            assertThrows(IllegalArgumentException::class.java) {
                NativeCasOutputReviewGate.verify(altered, "ABCDE1234F")
            }
            val unboundAccount = source()
            unboundAccount.getJSONArray("accounts").getJSONObject(0).remove(field)
            assertThrows(IllegalArgumentException::class.java) {
                NativeCasOutputReviewGate.verify(unboundAccount, "ABCDE1234F")
            }
        }
        assertEquals(1, NativeCasOutputReviewGate.verify(source(), "ABCDE1234F").count)
    }

    @Test fun unicodeLookalikeDematIdentifiersCannotPassAccountProvenanceGate() {
        for (field in listOf("dpId", "clientId")) {
            val payload = source()
            // Fullwidth Latin characters are Unicode letters but not the
            // exact ASCII depository identifiers needed for account binding.
            payload.getJSONArray("accounts").getJSONObject(0)
                .put(field, "ＤＰ０００００１")
            payload.getJSONArray("holdings").getJSONObject(0)
                .put(field, "ＤＰ０００００１")
            assertThrows(IllegalArgumentException::class.java) {
                NativeCasOutputReviewGate.verify(payload, "ABCDE1234F")
            }
        }
    }

    @Test fun duplicatedDematIsinCannotPassEvenWhenTotalsAgree() {
        val payload = source()
        val holdings = payload.getJSONArray("holdings")
        holdings.put(JSONObject(holdings.getJSONObject(0).toString()))
        payload.getJSONObject("parsedTotals")
            .put("holdingsCount", 2).put("holdingsValue", 2000)
        payload.getJSONObject("pdfTotals").put("grand", 2000)
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(payload, "ABCDE1234F")
        }
    }

    @Test fun nonDematIssuerCannotInheritMultiAccountDematExemption() {
        val payload = source().put("fileType", "CAMS")
        payload.getJSONArray("accounts").put(JSONObject()
            .put("name", "Unverified secondary source account"))
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(payload, "ABCDE1234F")
        }
    }

    @Test fun multiDematAccountPayloadPreservesPerPositionIssuerIdentity() {
        val document = source().put("fileType", "CDSL")
        val accounts = document.getJSONArray("accounts")
        accounts.put(JSONObject().put("name", "Second synthetic custody")
            .put("dpId", "DP000002").put("clientId", "CLIENT02"))
        val holdings = document.getJSONArray("holdings")
        holdings.put(JSONObject().put("kind", "equity")
            .put("name", "Same synthetic ISIN in second account")
            .put("isin", "INE000000001").put("dpId", "DP000002")
            .put("clientId", "CLIENT02")
            .put("quantity", 5).put("price", 100).put("value", 500))
        document.getJSONObject("parsedTotals")
            .put("holdingsCount", 2).put("holdingsValue", 1500)
        document.getJSONObject("pdfTotals").put("grand", 1500)
        val approved = NativeCasOutputReviewGate.verify(document, "ABCDE1234F")
        assertEquals("CDSL", approved.issuer)
        assertEquals(2, approved.count)

        // No account ordering fallback, and no duplicate custody identities.
        val swapped = JSONObject(document.toString())
        swapped.getJSONArray("holdings").getJSONObject(1)
            .put("clientId", "UNCLAIMED")
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(swapped, "ABCDE1234F")
        }
        val duplicateAccount = JSONObject(document.toString())
        duplicateAccount.getJSONArray("accounts").getJSONObject(1)
            .put("dpId", "DP000001").put("clientId", "CLIENT01")
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(duplicateAccount, "ABCDE1234F")
        }
    }

    private fun mixedDematAndFundFolioSource(): JSONObject {
        val document = source().put("fileType", "CDSL")
        document.getJSONArray("accounts").put(JSONObject()
            .put("name", "Artificial mutual fund folios")
            .put("type", "Mutual Fund Folios")
            .put("dpId", JSONObject.NULL).put("clientId", JSONObject.NULL)
            .put("folios", 1))
        document.getJSONArray("holdings").put(JSONObject()
            .put("kind", "mutual_fund").put("accountType", "Mutual Fund Folios")
            .put("name", "Artificial index fund")
            .put("isin", "INF000000001").put("folio", "DEMO-FOLIO-01")
            .put("dpId", JSONObject.NULL).put("clientId", JSONObject.NULL)
            .put("quantity", 5).put("price", 50).put("value", 250))
        document.getJSONObject("parsedTotals")
            .put("holdingsCount", 2).put("holdingsValue", 1250)
        document.getJSONObject("pdfTotals").put("grand", 1250)
        return document
    }

    @Test fun mixedDematAndExplicitFundFolioAdmittedWithoutGuessingCustody() {
        val admitted = NativeCasOutputReviewGate.verify(
            mixedDematAndFundFolioSource(), "ABCDE1234F")
        assertEquals("CDSL", admitted.issuer)
        assertEquals(2, admitted.count)

        val missingAccountKind = mixedDematAndFundFolioSource()
        missingAccountKind.getJSONArray("holdings").getJSONObject(1)
            .remove("accountType")
        val forgedEquity = mixedDematAndFundFolioSource()
        forgedEquity.getJSONArray("holdings").getJSONObject(1)
            .put("kind", "equity")
        val missingFolio = mixedDematAndFundFolioSource()
        missingFolio.getJSONArray("holdings").getJSONObject(1)
            .remove("folio")
        val forgedDp = mixedDematAndFundFolioSource()
        forgedDp.getJSONArray("holdings").getJSONObject(1)
            .put("dpId", "OTHER0001")
        val missingSummary = mixedDematAndFundFolioSource()
        missingSummary.getJSONArray("accounts").remove(1)
        val duplicateSummary = mixedDematAndFundFolioSource()
        duplicateSummary.getJSONArray("accounts").put(JSONObject()
            .put("type", "Mutual Fund Folios")
            .put("dpId", JSONObject.NULL).put("clientId", JSONObject.NULL))
        for (candidate in listOf(missingAccountKind, forgedEquity,
            missingFolio, forgedDp, missingSummary, duplicateSummary)) {
            assertThrows(Exception::class.java) {
                NativeCasOutputReviewGate.verify(candidate, "ABCDE1234F")
            }
        }
    }

    @Test fun multipleAccountsCannotAssumeFirstDematOwnerOwnsEverything() {
        val payload = source()
        payload.getJSONArray("accounts").put(JSONObject().put("name", "Second demat"))
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(payload, "ABCDE1234F")
        }
    }

    @Test fun validMultiHoldingSourceSubtotalReconcilesToFrozenParserTotal() {
        val payload = source()
        payload.getJSONArray("holdings").put(JSONObject()
            .put("kind", "mutual_fund").put("name", "Synthetic fund")
            .put("isin", "INF000000001")
            .put("dpId", "DP000001").put("clientId", "CLIENT01")
            .put("quantity", 5)
            .put("price", 50).put("value", 250))
        payload.getJSONObject("parsedTotals").put("holdingsCount", 2)
            .put("holdingsValue", 1250)
        payload.getJSONObject("pdfTotals").put("grand", 1250)
        assertEquals(2,
            NativeCasOutputReviewGate.verify(payload, "ABCDE1234F").count)
    }

    @Test fun tamperedIndividualHoldingCannotHideBehindConsistentGrandTotals() {
        val changedValue = source()
        changedValue.getJSONArray("holdings").getJSONObject(0)
            .put("value", 900)
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(changedValue, "ABCDE1234F")
        }
        val changedQuantity = source()
        changedQuantity.getJSONArray("holdings").getJSONObject(0)
            .put("quantity", -10)
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(changedQuantity, "ABCDE1234F")
        }
    }

    @Test fun missingStringAndNonfiniteSourceAmountsAreNotConvertedToZero() {
        for (field in listOf("quantity", "price", "value")) {
            val missing = source()
            missing.getJSONArray("holdings").getJSONObject(0).remove(field)
            assertThrows(Exception::class.java) {
                NativeCasOutputReviewGate.verify(missing, "ABCDE1234F")
            }
            val stringValue = source()
            stringValue.getJSONArray("holdings").getJSONObject(0)
                .put(field, "1000")
            assertThrows(IllegalArgumentException::class.java) {
                NativeCasOutputReviewGate.verify(stringValue, "ABCDE1234F")
            }
        }
    }

    @Test fun zeroUnitCasPositionsCannotConcealNonzeroPortfolioValue() {
        val contradictory = source()
        contradictory.getJSONArray("holdings").getJSONObject(0)
            .put("quantity", 0).put("value", 1000)
        // Even though parser and reported grand totals are unchanged,
        // zero units must not carry a positive closing holding valuation.
        assertThrows(IllegalArgumentException::class.java) {
            NativeCasOutputReviewGate.verify(contradictory, "ABCDE1234F")
        }
        val legitimateZero = source()
        legitimateZero.getJSONArray("holdings").getJSONObject(0)
            .put("quantity", 0).put("value", 0)
        legitimateZero.getJSONObject("parsedTotals").put("holdingsValue", 0)
        legitimateZero.getJSONObject("pdfTotals").put("grand", 0)
        assertEquals(1,
            NativeCasOutputReviewGate.verify(legitimateZero, "ABCDE1234F").count)
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
