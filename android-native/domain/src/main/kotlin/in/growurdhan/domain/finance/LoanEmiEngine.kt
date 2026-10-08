package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate

/** Pure port of src/lib/emi.ts; never auto-post on opening the vault. */
enum class LoanEmiStatus { PAYING, PAUSED, STOPPED }
data class LoanEmiSchedule(
    val holdingId: HoldingId,
    val ownerId: OwnerId,
    val nextDue: LocalDate,
    val remainingInstallments: Int,
    val outstanding: MoneyScaled,
    val monthlyEmi: MoneyScaled,
    val status: LoanEmiStatus,
) {
    init {
        require(remainingInstallments in 0..600)
        require(outstanding.raw >= 0 && monthlyEmi.raw >= 0)
    }
}
data class EmiPayment(val date: LocalDate, val amount: MoneyScaled)
data class EmiReview(
    val payments: List<EmiPayment>,
    val nextDue: LocalDate?,
    val remainingInstallments: Int,
    val outstanding: MoneyScaled,
    val status: LoanEmiStatus,
)

/** EMI here subtracts payment from outstanding, not an interest/principal split. */
object LoanEmiEngine {
    /** Match JS month clamping: 31 Jan -> 28 Feb -> 28 Mar. */
    fun addCalendarMonths(date: LocalDate, months: Long): LocalDate {
        require(months in -12_000..12_000)
        val target = date.withDayOfMonth(1).plusMonths(months)
        return target.withDayOfMonth(minOf(date.dayOfMonth, target.lengthOfMonth()))
    }

    fun dueDates(next: LocalDate, remaining: Int, asOf: LocalDate): List<LocalDate> {
        require(remaining in 0..600)
        val dates = mutableListOf<LocalDate>()
        var cursor = next
        while (dates.size < remaining && cursor <= asOf) {
            dates += cursor
            cursor = addCalendarMonths(cursor, 1)
        }
        return dates
    }

    fun resumeDate(next: LocalDate?, asOf: LocalDate): LocalDate {
        // Resolve the nullable input once, before mutating the date cursor.
        // Otherwise Kotlin infers LocalDate? and cannot compare or return it.
        var cursor: LocalDate = next ?: return asOf
        if (cursor >= asOf) return cursor
        repeat(360) {
            if (cursor >= asOf) return cursor
            cursor = addCalendarMonths(cursor, 1)
        }
        return cursor
    }

    /** Review-only repayment plan; requires later explicit user authorization. */
    fun plan(
        loan: LoanEmiSchedule,
        asOf: LocalDate,
        alreadyPostedDates: Set<LocalDate> = emptySet(),
    ): EmiReview? {
        if (loan.status != LoanEmiStatus.PAYING) return null
        if (loan.monthlyEmi.raw <= 0 || loan.remainingInstallments == 0) return null
        val dates = dueDates(loan.nextDue, loan.remainingInstallments, asOf)
            .filterNot { it in alreadyPostedDates }
        if (dates.isEmpty()) return null
        var outstanding = loan.outstanding.raw
        val payments = mutableListOf<EmiPayment>()
        for (date in dates) {
            if (outstanding <= 0) break
            val pay = minOf(outstanding, loan.monthlyEmi.raw)
            payments += EmiPayment(date, MoneyScaled(pay))
            outstanding = Math.subtractExact(outstanding, pay)
        }
        if (payments.isEmpty()) return null
        val remaining = loan.remainingInstallments - payments.size
        val stopped = outstanding <= 0 || remaining == 0
        return EmiReview(
            payments, if (stopped) null else addCalendarMonths(payments.last().date, 1),
            remaining, MoneyScaled(outstanding),
            if (stopped) LoanEmiStatus.STOPPED else LoanEmiStatus.PAYING,
        )
    }
}
