package app.wakil.agent.tools

import app.wakil.agent.data.local.entities.ActionLogEntity
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.TaskStatus
import org.json.JSONObject

/** Creates local audit entries and the small, typed payloads needed to reverse supported edits. */
object ActionLogFactory {
    fun created(recordType: String, recordId: String, title: String, timestamp: Long = System.currentTimeMillis()) =
        ActionLogEntity(
            actionType = "CREATE",
            recordType = recordType,
            recordId = recordId,
            reason = "USER_ACTION",
            result = title,
            permissionLevel = PermissionLevel.L1,
            approvalStatus = ApprovalStatus.APPROVED,
            undoDataJson = JSONObject()
                .put("action", "DELETE_CREATED")
                .put("entity", recordType)
                .put("id", recordId)
                .toString(),
            canUndo = true,
            createdAt = timestamp,
        )

    fun taskStatusChanged(
        taskId: String,
        title: String,
        previousStatus: TaskStatus,
        timestamp: Long = System.currentTimeMillis(),
    ) = ActionLogEntity(
        actionType = "UPDATE",
        recordType = "TASK",
        recordId = taskId,
        reason = "USER_ACTION",
        result = title,
        permissionLevel = PermissionLevel.L1,
        approvalStatus = ApprovalStatus.APPROVED,
        undoDataJson = JSONObject()
            .put("action", "RESTORE_TASK_STATUS")
            .put("id", taskId)
            .put("status", previousStatus.name)
            .toString(),
        canUndo = true,
        createdAt = timestamp,
    )

    fun fieldsUpdated(
        recordType: String,
        recordId: String,
        title: String,
        previousFields: Map<String, String?>,
        timestamp: Long = System.currentTimeMillis(),
    ): ActionLogEntity {
        val previous = JSONObject()
        previousFields.forEach { (key, value) -> previous.put(key, value ?: JSONObject.NULL) }
        return ActionLogEntity(
            actionType = "UPDATE",
            recordType = recordType,
            recordId = recordId,
            reason = "USER_ACTION",
            result = title,
            permissionLevel = PermissionLevel.L1,
            approvalStatus = ApprovalStatus.APPROVED,
            undoDataJson = JSONObject()
                .put("action", "RESTORE_RECORD_FIELDS")
                .put("entity", recordType)
                .put("id", recordId)
                .put("before", previous)
                .toString(),
            canUndo = true,
            createdAt = timestamp,
        )
    }

    fun deleted(
        recordType: String,
        recordId: String,
        title: String,
        timestamp: Long = System.currentTimeMillis(),
    ) = ActionLogEntity(
        actionType = "DELETE",
        recordType = recordType,
        recordId = recordId,
        reason = "USER_ACTION",
        result = title,
        permissionLevel = PermissionLevel.L1,
        approvalStatus = ApprovalStatus.APPROVED,
        undoDataJson = JSONObject()
            .put("action", "RESTORE_DELETION")
            .put("entity", recordType)
            .put("id", recordId)
            .toString(),
        canUndo = true,
        createdAt = timestamp,
    )
}
