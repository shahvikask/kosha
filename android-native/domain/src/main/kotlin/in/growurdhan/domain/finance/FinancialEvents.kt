package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.math.BigInteger
import java.time.LocalDate

/** Precision contracts from src/lib/money.ts: 1e4 INR and 1e8 asset quantity. */
internal const val QUANTITY_SCALE = 100_000_000L
internal val QUANTITY_SCALE_BIG: BigInteger = BigInteger.valueOf(QUANTITY_SCALE)

internal fun Long.big(): BigInteger = BigInteger.valueOf(this)
internal fun BigInteger.toCheckedLong(): Long = longValueExact()
internal fun timesQuantity(units: Long, unitPrice: Long): Long =
    (units.big() * unitPrice.big() / QUANTITY_SCALE_BIG).toCheckedLong()

/**
 * Pure domain event, separate from storage. M2's persisted LedgerEvent currently lacks
 * the unit price, charges, split/bonus and supersession metadata needed for full tax-lot
 * fidelity. No Android, SQLite, Python, I/O or auto-CAS import is allowed here.
 */
enum class FinancialEventKind {
    BUY, SELL, OPENING_BALANCE, BONUS, SPLIT, SNAPSHOT_ADJUSTMENT,
    DIVIDEND, INTEREST, VALUATION_UPDATE, REPAYMENT, CORRECTION,
}

data class FinancialEvent(
    val id: String,
    val holdingId: HoldingId,
    val ownerId: OwnerId,
    val kind: FinancialEventKind,
    val date: LocalDate,
    /** Signed scaled units. Negative on statement reductions; SELL accepts either sign. */
    val quantityScaled: Long = 0,
    /** Optional because M2 historical ledger stores a total but not a unit price. */
    val unitPrice: MoneyScaled? = null,
    val totalAmount: MoneyScaled = MoneyScaled(0),
    val charges: MoneyScaled = MoneyScaled(0),
    val supersedesEventId: String? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "Invalid financial event ID." }
        require(supersedesEventId == null || supersedesEventId != id) { "An event cannot supersede itself." }
        require(charges.raw >= 0) { "Transaction charges cannot be negative." }
    }

    /** Legacy TS lot engine derives parcel cost/proceeds from unit price, not from total. */
    fun principalFor(quantity: Long): Long {
        if (quantity < 0) throw IllegalArgumentException("Expected a nonnegative quantity.")
        return if (unitPrice != null) timesQuantity(quantity, unitPrice.raw)
        else totalAmount.raw.big().abs().toCheckedLong()
    }
}

data class FinancialHolding(
    val holdingId: HoldingId,
    val ownerId: OwnerId,
    val name: String,
    val category: String,
    val unitPrice: MoneyScaled,
    /** Reference-only fallback when opening history is absent; not an invented BUY. */
    val storedQuantityScaled: Long = 0,
    val openingCostPerUnit: MoneyScaled? = null,
    val subCategory: String = "",
    val goldHoldMode: String? = null,
) {
    init {
        require(name.isNotBlank())
        require(storedQuantityScaled >= 0)
    }
}

data class PurchaseLot(
    val eventId: String,
    val acquiredOn: LocalDate,
    val originalQtyScaled: Long,
    val remainingQtyScaled: Long,
    val unitCost: MoneyScaled,
    val remainingCost: MoneyScaled,
)

data class RealisedDisposal(
    val saleEventId: String,
    val purchaseEventId: String,
    val acquiredOn: LocalDate,
    val soldOn: LocalDate,
    val quantityScaled: Long,
    val cost: MoneyScaled,
    val proceeds: MoneyScaled,
    val gain: MoneyScaled,
    val longTerm: Boolean,
)

data class FoldedPosition(
    val holdingId: HoldingId,
    val quantityScaled: Long,
    val investedCost: MoneyScaled,
    val averageUnitCost: MoneyScaled,
    val realisedGain: MoneyScaled,
    val disposals: List<RealisedDisposal>,
    val openLots: List<PurchaseLot>,
    val errors: List<String>,
    val fromOpeningBalance: Boolean = false,
    val unknownInvestedCost: Boolean = false,
)
