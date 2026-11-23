package app.wakil.agent.tools

import app.wakil.agent.domain.rules.TaskStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogFactoryTest {
    @Test
    fun createdRecordCanBeUndoneAndUsesLocalUserPermission() {
        val log = ActionLogFactory.created(
            recordType = "TASK",
            recordId = "task-id",
            title = "Review plan",
            timestamp = 10L,
        )
        val undo = JSONObject(log.undoDataJson!!)

        assertEquals("CREATE", log.actionType)
        assertEquals("TASK", log.recordType)
        assertEquals("L1", log.permissionLevel.name)
        assertEquals("APPROVED", log.approvalStatus.name)
        assertEquals("DELETE_CREATED", undo.getString("action"))
        assertEquals("task-id", undo.getString("id"))
        assertTrue(log.canUndo)
        assertEquals(10L, log.createdAt)
    }

    @Test
    fun statusChangeStoresThePreviousTaskStatus() {
        val log = ActionLogFactory.taskStatusChanged(
            taskId = "task-id",
            title = "Review plan",
            previousStatus = TaskStatus.IN_PROGRESS,
            timestamp = 20L,
        )
        val undo = JSONObject(log.undoDataJson!!)

        assertEquals("UPDATE", log.actionType)
        assertEquals("RESTORE_TASK_STATUS", undo.getString("action"))
        assertEquals("IN_PROGRESS", undo.getString("status"))
        assertEquals(20L, log.createdAt)
    }
}
