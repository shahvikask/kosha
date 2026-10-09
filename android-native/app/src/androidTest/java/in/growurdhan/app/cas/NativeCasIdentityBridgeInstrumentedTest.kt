package `in`.growurdhan.app.cas

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.finance.CasValidationException
import `in`.growurdhan.domain.finance.FinancialHolding
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android URI/PDF-byte SHA to frozen-parser-shaped JSON to enrolled CAS identity.
 * Fake engine: NO PDF parsing or ARM64 PDFium execution is claimed.
 * All contents, names and IDs are synthetic.
 */
@RunWith(AndroidJUnit4::class)
class NativeCasIdentityBridgeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var file: File
    private val owner = OwnerId("owner-alpha")
    private val spouse = OwnerId("owner-beta")
    private val holding = FinancialHolding(
        HoldingId("synthetic-index-holding"),
        owner, "Synthetic Index Fund", "MUTUAL_FUND", MoneyScaled(0),
    )
    private val identity = CasHoldingIdentity(
        holding.holdingId, owner, "custodian-one", "INF000000001",
        "FOL1234", "CAMS",
    )
    private val pdf = "%PDF-1.4\n%Synthetic test-only fixture\n%%EOF\n".toByteArray()

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File.createTempFile("synthetic-cas-", ".pdf", context.cacheDir)
        file.writeBytes(pdf)
    }

    @After fun cleanup() {
        file.delete()
        pdf.fill(0)
    }

    private fun payload(
        isin: String = identity.isin,
        folio: String = identity.folio,
        issuer: String = "CAMS",
        scheme: String = holding.name,
        complete: Boolean = true,
        includeWarnings: Boolean = false,
    ): String = JSONObject()
        .put("ok", true)
        .put("engine", "PYTHON_CASPARSER")
        .put("fileType", issuer)
        .put("complete", complete)
        .put("periodTo", "2026-09-30")
        .put("warnings", if (includeWarnings) JSONArray().put("Synthetic discrepancy") else JSONArray())
        .put("holdings", JSONArray().put(JSONObject()
            .put("name", scheme)
            .put("category", "EQUITY")
            .put("isin", isin)
            .put("folio", folio)
            .put("quantity", 12.5)
            .put("price", 100.75)
            .put("cost", JSONObject.NULL)
        )).toString()

    private fun reader(json: String): NativeCasPdfReader =
        NativeCasPdfReader(context, object : CasPythonEngine {
            override fun parsePdfBase64(pdfBase64: String, password: String): String {
                assertTrue(pdfBase64.isNotBlank())
                return json
            }
        })

    private fun parse(json: String, ownerId: OwnerId = owner,
                      enrolled: List<CasHoldingIdentity> = listOf(identity)) = runBlocking {
        reader(json).parseAndValidate(
            Uri.fromFile(file), ownerId,
            listOf(holding), enrolled,
        )
    }

    @Test fun frozenPayloadShapeMapsExactFamilyRegistrarIsinFolioAndActualSourceHash() {
        val result = parse(payload())
        val sha = MessageDigest.getInstance("SHA-256").digest(pdf)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(sha, result.sourceSha256)
        assertEquals(owner, result.ownerId)
        assertEquals(holding.holdingId, result.lines.single().holdingId)
        assertEquals(1_250_000_000L, result.lines.single().quantityScaled)
        assertEquals(1_007_500L, result.lines.single().unitPrice.raw)
        assertEquals(null, result.lines.single().cumulativeInvested)
    }

    @Test fun wrongOwnerAndAbsentEnrollmentNeverFallBackToFundName() {
        assertThrows(CasValidationException::class.java) {
            parse(payload(), ownerId = spouse)
        }
        assertThrows(CasValidationException::class.java) {
            parse(payload(), enrolled = emptyList())
        }
    }

    @Test fun wrongIsinFolioRegistrarOrFundNameMustBlockImport() {
        for (json in listOf(
            payload(isin = "INF000000002"),
            payload(folio = "DIFFERENT"),
            payload(issuer = "KFINTECH"),
            payload(scheme = "Another Synthetic Fund"),
        )) {
            assertThrows(CasValidationException::class.java) { parse(json) }
        }
    }

    @Test fun parserWarningsAndIncompleteReconciliationFailClosed() {
        assertThrows(CasValidationException::class.java) {
            parse(payload(complete = false))
        }
        assertThrows(CasValidationException::class.java) {
            parse(payload(includeWarnings = true))
        }
    }

    @Test fun computedHashIsFromSelectedPdfBytesNotFromParserText() {
        val first = parse(payload()).sourceSha256
        file.appendText("%Appended synthetic revision\n")
        val second = parse(payload()).sourceSha256
        assertTrue(first != second)
    }
}
