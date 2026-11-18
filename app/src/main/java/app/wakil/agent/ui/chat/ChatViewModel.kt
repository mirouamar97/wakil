package app.wakil.agent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakil.agent.data.ProviderCatalog
import app.wakil.agent.data.SecureKeyStore
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.router.AttemptLog
import app.wakil.agent.router.ChatMessage
import app.wakil.agent.router.HttpChatTransport
import app.wakil.agent.router.ModelRouter
import app.wakil.agent.router.ProviderStatus
import app.wakil.agent.router.Role
import app.wakil.agent.router.RouterResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiMessage(val id: Long, val role: Role, val text: String, val providerId: String? = null)

sealed interface ChatError {
    data object NoKeys : ChatError
    data class AllFailed(val attempts: List<AttemptLog>) : ChatError
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val keyStore = SecureKeyStore(app)
    val settings = SettingsStore(app)

    private val router = ModelRouter(
        providers = { ProviderCatalog.providers(settings::model) },
        keysFor = keyStore::keys,
        transport = HttpChatTransport(),
    )

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<ChatError?>(null)
    val error: StateFlow<ChatError?> = _error.asStateFlow()

    private val _status = MutableStateFlow<List<ProviderStatus>>(router.status())
    val status: StateFlow<List<ProviderStatus>> = _status.asStateFlow()

    private var nextId = 1L

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _sending.value) return
        _messages.value = _messages.value + UiMessage(nextId++, Role.USER, clean)
        _error.value = null
        _sending.value = true
        viewModelScope.launch {
            val history = listOf(ChatMessage(Role.SYSTEM, SYSTEM_PROMPT)) +
                _messages.value.map { ChatMessage(it.role, it.text) }
            when (val result = router.chat(history)) {
                is RouterResult.Success ->
                    _messages.value = _messages.value + UiMessage(nextId++, Role.ASSISTANT, result.text, result.providerId)
                is RouterResult.Failure ->
                    _error.value = if (result.noKeys) ChatError.NoKeys else ChatError.AllFailed(result.attempts)
            }
            _status.value = router.status()
            _sending.value = false
        }
    }

    // --- Settings ---
    fun keyCount(providerId: String): Int = keyStore.keys(providerId).size

    fun addKey(providerId: String, key: String) {
        keyStore.add(providerId, key)
        router.clearRejectedKeys()
        _status.value = router.status()
    }

    fun clearKeys(providerId: String) {
        keyStore.clear(providerId)
        _status.value = router.status()
    }

    fun model(providerId: String): String =
        settings.model(providerId)
            ?: ProviderCatalog.entries.first { it.id == providerId }.defaultModel

    fun setModel(providerId: String, model: String) = settings.setModel(providerId, model)

    fun refreshStatus() {
        _status.value = router.status()
    }

    private companion object {
        // Instruction for the model, not UI text.
        const val SYSTEM_PROMPT =
            "You are Wakil, a helpful assistant. Reply in the language of the user's last message " +
                "(Arabic including Algerian Darija, French, or English). Be concise and honest; say when you do not know."
    }
}
