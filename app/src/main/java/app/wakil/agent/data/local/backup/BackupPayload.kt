package app.wakil.agent.data.local.backup

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GoalBackupItem(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String = "ACTIVE",
    val timeframe: String = "",
    val progress: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class ProjectBackupItem(
    val id: String,
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: String = "ACTIVE",
    val dueDate: Long? = null,
    val budget: String? = null,
    val nextAction: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class MilestoneBackupItem(
    val id: String,
    val projectId: String,
    val title: String,
    val orderIndex: Int = 0,
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TaskBackupItem(
    val id: String,
    val projectId: String? = null,
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: String = "TODO",
    val priority: String = "MEDIUM",
    val dueDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class NoteBackupItem(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class NoteLinkBackupItem(
    val id: String,
    val noteId: String,
    val targetType: String,
    val targetId: String,
    val createdAt: Long,
)

@Serializable
data class ConversationBackupItem(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean = false,
)

@Serializable
data class MessageBackupItem(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val providerId: String? = null,
    val model: String? = null,
    val status: String = "SENT",
    val createdAt: Long,
)

@Serializable
data class ActionLogBackupItem(
    val id: String,
    val actionType: String,
    val recordType: String,
    val recordId: String,
    val reason: String,
    val result: String,
    val permissionLevel: String = "L1",
    val approvalStatus: String = "APPROVED",
    val undoDataJson: String? = null,
    val canUndo: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class MemoryBackupItem(
    val id: String,
    val factText: String,
    val source: String = "chat",
    val approvalStatus: String = "PENDING",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class WakilBackupPayload(
    val schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val app: String = "Wakil",
    val goals: List<GoalBackupItem> = emptyList(),
    val projects: List<ProjectBackupItem> = emptyList(),
    val milestones: List<MilestoneBackupItem> = emptyList(),
    val tasks: List<TaskBackupItem> = emptyList(),
    val notes: List<NoteBackupItem> = emptyList(),
    val noteLinks: List<NoteLinkBackupItem> = emptyList(),
    val conversations: List<ConversationBackupItem> = emptyList(),
    val messages: List<MessageBackupItem> = emptyList(),
    val actionLogs: List<ActionLogBackupItem> = emptyList(),
    val memories: List<MemoryBackupItem> = emptyList(),
)

enum class BackupError {
    INVALID_JSON,
    INVALID_APP,
    UNSUPPORTED_VERSION,
    INVALID_DATA,
    BROKEN_REFERENCE,
}

sealed interface BackupDecodeResult {
    data class Valid(val payload: WakilBackupPayload) : BackupDecodeResult
    data class Invalid(val error: BackupError) : BackupDecodeResult
}

data class BackupCounts(
    val goals: Int = 0,
    val projects: Int = 0,
    val milestones: Int = 0,
    val tasks: Int = 0,
    val notes: Int = 0,
    val noteLinks: Int = 0,
    val conversations: Int = 0,
    val messages: Int = 0,
    val actionLogs: Int = 0,
    val memories: Int = 0,
) {
    val total: Int
        get() = goals + projects + milestones + tasks + notes + noteLinks +
            conversations + messages + actionLogs + memories

    fun collisionsWith(payload: WakilBackupPayload, existing: BackupRecordIds): Int =
        payload.goals.count { it.id in existing.goals } +
            payload.projects.count { it.id in existing.projects } +
            payload.milestones.count { it.id in existing.milestones } +
            payload.tasks.count { it.id in existing.tasks } +
            payload.notes.count { it.id in existing.notes } +
            payload.noteLinks.count { it.id in existing.noteLinks } +
            payload.conversations.count { it.id in existing.conversations } +
            payload.messages.count { it.id in existing.messages } +
            payload.actionLogs.count { it.id in existing.actionLogs } +
            payload.memories.count { it.id in existing.memories }

    companion object {
        fun from(payload: WakilBackupPayload) = BackupCounts(
            goals = payload.goals.size,
            projects = payload.projects.size,
            milestones = payload.milestones.size,
            tasks = payload.tasks.size,
            notes = payload.notes.size,
            noteLinks = payload.noteLinks.size,
            conversations = payload.conversations.size,
            messages = payload.messages.size,
            actionLogs = payload.actionLogs.size,
            memories = payload.memories.size,
        )
    }
}

data class BackupRecordIds(
    val goals: Set<String> = emptySet(),
    val projects: Set<String> = emptySet(),
    val milestones: Set<String> = emptySet(),
    val tasks: Set<String> = emptySet(),
    val notes: Set<String> = emptySet(),
    val noteLinks: Set<String> = emptySet(),
    val conversations: Set<String> = emptySet(),
    val messages: Set<String> = emptySet(),
    val actionLogs: Set<String> = emptySet(),
    val memories: Set<String> = emptySet(),
) {
    fun plus(payload: WakilBackupPayload) = BackupRecordIds(
        goals = goals + payload.goals.map { it.id },
        projects = projects + payload.projects.map { it.id },
        milestones = milestones + payload.milestones.map { it.id },
        tasks = tasks + payload.tasks.map { it.id },
        notes = notes + payload.notes.map { it.id },
        noteLinks = noteLinks + payload.noteLinks.map { it.id },
        conversations = conversations + payload.conversations.map { it.id },
        messages = messages + payload.messages.map { it.id },
        actionLogs = actionLogs + payload.actionLogs.map { it.id },
        memories = memories + payload.memories.map { it.id },
    )
}

object BackupCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: WakilBackupPayload): String = json.encodeToString(payload)

    fun decode(raw: String): BackupDecodeResult {
        val payload = try {
            json.decodeFromString<WakilBackupPayload>(raw)
        } catch (_: Exception) {
            return BackupDecodeResult.Invalid(BackupError.INVALID_JSON)
        }
        if (payload.app != "Wakil") return BackupDecodeResult.Invalid(BackupError.INVALID_APP)
        if (payload.schemaVersion != SCHEMA_VERSION || payload.exportedAt < 0L) {
            return BackupDecodeResult.Invalid(BackupError.UNSUPPORTED_VERSION)
        }
        return if (validate(payload) == null) {
            BackupDecodeResult.Valid(payload)
        } else {
            BackupDecodeResult.Invalid(BackupError.INVALID_DATA)
        }
    }

    /** Returns a validation error when records are malformed, duplicated, or refer to missing imports. */
    fun validateReferences(payload: WakilBackupPayload, available: BackupRecordIds): BackupError? {
        val all = available.plus(payload)
        val referencesArePresent =
            payload.projects.all { it.goalId == null || it.goalId in all.goals } &&
                payload.milestones.all { it.projectId in all.projects } &&
                payload.tasks.all {
                    (it.projectId == null || it.projectId in all.projects) &&
                        (it.goalId == null || it.goalId in all.goals)
                } &&
                payload.noteLinks.all {
                    it.noteId in all.notes && when (it.targetType) {
                        "GOAL" -> it.targetId in all.goals
                        "PROJECT" -> it.targetId in all.projects
                        "TASK" -> it.targetId in all.tasks
                        else -> false
                    }
                } &&
                payload.messages.all { it.conversationId in all.conversations }
        return if (referencesArePresent) null else BackupError.BROKEN_REFERENCE
    }

    private fun validate(payload: WakilBackupPayload): BackupError? {
        if (payload.exportedAt < 0L) return BackupError.INVALID_DATA
        if (!validRecords(payload.goals.map { it.id to (it.title.isNotBlank() && it.progress in 0..100 &&
                    it.status in setOf("ACTIVE", "COMPLETED", "ARCHIVED") && validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.projects.map { it.id to (it.title.isNotBlank() &&
                    it.status in setOf("IDEA", "PLANNED", "ACTIVE", "PAUSED", "COMPLETED", "ARCHIVED") &&
                    validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.milestones.map { it.id to (it.projectId.isNotBlank() && it.title.isNotBlank() &&
                    it.orderIndex >= 0 && validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.tasks.map { it.id to (it.title.isNotBlank() &&
                    it.status in setOf("TODO", "IN_PROGRESS", "DONE", "CANCELLED") &&
                    it.priority in setOf("LOW", "MEDIUM", "HIGH") &&
                    validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.notes.map { it.id to (it.title.isNotBlank() && validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.noteLinks.map { it.id to (it.noteId.isNotBlank() && it.targetId.isNotBlank() &&
                    it.targetType in setOf("GOAL", "PROJECT", "TASK") && it.createdAt >= 0L) }) ||
            !validRecords(payload.conversations.map { it.id to (it.title.isNotBlank() && validTimes(it.createdAt, it.updatedAt)) }) ||
            !validRecords(payload.messages.map { it.id to (it.role in setOf("user", "assistant", "system") &&
                    it.status in setOf("SENDING", "SENT", "FAILED") && it.createdAt >= 0L) }) ||
            !validRecords(payload.actionLogs.map { it.id to (it.actionType.isNotBlank() && it.recordType.isNotBlank() &&
                    it.recordId.isNotBlank() && it.result.isNotBlank() &&
                    it.permissionLevel in setOf("L0", "L1", "L2", "L3") &&
                    it.approvalStatus in setOf("APPROVED", "PENDING", "REJECTED") && it.createdAt >= 0L) }) ||
            !validRecords(payload.memories.map { it.id to (it.factText.isNotBlank() &&
                    it.approvalStatus in setOf("APPROVED", "PENDING", "REJECTED") &&
                    validTimes(it.createdAt, it.updatedAt)) })
        ) return BackupError.INVALID_DATA

        return null
    }

    private fun validTimes(createdAt: Long, updatedAt: Long): Boolean =
        createdAt >= 0L && updatedAt >= createdAt

    private fun validRecords(records: List<Pair<String, Boolean>>): Boolean {
        val ids = records.map { it.first }
        return ids.size == ids.toSet().size &&
            records.all { (id, validFields) -> validFields && isUuid(id) }
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }
}

data class ImportPreview(
    val counts: BackupCounts,
    val conflictCount: Int,
    val schemaVersion: Int,
    val isValid: Boolean,
    val error: BackupError? = null,
)

data class ImportResult(
    val importedCount: Int,
    val preservedConflictCount: Int,
    val error: BackupError? = null,
) {
    val isSuccess: Boolean get() = error == null
}
