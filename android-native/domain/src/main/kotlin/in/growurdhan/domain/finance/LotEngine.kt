package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Kotlin port of src/lib/lots.ts FIFO fold, with checked scaled-integer intermediates. */
object LotEngine {
    fun longTermDays(category: String): Long = when (category) {
        "EQUITY", "MUTUAL_FUND" -> 365L
        "REAL_ESTATE" -> 730L
        else -> 1095L
    }

    fun calendarDays(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
    fun fiscalYear(date: LocalDate): String {
        val start = if (date.monthValue >= 4) date.year else date.year - 1
        return "$start-${((start + 1) % 100).toString().padStart(2, '0')}"
    }
    fun assessmentYear(date: LocalDate): String {
        val start = if (date.monthValue >= 4) date.year else date.year - 1
        return "AY${((start + 1) % 100).toString().padStart(2, '0')}-${((start + 2) % 100).toString().padStart(2, '0')}"
    }

    private class MutableLot(
        val id: String,
        val acquired: LocalDate,
        var original: Long,
        var remaining: Long,
        var unit: Long,
        var cost: Long,
    ) {
        fun view() = PurchaseLot(id, acquired, original, remaining, MoneyScaled(unit), MoneyScaled(cost))
    }

    /**
     * Does not silently net different members. The caller validates ownership and matching
     * holding IDs before grouping events. This function operates on exactly one holding.
     */
    fun fold(
        holdingId: HoldingId,
        category: String,
        events: List<FinancialEvent>,
    ): FoldedPosition {
        require(events.all { it.holdingId == holdingId }) { "Cross-holding ledger fold refused." }
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate event IDs." }
        val lookup = events.associateBy { it.id }
        val errors = mutableListOf<String>()
        val superseded = events.mapNotNull { it.supersedesEventId }.toSet()
        fun resolvedKind(event: FinancialEvent): FinancialEventKind {
            if (event.kind != FinancialEventKind.CORRECTION) return event.kind
            var current = event
            val visited = mutableSetOf(event.id)
            while (current.kind == FinancialEventKind.CORRECTION) {
                val ancestor = current.supersedesEventId?.let(lookup::get)
                    ?: return FinancialEventKind.OPENING_BALANCE
                if (!visited.add(ancestor.id)) {
                    errors += "Cyclic correction chain at ${event.id}."
                    return FinancialEventKind.OPENING_BALANCE
                }
                current = ancestor
            }
            return current.kind
        }
        val order = mapOf(
            FinancialEventKind.OPENING_BALANCE to 0,
            FinancialEventKind.BUY to 1,
            FinancialEventKind.BONUS to 2,
            FinancialEventKind.SPLIT to 3,
            FinancialEventKind.CORRECTION to 4,
            FinancialEventKind.SNAPSHOT_ADJUSTMENT to 5,
            FinancialEventKind.SELL to 6,
            FinancialEventKind.DIVIDEND to 7,
            FinancialEventKind.INTEREST to 8,
            FinancialEventKind.VALUATION_UPDATE to 9,
            FinancialEventKind.REPAYMENT to 10,
        )
        val sorted = events.filterNot { it.id in superseded }
            .sortedWith(compareBy<FinancialEvent>({ it.date }, { order[resolvedKind(it)] ?: 99 }, { it.id }))
        val lots = mutableListOf<MutableLot>()
        val disposals = mutableListOf<RealisedDisposal>()
        for (event in sorted) {
            val qty = event.quantityScaled
            when (resolvedKind(event)) {
                FinancialEventKind.BUY, FinancialEventKind.OPENING_BALANCE -> {
                    if (qty <= 0) {
                        if (event.kind != FinancialEventKind.CORRECTION) errors += "${event.kind} ${event.id} has no quantity."
                        continue
                    }
                    val cost = Math.addExact(event.principalFor(qty), event.charges.raw)
                    val price = event.unitPrice?.raw ?: (cost.big() * QUANTITY_SCALE_BIG / qty.big()).toCheckedLong()
                    lots += MutableLot(event.id, event.date, qty, qty, price, cost)
                }
                FinancialEventKind.BONUS -> {
                    if (qty <= 0) continue
                    val earliest = lots.firstOrNull { it.remaining > 0 }?.acquired ?: event.date
                    lots += MutableLot(event.id, earliest, qty, qty, 0, 0)
                }
                FinancialEventKind.SPLIT -> {
                    val numerator = qty
                    val denominator = event.unitPrice?.raw ?: 0
                    if (numerator <= 0 || denominator <= 0) {
                        errors += "SPLIT ${event.id} has an invalid ratio."
                        continue
                    }
                    for (lot in lots) {
                        lot.original = (lot.original.big() * numerator.big() / denominator.big()).toCheckedLong()
                        lot.remaining = (lot.remaining.big() * numerator.big() / denominator.big()).toCheckedLong()
                        if (lot.remaining > 0) lot.unit =
                            (lot.cost.big() * QUANTITY_SCALE_BIG / lot.remaining.big()).toCheckedLong()
                    }
                }
                FinancialEventKind.SNAPSHOT_ADJUSTMENT -> {
                    if (qty > 0) {
                        val cost = event.principalFor(qty)
                        val price = event.unitPrice?.raw ?: (cost.big() * QUANTITY_SCALE_BIG / qty.big()).toCheckedLong()
                        lots += MutableLot(event.id, event.date, qty, qty, price, cost)
                    } else if (qty < 0) {
                        var left = qty.big().negate()
                        for (lot in lots) {
                            if (left == BigInteger.ZERO) break
                            if (lot.remaining <= 0) continue
                            val take = left.min(lot.remaining.big()).toCheckedLong()
                            val allocated = timesQuantity(take, lot.unit)
                            lot.remaining = Math.subtractExact(lot.remaining, take)
                            lot.cost = Math.subtractExact(lot.cost, allocated)
                            left -= take.big()
                        }
                        if (left > BigInteger.ZERO) errors += "Statement adjustment on ${event.date} exceeds units held."
                    }
                }
                FinancialEventKind.SELL -> {
                    var left = qty.big().abs()
                    if (left == BigInteger.ZERO) {
                        errors += "SELL ${event.id} has no quantity."
                        continue
                    }
                    for (lot in lots) {
                        if (left == BigInteger.ZERO) break
                        if (lot.remaining <= 0) continue
                        val take = left.min(lot.remaining.big()).toCheckedLong()
                        val cost = timesQuantity(take, lot.unit)
                        val proceeds = if (event.unitPrice != null) timesQuantity(take, event.unitPrice.raw)
                            else if (event.quantityScaled.big().abs() == take.big()) event.totalAmount.raw.big().abs().toCheckedLong()
                            else (event.totalAmount.raw.big().abs() * take.big() / event.quantityScaled.big().abs()).toCheckedLong()
                        val days = calendarDays(lot.acquired, event.date)
                        disposals += RealisedDisposal(
                            event.id, lot.id, lot.acquired, event.date, take,
                            MoneyScaled(cost), MoneyScaled(proceeds),
                            MoneyScaled(Math.subtractExact(proceeds, cost)),
                            days > longTermDays(category),
                        )
                        lot.remaining = Math.subtractExact(lot.remaining, take)
                        lot.cost = Math.subtractExact(lot.cost, cost)
                        left -= take.big()
                    }
                    if (left > BigInteger.ZERO) errors += "SELL ${event.id} on ${event.date} exceeds units held."
                }
                FinancialEventKind.DIVIDEND, FinancialEventKind.INTEREST,
                FinancialEventKind.VALUATION_UPDATE, FinancialEventKind.REPAYMENT -> Unit
                FinancialEventKind.CORRECTION -> Unit
            }
        }
        val open = lots.filter { it.remaining > 0 }
        val totalQty = open.fold(0L) { sum, lot -> Math.addExact(sum, lot.remaining) }
        val totalCost = open.fold(0L) { sum, lot -> Math.addExact(sum, lot.cost) }
        val realised = disposals.fold(0L) { sum, disposal -> Math.addExact(sum, disposal.gain.raw) }
        val avg = if (totalQty > 0) (totalCost.big() * QUANTITY_SCALE_BIG / totalQty.big()).toCheckedLong() else 0
        return FoldedPosition(
            holdingId, totalQty, MoneyScaled(totalCost), MoneyScaled(avg), MoneyScaled(realised),
            disposals.toList(), open.map { it.view() }, errors.toList(),
        )
    }
}
