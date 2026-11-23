package app.wakil.agent.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.domain.rules.ActiveProjectsCheck
import app.wakil.agent.domain.rules.LifeRules
import app.wakil.agent.domain.rules.PriorityItem
import app.wakil.agent.domain.rules.ProjectRuleInput
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskRuleInput
import app.wakil.agent.domain.rules.TaskStatus
import app.wakil.agent.tools.AgentTools
import app.wakil.agent.tools.DailyBriefing
import app.wakil.agent.tools.DailyBriefingData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CommandCenterUiState(
    val topPriorities: List<PriorityItem> = emptyList(),
    val overdueTasks: List<TaskEntity> = emptyList(),
    val activeProjects: List<ProjectEntity> = emptyList(),
    val activeProjectsCheck: ActiveProjectsCheck? = null,
    val nextActionsMap: Map<String, Boolean> = emptyMap(),
    val dailyBriefing: DailyBriefingData? = null,
    val quickCaptureText: String = "",
    val isLoading: Boolean = false
)

class CommandCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WakilDatabase.getInstance(application)
    private val settingsStore = SettingsStore(application)
    private val agentTools = AgentTools(database, settingsStore)

    private val _uiState = MutableStateFlow(CommandCenterUiState())
    val uiState: StateFlow<CommandCenterUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            combine(
                database.taskDao().getAllTasksFlow(),
                database.projectDao().getAllProjectsFlow()
            ) { tasks, projects ->
                val now = System.currentTimeMillis()
                val taskInputs = tasks.map {
                    TaskRuleInput(it.id, it.projectId, it.title, it.status, it.priority, it.dueDate, it.isDeleted)
                }
                val projectInputs = projects.map {
                    ProjectRuleInput(it.id, it.title, it.status, it.nextAction, it.updatedAt, it.isDeleted)
                }

                val activeProjects = projects.filter { it.status == ProjectStatus.ACTIVE && !it.isDeleted }
                val activeInputs = projectInputs.filter { it.status == ProjectStatus.ACTIVE && !it.isDeleted }

                val top3 = LifeRules.calculateTop3Priorities(taskInputs, activeInputs, now)
                val overdue = tasks.filter {
                    !it.isDeleted && it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED && it.dueDate != null && it.dueDate < now
                }
                val check = LifeRules.checkActiveProjectsLimit(projectInputs, settingsStore.maxActiveProjects())
                val nextActions = LifeRules.validateActiveProjectsNextActions(activeInputs)
                val briefing = DailyBriefing.generate(database)

                _uiState.value.copy(
                    topPriorities = top3,
                    overdueTasks = overdue,
                    activeProjects = activeProjects,
                    activeProjectsCheck = check,
                    nextActionsMap = nextActions,
                    dailyBriefing = briefing
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    fun onQuickCaptureChange(text: String) {
        _uiState.value = _uiState.value.copy(quickCaptureText = text)
    }

    fun submitQuickCapture() {
        val text = _uiState.value.quickCaptureText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            agentTools.executeCreateTask(
                title = text,
                reason = "Quick capture from Command Center"
            )
            _uiState.value = _uiState.value.copy(quickCaptureText = "")
        }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            val nextStatus = if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
            database.taskDao().updateTask(task.copy(status = nextStatus, updatedAt = System.currentTimeMillis()))
        }
    }
}
