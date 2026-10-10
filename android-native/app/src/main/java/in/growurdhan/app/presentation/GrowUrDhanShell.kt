package `in`.growurdhan.app.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.growurdhan.app.presentation.screens.PortableRecoveryIntent
import `in`.growurdhan.app.presentation.screens.PortableRecoveryScreen
import `in`.growurdhan.app.presentation.screens.CustodyHoldingScreen
import `in`.growurdhan.app.presentation.screens.FamilyMemberScreen
import `in`.growurdhan.app.presentation.screens.HomeScreen
import `in`.growurdhan.app.presentation.screens.StatementReviewScreen
import `in`.growurdhan.app.presentation.screens.PrivacyScreen
import `in`.growurdhan.app.presentation.screens.SettingsScreen
import `in`.growurdhan.app.presentation.screens.VaultScreen

private enum class MainDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    VAULT("vault", "Vault", Icons.Outlined.Lock),
    REVIEW("review", "Statements", Icons.Outlined.Description),
    PRIVACY("privacy", "Privacy", Icons.Outlined.Shield),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

/** Secondary route; intentionally not another bottom-navigation tab. */
private const val FAMILY_ROUTE = "family"
/** Secondary secure registrar/holding setup route, not an extra bottom tab. */
private const val CUSTODY_ROUTE = "custody"
private const val RECOVERY_ROUTE = "recovery"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowUrDhanShell(
    state: AppUiState,
    onIntent: (AppIntent) -> Unit,
    vaultState: VaultUiState = VaultUiState.Loading,
    dashboardState: HouseholdDashboardUiState = HouseholdDashboardUiState.Locked,
    onDashboardRefresh: () -> Unit = {},
    onVaultIntent: (VaultIntent) -> Unit = {},
    reviewState: StatementReviewUiState = StatementReviewUiState.Empty,
    onReviewIntent: (StatementReviewIntent) -> Unit = {},
    familyState: FamilyMemberUiState = FamilyMemberUiState.Locked,
    onFamilyIntent: (FamilyMemberIntent) -> Unit = {},
    custodyState: CustodyHoldingUiState = CustodyHoldingUiState.Locked,
    onCustodyIntent: (CustodyHoldingIntent) -> Unit = {},
    recoveryState: PortableRecoveryUiState = PortableRecoveryUiState.Empty,
    onRecoveryIntent: (PortableRecoveryIntent) -> Unit = {},
) {
    val controller = rememberNavController()
    val currentBackStackEntry by controller.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: MainDestination.HOME.route
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null) {
            snackbars.showSnackbar(message)
            onIntent(AppIntent.DismissError)
        }
    }

    val navigate: (String) -> Unit = { route ->
        controller.navigate(route) {
            popUpTo(MainDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val extended = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize()) {
            if (extended) {
                NavigationRail {
                    MainDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                            modifier = Modifier.semantics {
                                contentDescription = destination.title
                            },
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(title = { Text("GrowUrDhan") })
                },
                snackbarHost = { SnackbarHost(snackbars) },
                bottomBar = {
                    if (!extended) {
                        NavigationBar {
                            MainDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { navigate(destination.route) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(destination.title) },
                                    modifier = Modifier.semantics {
                                        contentDescription = destination.title
                                    },
                                )
                            }
                        }
                    }
                },
            ) { insets ->
                NavHost(
                    navController = controller,
                    startDestination = MainDestination.HOME.route,
                    modifier = Modifier.fillMaxSize().padding(insets),
                ) {
                    composable(MainDestination.HOME.route) {
                        HomeScreen(
                            dashboardState = dashboardState,
                            onRefreshDashboard = onDashboardRefresh,
                            onOpenSettings = { navigate(MainDestination.SETTINGS.route) },
                            onOpenPrivacy = { navigate(MainDestination.PRIVACY.route) },
                            onOpenVault = { navigate(MainDestination.VAULT.route) },
                            onOpenStatements = { navigate(MainDestination.REVIEW.route) },
                        )
                    }
                    composable(MainDestination.VAULT.route) {
                        VaultScreen(
                            state = vaultState,
                            onIntent = onVaultIntent,
                            onAddFamilyMember = { navigate(FAMILY_ROUTE) },
                            onManageHoldings = { navigate(CUSTODY_ROUTE) },
                            onBackupRecovery = { navigate(RECOVERY_ROUTE) },
                        )
                    }
                    composable(MainDestination.REVIEW.route) {
                        StatementReviewScreen(
                            state = reviewState,
                            onIntent = onReviewIntent,
                            onAddFamilyMember = { navigate(FAMILY_ROUTE) },
                            onSetUpHoldings = { navigate(CUSTODY_ROUTE) },
                        )
                    }
                    composable(FAMILY_ROUTE) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            onFamilyIntent(FamilyMemberIntent.Refresh)
                        }
                        FamilyMemberScreen(
                            state = familyState,
                            onIntent = onFamilyIntent,
                            onReturnToVault = {
                                onFamilyIntent(FamilyMemberIntent.Cancel)
                                navigate(MainDestination.VAULT.route)
                            },
                            onContinueToStatements = {
                                onFamilyIntent(FamilyMemberIntent.Cancel)
                                onReviewIntent(StatementReviewIntent.LoadOwners)
                                navigate(MainDestination.REVIEW.route)
                            },
                            onSetUpHoldings = {
                                onFamilyIntent(FamilyMemberIntent.Cancel)
                                navigate(CUSTODY_ROUTE)
                            },
                        )
                    }
                    composable(CUSTODY_ROUTE) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            onCustodyIntent(CustodyHoldingIntent.Refresh)
                        }
                        CustodyHoldingScreen(
                            state = custodyState,
                            onIntent = onCustodyIntent,
                            onBackToVault = {
                                onCustodyIntent(CustodyHoldingIntent.Cancel)
                                navigate(MainDestination.VAULT.route)
                            },
                            onContinueToStatements = {
                                onCustodyIntent(CustodyHoldingIntent.Cancel)
                                onReviewIntent(StatementReviewIntent.LoadOwners)
                                navigate(MainDestination.REVIEW.route)
                            },
                        )
                    }
                    composable(RECOVERY_ROUTE) {
                        PortableRecoveryScreen(
                            state = recoveryState,
                            onIntent = onRecoveryIntent,
                            onReturnToVault = {
                                onRecoveryIntent(PortableRecoveryIntent.Cancel)
                                navigate(MainDestination.VAULT.route)
                            },
                        )
                    }
                    composable(MainDestination.PRIVACY.route) {
                        PrivacyScreen()
                    }
                    composable(MainDestination.SETTINGS.route) {
                        SettingsScreen(
                            appearance = state.appearance,
                            onIntent = onIntent,
                        )
                    }
                }
            }
        }
    }
}
