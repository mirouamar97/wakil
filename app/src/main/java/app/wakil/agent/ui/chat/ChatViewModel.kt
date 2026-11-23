package app.wakil.agent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakil.agent.data.ProviderCatalog
import app.wakil.agent.data.SecureKeyStore
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.ConversationEntity
import app.wakil.agent.data.local.entities.MessageEntity
import app.wakil.agent.router.ChatMessage
import app.wakil.agent.router.HttpChatTransport
import app.wakil.agent.router.ModelRouter
import app.wakil.agent.router.Role
import app.wakil.agent.router.RouterResult
import app.wakil.agent.tools.AgentTools
import app.wakil.agent.tools.ToolExecutionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val currentStreamingChunk: String = "",
    val pendingApproval: ToolExecutionResult.RequiresApproval? = null,
    val lastError: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WakilDatabase.getInstance(application)
    private val keyStore = SecureKeyStore(application)
    private val settingsStore = SettingsStore(application)
    private val agentTools = AgentTools(database, settingsStore)

    private val transport = HttpChatTransport()
    private val router = ModelRouter(
        providers = { ProviderCatalog.providers(settingsStore) },
        keysFor = { keyStore.keys(it) },
        transport = transport,
        isKillSwitchActive = { settingsStore.isKillSwitchActive() },
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null
    private var loadedConversationId: String? = null

    init {
        viewModelScope.launch {
            database.conversationDao().getAllConversationsFlow().collect { convs ->
                val currentId = _uiState.value.currentConversationId
                    ?.takeIf { id -> convs.any { it.id == id } }
                    ?: convs.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    conversations = convs,
                    currentConversationId = currentId
                )
                if (currentId == null && convs.isEmpty()) {
                    startNewConversation()
                } else if (currentId != null && currentId != loadedConversationId) {
                    loadMessages(currentId)
                }
            }
        }
    }

    fun startNewConversation() {
        val id = UUID.randomUUID().toString()
        val convTitle = getApplication<Application>().getString(app.wakil.agent.R.string.new_chat)
        val conv = ConversationEntity(id = id, title = convTitle)
        loadedConversationId = id
        _uiState.value = _uiState.value.copy(currentConversationId = id, messages = emptyList())
        viewModelScope.launch {
            database.conversationDao().insertConversation(conv)
            loadMessages(id)
        }
    }

    fun selectConversation(id: String) {
        if (_uiState.value.currentConversationId == id) return
        loadedConversationId = id
        _uiState.value = _uiState.value.copy(currentConversationId = id, messages = emptyList())
        loadMessages(id)
    }

    private fun loadMessages(conversationId: String) {
        messagesJob?.cancel()
        loadedConversationId = conversationId
        messagesJob = viewModelScope.launch {
            database.conversationDao().getMessagesForConversationFlow(conversationId).collect { msgs ->
                if (_uiState.value.currentConversationId == conversationId) {
                    _uiState.value = _uiState.value.copy(messages = msgs)
                }
            }
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        val convId = _uiState.value.currentConversationId ?: return

        viewModelScope.launch {
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "user",
                content = text
            )
            database.conversationDao().insertMessage(userMsg)
            val conversation = database.conversationDao().getConversationById(convId)
            if (conversation != null) {
                val firstUserMessage = database.conversationDao()
                    .getMessagesForConversation(convId)
                    .count { it.role == "user" } == 1
                database.conversationDao().insertConversation(
                    conversation.copy(
                        title = if (firstUserMessage) text.take(48) else conversation.title,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.value = _uiState.value.copy(inputText = "", isStreaming = true, currentStreamingChunk = "")

            val history = database.conversationDao().getMessagesForConversation(convId)
                .filter { it.status == "SENT" }
                .map {
                val role = when (it.role) {
                    "assistant" -> Role.ASSISTANT
                    "system" -> Role.SYSTEM
                    else -> Role.USER
                }
                ChatMessage(role, it.content)
            }

            val assistantMsgId = UUID.randomUUID().toString()
            val streamedAccumulator = StringBuilder()

            streamJob = launch {
                val result = router.chatStream(
                    messages = history,
                    onChunk = { chunk ->
                        streamedAccumulator.append(chunk)
                        _uiState.value = _uiState.value.copy(currentStreamingChunk = streamedAccumulator.toString())
                    },
                    onAttemptReset = {
                        streamedAccumulator.setLength(0)
                        _uiState.value = _uiState.value.copy(currentStreamingChunk = "")
                    },
                )

                when (result) {
                    is RouterResult.Success -> {
                        val replyText = if (streamedAccumulator.isNotEmpty()) streamedAccumulator.toString() else result.text
                        val assistantMsg = MessageEntity(
                            id = assistantMsgId,
                            conversationId = convId,
                            role = "assistant",
                            content = replyText,
                            providerId = result.providerId,
                            model = result.model
                        )
                        database.conversationDao().insertMessage(assistantMsg)
                    }
                    is RouterResult.Failure -> {
                        val errorRes = if (result.noKeys) {
                            app.wakil.agent.R.string.no_provider_keys
                        } else {
                            app.wakil.agent.R.string.all_providers_failed
                        }
                        val errText = getApplication<Application>().getString(errorRes)
                        val assistantMsg = MessageEntity(
                            id = assistantMsgId,
                            conversationId = convId,
                            role = "assistant",
                            content = errText,
                            status = "FAILED"
                        )
                        database.conversationDao().insertMessage(assistantMsg)
                    }
                }
                _uiState.value = _uiState.value.copy(isStreaming = false, currentStreamingChunk = "")
            }
        }
    }

    fun stopGenerating() {
        streamJob?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = false)
    }

    fun confirmPendingAction() {
        viewModelScope.launch {
            _uiState.value.pendingApproval?.onConfirm?.invoke()
            _uiState.value = _uiState.value.copy(pendingApproval = null)
        }
    }

    fun dismissPendingAction() {
        _uiState.value = _uiState.value.copy(pendingApproval = null)
    }
}
