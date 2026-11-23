package app.wakil.agent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.wakil.agent.R

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        Triple(NavRoute.Home.route, R.string.nav_home, Icons.Default.Home),
        Triple(NavRoute.Projects.route, R.string.nav_projects, Icons.Default.List),
        Triple(NavRoute.Chat.route, R.string.nav_chat, Icons.Default.Chat),
        Triple(NavRoute.Settings.route, R.string.nav_settings, Icons.Default.Settings)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    NavigationBar {
        items.forEach { (route, labelRes, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(text = stringResource(labelRes)) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(NavRoute.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
