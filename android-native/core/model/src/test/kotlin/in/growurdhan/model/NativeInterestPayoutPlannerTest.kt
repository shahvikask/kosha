package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeInterestPayoutPlannerTest {
    private fun input(kind: NativeInterestPayoutPlanner.Kind, principal: Long = 100_000L,
        rate: Int = 250, from: String = "2025-01-31", to: String = "2026-01-31",
        maturity: String? = null) = NativeInterestPayoutPlanner.Input(
        kind, principal, rate, LocalDate.parse(from), LocalDate.parse(to),
        maturity?.let(LocalDate::parse))

    @Test fun sgbHalfYearCashCouponsDoNotCompoundOrIncreasePrincipal() {
        val plan = NativeInterestPayoutPlanner.plan(
            input(NativeInterestPayoutPlanner.Kind.SGB, principal = 400_000L))
        assertEquals(listOf("2025-07-31", "2026-01-31"),
            plan.coupons.map { it.date.toString() })
        // 4,00,000 scaled minor at 2.5% yearly / 2 = 5,000 minor per cash coupon
        assertEquals(listOf(5_000L, 5_000L), plan.coupons.map { it.amountMinor })
    }

    @Test fun pomisMonthEndReanchorsInsteadOfDriftingAfterFebruary() {
        val plan = NativeInterestPayoutPlanner.plan(
            input(NativeInterestPayoutPlanner.Kind.POMIS,
                from = "2025-01-31", to = "2025-04-30", rate = 1200))
        assertEquals(listOf("2025-02-28", "2025-03-31", "2025-04-30"),
            plan.coupons.map { it.date.toString() })
        assertEquals(listOf(1_000L, 1_000L, 1_000L),
            plan.coupons.map { it.amountMinor })
    }

    @Test fun scssQuarterlyCouponsStopAtMaturityAndAreIdempotentlyFiltered() {
        val plan = NativeInterestPayoutPlanner.plan(
            input(NativeInterestPayoutPlanner.Kind.SCSS, from = "2024-01-31",
                to = "2025-01-31", rate = 800, maturity = "2024-11-01"),
            setOf(LocalDate.parse("2024-04-30")))
        assertEquals(listOf("2024-07-31", "2024-10-31"),
            plan.coupons.map { it.date.toString() })
        assertEquals(1, plan.alreadyRecorded)
        assertEquals(listOf(2_000L, 2_000L),
            plan.coupons.map { it.amountMinor })
    }

    @Test fun invalidRatePrincipalFutureMaturityAndTinyRoundingFailClosed() {
        val kind = NativeInterestPayoutPlanner.Kind.SGB
        val attempts = listOf(
            input(kind, principal = 0),
            input(kind, principal = -1),
            input(kind, rate = 0),
            input(kind, rate = 10_001),
            input(kind, rate = 1, principal = 1),
            input(kind, from = "2026-01-31", to = "2025-01-31"),
            input(kind, maturity = "2024-12-31"))
        for (value in attempts) assertThrows(IllegalArgumentException::class.java) {
            NativeInterestPayoutPlanner.plan(value)
        }
    }

    @Test fun dueDateWindowIsBoundedForOriginalHistoricalContracts() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeInterestPayoutPlanner.plan(
                input(NativeInterestPayoutPlanner.Kind.POMIS,
                    from = "1970-01-01", to = "2025-01-01"))
        }
        assertTrue(NativeInterestPayoutPlanner.plan(
            input(NativeInterestPayoutPlanner.Kind.SGB,
                from = "2025-01-01", to = "2025-01-02")).coupons.isEmpty())
    }
}
