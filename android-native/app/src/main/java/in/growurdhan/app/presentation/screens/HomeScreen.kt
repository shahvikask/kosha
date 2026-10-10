package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import `in`.growurdhan.app.presentation.HouseholdDashboardUiState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Honest empty state: M1 does not create a financial vault, import fabricated
 * holdings, or read/mutate the existing Tauri database.
 */
@Composable
fun HomeScreen(
    dashboardState: HouseholdDashboardUiState = HouseholdDashboardUiState.Locked,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenVault: () -> Unit = {},
    onRefreshDashboard: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Family wealth, with clarity.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Private by design. Your financial books remain on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Your financial overview", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(when (dashboardState) {
                    is HouseholdDashboardUiState.Ready -> "Recorded net worth: " + dashboardState.dashboard.netWorth.rupees().toPlainString() + " INR; recorded assets: " + dashboardState.dashboard.assets.rupees().toPlainString() + " INR; recorded liabilities: " + dashboardState.dashboard.liabilities.rupees().toPlainString() + " INR. Values use stored unit prices, not live market quotes. " + dashboardState.dashboard.memberCount + " family members; " + dashboardState.dashboard.holdingCount + " holdings." + (if (dashboardState.dashboard.unknownCostHoldings > 0) " Historical cost unknown for " + dashboardState.dashboard.unknownCostHoldings + " holdings." else "") + (if (!dashboardState.dashboard.isReliable) " Financial integrity review required; totals may be incomplete." else "")
                    HouseholdDashboardUiState.Loading -> "Calculating your household overview."
                    HouseholdDashboardUiState.Unavailable -> "Overview unavailable. Please retry after unlocking."
                    HouseholdDashboardUiState.Locked -> "Unlock the vault to view your family wealth."
                },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onOpenVault) {
                        Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                        Text("Open vault", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (dashboardState is HouseholdDashboardUiState.Ready ||
                        dashboardState is HouseholdDashboardUiState.Unavailable
                    ) {
                        OutlinedButton(onClick = onRefreshDashboard) {
                            Text(if (dashboardState is HouseholdDashboardUiState.Unavailable)
                                "Retry overview" else "Refresh totals")
                        }
                    }
                }
            }
        }

        if (dashboardState is HouseholdDashboardUiState.Ready &&
            dashboardState.dashboard.unknownCostHoldings > 0
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Historical purchase cost unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(
                        dashboardState.dashboard.unknownCostHoldings.toString() +
                            " holding(s) have no verified historical purchase cost. " +
                            "Recorded valuations are shown, but unrealised gains " +
                            "must not be inferred from these balances.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (dashboardState is HouseholdDashboardUiState.Ready &&
            dashboardState.dashboard.byCategory.isNotEmpty()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Assets by category", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Recorded valuations, not live market quotes. " +
                            "Total assets: " + dashboardState.dashboard.assets.rupees().toPlainString() +
                            " INR across " + dashboardState.dashboard.byCategory.sumOf { it.holdingCount } +
                            " asset holdings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    dashboardState.dashboard.byCategory.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                category.category.replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                category.value.rupees().toPlainString() + " INR (" +
                                    category.holdingCount + ")",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (dashboardState is HouseholdDashboardUiState.Ready &&
            dashboardState.dashboard.integrityWarnings.isNotEmpty()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Financial data needs review", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Some records could not be included reliably in your totals. " +
                            "Review the affected holdings before relying on this overview.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        dashboardState.dashboard.integrityWarnings.size.toString() +
                            " calculation or ownership warning(s).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (dashboardState is HouseholdDashboardUiState.Ready &&
            dashboardState.dashboard.byMember.isNotEmpty()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Family breakdown", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Recorded net worth by member. Names are shown only while the vault is unlocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    dashboardState.dashboard.byMember.forEachIndexed { index, member ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                member.displayName ?: "Family member " + (index + 1),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Assets: " + member.assets.rupees().toPlainString() +
                                    " INR; liabilities: " + member.liabilities.rupees().toPlainString() +
                                    " INR; net worth: " + member.netWorth.rupees().toPlainString() +
                                    " INR; holdings: " + member.holdings,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (dashboardState is HouseholdDashboardUiState.Ready &&
            dashboardState.dashboard.recordedHoldings.isNotEmpty()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Recorded holdings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Values use recorded unit prices, not live quotes. " +
                            "Account IDs and folio fields are not shown. " +
                            "Use nicknames without personal identifiers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    dashboardState.dashboard.recordedHoldings.take(30).forEach { holding ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(holding.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                holding.category.replace('_', ' ') +
                                    (holding.ownerName?.let { " · " + it } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                (if (holding.isLiability) "Outstanding liability: " else "Recorded value: ") +
                                    holding.recordedValue.rupees().toPlainString() + " INR",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!holding.isLiability) {
                                Text(
                                    "Units: " + java.math.BigDecimal.valueOf(
                                        holding.quantityScaled, 8,
                                    ).stripTrailingZeros().toPlainString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    when {
                                        holding.unknownHistoricalCost -> "Historical cost unknown; gain unavailable"
                                        holding.unrealisedGain != null -> "Unrealised gain/loss: " +
                                            holding.unrealisedGain.rupees().toPlainString() + " INR"
                                        else -> "Unrealised gain/loss unavailable"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    val hidden = dashboardState.dashboard.recordedHoldings.size - 30
                    if (hidden > 0) {
                        Text(
                            "$hidden more holding(s) are included in totals but not shown here.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenStatements, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Text("Statements", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Text("Privacy", modifier = Modifier.padding(start = 6.dp))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                    Text(
                        "Portfolio development status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Your recorded household totals and asset categories are available after " +
                        "unlocking the native vault. These are not live market valuations. " +
                        "Existing React/Tauri books are not automatically migrated. " +
                        "CAS PDF imports require enrolled family members and holding mappings; " +
                        "verified parsing is supported on ARM64 release builds. " +
                        "Full portfolio and onboarding parity remain in development.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenSettings) {
                Text("Personalize")
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOpenPrivacy) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Privacy")
            }
        }
    }
}
