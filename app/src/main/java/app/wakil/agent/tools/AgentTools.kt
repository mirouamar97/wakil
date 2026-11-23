package app.wakil.agent.tools

import app.wakil.agent.data.SettingsStore
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.ActionLogEntity
import app.wakil.agent.data.local.entities.TaskEntity
import androidx.room.withTransaction
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
import org.json.JSONObject
import java.util.UUID

sealed interface ToolExecutionResult {
    data class Success(val message: String, val actionLogId: String) : ToolExecutionResult
    data class RequiresApproval(
        val pendingActionId: String,
        val actionType: String,
        val description: String,
        val onConfirm: suspend () -> Unit
    ) : ToolExecutionResult
    data class Blocked(val reason: String) : ToolExecutionResult
}

class AgentTools(
    private val database: WakilDatabase,
    private val settingsStore: SettingsStore
) {

    /**
     * Executes an AI tool call locally under strict permission rules and KillSwitch.
     */
    suspend fun executeCreateTask(
        title: String,
        projectId: String? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueDate: Long? = null,
        reason: String
    ): ToolExecutionResult {
        if (settingsStore.isKillSwitchActive()) {
            return ToolExecutionResult.Blocked("Kill switch is active")
        }
        if (title.isBlank()) return ToolExecutionResult.Blocked("Task title is required")

        val taskId = UUID.randomUUID().toString()
        val task = TaskEntity(
            id = taskId,
            projectId = projectId,
            title = title,
            priority = priority,
            dueDate = dueDate
        )

        val log = ActionLogFactory.created("TASK", taskId, title).copy(reason = reason)
        database.withTransaction {
            database.taskDao().insertTask(task)
            database.actionLogDao().insertActionLog(log)
        }

        return ToolExecutionResult.Success("Task created successfully", log.id)
    }

    suspend fun executeDeleteTask(
        taskId: String,
        reason: String,
        onConfirmed: suspend () -> Unit = {}
    ): ToolExecutionResult {
        if (settingsStore.isKillSwitchActive()) {
            return ToolExecutionResult.Blocked("Kill switch is active")
        }

        val existing = database.taskDao().getTaskById(taskId)
            ?: return ToolExecutionResult.Blocked("Task not found")

        // Hard security rule: Deletion ALWAYS requires user approval
        return ToolExecutionResult.RequiresApproval(
            pendingActionId = UUID.randomUUID().toString(),
            actionType = "DELETE",
            description = "Delete task after approval",
            onConfirm = confirm@{
                if (settingsStore.isKillSwitchActive()) return@confirm
                database.withTransaction {
                    val latest = database.taskDao().getTaskById(taskId) ?: return@withTransaction
                    if (latest.isDeleted) return@withTransaction
                    database.taskDao().updateTask(latest.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
                    database.actionLogDao().insertActionLog(
                        ActionLogEntity(
                            actionType = "DELETE",
                            recordType = "TASK",
                            recordId = taskId,
                            reason = reason,
                            result = latest.title,
                            permissionLevel = PermissionLevel.L1,
                            approvalStatus = ApprovalStatus.APPROVED,
                            undoDataJson = JSONObject()
                                .put("action", "RESTORE_TASK_DELETION")
                                .put("id", taskId)
                                .toString(),
                            canUndo = true,
                        )
                    )
                }
                onConfirmed()
            }
        )
    }

    suspend fun undoLastAction(): Boolean {
        return try {
            database.withTransaction {
                val lastAction = database.actionLogDao().getLastUndoableAction() ?: return@withTransaction false
                val undoJson = lastAction.undoDataJson ?: return@withTransaction false
                val json = JSONObject(undoJson)
                val action = json.optString("action")
                val entity = json.optString("entity")
                val id = json.optString("id")

                val applied = when {
                    action == "DELETE_CREATED" && entity == "TASK" ->
                        database.taskDao().getTaskById(id)?.let {
                            database.taskDao().updateTask(it.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
                            true
                        } ?: false
                    action == "DELETE_CREATED" && entity == "PROJECT" ->
                        database.projectDao().getProjectById(id)?.let {
                            database.projectDao().updateProject(it.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
                            true
                        } ?: false
                    action == "DELETE_CREATED" && entity == "GOAL" ->
                        database.goalDao().getGoalById(id)?.let {
                            database.goalDao().updateGoal(it.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
                            true
                        } ?: false
                    action == "DELETE_CREATED" && entity == "NOTE" ->
                        database.noteDao().getNoteById(id)?.let {
                            database.noteDao().updateNote(it.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
                            true
                        } ?: false
                    action == "DELETE_CREATED" && entity == "MILESTONE" -> {
                        database.projectDao().deleteMilestone(id)
                        true
                    }
                    action == "RESTORE_TASK_STATUS" -> {
                        val task = database.taskDao().getTaskById(id)
                        val status = runCatching { TaskStatus.valueOf(json.optString("status")) }.getOrNull()
                        if (task != null && status != null) {
                            database.taskDao().updateTask(task.copy(status = status, updatedAt = System.currentTimeMillis()))
                            true
                        } else false
                    }
                    action == "RESTORE_TASK_DELETION" -> {
                        val task = database.taskDao().getTaskById(id)
                        if (task != null) {
                            database.taskDao().updateTask(task.copy(isDeleted = false, updatedAt = System.currentTimeMillis()))
                            true
                        } else false
                    }
                    action == "RESTORE_DELETION" -> restoreDeletion(entity, id)
                    action == "RESTORE_RECORD_FIELDS" ->
                        restoreFields(entity, id, json.optJSONObject("before"))
                    else -> false
                }

                if (applied) database.actionLogDao().markUndone(lastAction.id)
                applied
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun restoreDeletion(entity: String, id: String): Boolean {
        val now = System.currentTimeMillis()
        return when (entity) {
            "TASK" -> database.taskDao().getTaskById(id)?.let {
                database.taskDao().updateTask(it.copy(isDeleted = false, updatedAt = now))
                true
            } ?: false
            "PROJECT" -> database.projectDao().getProjectById(id)?.let {
                database.projectDao().updateProject(it.copy(isDeleted = false, updatedAt = now))
                true
            } ?: false
            "GOAL" -> database.goalDao().getGoalById(id)?.let {
                database.goalDao().updateGoal(it.copy(isDeleted = false, updatedAt = now))
                true
            } ?: false
            "NOTE" -> database.noteDao().getNoteById(id)?.let {
                database.noteDao().updateNote(it.copy(isDeleted = false, updatedAt = now))
                true
            } ?: false
            else -> false
        }
    }

    private suspend fun restoreFields(entity: String, id: String, before: JSONObject?): Boolean {
        if (before == null) return false
        val now = System.currentTimeMillis()
        fun string(name: String, fallback: String): String =
            if (!before.has(name)) fallback else before.optString(name)
        fun nullable(name: String, fallback: String?): String? =
            if (!before.has(name)) fallback else if (before.isNull(name)) null else before.optString(name)

        return when (entity) {
            "PROJECT" -> database.projectDao().getProjectById(id)?.let {
                database.projectDao().updateProject(
                    it.copy(title = string("title", it.title), description = string("description", it.description),
                        goalId = nullable("goalId", it.goalId),
                        nextAction = nullable("nextAction", it.nextAction),
                        status = runCatching { ProjectStatus.valueOf(string("status", it.status.name)) }.getOrDefault(it.status),
                        updatedAt = now)
                )
                true
            } ?: false
            "TASK" -> database.taskDao().getTaskById(id)?.let {
                database.taskDao().updateTask(
                    it.copy(
                        title = string("title", it.title),
                        description = string("description", it.description),
                        projectId = nullable("projectId", it.projectId),
                        goalId = nullable("goalId", it.goalId),
                        dueDate = if (!before.has("dueDate")) it.dueDate
                            else if (before.isNull("dueDate")) null
                            else before.optString("dueDate").toLongOrNull() ?: it.dueDate,
                        status = runCatching { TaskStatus.valueOf(string("status", it.status.name)) }.getOrDefault(it.status),
                        priority = runCatching { TaskPriority.valueOf(string("priority", it.priority.name)) }.getOrDefault(it.priority),
                        updatedAt = now
                    )
                )
                true
            } ?: false
            "GOAL" -> database.goalDao().getGoalById(id)?.let {
                database.goalDao().updateGoal(
                    it.copy(
                        title = string("title", it.title),
                        description = string("description", it.description),
                        status = runCatching { GoalStatus.valueOf(string("status", it.status.name)) }.getOrDefault(it.status),
                        updatedAt = now
                    )
                )
                true
            } ?: false
            "NOTE" -> database.noteDao().getNoteById(id)?.let {
                database.noteDao().updateNote(
                    it.copy(title = string("title", it.title), content = string("content", it.content), updatedAt = now)
                )
                true
            } ?: false
            "MILESTONE" -> database.projectDao().getAllMilestones().firstOrNull { it.id == id }?.let {
                database.projectDao().updateMilestone(it.copy(title = string("title", it.title), updatedAt = now))
                true
            } ?: false
            else -> false
        }
    }
}
