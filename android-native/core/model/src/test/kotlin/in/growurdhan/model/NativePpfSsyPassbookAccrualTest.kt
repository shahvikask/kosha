package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Explicit synthetic passbook dates/rates; no live scheme-rate fixtures. */
class NativePpfSsyPassbookAccrualTest {
    private fun day(s: String) = LocalDate.parse(s)
    private val owner = "owner-a"
    private val holding = "ppf-holding-a"
    private fun deposit(date: String, amount: Long, id: String,
        entity: String = owner, holdingId: String = holding) =
        NativePpfSsyPassbookAccrual.Deposit(entity, holdingId, day(date),
            amount, id, "fictional-passbook-$id")
    private fun rate(endYear: Int, index: Int, bps: Int) =
        NativePpfSsyPassbookAccrual.QuarterRate(day(
            listOf("${endYear-1}-04-01", "${endYear-1}-07-01",
                "${endYear-1}-10-01", "${endYear}-01-01")[index]),
            bps, "fictional-rate-$endYear-$index")
    private fun fy(endYear: Int = 2026, principal: Long = 1_000_000L,
        closing: Long = 2_116_012L,
        deposits: List<NativePpfSsyPassbookAccrual.Deposit> = listOf(
            deposit("2025-04-05",120_000L,"a"),
            deposit("2025-04-06",230_000L,"b"),
            deposit("2025-05-04",50_000L,"c"),
            deposit("2025-07-05",100_000L,"d"),
            deposit("2025-07-06",200_000L,"e"),
            deposit("2026-03-31",300_000L,"f")),
        rates: List<NativePpfSsyPassbookAccrual.QuarterRate> =
            listOf(700,720,740,760).mapIndexed { i,b -> rate(endYear,i,b) },
        ownerId: String = owner, holdingId: String = holding,
        scheme: NativePpfSsyPassbookAccrual.Scheme =
            NativePpfSsyPassbookAccrual.Scheme.PPF,
        withdrawals: List<NativePpfSsyPassbookAccrual.Withdrawal> = emptyList()) =
        NativePpfSsyPassbookAccrual.YearEvidence(ownerId, holdingId, scheme,
            day("$endYear-03-31"), principal, "owner-opening-passbook",
            deposits, rates, closing, "owner-closing-passbook", withdrawals)
    private fun compare(vararg y: NativePpfSsyPassbookAccrual.YearEvidence) =
        NativePpfSsyPassbookAccrual.compare(y.toList(), day("2026-10-27"))

