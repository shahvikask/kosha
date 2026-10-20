package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Narrow P22 annual CREDIT calculation. A recorded deposit, explicit annual
 * rate and fiscal window are mandatory; no scheme rates or historical
 * contributions are inferred. This is NOT full PPF/SSY daily-minimum balance,
 * compound FD/RD/NSC/KVP, variable-rate EPF, tax or bank passbook reconciliation.
 */
object NativeAnnualInterestCredit {
    data class Input(
        val kind: String,
        val financialYearEnd: LocalDate,
        val principalMinor: Long,
        val annualRateBps: Int,
        val entitledFrom: LocalDate
    )
    data class Credit(val date: String, val amountMinor: Long,
        val openingMinor: Long, val closingMinor: Long, val annualRateBps: Int)

    fun calculate(input: Input, today: LocalDate): Credit {
        require(input.kind in setOf("PPF", "EPF", "SSY"))
        require(input.principalMinor > 0L && input.annualRateBps in 1..10_000)
        val end = input.financialYearEnd
        require(end.monthValue == 3 && end.dayOfMonth == 31 &&
            !end.isAfter(today)) { "Only completed financial years can be credited" }
        val fyStart = LocalDate.of(end.year - 1, 4, 1)
        require(!input.entitledFrom.isAfter(end)) { "No entitlement in that FY" }
        // Require entitlement for the full crediting year. Partial-year PPF
        // requires dated day-5 deposits; never substitute blanket proration.
        require(!input.entitledFrom.isAfter(fyStart)) {
            "Partial-year accrual needs actual dated deposits, not assumptions"
        }
        require(ChronoUnit.DAYS.between(fyStart, end) in 364..365)
        val interest = BigInteger.valueOf(input.principalMinor)
            .multiply(BigInteger.valueOf(input.annualRateBps.toLong()))
            .divide(BigInteger.valueOf(10_000L)).longValueExact()
        require(interest > 0L) { "Amount too small for exact minor-unit credit" }
        return Credit(end.toString(), interest, input.principalMinor,
            Math.addExact(input.principalMinor, interest), input.annualRateBps)
    }
}
