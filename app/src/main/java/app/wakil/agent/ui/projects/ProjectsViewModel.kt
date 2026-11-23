package app.wakil.agent.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.GoalEntity
import app.wakil.agent.data.local.entities.NoteEntity
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class ProjectsUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val selectedTab: Int = 0, // 0: Projects, 1: Tasks, 2: Goals, 3: Notes
    val showAddDialog: Boolean = false
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WakilDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                database.projectDao().getAllProjectsFlow(),
                database.taskDao().getAllTasksFlow(),
                database.goalDao().getAllGoalsFlow(),
                database.noteDao().getAllNotesFlow()
            ) { projects, tasks, goals, notes ->
                _uiState.value.copy(
                    projects = projects,
                    tasks = tasks,
                    goals = goals,
                    notes = notes
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun toggleAddDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddDialog = show)
    }

    fun addProject(title: String, description: String, nextAction: String, status: ProjectStatus = ProjectStatus.ACTIVE) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.projectDao().insertProject(
                ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = description.trim(),
                    nextAction = nextAction.trim().takeIf { it.isNotEmpty() },
                    status = status
                )
            )
            toggleAddDialog(false)
        }
    }

    fun addTask(title: String, description: String, priority: TaskPriority = TaskPriority.MEDIUM) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.taskDao().insertTask(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = description.trim(),
                    priority = priority
                )
            )
            toggleAddDialog(false)
        }
    }

    fun addGoal(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.goalDao().insertGoal(
                GoalEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = description.trim()
                )
            )
            toggleAddDialog(false)
        }
    }

    fun addNote(title: String, content: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.noteDao().insertNote(
                NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    content = content.trim()
                )
            )
            toggleAddDialog(false)
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val next = if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
            database.taskDao().updateTask(task.copy(status = next, updatedAt = System.currentTimeMillis()))
        }
    }
}
