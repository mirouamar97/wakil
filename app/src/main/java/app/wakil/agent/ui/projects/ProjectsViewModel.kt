package app.wakil.agent.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.GoalEntity
import app.wakil.agent.data.local.entities.MilestoneEntity
import app.wakil.agent.data.local.entities.NoteEntity
import app.wakil.agent.data.local.entities.NoteLinkEntity
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
import app.wakil.agent.tools.ActionLogFactory
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
    val noteLinks: List<NoteLinkEntity> = emptyList(),
    val milestones: List<MilestoneEntity> = emptyList(),
    val selectedTab: Int = 0,
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
                database.noteDao().getAllNotesFlow(),
                database.projectDao().getAllMilestonesFlow()
            ) { projects, tasks, goals, notes, milestones ->
                _uiState.value.copy(
                    projects = projects, tasks = tasks, goals = goals, notes = notes, milestones = milestones
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
        viewModelScope.launch {
            database.noteDao().getAllNoteLinksFlow().collect { links ->
                _uiState.value = _uiState.value.copy(noteLinks = links)
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex.coerceIn(0, 3))
    }

    fun toggleAddDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddDialog = show)
    }

    fun addProject(
        title: String,
        description: String,
        nextAction: String,
        status: ProjectStatus = ProjectStatus.ACTIVE,
        goalId: String? = null,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (goalId != null && database.goalDao().getGoalById(goalId)?.isDeleted != false) return@launch
            val project = ProjectEntity(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                title = title.trim(),
                description = description.trim(),
                nextAction = nextAction.trim().takeIf { it.isNotEmpty() },
                status = status
            )
            insertWithLog("PROJECT", project.id, project.title) {
                database.projectDao().insertProject(project)
            }
            toggleAddDialog(false)
        }
    }

    fun addTask(
        title: String,
        description: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        projectId: String? = null,
        goalId: String? = null,
        dueDate: Long? = null,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (projectId != null && database.projectDao().getProjectById(projectId)?.isDeleted != false) return@launch
            if (goalId != null && database.goalDao().getGoalById(goalId)?.isDeleted != false) return@launch
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                goalId = goalId,
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                dueDate = dueDate,
            )
            insertWithLog("TASK", task.id, task.title) {
                database.taskDao().insertTask(task)
            }
            toggleAddDialog(false)
        }
    }

    fun addGoal(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val goal = GoalEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim()
            )
            insertWithLog("GOAL", goal.id, goal.title) {
                database.goalDao().insertGoal(goal)
            }
            toggleAddDialog(false)
        }
    }

    fun addNote(title: String, content: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val note = NoteEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                content = content.trim()
            )
            insertWithLog("NOTE", note.id, note.title) {
                database.noteDao().insertNote(note)
            }
            toggleAddDialog(false)
        }
    }

    fun addNoteLink(noteId: String, targetType: String, targetId: String) {
        viewModelScope.launch {
            database.withTransaction {
                val note = database.noteDao().getNoteById(noteId)?.takeUnless { it.isDeleted }
                    ?: return@withTransaction
                val targetExists = when (targetType) {
                    "GOAL" -> database.goalDao().getGoalById(targetId)?.isDeleted == false
                    "PROJECT" -> database.projectDao().getProjectById(targetId)?.isDeleted == false
                    "TASK" -> database.taskDao().getTaskById(targetId)?.isDeleted == false
                    else -> false
                }
                if (!targetExists) return@withTransaction
                val link = NoteLinkEntity(
                    noteId = noteId,
                    targetType = targetType,
                    targetId = targetId,
                )
                val alreadyLinked = database.noteDao().getLinksForNote(noteId)
                    .any { it.targetType == targetType && it.targetId == targetId }
                if (alreadyLinked) return@withTransaction
                database.noteDao().insertNoteLink(link)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.created("NOTE_LINK", link.id, note.title)
                        .copy(undoDataJson = null, canUndo = false)
                )
            }
        }
    }

    fun deleteNoteLink(linkId: String) {
        viewModelScope.launch {
            database.withTransaction {
                val link = database.noteDao().getAllNoteLinks().firstOrNull { it.id == linkId }
                    ?: return@withTransaction
                val noteTitle = database.noteDao().getNoteById(link.noteId)?.title.orEmpty()
                database.noteDao().deleteNoteLink(linkId)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.deleted("NOTE_LINK", linkId, noteTitle)
                        .copy(undoDataJson = null, canUndo = false)
                )
            }
        }
    }

    fun addMilestone(projectId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val project = database.projectDao().getProjectById(projectId) ?: return@withTransaction
                if (project.isDeleted) return@withTransaction
                val milestone = MilestoneEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = title.trim(),
                    orderIndex = database.projectDao().nextMilestoneOrderIndex(projectId),
                )
                database.projectDao().insertMilestone(milestone)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.created("MILESTONE", milestone.id, milestone.title)
                )
            }
        }
    }

    fun updateMilestone(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val current = database.projectDao().getAllMilestones().firstOrNull { it.id == id }
                    ?: return@withTransaction
                val updated = current.copy(title = title.trim(), updatedAt = System.currentTimeMillis())
                database.projectDao().updateMilestone(updated)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated("MILESTONE", id, updated.title, mapOf("title" to current.title))
                )
            }
        }
    }

    fun deleteMilestone(id: String) {
        viewModelScope.launch {
            database.withTransaction {
                val current = database.projectDao().getAllMilestones().firstOrNull { it.id == id }
                    ?: return@withTransaction
                database.projectDao().deleteMilestone(id)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.deleted("MILESTONE", id, current.title)
                        .copy(undoDataJson = null, canUndo = false)
                )
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            database.withTransaction {
                val current = database.taskDao().getTaskById(task.id) ?: return@withTransaction
                val next = if (current.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
                database.taskDao().updateTask(current.copy(status = next, updatedAt = System.currentTimeMillis()))
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.taskStatusChanged(current.id, current.title, current.status)
                )
            }
        }
    }

    fun updateProject(id: String, title: String, description: String, nextAction: String) {
        updateProject(id, title, description, nextAction, goalId = null, preserveGoal = true)
    }

    fun updateProject(
        id: String,
        title: String,
        description: String,
        nextAction: String,
        goalId: String?,
        preserveGoal: Boolean = false,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val current = database.projectDao().getProjectById(id) ?: return@withTransaction
                if (current.isDeleted) return@withTransaction
                if (!preserveGoal && goalId != null && database.goalDao().getGoalById(goalId)?.isDeleted != false) return@withTransaction
                val updated = current.copy(
                    title = title.trim(),
                    description = description.trim(),
                    goalId = if (preserveGoal) current.goalId else goalId,
                    nextAction = nextAction.trim().takeIf { it.isNotEmpty() },
                    updatedAt = System.currentTimeMillis()
                )
                database.projectDao().updateProject(updated)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated(
                        "PROJECT", id, updated.title,
                        mapOf("title" to current.title, "description" to current.description,
                            "nextAction" to current.nextAction, "goalId" to current.goalId)
                    )
                )
            }
        }
    }

    fun changeProjectStatus(id: String, status: ProjectStatus) {
        viewModelScope.launch {
            database.withTransaction {
                val current = database.projectDao().getProjectById(id) ?: return@withTransaction
                if (current.isDeleted || current.status == status) return@withTransaction
                database.projectDao().updateProject(current.copy(status = status, updatedAt = System.currentTimeMillis()))
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated(
                        "PROJECT", id, current.title, mapOf("status" to current.status.name)
                    )
                )
            }
        }
    }

    fun updateTask(id: String, title: String, description: String) {
        updateTask(id, title, description, null, null, null, preserveRelations = true)
    }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        projectId: String?,
        goalId: String?,
        dueDate: Long?,
        priority: TaskPriority = TaskPriority.MEDIUM,
        preserveRelations: Boolean = false,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val current = database.taskDao().getTaskById(id) ?: return@withTransaction
                if (current.isDeleted) return@withTransaction
                if (!preserveRelations && projectId != null && database.projectDao().getProjectById(projectId)?.isDeleted != false) return@withTransaction
                if (!preserveRelations && goalId != null && database.goalDao().getGoalById(goalId)?.isDeleted != false) return@withTransaction
                val updated = current.copy(
                    title = title.trim(),
                    description = description.trim(),
                    projectId = if (preserveRelations) current.projectId else projectId,
                    goalId = if (preserveRelations) current.goalId else goalId,
                    dueDate = if (preserveRelations) current.dueDate else dueDate,
                    priority = if (preserveRelations) current.priority else priority,
                    updatedAt = System.currentTimeMillis()
                )
                database.taskDao().updateTask(updated)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated(
                        "TASK", id, updated.title,
                        mapOf(
                            "title" to current.title,
                            "description" to current.description,
                            "projectId" to current.projectId,
                            "goalId" to current.goalId,
                            "dueDate" to current.dueDate?.toString(),
                            "priority" to current.priority.name,
                        )
                    )
                )
            }
        }
    }

    fun updateGoal(id: String, title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val current = database.goalDao().getGoalById(id) ?: return@withTransaction
                if (current.isDeleted) return@withTransaction
                val updated = current.copy(title = title.trim(), description = description.trim(), updatedAt = System.currentTimeMillis())
                database.goalDao().updateGoal(updated)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated("GOAL", id, updated.title, mapOf("title" to current.title, "description" to current.description))
                )
            }
        }
    }

    fun updateNote(id: String, title: String, content: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            database.withTransaction {
                val current = database.noteDao().getNoteById(id) ?: return@withTransaction
                if (current.isDeleted) return@withTransaction
                val updated = current.copy(title = title.trim(), content = content.trim(), updatedAt = System.currentTimeMillis())
                database.noteDao().updateNote(updated)
                database.actionLogDao().insertActionLog(
                    ActionLogFactory.fieldsUpdated("NOTE", id, updated.title, mapOf("title" to current.title, "content" to current.content))
                )
            }
        }
    }

    fun deleteRecord(recordType: String, id: String) {
        viewModelScope.launch {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val record = when (recordType) {
                    "PROJECT" -> database.projectDao().getProjectById(id)?.takeUnless { it.isDeleted }?.let {
                        database.projectDao().updateProject(it.copy(isDeleted = true, updatedAt = now))
                        it.title
                    }
                    "TASK" -> database.taskDao().getTaskById(id)?.takeUnless { it.isDeleted }?.let {
                        database.taskDao().updateTask(it.copy(isDeleted = true, updatedAt = now))
                        it.title
                    }
                    "GOAL" -> database.goalDao().getGoalById(id)?.takeUnless { it.isDeleted }?.let {
                        database.goalDao().updateGoal(it.copy(isDeleted = true, updatedAt = now))
                        it.title
                    }
                    "NOTE" -> database.noteDao().getNoteById(id)?.takeUnless { it.isDeleted }?.let {
                        database.noteDao().updateNote(it.copy(isDeleted = true, updatedAt = now))
                        it.title
                    }
                    else -> null
                } ?: return@withTransaction
                database.actionLogDao().insertActionLog(ActionLogFactory.deleted(recordType, id, record))
            }
        }
    }

    private suspend fun insertWithLog(
        recordType: String,
        id: String,
        title: String,
        insert: suspend () -> Unit,
    ) {
        database.withTransaction {
            insert()
            database.actionLogDao().insertActionLog(ActionLogFactory.created(recordType, id, title))
        }
    }
}
