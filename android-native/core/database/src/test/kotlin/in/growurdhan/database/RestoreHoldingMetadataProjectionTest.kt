package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

/** Synthetic values only: original GUDVLT01/KOSHA1 metadata, never real identities. */
class RestoreHoldingMetadataProjectionTest {
    private val metadata = RestoreV9Plan.HoldingMetadata(
        benchmarkPriceMinor = "9007199254740993",
        issuePriceMinor = "31190000",
        couponRateBps = 250L, interestRateBps = 825L,
        nextCouponDate = "2027-06-01", maturityDate = "2031-01-01",
        institutionName = "Synthetic issuer", accountNumberMasked = "****1234",
        monthlyEmiMinor = "500000", emiStatus = "PAUSED",
        remainingEmiCount = 18L, acquiredOn = "2024-01-01",
        detailsJson = """{"compounding":"ANNUAL","unknownSpecific":true}""",
        reviewReasonsJson = """["needs-document"]""", tagsJson = """["family"]""",
        location = "Synthetic city", tag = "Retirement",
        fmv31Jan2018Minor = "10001234", exposureNode = "Equity",
        l2Node = "Indian", wrapper = "SGB",
        allocationSplitJson = """[{"exposure":"Gold","percent":100}]""",
        amc = "Synthetic AMC", rta = "Synthetic RTA", amfiCode = "124567",
        advisor = "Synthetic", schemeType = "EQUITY", ucc = "synthetic-UCC",
        exchange = "NSE", dpName = "Depository"
    )

    private fun source(meta: RestoreV9Plan.HoldingMetadata) = RestoreV9Plan.Snapshot(
        exportedAt = "2026-10-19T00:00:00Z",
        login = RestoreV9Plan.Login(
            email = "synthetic@example.invalid", firstName = "Synthetic",
            lastName = "Owner", passwordSalt = "syntheticSalt", passwordHash = "syntheticHash",
            passwordIterations = 210000, passwordChangedAt = "2026-10-19",
            q1Id = "city", q1Hash = "synthetic1",
            q2Id = "school", q2Hash = "synthetic2",
            createdAt = "2026-10-19"
        ),
        families = listOf(RestoreV9Plan.Family(
            "owner-1", "Synthetic Owner", "Self", true, null)),
        accounts = emptyList(),
        holdings = listOf(RestoreV9Plan.Holding(
            "holding-1", "owner-1", null, "Synthetic holding", "GOLD_SGB",
            "Sovereign Gold Bond", "SGB-SYNTH", "100000000", "31190000",
            "2026-10-19", "ORIGINAL", averageCostPerUnitMinor = "31190000",
            metadata = meta)),
        events = listOf(RestoreV9Plan.Event(
            id = "buy-1", holdingId = "holding-1", holdingName = "Synthetic holding",
            ownerId = "owner-1", eventType = "BUY", eventDate = "2024-01-01",
            quantityScaled = "100000000", unitPriceMinor = "31190000",
            totalAmountMinor = "31190000", chargesMinor = "0",
            createdAt = "2024-01-01")),
        goals = emptyList(), authenticatedOriginalJson = """{"synthetic":"unchanged"}"""
    )

    private fun column(row: RestoreV9Plan.Row, name: String): Any? =
        row.values[row.columns.indexOf(name).also { require(it >= 0) }]

    @Test fun everyTypedRestorableFieldPersistsWithoutSynthesizingMoneyEvents() {
        val plan = RestoreV9Plan.project(source(metadata))
        val instrument = plan.rows.single { it.table == "instrument" }
        val holding = plan.rows.single { it.table == "holding" }
        assertEquals(9007199254740993L, column(holding, "benchmark_price_minor"))
        assertEquals(31190000L, column(holding, "issue_price_minor"))
        assertEquals(250L, column(holding, "coupon_rate_bps"))
        assertEquals(825L, column(holding, "interest_rate_bps"))
        assertEquals(500000L, column(holding, "monthly_emi_minor"))
        assertEquals(18L, column(holding, "remaining_emi_count"))
        assertEquals("PAUSED", column(holding, "emi_status"))
        assertEquals("2031-01-01", column(holding, "maturity_date"))
        assertEquals(metadata.detailsJson, column(holding, "details_json"))
        assertEquals(metadata.reviewReasonsJson, column(holding, "review_reasons"))
        assertEquals(metadata.tagsJson, column(holding, "tags_json"))
        assertEquals(metadata.location, column(holding, "location"))
        assertEquals(metadata.tag, column(holding, "tag"))
        assertEquals(10001234L, column(instrument, "fmv_31jan2018_minor"))
        assertEquals(metadata.allocationSplitJson, column(instrument, "allocation_split"))
        assertEquals(metadata.dpName, column(instrument, "dp_name"))
        assertEquals(metadata.amfiCode, column(instrument, "amfi_code"))
        assertEquals("synthetic-UCC", column(instrument, "ucc"))
        assertEquals(1, plan.rows.count { it.table == "ledger_event" })
        assertEquals("""{"synthetic":"unchanged"}""", plan.rows.last().values.last())
    }

    @Test fun invalidFinancialMetadataFailsWithoutProducingAPlan() {
        for (bad in listOf(
            metadata.copy(couponRateBps = 100001L),
            metadata.copy(interestRateBps = -1L),
            metadata.copy(remainingEmiCount = -1L),
            metadata.copy(emiStatus = "UNKNOWN"),
            metadata.copy(monthlyEmiMinor = "1.25"),
            metadata.copy(fmv31Jan2018Minor = "9223372036854775808")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                RestoreV9Plan.project(source(bad))
            }
        }
    }
}
