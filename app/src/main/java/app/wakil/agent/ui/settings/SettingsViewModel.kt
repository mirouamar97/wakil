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
    val importPreview: ImportPreview? = null,
    val pendingJsonToImport: String? = null,
    val statusMessage: String? = null
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
    }

    private fun loadSettings() {
        val counts = ProviderCatalog.entries.associate {
            it.id to keyStore.keys(it.id).size
        }
        _uiState.value = _uiState.value.copy(
            currentLanguage = settingsStore.language(),
            isKillSwitchActive = settingsStore.isKillSwitchActive(),
            maxActiveProjects = settingsStore.maxActiveProjects(),
            providerKeyCounts = counts
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

    suspend fun getExportJson(): String {
        return backupManager.exportToJson()
    }

    fun inspectImportJson(jsonContent: String) {
        val preview = backupManager.inspectBackup(jsonContent)
        _uiState.value = _uiState.value.copy(
            importPreview = preview,
            pendingJsonToImport = if (preview.isValid) jsonContent else null
        )
    }

    fun confirmImport() {
        val json = _uiState.value.pendingJsonToImport ?: return
        viewModelScope.launch {
            backupManager.importFromJson(json)
            _uiState.value = _uiState.value.copy(importPreview = null, pendingJsonToImport = null)
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(importPreview = null, pendingJsonToImport = null)
    }
}
