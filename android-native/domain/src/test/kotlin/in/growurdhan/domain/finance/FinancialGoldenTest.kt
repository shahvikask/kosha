package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Fixed-input parity oracles taken from:
 * src/lib/kosha.test.ts, src/lib/snapshot.test.ts and src/lib/lots.ts.
 * No CASPARSER code or real statement files are used.
 */
class FinancialGoldenTest {
    private val owner = OwnerId("f1")
    private val holdingId = HoldingId("h1")
    private fun date(v: String) = LocalDate.parse(v)
    private fun rupees(value: Long) = MoneyScaled(Math.multiplyExact(value, 10_000))
    private fun ev(
        id: String, kind: FinancialEventKind, on: String, qty: Long,
        price: Long = 0, holding: HoldingId = holdingId, ownerId: OwnerId = owner,
        supersedes: String? = null,
    ) = FinancialEvent(id, holding, ownerId, kind, date(on),
        Math.multiplyExact(qty, QUANTITY_SCALE), rupees(price),
        rupees(Math.multiplyExact(qty, price)), supersedesEventId = supersedes)
    private fun holding(
        category: String = "EQUITY",
        price: Long = 2950,
        storedQuantity: Long = 999,
        ownerId: OwnerId = owner,
        id: HoldingId = holdingId,
    ) = FinancialHolding(id, ownerId, "Synthetic holding", category, rupees(price),
        Math.multiplyExact(storedQuantity, QUANTITY_SCALE))

    @Test fun goldenFifoLedgerNotStoredQuantity() {
        val events = listOf(
            ev("e1", FinancialEventKind.BUY, "2024-03-14", 100, 2450),
            ev("e2", FinancialEventKind.BUY, "2024-09-01", 50, 2600),
            ev("e3", FinancialEventKind.SELL, "2025-04-09", 40, 2750),
        )
        val position = LotEngine.fold(holdingId, "EQUITY", events)
        assertEquals(110 * QUANTITY_SCALE, position.quantityScaled)
        assertEquals(277_000, position.investedCost.rupees().longValueExact())
        assertEquals(2_518, position.averageUnitCost.rupees().toLong())
        assertEquals(12_000, position.realisedGain.rupees().longValueExact())
        assertTrue(position.disposals.first().longTerm) // 391 days > 365
        assertEquals(391, LotEngine.calendarDays(date("2024-03-14"), date("2025-04-09")))
        val book = PortfolioEngine.derive(listOf(holding()), events, date("2026-08-01"))
        assertEquals(324_500, book.familyNetWorth.rupees().longValueExact())
        assertEquals(110 * QUANTITY_SCALE, book.members.single().holdings.single().position.quantityScaled)
    }

    @Test fun bonusAndSplitPreserveInvestedCost() {
        val events = listOf(
            ev("buy", FinancialEventKind.BUY, "2025-01-01", 10, 100),
            ev("bonus", FinancialEventKind.BONUS, "2025-02-01", 5),
            FinancialEvent("split", holdingId, owner, FinancialEventKind.SPLIT,
                date("2025-03-01"), 2, MoneyScaled(1)),
        )
        val folded = LotEngine.fold(holdingId, "EQUITY", events)
        assertEquals(30 * QUANTITY_SCALE, folded.quantityScaled)
        assertEquals(1000, folded.investedCost.rupees().longValueExact())
    }

    @Test fun oversellReportedAndSnapshotReductionsNotTaxable() {
        val buy = ev("buy", FinancialEventKind.BUY, "2026-01-31", 10, 100)
        val close = ev("adj", FinancialEventKind.SNAPSHOT_ADJUSTMENT, "2026-02-28", -10)
        val p = LotEngine.fold(holdingId, "MUTUAL_FUND", listOf(buy, close))
        assertEquals(0, p.quantityScaled)
        assertTrue(p.disposals.isEmpty())
        assertTrue(p.errors.isEmpty())
        val tooFar = LotEngine.fold(holdingId, "MUTUAL_FUND", listOf(buy,
            ev("more", FinancialEventKind.SNAPSHOT_ADJUSTMENT, "2026-02-28", -20)))
        assertTrue(tooFar.errors.any { it.contains("exceeds units held") })
        val oversold = LotEngine.fold(holdingId, "EQUITY", listOf(buy,
            ev("sell", FinancialEventKind.SELL, "2026-03-10", 50, 120)))
        assertTrue(oversold.errors.any { it.contains("exceeds units held") })
    }

