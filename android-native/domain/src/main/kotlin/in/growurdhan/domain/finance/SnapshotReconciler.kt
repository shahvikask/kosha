package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.math.BigInteger
import java.time.LocalDate

/** Immutable statement input, never interpreted as a BUY from an actual trade. */
data class StatementLine(
    val holdingId: HoldingId,
    val ownerId: OwnerId,
    val statementDate: LocalDate,
    val quantityScaled: Long,
    val unitPrice: MoneyScaled,
    val cumulativeInvested: MoneyScaled? = null,
) {
    init {
        require(quantityScaled >= 0) { "Statement quantity cannot be negative." }
        require(unitPrice.raw >= 0) { "Statement unit price cannot be negative." }
        require(cumulativeInvested == null || cumulativeInvested.raw >= 0)
    }
}

enum class ReconciliationOutcome {
    MATCH, NEW_POSITION, ADJUST_UP, ADJUST_DOWN, CLOSED, COST_UPDATE, CONFLICT,
}

data class ReconciledLine(
    val source: StatementLine,
    val outcome: ReconciliationOutcome,
    val expectedQtyScaled: Long,
    val deltaQtyScaled: Long,
    val proposedEvents: List<FinancialEvent>,
    val explanation: String,
)

data class StatementReview(
    val statementDate: LocalDate,
    val sourceSha256: String,
    val lines: List<ReconciledLine>,
    val warnings: List<String>,
    val estimatedValueChange: MoneyScaled,
) {
    /** No persistence is performed here. Never commit an unreviewed/conflicted import. */
    fun acceptedEvents(acceptedHoldingIds: Set<HoldingId>): List<FinancialEvent> {
        val selected = lines.filter { it.source.holdingId in acceptedHoldingIds }
        require(selected.none { it.outcome == ReconciliationOutcome.CONFLICT }) {
            "Unresolved statement conflicts cannot be committed."
        }
        return selected.flatMap { it.proposedEvents }
    }
}

/**
 * Mirrors src/lib/snapshot.ts: differences only, 1e-6 unit tolerance,
 * no implicit closure for holdings not covered by the statement, and a
 * cost rebase only when a printed contribution differs by over ₹1.
 *
 * This is a PLAN for user confirmation, not a parser or a data writer.
 */
object SnapshotReconciler {
    private const val QTY_TOLERANCE = 100L
    private const val COST_TOLERANCE = 10_000L

    fun coalesce(lines: List<StatementLine>): List<StatementLine> {
        val grouped = linkedMapOf<HoldingId, StatementLine>()
        for (line in lines) {
            val previous = grouped[line.holdingId]
            if (previous == null) {
                grouped[line.holdingId] = line
            } else {
                require(previous.ownerId == line.ownerId && previous.statementDate == line.statementDate) {
                    "Cannot merge statement lots of different owners or dates."
                }
                val qty = Math.addExact(previous.quantityScaled, line.quantityScaled)
                val invested = if (previous.cumulativeInvested != null || line.cumulativeInvested != null) {
                    (previous.cumulativeInvested ?: MoneyScaled(0)) +
                        (line.cumulativeInvested ?: MoneyScaled(0))
                } else null
                grouped[line.holdingId] = previous.copy(
                    quantityScaled = qty,
                    unitPrice = if (line.unitPrice.raw != 0L) line.unitPrice else previous.unitPrice,
                    cumulativeInvested = invested,
                )
            }
        }
        return grouped.values.toList()
    }

