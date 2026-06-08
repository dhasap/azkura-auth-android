package id.azkura.auth.ui.navigation

sealed class Screen(val route: String) {
    data object Lock : Screen("lock")
    data object Home : Screen("home")
    data object AddAccount : Screen("add_account")
    data object EditAccount : Screen("edit_account/{accountId}") {
        fun createRoute(accountId: String) = "edit_account/$accountId"
    }
    data object Scanner : Screen("scanner")
    data object Settings : Screen("settings")
    data object Statistics : Screen("statistics")
    data object Folders : Screen("folders")
    data object Backup : Screen("backup")
    data object About : Screen("about")
}
