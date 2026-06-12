package id.azkura.auth.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.ui.screens.addaccount.AddAccountScreen
import id.azkura.auth.ui.screens.editaccount.EditAccountScreen
import id.azkura.auth.ui.screens.home.HomeScreen
import id.azkura.auth.ui.screens.lock.LockScreen
import id.azkura.auth.ui.screens.scanner.ScannerScreen
import id.azkura.auth.ui.screens.settings.SettingsScreen
import id.azkura.auth.ui.screens.statistics.StatisticsScreen

@Composable
fun AzkuraNavGraph(
    navController: NavHostController,
    vaultManager: VaultManager,
    startDestination: String = Screen.Lock.route,
    pendingOtpauthUri: String? = null,
    onPendingOtpauthUriConsumed: () -> Unit = {},
) {
    // Observe vault lock state — navigate back to Lock screen on warm resume
    val isLocked by vaultManager.isLocked.collectAsState()
    LaunchedEffect(isLocked) {
        if (isLocked) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null && currentRoute != Screen.Lock.route) {
                navController.navigate(Screen.Lock.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Lock.route) {
            LockScreen(
                onUnlocked = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            // Only consume deeplink after vault is unlocked (we're on Home = unlocked)
            LaunchedEffect(pendingOtpauthUri) {
                if (!pendingOtpauthUri.isNullOrBlank() && !vaultManager.isLocked.value) {
                    navController.navigate(Screen.AddAccount.route)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("scanned_uri", pendingOtpauthUri)
                    onPendingOtpauthUriConsumed()
                }
            }

            HomeScreen(
                onNavigateToAdd = { navController.navigate(Screen.AddAccount.route) },
                onNavigateToEdit = { id -> navController.navigate(Screen.EditAccount.createRoute(id)) },
                // Put AddAccount under Scanner so the scanned URI is returned to
                // AddAccount's SavedStateHandle. Navigating directly to Scanner
                // from Home would return the value to Home, which does not
                // consume scanned_uri.
                onNavigateToScanner = {
                    navController.navigate(Screen.AddAccount.route)
                    navController.navigate(Screen.Scanner.route)
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToStats = { navController.navigate(Screen.Statistics.route) },
                onLock = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.AddAccount.route) {
            AddAccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
            )
        }

        composable(
            route = Screen.EditAccount.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            EditAccountScreen(
                accountId = accountId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Scanner.route) {
            ScannerScreen(
                onAccountScanned = { uri ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_uri", uri)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