    /**
     * Source SHA is part of deterministic event IDs. The repository also rejects duplicate
     * source SHA, making repeated imports idempotent after explicit user acceptance.
     */
    fun review(
        rawLines: List<StatementLine>,
        holdings: List<FinancialHolding>,
        events: List<FinancialEvent>,
        ownerId: OwnerId,
        sourceSha256: String,
    ): StatementReview {
        require(Regex("^[a-f0-9]{64}$").matches(sourceSha256)) { "Invalid statement fingerprint." }
        require(rawLines.isNotEmpty()) { "Empty statements require manual review." }
        val lines = coalesce(rawLines)
        val date = lines.first().statementDate
        require(lines.all { it.statementDate == date }) { "Mixed statement dates." }
        require(lines.all { it.ownerId == ownerId }) { "Statement ownership mismatch." }
        val holdingsById = holdings.associateBy { it.holdingId }
        val warnings = mutableListOf<String>()
        val results = mutableListOf<ReconciledLine>()
        var valueChange = MoneyScaled(0)
        val eventsByHolding = events.filter { it.date <= date }.groupBy { it.holdingId }

        for ((index, line) in lines.withIndex()) {
            val holding = holdingsById[line.holdingId]
            val own = eventsByHolding[line.holdingId].orEmpty()
            fun suggestion(kind: FinancialEventKind, qty: Long, price: MoneyScaled, total: MoneyScaled, suffix: Int = 0, supersedes: String? = null): FinancialEvent =
                FinancialEvent(
                    id = "st_${sourceSha256}_${index}_$suffix",
                    holdingId = line.holdingId, ownerId = ownerId, kind = kind,
                    date = date, quantityScaled = qty, unitPrice = price,
                    totalAmount = total, supersedesEventId = supersedes,
                )
            if (holding == null || holding.ownerId != ownerId || own.any { it.ownerId != ownerId }) {
                results += ReconciledLine(line, ReconciliationOutcome.CONFLICT, 0, 0, emptyList(),
                    "Unknown holding or conflicting family ownership requires manual review.")
                continue
            }
            val folded = LotEngine.fold(line.holdingId, holding.category, own)
            if (folded.errors.isNotEmpty()) {
                results += ReconciledLine(line, ReconciliationOutcome.CONFLICT, folded.quantityScaled, 0,
                    emptyList(), "Existing ledger has errors; review required before reconciliation.")
                continue
            }
            val expected = folded.quantityScaled
            val delta = Math.subtractExact(line.quantityScaled, expected)
            val total = timesQuantity(delta.big().abs().toCheckedLong(), line.unitPrice.raw)
            val proposed: List<FinancialEvent>
            val outcome: ReconciliationOutcome
            val reason: String
            if (delta.big().abs() <= BigInteger.valueOf(QTY_TOLERANCE)) {
                val want = line.cumulativeInvested
                val drift = if (want == null) BigInteger.ZERO
                    else (want.raw.big() - folded.investedCost.raw.big()).abs()
                if (want != null && line.quantityScaled > 0 &&
                    drift > BigInteger.valueOf(COST_TOLERANCE) && folded.openLots.isNotEmpty()) {
                    // Exactly as the TS implementation: replace active parcels rather than
                    // adding a second purchase. All adjustment proposals need user confirmation.
                    val price = MoneyScaled(
                        (want.raw.big() * QUANTITY_SCALE_BIG / line.quantityScaled.big()).toCheckedLong()
                    )
                    val activeLots = folded.openLots
                    proposed = activeLots.mapIndexed { parcelIndex, lot ->
                        suggestion(FinancialEventKind.CORRECTION,
                            if (parcelIndex == 0) line.quantityScaled else 0,
                            if (parcelIndex == 0) price else MoneyScaled(0),
                            if (parcelIndex == 0) want else MoneyScaled(0),
                            suffix = parcelIndex,
                            supersedes = lot.eventId,
                        )
                    }
                    outcome = ReconciliationOutcome.COST_UPDATE
                    reason = "Units agree. Printed contribution changes historical invested cost, not units."
                } else {
                    outcome = ReconciliationOutcome.MATCH
                    reason = "Statement units already match the ledger."
                    proposed = emptyList()
                }
            } else if (own.isEmpty()) {
                val invested = line.cumulativeInvested
                val price = if (invested != null && line.quantityScaled > 0 && invested.raw > 0) {
                    MoneyScaled((invested.raw.big() * QUANTITY_SCALE_BIG / line.quantityScaled.big()).toCheckedLong())
                } else line.unitPrice
                outcome = ReconciliationOutcome.NEW_POSITION
                reason = "No purchase history exists; propose an opening balance, not a new BUY."
                proposed = listOf(suggestion(
                    FinancialEventKind.OPENING_BALANCE, line.quantityScaled, price,
                    invested ?: MoneyScaled(timesQuantity(line.quantityScaled, price.raw)),
                ))
            } else if (line.quantityScaled == 0L) {
                outcome = ReconciliationOutcome.CLOSED
                reason = "Statement shows zero units; propose a non-taxable statement adjustment."
                proposed = listOf(suggestion(FinancialEventKind.SNAPSHOT_ADJUSTMENT, Math.negateExact(expected),
                    line.unitPrice, MoneyScaled(total)))
            } else {
                outcome = if (delta > 0) ReconciliationOutcome.ADJUST_UP else ReconciliationOutcome.ADJUST_DOWN
                reason = "Only the difference in units is proposed, never the full balance as a purchase."
                proposed = listOf(suggestion(FinancialEventKind.SNAPSHOT_ADJUSTMENT, delta,
                    line.unitPrice, MoneyScaled(total)))
            }
            if (proposed.isNotEmpty() && outcome != ReconciliationOutcome.COST_UPDATE) {
                valueChange += MoneyScaled(timesQuantity(delta, line.unitPrice.raw))
            }
            results += ReconciledLine(line, outcome, expected, delta, proposed, reason)
        }
        val mentioned = lines.map { it.holdingId }.toSet()
        val notOnStatement = holdings.count { it.ownerId == ownerId && it.holdingId !in mentioned }
        if (notOnStatement > 0) warnings += "$notOnStatement holding(s) not on this custodian statement; left untouched."
        return StatementReview(date, sourceSha256, results, warnings, valueChange)
    }
}
