package app.wakil.agent

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.ui.chat.ChatScreen
import app.wakil.agent.ui.chat.ChatViewModel
import app.wakil.agent.ui.home.CommandCenterScreen
import app.wakil.agent.ui.home.CommandCenterViewModel
import app.wakil.agent.ui.navigation.BottomNavBar
import app.wakil.agent.ui.navigation.NavRoute
import app.wakil.agent.ui.projects.ProjectsScreen
import app.wakil.agent.ui.projects.ProjectsViewModel
import app.wakil.agent.ui.settings.SettingsScreen
import app.wakil.agent.ui.settings.SettingsViewModel
import app.wakil.agent.ui.theme.WakilTheme

class WakilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore(this).applyStoredLanguage()
    }
}

class MainActivity : AppCompatActivity() {
    private val homeVm: CommandCenterViewModel by viewModels()
    private val projectsVm: ProjectsViewModel by viewModels()
    private val chatVm: ChatViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WakilTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BottomNavBar(navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoute.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavRoute.Home.route) {
                            CommandCenterScreen(homeVm)
                        }
                        composable(NavRoute.Projects.route) {
                            ProjectsScreen(projectsVm)
                        }
                        composable(NavRoute.Chat.route) {
                            ChatScreen(chatVm)
                        }
                        composable(NavRoute.Settings.route) {
                            SettingsScreen(settingsVm)
                        }
                    }
                }
            }
        }
    }
}
