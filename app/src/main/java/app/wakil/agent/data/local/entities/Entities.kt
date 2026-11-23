package app.wakil.agent.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
import java.util.UUID

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val timeframe: String = "",
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "projects",
    indices = [Index("goalId")]
)
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val dueDate: Long? = null,
    val budget: String? = null,
    val nextAction: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "milestones",
    indices = [Index("projectId")]
)
data class MilestoneEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val orderIndex: Int = 0,
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tasks",
    indices = [Index("projectId"), Index("goalId")]
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "note_links",
    indices = [Index("noteId"), Index("targetId")]
)
data class NoteLinkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val targetType: String, // "GOAL", "PROJECT", "TASK"
    val targetId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val providerId: String? = null,
    val model: String? = null,
    val status: String = "SENT", // "SENDING", "SENT", "FAILED"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val actionType: String, // "CREATE", "UPDATE", "DELETE", "AI_SUGGESTION"
    val recordType: String, // "TASK", "PROJECT", "GOAL", "NOTE"
    val recordId: String,
    val reason: String,
    val result: String,
    val permissionLevel: PermissionLevel = PermissionLevel.L1,
    val approvalStatus: ApprovalStatus = ApprovalStatus.APPROVED,
    val undoDataJson: String? = null,
    val canUndo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val factText: String,
    val source: String = "chat",
    val approvalStatus: ApprovalStatus = ApprovalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
