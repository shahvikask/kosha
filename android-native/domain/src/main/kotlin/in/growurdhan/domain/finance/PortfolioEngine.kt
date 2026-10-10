package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
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
        knownMembers: List<OwnerId> = emptyList(),
        /** Production books must NEVER invent an owner from orphan holdings. */
        enforceKnownMembers: Boolean = false,
    ): PortfolioReport {
        require(holdings.map { it.holdingId }.distinct().size == holdings.size) {
            "Duplicate holding identifiers."
        }
        require(knownMembers.distinct().size == knownMembers.size) { "Duplicate family member identifiers." }
        val known = knownMembers.toSet()
        val eligibleHoldings = if (!enforceKnownMembers && knownMembers.isEmpty()) holdings
            else holdings.filter { it.ownerId in known }
        val byHolding = eligibleHoldings.associateBy { it.holdingId }
        val allEvents = events.filter { it.date <= asOf }
        val errors = mutableListOf<String>()
        if (enforceKnownMembers || knownMembers.isNotEmpty()) {
            errors += holdings.filter { it.ownerId !in known }.map {
                "Unknown family member owns holding ${it.holdingId.raw}; excluded from totals."
            }
        }
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
        val valued = eligibleHoldings.map { holding ->
            val mine = eventsByHolding[holding.holdingId].orEmpty()
            var result = LotEngine.fold(holding.holdingId, holding.category, mine)
            if (mine.isEmpty() && holding.storedQuantityScaled > 0) {
                // Do not pretend the current market price is an actual purchase price.
                val knownCost = holding.openingCostPerUnit
                val cost = if (knownCost == null) MoneyScaled(0)
                    else MoneyScaled(timesQuantity(holding.storedQuantityScaled, knownCost.raw))
                result = result.copy(
                    quantityScaled = holding.storedQuantityScaled,
                    investedCost = cost,
                    averageUnitCost = knownCost ?: MoneyScaled(0),
                    fromOpeningBalance = true,
                    unknownInvestedCost = knownCost == null,
                )
            }
            val activeLotIds = result.openLots.map { it.eventId }.toSet()
            if (result.quantityScaled > 0 && mine.any {
                it.id in activeLotIds && it.quantityScaled > 0 &&
                    it.kind in setOf(
                        FinancialEventKind.OPENING_BALANCE,
                        FinancialEventKind.SNAPSHOT_ADJUSTMENT,
                    ) && (it.unitPrice?.raw ?: 0L) == 0L
            }) {
                // A holdings-only CAS prints market price without historical buy price.
                // Keeping zero as "known invested" would invent the entire unrealised gain.
                result = result.copy(unknownInvestedCost = true)
            }
            errors += result.errors.map { "${holding.name}: $it" }
            // TS money/positions.ts truncates at the scaled boundary (not HALF_UP).
            val value = MoneyScaled(timesQuantity(result.quantityScaled, holding.unitPrice.raw))
            HoldingValuation(
                holding, result, value,
                if (result.unknownInvestedCost) null else value - result.investedCost,
            )
        }
        val grouped = valued.groupBy { it.holding.ownerId }
        val owners = (grouped.keys + knownMembers).distinct().sortedBy { it.raw }
        val perMember = owners.map { owner ->
            val rows = grouped[owner].orEmpty()
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