    @Test fun correctionSupersedesOpeningWithoutChangingQuantity() {
        val opening = ev("ob", FinancialEventKind.OPENING_BALANCE, "2026-05-31", 100, 75)
        val correction = FinancialEvent("cor", holdingId, owner,
            FinancialEventKind.CORRECTION, date("2026-05-31"), 100 * QUANTITY_SCALE,
            rupees(50), rupees(5000), supersedesEventId = "ob")
        val p = LotEngine.fold(holdingId, "FIXED_INCOME", listOf(opening, correction))
        assertEquals(100 * QUANTITY_SCALE, p.quantityScaled)
        assertEquals(5_000, p.investedCost.rupees().longValueExact())
        assertEquals(1, p.openLots.size)
    }

    @Test fun fiscalYearAndTaxRulesMatchLegacyOracles() {
        assertEquals("2024-25", LotEngine.fiscalYear(date("2025-03-31")))
        assertEquals("2025-26", LotEngine.fiscalYear(date("2025-04-01")))
        assertEquals("AY26-27", LotEngine.assessmentYear(date("2025-06-10")))
        assertNull(IndianTaxRules.forAssessmentYear("AY40-41"))
        val rules = requireNotNull(IndianTaxRules.forAssessmentYear("AY26-27"))
        assertEquals(12_500, IndianTaxRules.applyRate(rupees(100_000), rules.ltcgEquityBps).rupees().longValueExact())
        assertFalse(IndianTaxRules.equityOriented("MUTUAL_FUND", "Gilt Fund - Long Duration"))
        assertTrue(IndianTaxRules.equityOriented("MUTUAL_FUND", "Flexi Cap Fund"))
        assertEquals(GainClass.DEBT_SLAB,
            IndianTaxRules.classify("MUTUAL_FUND", "Corporate Bond Fund", false))
        assertEquals(GainClass.SGB_EXEMPT,
            IndianTaxRules.classify("GOLD_SGB", "Sovereign Gold Bond", true, "CERTIFICATE"))
        assertEquals(GainClass.DEBT_SLAB,
            IndianTaxRules.classify("GOLD_SGB", "Sovereign Gold Bond", true, "DEMAT"))
        assertEquals(rupees(400), IndianTaxRules.grandfatheredCost(
            rules, date("2015-06-01"), rupees(100), rupees(900), rupees(400)))
        assertEquals(rupees(300), IndianTaxRules.grandfatheredCost(
            rules, date("2015-06-01"), rupees(100), rupees(300), rupees(900)))
    }

    @Test fun memberNetWorthAndOrphanEventsAreExplicit() {
        val spouse = OwnerId("spouse")
        val hs = listOf(holding(price = 1000, storedQuantity = 0),
            holding(category = "LIABILITY", price = 300, storedQuantity = 0,
                ownerId = spouse, id = HoldingId("debt")))
        val ledger = listOf(
            ev("asset", FinancialEventKind.BUY, "2026-01-01", 10, 1000),
            ev("loan", FinancialEventKind.OPENING_BALANCE, "2026-01-01", 10, 300,
                HoldingId("debt"), spouse),
            ev("orphan", FinancialEventKind.BUY, "2026-01-01", 10, 100, HoldingId("gone")),
        )
        val book = PortfolioEngine.derive(hs, ledger, date("2026-06-01"))
        assertEquals(10_000, book.familyAssets.rupees().longValueExact())
        assertEquals(3_000, book.familyLiabilities.rupees().longValueExact())
        assertEquals(7_000, book.familyNetWorth.rupees().longValueExact())
        assertTrue(book.errors.any { it.contains("no longer listed") })
    }

