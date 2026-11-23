package app.wakil.agent.data.local

import androidx.room.TypeConverter
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus

class Converters {
    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus = try {
        ProjectStatus.valueOf(value)
    } catch (_: Exception) {
        ProjectStatus.ACTIVE
    }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (_: Exception) {
        TaskStatus.TODO
    }

    @TypeConverter
    fun fromTaskPriority(priority: TaskPriority): String = priority.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = try {
        TaskPriority.valueOf(value)
    } catch (_: Exception) {
        TaskPriority.MEDIUM
    }

    @TypeConverter
    fun fromGoalStatus(status: GoalStatus): String = status.name

    @TypeConverter
    fun toGoalStatus(value: String): GoalStatus = try {
        GoalStatus.valueOf(value)
    } catch (_: Exception) {
        GoalStatus.ACTIVE
    }

    @TypeConverter
    fun fromPermissionLevel(level: PermissionLevel): String = level.name

    @TypeConverter
    fun toPermissionLevel(value: String): PermissionLevel = try {
        PermissionLevel.valueOf(value)
    } catch (_: Exception) {
        PermissionLevel.L0
    }

    @TypeConverter
    fun fromApprovalStatus(status: ApprovalStatus): String = status.name

    @TypeConverter
    fun toApprovalStatus(value: String): ApprovalStatus = try {
        ApprovalStatus.valueOf(value)
    } catch (_: Exception) {
        ApprovalStatus.PENDING
    }
}
