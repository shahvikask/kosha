package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.Holding
import `in`.growurdhan.core.model.LedgerEvent
import `in`.growurdhan.core.model.LedgerKind
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.domain.vault.VaultRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * M3 read-only bridge to the M2 repository. Retains stored event totals rather than
 * pretending to know the purchase unit price or transaction charges that M2 did not
 * persist. Later persistence upgrade must add these fields before complete lot parity.
 */
internal fun LedgerEvent.toFinancialEvent(): FinancialEvent {
    val financialKind = when (kind) {
        LedgerKind.BUY -> FinancialEventKind.BUY
        LedgerKind.SELL -> FinancialEventKind.SELL
        LedgerKind.OPENING_BALANCE -> FinancialEventKind.OPENING_BALANCE
        LedgerKind.ADJUSTMENT -> FinancialEventKind.SNAPSHOT_ADJUSTMENT
        LedgerKind.DIVIDEND -> FinancialEventKind.DIVIDEND
        LedgerKind.INTEREST -> FinancialEventKind.INTEREST
        LedgerKind.CORRECTION -> throw IllegalStateException(
            "Legacy stored correction lacks supersession metadata; cost parity cannot be established."
        )
    }
    return FinancialEvent(
        id = id, holdingId = holdingId, ownerId = ownerId, kind = financialKind,
        date = LocalDate.parse(eventDate), quantityScaled = quantityDeltaScaled,
        unitPrice = null, totalAmount = amount,
    )
}

internal fun Holding.toFinancialHolding(): FinancialHolding = FinancialHolding(
    holdingId = id, ownerId = ownerId, name = name, category = assetCategory,
    unitPrice = currentUnitPrice, storedQuantityScaled = quantity.raw,
)

/** Only a read. Financial mutations always remain repository transactions. */
class CalculateFamilyPortfolio @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(asOf: LocalDate): PortfolioReport {
        // M2 exposes separate reads, so concurrent writes can make this snapshot
        // inconsistent; a single-transaction vault book read is the next data gate.
        val holdings = vault.holdings().map { it.toFinancialHolding() }
        val events = vault.events().map { it.toFinancialEvent() }
        return PortfolioEngine.derive(holdings, events, asOf)
    }
}
