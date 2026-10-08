package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.marketValue
import java.time.LocalDate

/** Checked totals calculated without trusting the legacy holding's stored quantity. */
data class HoldingValuation(
    val holding: FinancialHolding,
    val position: FoldedPosition,
    val marketValue: MoneyScaled,
    val unrealisedGain: MoneyScaled?,
)

data class MemberPortfolio(
    val ownerId: OwnerId,
    val assetValue: MoneyScaled,
    val liabilities: MoneyScaled,
    val netWorth: MoneyScaled,
    val holdings: List<HoldingValuation>,
)

data class PortfolioReport(
    val asOf: LocalDate,
    val members: List<MemberPortfolio>,
    val familyAssets: MoneyScaled,
    val familyLiabilities: MoneyScaled,
    val familyNetWorth: MoneyScaled,
    val errors: List<String>,
)

object PortfolioEngine {
    fun derive(
        holdings: List<FinancialHolding>,
        events: List<FinancialEvent>,
        asOf: LocalDate,
    ): PortfolioReport {
        require(holdings.map { it.holdingId }.distinct().size == holdings.size) {
            "Duplicate holding identifiers."
        }
        val byHolding = holdings.associateBy { it.holdingId }
        val allEvents = events.filter { it.date <= asOf }
        val errors = mutableListOf<String>()
        val validEvents = mutableListOf<FinancialEvent>()
        for (event in allEvents) {
            val holding = byHolding[event.holdingId]
            if (holding == null) {
                errors += "Orphan financial event ${event.id}: holding no longer listed."
            } else if (holding.ownerId != event.ownerId) {
                errors += "Ownership conflict for event ${event.id}. Event excluded from portfolio."
            } else validEvents += event
        }
        val eventsByHolding = validEvents.groupBy { it.holdingId }
        val valued = holdings.map { holding ->
            val mine = eventsByHolding[holding.holdingId].orEmpty()
            var result = LotEngine.fold(holding.holdingId, holding.category, mine)
            if (mine.isEmpty() && holding.storedQuantityScaled > 0) {
                // Do not pretend the current market price is an actual purchase price.
                val knownCost = holding.openingCostPerUnit
                val cost = if (knownCost == null) MoneyScaled(0)
                    else marketValue(`in`.growurdhan.core.model.QuantityScaled(holding.storedQuantityScaled), knownCost)
                result = result.copy(
                    quantityScaled = holding.storedQuantityScaled,
                    investedCost = cost,
                    averageUnitCost = knownCost ?: MoneyScaled(0),
                    fromOpeningBalance = true,
                    unknownInvestedCost = knownCost == null,
                )
            }
            errors += result.errors.map { "${holding.name}: $it" }
            val value = marketValue(
                `in`.growurdhan.core.model.QuantityScaled(result.quantityScaled),
                holding.unitPrice,
            )
            HoldingValuation(
                holding, result, value,
                if (result.unknownInvestedCost) null else value - result.investedCost,
            )
        }
        val grouped = valued.groupBy { it.holding.ownerId }
        val perMember = grouped.entries.sortedBy { it.key.raw }.map { (owner, rows) ->
            val assets = rows.filter { it.holding.category != "LIABILITY" }
                .fold(MoneyScaled(0)) { sum, row -> sum + row.marketValue }
            val debt = rows.filter { it.holding.category == "LIABILITY" }
                .fold(MoneyScaled(0)) { sum, row -> sum + row.marketValue }
            MemberPortfolio(owner, assets, debt, assets - debt, rows)
        }
        val assets = perMember.fold(MoneyScaled(0)) { sum, member -> sum + member.assetValue }
        val debt = perMember.fold(MoneyScaled(0)) { sum, member -> sum + member.liabilities }
        return PortfolioReport(asOf, perMember, assets, debt, assets - debt, errors)
    }
}
