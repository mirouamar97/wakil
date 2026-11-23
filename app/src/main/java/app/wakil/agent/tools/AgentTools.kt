package app.wakil.agent.tools

import app.wakil.agent.data.SettingsStore
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.ActionLogEntity
import app.wakil.agent.data.local.entities.NoteEntity
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.SecurityPolicy
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

        val taskId = UUID.randomUUID().toString()
        val task = TaskEntity(
            id = taskId,
            projectId = projectId,
            title = title,
            priority = priority,
            dueDate = dueDate
        )

        database.taskDao().insertTask(task)

        val undoJson = JSONObject().apply {
            put("action", "DELETE_CREATED")
            put("entity", "TASK")
            put("id", taskId)
        }.toString()

        val logId = UUID.randomUUID().toString()
        database.actionLogDao().insertActionLog(
            ActionLogEntity(
                id = logId,
                actionType = "CREATE",
                recordType = "TASK",
                recordId = taskId,
                reason = reason,
                result = "Created task: $title",
                permissionLevel = PermissionLevel.L1,
                approvalStatus = ApprovalStatus.APPROVED,
                undoDataJson = undoJson,
                canUndo = true
            )
        )

        return ToolExecutionResult.Success("Task created successfully", logId)
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
            description = "Delete task: ${existing.title} (Reason: $reason)",
            onConfirm = {
                database.taskDao().softDeleteTask(taskId)
                val logId = UUID.randomUUID().toString()
                database.actionLogDao().insertActionLog(
                    ActionLogEntity(
                        id = logId,
                        actionType = "DELETE",
                        recordType = "TASK",
                        recordId = taskId,
                        reason = reason,
                        result = "Deleted task: ${existing.title}",
                        permissionLevel = PermissionLevel.L1,
                        approvalStatus = ApprovalStatus.APPROVED,
                        undoDataJson = null,
                        canUndo = false
                    )
                )
                onConfirmed()
            }
        )
    }

    suspend fun undoLastAction(): Boolean {
        val lastAction = database.actionLogDao().getLastUndoableAction() ?: return false
        val undoJson = lastAction.undoDataJson ?: return false

        try {
            val json = JSONObject(undoJson)
            val action = json.optString("action")
            val entity = json.optString("entity")
            val id = json.optString("id")

            if (action == "DELETE_CREATED" && entity == "TASK") {
                database.taskDao().softDeleteTask(id)
            } else if (action == "DELETE_CREATED" && entity == "NOTE") {
                database.noteDao().softDeleteNote(id)
            }
            database.actionLogDao().markUndone(lastAction.id)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
