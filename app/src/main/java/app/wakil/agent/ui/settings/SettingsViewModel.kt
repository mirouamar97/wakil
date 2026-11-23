package app.wakil.agent.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakil.agent.data.ProviderCatalog
import app.wakil.agent.data.SecureKeyStore
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.backup.BackupManager
import app.wakil.agent.data.local.backup.ImportPreview
import app.wakil.agent.data.local.entities.ActionLogEntity
import app.wakil.agent.data.local.entities.UserMemoryEntity
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.tools.AgentTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentLanguage: String = "ar",
    val isKillSwitchActive: Boolean = false,
    val maxActiveProjects: Int = 3,
    val providerKeyCounts: Map<String, Int> = emptyMap(),
    val actionLogs: List<ActionLogEntity> = emptyList(),
    val memories: List<UserMemoryEntity> = emptyList(),
    val importPreview: ImportPreview? = null,
    val pendingJsonToImport: String? = null,
    val statusMessage: String? = null,
    val cloudflareAccountId: String = "",
    val cloudflareAccountIdInvalid: Boolean = false,
    val providerPriority: List<String> = ProviderCatalog.defaultPriority,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WakilDatabase.getInstance(application)
    private val keyStore = SecureKeyStore(application)
    private val settingsStore = SettingsStore(application)
    private val backupManager = BackupManager(database)
    private val agentTools = AgentTools(database, settingsStore)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            database.actionLogDao().getRecentActionLogsFlow().collect { logs ->
                _uiState.value = _uiState.value.copy(actionLogs = logs)
            }
        }
        viewModelScope.launch {
            database.userMemoryDao().getAllMemoriesFlow().collect { memories ->
                _uiState.value = _uiState.value.copy(memories = memories)
            }
        }
    }

    private fun loadSettings() {
        val counts = ProviderCatalog.entries.associate { entry ->
            entry.id to keyStore.keys(entry.id).size
        }
        _uiState.value = _uiState.value.copy(
            currentLanguage = settingsStore.language(),
            isKillSwitchActive = settingsStore.isKillSwitchActive(),
            maxActiveProjects = settingsStore.maxActiveProjects(),
            providerKeyCounts = counts,
            cloudflareAccountId = settingsStore.cloudflareAccountId().orEmpty(),
            cloudflareAccountIdInvalid = false,
            providerPriority = settingsStore.providerPriority(),
        )
    }

    fun setLanguage(lang: String) {
        settingsStore.setLanguage(lang)
        _uiState.value = _uiState.value.copy(currentLanguage = lang)
    }

    fun toggleKillSwitch(active: Boolean) {
        settingsStore.setKillSwitch(active)
        _uiState.value = _uiState.value.copy(isKillSwitchActive = active)
    }

    fun setMaxActiveProjects(max: Int) {
        settingsStore.setMaxActiveProjects(max)
        _uiState.value = _uiState.value.copy(maxActiveProjects = max)
    }

    fun updateCloudflareAccountId(id: String) {
        _uiState.value = _uiState.value.copy(cloudflareAccountId = id, cloudflareAccountIdInvalid = false)
    }

    fun saveCloudflareAccountId() {
        val id = _uiState.value.cloudflareAccountId
        val saved = settingsStore.setCloudflareAccountId(id)
        _uiState.value = _uiState.value.copy(
            cloudflareAccountIdInvalid = !saved,
            cloudflareAccountId = if (saved) settingsStore.cloudflareAccountId().orEmpty() else id,
        )
    }

    fun moveProvider(providerId: String, offset: Int) {
        val order = ProviderCatalog.normalizePriority(_uiState.value.providerPriority)
        val from = order.indexOf(providerId)
        val to = from + offset
        if (from < 0 || to !in order.indices) return
        val reordered = order.toMutableList().apply {
            add(to, removeAt(from))
        }
        settingsStore.setProviderPriority(reordered)
        _uiState.value = _uiState.value.copy(providerPriority = reordered)
    }

    fun addApiKey(providerId: String, key: String) {
        if (key.isBlank()) return
        keyStore.add(providerId, key)
        loadSettings()
    }

    fun clearApiKeys(providerId: String) {
        keyStore.clear(providerId)
        loadSettings()
    }

    fun undoLastAction() {
        viewModelScope.launch {
            agentTools.undoLastAction()
        }
    }

    fun proposeMemory(factText: String) {
        val text = factText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            database.userMemoryDao().insertMemory(
                UserMemoryEntity(factText = text, source = "user", approvalStatus = ApprovalStatus.PENDING)
            )
        }
    }

    fun approveMemory(memory: UserMemoryEntity) {
        viewModelScope.launch {
            database.userMemoryDao().updateMemory(
                memory.copy(approvalStatus = ApprovalStatus.APPROVED, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { database.userMemoryDao().deleteMemory(id) }
    }

    suspend fun getExportJson(): String = backupManager.exportToJson()

    fun inspectImportJson(jsonContent: String) {
        viewModelScope.launch {
            val preview = backupManager.inspectBackup(jsonContent)
            _uiState.value = _uiState.value.copy(
                importPreview = preview,
                pendingJsonToImport = if (preview.isValid) jsonContent else null,
            )
        }
    }

    fun confirmImport() {
        val json = _uiState.value.pendingJsonToImport ?: return
        viewModelScope.launch {
            val result = backupManager.importFromJson(json)
            val message = if (result.isSuccess) {
                getApplication<Application>().getString(
                    app.wakil.agent.R.string.backup_import_result,
                    result.importedCount,
                    result.preservedConflictCount
                )
            } else {
                getApplication<Application>().getString(app.wakil.agent.R.string.backup_import_failed)
            }
            _uiState.value = _uiState.value.copy(
                importPreview = null,
                pendingJsonToImport = null,
                statusMessage = message,
            )
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(importPreview = null, pendingJsonToImport = null)
    }

    fun setStatusMessage(message: String?) {
        _uiState.value = _uiState.value.copy(statusMessage = message)
    }

    fun backupExported() {
        _uiState.value = _uiState.value.copy(
            statusMessage = getApplication<Application>().getString(app.wakil.agent.R.string.backup_export_success)
        )
    }
}
