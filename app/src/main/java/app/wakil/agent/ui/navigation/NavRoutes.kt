package app.wakil.agent.ui.navigation

sealed class NavRoute(val route: String) {
    object Home : NavRoute("home")
    object Projects : NavRoute("projects")
    object Chat : NavRoute("chat")
    object Settings : NavRoute("settings")
}
