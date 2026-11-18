package app.wakil.agent

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.ui.chat.ChatScreen
import app.wakil.agent.ui.chat.ChatViewModel
import app.wakil.agent.ui.settings.SettingsScreen
import app.wakil.agent.ui.theme.WakilTheme

class WakilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore(this).applyStoredLanguage()
    }
}

class MainActivity : AppCompatActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WakilTheme {
                var inSettings by rememberSaveable { mutableStateOf(false) }
                if (inSettings) {
                    BackHandler { inSettings = false }
                    SettingsScreen(vm, onBack = { inSettings = false })
                } else {
                    ChatScreen(vm, onOpenSettings = { inSettings = true })
                }
            }
        }
    }
}
