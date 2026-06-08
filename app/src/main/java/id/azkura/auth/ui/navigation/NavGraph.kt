package id.azkura.auth.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
    startDestination: String = Screen.Lock.route,
) {
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
            HomeScreen(
                onNavigateToAdd = { navController.navigate(Screen.AddAccount.route) },
                onNavigateToEdit = { id -> navController.navigate(Screen.EditAccount.createRoute(id)) },
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
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
