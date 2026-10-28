package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Frozen src/lib/returns.ts money-weighted return (SRS-ANA-001) for native
 * scaled-integer ledger. Doubles are transient SOLVER arithmetic only: all
 * source cash flows and stored prices remain scaled Long integers.
 *
 * Non-cash snapshots/openings, splits, bonuses, and valuations never become
 * fabricated investor contributions. Missing a positive/negative cash flow,
 * unknown cost, invalid dates or nonconvergence yields N/A (null).
 */
object NativeMoneyWeightedReturn {
    data class Flow(val day: LocalDate, val amountMinor: Long)

    private fun mulDiv(x: Long, y: Long, denominator: Long): Long =
        BigInteger.valueOf(x).multiply(BigInteger.valueOf(y))
            .divide(BigInteger.valueOf(denominator)).longValueExact()

    /** First collapse immutable CORRECTION records; never count both versions. */
    private fun activeEvents(events: List<NativePortfolioMath.Event>):
        List<Pair<NativePortfolioMath.Event, String>> {
        require(events.map { it.id }.distinct().size == events.size)
        val byId = events.associateBy { it.id }
        val superseded = mutableSetOf<String>()
        for (e in events) {
            require(e.kind in NativePortfolioMath.eventTypes)
            if (e.kind == "CORRECTION") {
                val target = e.supersedes ?: error("Correction without original")
                require(target != e.id && target in byId)
                require(byId.getValue(target).ownerId == e.ownerId &&
                    byId.getValue(target).holdingId == e.holdingId)
                require(superseded.add(target)) { "Ambiguous correction chain" }
            } else require(e.supersedes == null)
        }
        fun kind(e: NativePortfolioMath.Event): String {
            var current = e
            val seen = mutableSetOf<String>()
            while (current.kind == "CORRECTION") {
                require(seen.add(current.id)) { "Circular correction" }
                current = byId[current.supersedes] ?: error("Broken correction link")
            }
            return current.kind
        }
        return events.filter { it.id !in superseded }.map { it to kind(it) }
    }

    fun cashFlows(events: List<NativePortfolioMath.Event>, currentValueMinor: Long,
        asOf: LocalDate): List<Flow> {
        require(currentValueMinor >= 0L)
        require(events.isEmpty() || events.all { it.ownerId == events.first().ownerId &&
            it.holdingId == events.first().holdingId })
        val flows = mutableListOf<Flow>()
        for ((event, kind) in activeEvents(events)) {
            val day = LocalDate.parse(event.date)
            require(!day.isAfter(asOf)) { "Future ledger transaction" }
            if (kind !in setOf("BUY", "SELL", "DIVIDEND", "INTEREST")) continue
            require(event.chargesMinor >= 0L)
            val amount = if (event.totalAmountMinor != 0L) {
                require(event.totalAmountMinor != Long.MIN_VALUE)
                abs(event.totalAmountMinor)
            } else {
                require(event.quantityScaled != Long.MIN_VALUE)
                abs(mulDiv(event.quantityScaled, event.unitPriceMinor,
                    NativePortfolioMath.QTY_SCALE))
            }
            if (amount == 0L) continue
            val signed = when (kind) {
                "BUY" -> Math.negateExact(Math.addExact(amount, event.chargesMinor))
                "SELL" -> Math.subtractExact(amount, event.chargesMinor)
                else -> amount
            }
            if (signed != 0L) flows += Flow(day, signed)
        }
        if (flows.isEmpty()) return emptyList()
        flows += Flow(asOf, currentValueMinor)
        return flows.sortedBy { it.day }
    }

    /**
     * Frozen returns.ts totalGainPercent in exact basis points, NOT annualized.
     * Reproduces JavaScript Math.round half-toward-positive-infinity, including
     * negative returns. Null means unknown basis rather than a fake 0% gain.
     */
    fun totalGainBasisPoints(investedMinor: Long?, gainMinor: Long?): Long? {
        if (investedMinor == null || investedMinor <= 0L || gainMinor == null) return null
        val denominator = BigInteger.valueOf(investedMinor).multiply(BigInteger.valueOf(2L))
        val numerator = BigInteger.valueOf(gainMinor)
            .multiply(BigInteger.valueOf(20_000L))
            .add(BigInteger.valueOf(investedMinor))
        val parts = numerator.divideAndRemainder(denominator)
        val rounded = if (numerator.signum() < 0 && parts[1] != BigInteger.ZERO)
            parts[0].subtract(BigInteger.ONE) else parts[0]
        return rounded.longValueExact()
    }

    /**
     * Returns annual percent to two decimals, never a guessed intermediate
     * value. Mirrors frozen Newton-Raphson then bounded bisection fallback.
     */
    fun percent(flows: List<Flow>): Double? {
        if (flows.size < 2 || flows.none { it.amountMinor < 0L } ||
            flows.none { it.amountMinor > 0L }) return null
        val ordered = flows.sortedBy { it.day }
        val first = ordered.first().day
        if (ordered.last().day == first) return null
        val timed = ordered.map {
            (ChronoUnit.DAYS.between(first, it.day).toDouble() / 365.25) to
                it.amountMinor.toDouble()
        }
        fun npv(rate: Double): Double {
            if (!rate.isFinite() || rate <= -1.0) return Double.NaN
            return timed.sumOf { (year, value) -> value / (1.0 + rate).pow(year) }
        }
        fun rendered(rate: Double): Double? =
            if (rate.isFinite() && rate > -1.0)
                round(rate * 10_000.0) / 100.0 else null
        var rate = .10
        for (step in 0 until 60) {
            val value = npv(rate)
            if (!value.isFinite()) break
            if (abs(value) < 1.0) return rendered(rate) // < ₹0.0001 residual
            val delta = 1e-6
            val derivative = (npv(rate + delta) - value) / delta
            if (!derivative.isFinite() || abs(derivative) < 1e-12) break
            val next = rate - value / derivative
            if (!next.isFinite() || next <= -.999999) break
            if (abs(next - rate) < 1e-9 && abs(npv(next)) < 1.0)
                return rendered(next)
            rate = next
        }
        var low = -.9999
        var high = 10.0
        var loNpv = npv(low)
        val hiNpv = npv(high)
        if (!loNpv.isFinite() || !hiNpv.isFinite() || loNpv * hiNpv > 0)
            return null
        for (step in 0 until 300) {
            val midpoint = (low + high) / 2
            val value = npv(midpoint)
            if (!value.isFinite()) return null
            if (abs(value) < 1.0) return rendered(midpoint)
            if (loNpv * value < 0) high = midpoint
            else { low = midpoint; loNpv = value }
        }
        return null
    }

    /** Never show a rate when the native position/cost is unreconciled. */
    fun forHolding(events: List<NativePortfolioMath.Event>,
        category: String, currentUnitPriceMinor: Long,
        asOf: LocalDate): Double? {
        if (currentUnitPriceMinor < 0L) return null
        val ledger = NativePortfolioMath.fold(events, category)
        if (ledger.errors.isNotEmpty() || ledger.hasUnknownCost ||
            ledger.quantityScaled < 0L) return null
        val value = mulDiv(ledger.quantityScaled, currentUnitPriceMinor,
            NativePortfolioMath.QTY_SCALE)
        return percent(cashFlows(events, value, asOf))
    }
}
