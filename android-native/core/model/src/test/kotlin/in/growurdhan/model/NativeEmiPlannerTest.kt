package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeEmiPlannerTest {
    private fun liability(next: String = "2025-01-31", balance: Long = 25_000L,
        status: String = "PAYING", remaining: Int = 5): NativeEmiPlanner.Loan =
        NativeEmiPlanner.Loan("owner", "loan", "LIABILITY", status, 10_000L,
            next, remaining, balance)

    @Test fun sourceMonthClampHandlesLeapFebruaryWithoutDouble() {
        assertEquals("2024-02-29", NativeEmiPlanner.addCalendarMonths("2024-01-31", 1))
        assertEquals("2025-02-28", NativeEmiPlanner.addCalendarMonths("2025-01-31", 1))
        assertEquals("2025-03-28", NativeEmiPlanner.addCalendarMonths("2025-02-28", 1))
    }

    @Test fun catchesUpDueDatesAndStopsWhenOutstandingIsZero() {
        val plan = NativeEmiPlanner.plan(liability(), "2025-05-01", emptySet())!!
        assertEquals(listOf("2025-01-31", "2025-02-28", "2025-03-28"),
            plan.payments.map { it.dueDate })
        assertEquals(listOf(10_000L, 10_000L, 5_000L),
            plan.payments.map { it.amountMinor })
        assertEquals(0L, plan.outstandingMinor)
        assertEquals("STOPPED", plan.status)
        assertNull(plan.nextDueDate)
    }

    @Test fun partialPlanMovesCursorWithoutInventingLoanInterest() {
        val plan = NativeEmiPlanner.plan(liability(balance = 100_000), "2025-01-31",
            emptySet())!!
        assertEquals(1, plan.payments.size)
        assertEquals(90_000L, plan.outstandingMinor)
        assertEquals(4, plan.remainingCount)
        assertEquals("2025-02-28", plan.nextDueDate)
        assertEquals("PAYING", plan.status)
    }

    @Test fun alreadyPaidOrPausedNeverDoublePosts() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmiPlanner.plan(liability(), "2025-03-31", setOf("2025-01-31"))
        }
        assertNull(NativeEmiPlanner.plan(liability(status = "PAUSED"), "2025-06-01", emptySet()))
        assertNull(NativeEmiPlanner.plan(liability(remaining = 0), "2025-06-01", emptySet()))
        assertNull(NativeEmiPlanner.plan(liability(), "2024-12-01", emptySet()))
    }

    @Test fun crossCategoryAndNegativeValuesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmiPlanner.plan(liability().copy(category = "EQUITY"),
                "2025-01-31", emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmiPlanner.plan(liability(balance = -1), "2025-01-31", emptySet())
        }
    }
}