    @Test fun absentBuyHistoryIsShownUnknownNotFabricated() {
        val book = PortfolioEngine.derive(listOf(holding(price = 1500, storedQuantity = 75)),
            emptyList(), date("2026-08-01"))
        val item = book.members.single().holdings.single()
        assertEquals(75 * QUANTITY_SCALE, item.position.quantityScaled)
        assertTrue(item.position.fromOpeningBalance)
        assertTrue(item.position.unknownInvestedCost)
        assertNull(item.unrealisedGain)
    }

    @Test fun statementLotsCoalesceAndOnlyTheDeltaIsProposed() {
        val holding = holding(category = "MUTUAL_FUND", storedQuantity = 0)
        val sha = "a".repeat(64)
        fun line(q: Long, day: String = "2026-08-31") =
            StatementLine(holdingId, owner, date(day), Math.multiplyExact(q, QUANTITY_SCALE), rupees(100))
        val first = SnapshotReconciler.review(
            listOf(line(38), line(11703)), listOf(holding), emptyList(), owner, sha)
        assertEquals(1, first.lines.size)
        assertEquals(ReconciliationOutcome.NEW_POSITION, first.lines.single().outcome)
        val opening = first.acceptedEvents(setOf(holdingId))
        assertEquals(1, opening.size)
        assertEquals(FinancialEventKind.OPENING_BALANCE, opening.single().kind)
        val reimport = SnapshotReconciler.review(
            listOf(line(38), line(11703)), listOf(holding), opening, owner, sha)
        assertEquals(ReconciliationOutcome.MATCH, reimport.lines.single().outcome)
        assertTrue(reimport.acceptedEvents(setOf(holdingId)).isEmpty())
        val next = SnapshotReconciler.review(
            listOf(line(12241, "2026-09-30")), listOf(holding), opening, owner, "b".repeat(64))
        assertEquals(ReconciliationOutcome.ADJUST_UP, next.lines.single().outcome)
        assertEquals(500 * QUANTITY_SCALE, next.lines.single().deltaQtyScaled)
        assertEquals(FinancialEventKind.SNAPSHOT_ADJUSTMENT,
            next.acceptedEvents(setOf(holdingId)).single().kind)
    }

    @Test fun missingCustodianLinesAreNeverAutoClosed() {
        val hs = listOf(holding(), holding(id = HoldingId("other")))
        val preview = SnapshotReconciler.review(
            listOf(StatementLine(holdingId, owner, date("2026-10-08"),
                100 * QUANTITY_SCALE, rupees(100))),
            hs, emptyList(), owner, "c".repeat(64))
        assertEquals(1, preview.lines.size)
        assertEquals(1, preview.warnings.size)
        assertTrue(preview.warnings.first().contains("left untouched"))
    }

    @Test fun crossOwnerStatementIsRejectedBeforePlanning() {
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotReconciler.review(
                listOf(StatementLine(holdingId, OwnerId("spouse"),
                    date("2026-10-08"), 100 * QUANTITY_SCALE, rupees(100))),
                listOf(holding()), emptyList(), owner, "e".repeat(64))
        }
    }

    @Test fun xirrReturnsNullOnNoOutflowAndKnownCase() {
        assertNull(ReturnEngine.xirr(listOf(DatedCashFlow(date("2024-01-01"), rupees(100)),
            DatedCashFlow(date("2025-01-01"), rupees(100)))))
        val rate = ReturnEngine.xirr(listOf(
            DatedCashFlow(date("2025-01-01"), rupees(-1000)),
            DatedCashFlow(date("2026-01-01"), rupees(1100))))
        assertNotNull(rate)
        assertTrue(rate!! in 9.90..10.20, "Expected annual IRR near 10%, not $rate")
    }
}
