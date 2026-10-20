package `in`.growurdhan.database

import `in`.growurdhan.model.NativePortfolioMath

/**
 * A historical frozen snapshot stores the holding's reported quantity AND an
 * append-only event history. Native books derive units exclusively from the
 * ledger. Before restoring, prove that the authenticated original record
 * matches what the immutable native FIFO engine will actually display.
 *
 * This is a PURE preflight: never synthesize an OPENING_BALANCE, BUY, cost,
 * split or disposal and never change the signed source JSON. Genuine source
 * discrepancies are left in the encrypted quarantine for owner review.
 */
object RestorePositionParity {
    fun requireReconciled(snapshot: RestoreV9Plan.Snapshot) {
        val byHolding = snapshot.events.groupBy { it.holdingId }
        for (holding in snapshot.holdings) {
            val events = byHolding[holding.id].orEmpty().map { old ->
                val known = old.eventType !in
                    setOf("SNAPSHOT_ADJUSTMENT", "OPENING_BALANCE") ||
                    old.unitPriceMinor != "0" || old.totalAmountMinor != "0"
                NativePortfolioMath.Event(
                    id = old.id, ownerId = old.ownerId, holdingId = old.holdingId,
                    kind = old.eventType, date = old.eventDate,
                    quantityScaled = old.quantityScaled.toLong(),
                    unitPriceMinor = old.unitPriceMinor.toLong(),
                    totalAmountMinor = old.totalAmountMinor.toLong(),
                    chargesMinor = old.chargesMinor.toLong(),
                    supersedes = old.supersedesEventId, knownCost = known)
            }
            // By design the model folds corrections and sorted dated events.
            // An overdrawn FIFO, bad split or overflow must never pass simply
            // because a matching positive source event exists somewhere.
            val folded = NativePortfolioMath.fold(events, holding.category)
            require(folded.errors.isEmpty()) {
                "Authenticated historical ledger cannot be safely reconstructed"
            }
            val reported = holding.quantityScaled.toLong()
            require(reported >= 0L && folded.quantityScaled == reported) {
                "Authenticated stored balance differs from native dated ledger"
            }
        }
    }
}
