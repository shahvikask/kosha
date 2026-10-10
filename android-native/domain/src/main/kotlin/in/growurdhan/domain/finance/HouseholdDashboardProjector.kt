package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate

/**
 * UI-safe summary of an already-derived, single-snapshot portfolio.
 *
 * This does not fetch prices, infer purchase cost, or silently suppress integrity
 * errors. The caller must obtain the underlying book through one authorized
 * VaultRepository.readBook() transaction before calculating PortfolioReport.
 */
data class HouseholdDashboard(
    val asOf: LocalDate,
    val assets: MoneyScaled,
    val liabilities: MoneyScaled,
    val netWorth: MoneyScaled,
    val memberCount: Int,
    val holdingCount: Int,
    val unknownCostHoldings: Int,
    val integrityWarnings: List<String>,
    val byMember: List<MemberDashboard>,
    val byCategory: List<CategoryDashboard> = emptyList(),
    val recordedHoldings: List<RecordedHoldingDashboard> = emptyList(),
) {
    val isReliable: Boolean get() = integrityWarnings.isEmpty()
}

data class CategoryDashboard(
    val category: String,
    val value: MoneyScaled,
    val holdingCount: Int,
)

data class MemberDashboard(
    val ownerId: OwnerId,
    val assets: MoneyScaled,
    val liabilities: MoneyScaled,
    val netWorth: MoneyScaled,
    val holdings: Int,
    val displayName: String? = null,
)

/**
 * One recorded portfolio position. Values come from the same authorized book
 * snapshot as household totals. No PAN, folio, account ID or holding ID enters UI.
 * Liabilities never have investment gain/cost semantics.
 */
data class RecordedHoldingDashboard(
    val name: String,
    val category: String,
    val ownerName: String?,
    val quantityScaled: Long,
    val recordedValue: MoneyScaled,
    val unrealisedGain: MoneyScaled?,
    val unknownHistoricalCost: Boolean,
    val isLiability: Boolean,
)

/**
 * Pure projection only: no network access, persistence or synthetic values.
 * Unknown cost and book-integrity warnings remain visible to the presentation.
 */
object HouseholdDashboardProjector {
    fun from(report: PortfolioReport, memberNames: Map<OwnerId, String> = emptyMap()): HouseholdDashboard {
        val memberRows = report.members.map { member ->
            MemberDashboard(
                ownerId = member.ownerId,
                assets = member.assetValue,
                liabilities = member.liabilities,
                netWorth = member.netWorth,
                holdings = member.holdings.size,
                displayName = memberNames[member.ownerId]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        // Loan balances are debt, not investment lots. Do not count them as
        // holdings needing historical acquisition-cost reconciliation.
        val unknown = report.members.sumOf { member ->
            member.holdings.count {
                it.holding.category != "LIABILITY" && it.position.unknownInvestedCost
            }
        }
        val recorded = report.members.flatMap { member ->
            member.holdings.map { position ->
                val liability = position.holding.category == "LIABILITY"
                RecordedHoldingDashboard(
                    name = position.holding.name,
                    category = position.holding.category,
                    ownerName = memberNames[member.ownerId]?.trim()?.takeIf { it.isNotEmpty() },
                    quantityScaled = position.position.quantityScaled,
                    recordedValue = position.marketValue,
                    unrealisedGain = if (liability) null else position.unrealisedGain,
                    unknownHistoricalCost = !liability && position.position.unknownInvestedCost,
                    isLiability = liability,
                )
            }
        }.sortedWith(compareBy<RecordedHoldingDashboard>({ it.isLiability }, { it.category },
            { it.ownerName ?: "" }, { it.name }))

        val categories = report.members.flatMap { it.holdings }
            .filter { it.holding.category != "LIABILITY" }
            .groupBy { it.holding.category }
            .toSortedMap()
            .map { (category, positions) ->
                CategoryDashboard(
                    category = category,
                    value = positions.fold(MoneyScaled(0)) { total, position ->
                        total + position.marketValue
                    },
                    holdingCount = positions.size,
                )
            }
        return HouseholdDashboard(
            asOf = report.asOf,
            assets = report.familyAssets,
            liabilities = report.familyLiabilities,
            netWorth = report.familyNetWorth,
            memberCount = report.members.size,
            holdingCount = memberRows.sumOf { it.holdings },
            unknownCostHoldings = unknown,
            integrityWarnings = report.errors.toList(),
            byMember = memberRows,
            byCategory = categories,
            recordedHoldings = recorded,
        )
    }
}
