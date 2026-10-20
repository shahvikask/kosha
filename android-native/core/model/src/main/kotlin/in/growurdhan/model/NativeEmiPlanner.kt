package `in`.growurdhan.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Source-faithful frozen src/lib/emi.ts repayment catch-up.
 * Monthly EMI is an explicit balance reduction, not an interest amortisation
 * estimate. Never invent an interest accrual, cost, loan ownership or payment.
 */
object NativeEmiPlanner {
    data class Loan(
        val ownerId: String,
        val holdingId: String,
        val category: String,
        val status: String?,
        val monthlyEmiMinor: Long,
        val nextDueDate: String?,
        val remainingCount: Int,
        val outstandingMinor: Long
    )
    data class Payment(val dueDate: String, val amountMinor: Long)
    data class Plan(
        val payments: List<Payment>,
        val nextDueDate: String?,
        val remainingCount: Int,
        val outstandingMinor: Long,
        val status: String
    )

    fun addCalendarMonths(iso: String, months: Int): String {
        val base = LocalDate.parse(iso)
        // Preserve the frozen implementation's end-of-month clamp.
        return base.withDayOfMonth(1).plusMonths(months.toLong())
            .withDayOfMonth(minOf(base.dayOfMonth,
                base.withDayOfMonth(1).plusMonths(months.toLong()).lengthOfMonth()))
            .toString()
    }

    fun dueDates(nextDue: String, remaining: Int, asOf: String): List<String> {
        require(remaining in 0..3600) { "Invalid loan tenor" }
        val end = LocalDate.parse(asOf)
        var day = LocalDate.parse(nextDue)
        val dates = ArrayList<String>()
        while (dates.size < remaining && !day.isAfter(end)) {
            dates.add(day.toString())
            day = LocalDate.parse(addCalendarMonths(day.toString(), 1))
        }
        return dates
    }

    /**
     * Existing events are looked up by holding, due-date, source and owner.
     * If the earliest due date was already posted but state was not advanced,
     * refuse ambiguous partial catch-up instead of silently double-paying.
     */
    fun plan(loan: Loan, asOf: String, postedDates: Set<String>): Plan? {
        require(loan.category == "LIABILITY") { "EMI requires a liability holding" }
        require(loan.outstandingMinor >= 0 && loan.monthlyEmiMinor >= 0)
        require(loan.remainingCount in 0..3600)
        if (loan.status != "PAYING" || loan.monthlyEmiMinor == 0L ||
            loan.nextDueDate == null || loan.remainingCount == 0 || loan.outstandingMinor == 0L)
            return null

        val due = dueDates(loan.nextDueDate, loan.remainingCount, asOf)
        if (due.isEmpty()) return null
        // Fail closed on any stale repayment cursor; no heuristic resynchronisation.
        require(due.none { it in postedDates }) {
            "Previously recorded EMI date requires reconciliation"
        }
        var outstanding = loan.outstandingMinor
        val entries = mutableListOf<Payment>()
        for (day in due) {
            if (outstanding == 0L) break
            val amount = minOf(outstanding, loan.monthlyEmiMinor)
            outstanding = Math.subtractExact(outstanding, amount)
            entries += Payment(day, amount)
        }
        if (entries.isEmpty()) return null
        val remaining = loan.remainingCount - entries.size
        val closed = outstanding == 0L || remaining == 0
        return Plan(entries,
            if (closed) null else addCalendarMonths(entries.last().dueDate, 1),
            remaining, outstanding, if (closed) "STOPPED" else "PAYING")
    }
}
