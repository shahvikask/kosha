package `in`.growurdhan.model

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class NativeE09AnalyticsTest {
    private fun b(n: Long) = BigInteger.valueOf(n)
    @Test fun exactConcentrationReconcilesTenThousandBasisPoints() {
        val a = NativeE09Analytics.allocation(mapOf(
            "EQUITY" to b(10001), "MUTUAL_FUND" to b(10001),
            "FIXED_INCOME" to b(10001)), b(30003), 2)
        assertEquals(10000, a.categories.sumOf { it.basisPoints })
        assertEquals(listOf(3334, 3333, 3333),
            a.categories.map { it.basisPoints })
        assertEquals("EQUITY", a.leadingCategory)
        assertEquals(2, a.liabilityRecordCountExcluded)
    }
    @Test fun largeValuesAndTinyRemaindersRemainExact() {
        val big = BigInteger("123456789012345678901234567890")
        val result = NativeE09Analytics.allocation(
            mapOf("EQUITY" to big, "GOLD_SGB" to BigInteger.ONE),
            big + BigInteger.ONE, 0)
        assertEquals(10000, result.categories.sumOf { it.basisPoints })
        assertEquals(10000, result.categories.first().basisPoints)
        assertEquals(BigInteger.ONE, result.categories.last().pricedMinor)
    }
    @Test fun rejectsLiabilitiesNegativeAmountsAndUnreconciledTotals() {
        for (bad in listOf(mapOf("LIABILITY" to b(100)),
            mapOf("EQUITY" to b(-100)))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeE09Analytics.allocation(bad, b(100), 0)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeE09Analytics.allocation(mapOf("EQUITY" to b(99)), b(100), 0)
        }
        assertTrue(NativeE09Analytics.allocation(emptyMap(), b(0), 3)
            .categories.isEmpty())
    }
    @Test fun incomeSeriesRetainsNegativeRealizedWithoutInventingExpenses() {
        val series = NativeE09Analytics.recordedYears(mapOf(2025 to b(500)),
            mapOf(2024 to b(-250), 2025 to b(125)))
        assertEquals(listOf(2024, 2025), series.map { it.year })
        assertEquals(b(0), series.first().recordedIncomeMinor)
        assertEquals(b(-250), series.first().realizedKnownMinor)
        assertThrows(IllegalArgumentException::class.java) {
            NativeE09Analytics.recordedYears(mapOf(2025 to b(-1)), emptyMap())
        }
    }
    @Test fun explicitCashGoldensStayExactAndSeparateContributionExpense() {
        val a = NativeE09Analytics.CashEntry("a", "owner",
            NativeE09Analytics.CashKind.CONTRIBUTION, "2025-01-01", 123456789L, "")
        val second = a.copy(id = "b", amountMinor = 77777777L, day = "2025-12-31")
        val c = a.copy(id = "c", kind = NativeE09Analytics.CashKind.EXPENSE,
            day = "2025-04-15", amountMinor = 50000000L)
        val d = c.copy(id = "d", day = "2024-01-01", amountMinor = 1L)
        val years = NativeE09Analytics.manualCashYears(listOf(d, c, second, a), "owner")
        assertEquals(listOf(2024, 2025), years.map { it.year })
        assertEquals(b(0), years[0].contributionMinor)
        assertEquals(b(1), years[0].expenseMinor)
        assertEquals(b(201234566L), years[1].contributionMinor)
        assertEquals(b(50000000L), years[1].expenseMinor)
        val max = a.copy(amountMinor = Long.MAX_VALUE)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(b(2)),
            NativeE09Analytics.manualCashYears(
                listOf(max, max.copy(id = "other")), "owner").single().contributionMinor)
        assertTrue(NativeE09Analytics.manualCashYears(emptyList(), "owner").isEmpty())
    }
    @Test fun explicitCashFailsClosedOnCorruptForeignDuplicateData() {
        val row = NativeE09Analytics.CashEntry("a", "owner",
            NativeE09Analytics.CashKind.CONTRIBUTION, "2025-02-01", 1L, "")
        for (bad in listOf(row.copy(ownerId = "foreign"), row.copy(amountMinor = -1),
            row.copy(day = "2025-02-30"), row.copy(note = "\\n"))) {
            assertThrows(Exception::class.java) {
                NativeE09Analytics.manualCashYears(listOf(bad), "owner")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeE09Analytics.manualCashYears(listOf(row, row), "owner")
        }
    }

    @Test fun unpricedGoalsNeverDisplayInventedProgress() {
        assertNull(NativeE09Analytics.verifiedGoalProgress(b(100), b(90), false))
        assertNull(NativeE09Analytics.verifiedGoalProgress(null, b(0), true))
        assertEquals(9000, NativeE09Analytics.verifiedGoalProgress(b(100), b(90), true))
        assertEquals(10000, NativeE09Analytics.verifiedGoalProgress(b(100), b(140), true))
    }
}
