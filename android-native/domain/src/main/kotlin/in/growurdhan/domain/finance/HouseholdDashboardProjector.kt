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
        val unknown = report.members.sumOf { member ->
            member.holdings.count { it.position.unknownInvestedCost }
        }
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
        )
    }
}