    @Test fun dayFiveInclusiveAndDaySixDeferredAndQuarterRatesExact() {
        val x = compare(fy())
        assertEquals(12, x.comparisons.single().months.size)
        assertEquals(1_120_000L, x.comparisons.single().months[0].lowestFromFifthMinor)
        assertEquals(1_400_000L, x.comparisons.single().months[1].lowestFromFifthMinor)
        assertEquals(1_500_000L, x.comparisons.single().months[3].lowestFromFifthMinor)
        assertEquals(1_700_000L, x.comparisons.single().months[4].lowestFromFifthMinor)
        assertEquals(listOf(700,720,740,760),
            listOf(0,3,6,9).map { x.comparisons.single().months[it].rateBps })
        assertEquals(116_012L, x.totalCalculatedInterestMinor)
        assertEquals(2_116_012L, x.calculatedClosingMinor)
        assertTrue(x.allEnteredClosingsMatch)
    }
    @Test fun closingPassbookMismatchIsClearlyUnreconciledNotInvented() {
        val x=compare(fy(closing=2_116_013L))
        assertFalse(x.allEnteredClosingsMatch)
        assertFalse(x.comparisons.single().matchesEnteredClosing)
        assertEquals(2_116_012L, x.calculatedClosingMinor)
    }
    @Test fun missingOrShiftedRateQuarterAndRateSourceFailClosed() {
        val basic=fy()
        for (rates in listOf(basic.quarters.take(3),
            basic.quarters.reversed(),
            basic.quarters.mapIndexed { i,v ->
                if (i==1) v.copy(effectiveOn=day("2025-07-02")) else v },
            basic.quarters.mapIndexed { i,v ->
                if (i==1) v.copy(rateSourceRef="") else v },
            basic.quarters.mapIndexed { i,v ->
                if (i==1) v.copy(annualBps=0) else v })) {
            assertThrows(IllegalArgumentException::class.java) {
                compare(basic.copy(quarters=rates))
            }
        }
    }
    @Test fun crossOwnerAndForeignHoldingDepositCannotEnterComparison() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(deposit("2025-05-05",10_000L,
                "foreign","other-owner"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(deposit("2025-05-05",10_000L,
                "foreign-holding",holdingId="unrelated"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(ownerId="different-owner"))
        }
    }
    @Test fun duplicateAndUnreferencedDepositsAreNeverSilentlyDeduplicated() {
        val a=deposit("2025-05-05",10_000L,"repeat")
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(a,a.copy(day=day("2025-06-05")))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(a.copy(statementRef=""))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(a.copy(amountMinor=-1L))))
        }
    }
    @Test fun noMissingOpeningClosingSourceOrUnknownCashFlowAccepted() {
        val x=fy()
        assertThrows(IllegalArgumentException::class.java) {
            compare(x.copy(openingSourceRef=""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(x.copy(closingSourceRef=""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(x.copy(openingBalanceMinor=-1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(x.copy(fyEnd=day("2026-03-30")))
        }
    }
    @Test fun fiscalTransitionStartsWithExplicitReconciledLastYearClose() {
        val base=fy()
        val second=fy(endYear=2027, principal=2_116_012L, closing=0L,
            deposits=listOf(deposit("2026-04-05",100_000L,"next-fy")),
            rates=listOf(700,700,700,700).mapIndexed { i,b->rate(2027,i,b) })
        // A future FY is never counted as earned at an October 2026 as-of.
        assertThrows(IllegalArgumentException::class.java) {
            compare(base,second)
        }
        val completed=NativePpfSsyPassbookAccrual.compare(
            listOf(base,second), day("2027-03-31"))
        assertEquals(2,completed.comparisons.size)
        assertFalse(completed.allEnteredClosingsMatch)
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyPassbookAccrual.compare(
                listOf(base,second.copy(openingBalanceMinor=2_116_013L)),
                day("2027-03-31"))
        }
    }
    @Test fun noOutOfFyDepositsOrFutureAsOfOrMixOfSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(deposit("2026-04-01",10_000L,"future"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(scheme=NativePpfSsyPassbookAccrual.Scheme.SSY),
                fy(endYear=2027,scheme=NativePpfSsyPassbookAccrual.Scheme.PPF))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyPassbookAccrual.compare(emptyList(),day("2026-10-27"))
        }
    }
    @Test fun overflowingOwnerProofFailsNotWrapsOrInventsInterest() {
        assertThrows(ArithmeticException::class.java) {
            compare(fy(principal=Long.MAX_VALUE,
                deposits=listOf(deposit("2025-04-05",1L,"overflow"))))
        }
    }

    private fun withdrawal(date: String, amount: Long, id: String,
        ownerId: String = owner, holdingId: String = holding) =
        NativePpfSsyPassbookAccrual.Withdrawal(ownerId, holdingId, day(date),
            amount, id, "fictional-withdrawal-$id")

    @Test fun withdrawalAfterFifthReducesCurrentMonthMinimumOnlyWhenLower() {
        val x = compare(fy(withdrawals = listOf(
            withdrawal("2025-07-07", 400_000L, "withdraw-july"))))
        val months = x.comparisons.single().months
        // July6 deposited 200k before July7 withdrew 400k; the minimum
        // actually held from July5 through month end was 1.3m.
        assertEquals(1_300_000L, months[3].lowestFromFifthMinor)
        assertEquals(1_300_000L, months[4].lowestFromFifthMinor)
        assertEquals(400_000L, x.comparisons.single().withdrawalsMinor)
        assertFalse(x.allEnteredClosingsMatch)
    }
    @Test fun withdrawalAfterFifthCannotImproveBaseOrCreateInterest() {
        val x = compare(fy(withdrawals = listOf(
            withdrawal("2025-04-15", 100_000L, "apr-withdraw"))))
        assertEquals(1_120_000L,
            x.comparisons.single().months[0].lowestFromFifthMinor)
        assertEquals(1_300_000L,
            x.comparisons.single().months[1].lowestFromFifthMinor)
    }
    @Test fun sameDayOppositeFlowsAndReusedRowsAcrossDirectionsFailClosed() {
        val d = deposit("2025-07-05", 10_000L, "new-july")
        val opposite = withdrawal("2025-07-05", 2_000L, "separate")
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(d), withdrawals=listOf(opposite)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(deposits=listOf(d),
                withdrawals=listOf(withdrawal("2025-08-05",2_000L,"new-july"))))
        }
    }
    @Test fun sourcedWithdrawalsCannotExceedActualChronologicalAvailableBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(withdrawals=listOf(
                withdrawal("2025-04-01",1_000_001L,"overdraw"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(withdrawals=listOf(withdrawal("2026-04-01",1L,"future"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(fy(withdrawals=listOf(withdrawal("2025-04-01",1L,"foreign",
                ownerId="someone-else"))))
        }
    }
    @Test fun optionalOwnerSourcedContributionCeilingNeverMasqueradesAsStatutory() {
        val scenario = fy()
        assertThrows(IllegalArgumentException::class.java) {
            compare(scenario.copy(statedContributionLimitMinor=999_999L,
                contributionLimitSourceRef="owner-entered-limit"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(scenario.copy(statedContributionLimitMinor=1_000_000L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(scenario.copy(contributionLimitSourceRef="unpaired"))
        }
        assertTrue(compare(scenario.copy(statedContributionLimitMinor=1_000_000L,
            contributionLimitSourceRef="owner-entered-limit"))
            .allEnteredClosingsMatch)
    }
    @Test fun reusedPassbookEntryIdAcrossFiscalYearsRejected() {
        val first = fy()
        val second = fy(endYear=2027, principal=2_116_012L, closing=0L,
            deposits=listOf(deposit("2026-04-05", 100_000L, "a")),
            rates=listOf(700,700,700,700).mapIndexed { i,b -> rate(2027,i,b) })
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyPassbookAccrual.compare(listOf(first,second),
                day("2027-03-31"))
        }
    }
}
