package app.wakil.agent.domain.rules

import java.util.UUID

enum class ProjectStatus {
    IDEA, PLANNED, ACTIVE, PAUSED, COMPLETED, ARCHIVED
}

enum class TaskStatus {
    TODO, IN_PROGRESS, DONE, CANCELLED
}

enum class TaskPriority {
    LOW, MEDIUM, HIGH
}

enum class GoalStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

enum class PermissionLevel {
    L0, L1, L2, L3
}

enum class ApprovalStatus {
    APPROVED, PENDING, REJECTED
}

data class PriorityItem(
    val taskId: String,
    val title: String,
    val reasonKey: String, // Resource key for localized reason
    val score: Int
)

data class ActiveProjectsCheck(
    val count: Int,
    val maxAllowed: Int = 3,
    val isExceeded: Boolean,
    val suggestedToPauseId: String? = null,
    val warningKey: String? = null
)

object LifeRules {

    /**
     * Deterministic calculation for today's top 3 priorities.
     * Weights:
     * - Overdue: +1000
     * - Due Today: +500
     * - High Priority: +300, Medium: +150, Low: +50
     * - Part of an ACTIVE project: +200
     * - In Progress: +100
     */
    fun calculateTop3Priorities(
        tasks: List<TaskRuleInput>,
        activeProjects: List<ProjectRuleInput>,
        nowEpochMs: Long
    ): List<PriorityItem> {
        val activeProjectIds = activeProjects.map { it.id }.toSet()
        val openTasks = tasks.filter { it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED && !it.isDeleted }

        val scored = openTasks.map { task ->
            var score = 0
            var reason = "reason_priority_standard"

            val isOverdue = task.dueDate != null && task.dueDate < nowEpochMs
            val isDueToday = task.dueDate != null && !isOverdue && (task.dueDate - nowEpochMs <= 24 * 60 * 60 * 1000L)
            val isLinkedToActiveProject = task.projectId != null && task.projectId in activeProjectIds

            if (isOverdue) {
                score += 1000
                reason = "reason_overdue"
            } else if (isDueToday) {
                score += 500
                reason = "reason_due_today"
            }

            when (task.priority) {
                TaskPriority.HIGH -> {
                    score += 300
                    if (reason == "reason_priority_standard") reason = "reason_high_priority"
                }
                TaskPriority.MEDIUM -> score += 150
                TaskPriority.LOW -> score += 50
            }

            if (isLinkedToActiveProject) {
                score += 200
                if (reason == "reason_priority_standard") reason = "reason_active_project"
            }

            if (task.status == TaskStatus.IN_PROGRESS) {
                score += 100
                if (reason == "reason_priority_standard") reason = "reason_in_progress"
            }

            PriorityItem(
                taskId = task.id,
                title = task.title,
                reasonKey = reason,
                score = score
            )
        }

        return scored.sortedByDescending { it.score }.take(3)
    }

    /**
     * Active projects rule:
     * Max 3 active projects by default (adjustable).
     * Warns and suggests which project to pause (e.g. oldest updated or lowest progress), but NEVER blocks.
     */
    fun checkActiveProjectsLimit(
        projects: List<ProjectRuleInput>,
        maxAllowed: Int = 3
    ): ActiveProjectsCheck {
        val activeProjects = projects.filter { it.status == ProjectStatus.ACTIVE && !it.isDeleted }
        val count = activeProjects.size
        val isExceeded = count > maxAllowed

        val suggestedToPause = if (isExceeded) {
            // Suggest pausing the active project with oldest last update or furthest deadline
            activeProjects.minByOrNull { it.updatedAt }?.id
        } else {
            null
        }

        return ActiveProjectsCheck(
            count = count,
            maxAllowed = maxAllowed,
            isExceeded = isExceeded,
            suggestedToPauseId = suggestedToPause,
            warningKey = if (isExceeded) "warn_active_projects_limit" else null
        )
    }

    /**
     * Checks whether every ACTIVE project has exactly one next action.
     * Returns a map of projectId -> Boolean (true if valid).
     */
    fun validateActiveProjectsNextActions(
        activeProjects: List<ProjectRuleInput>
    ): Map<String, Boolean> {
        return activeProjects
            .filter { it.status == ProjectStatus.ACTIVE && !it.isDeleted }
            .associate { project ->
                val hasNextAction = !project.nextAction.isNullOrBlank()
                project.id to hasNextAction
            }
    }

    /**
     * Returns overdue tasks that are not done.
     */
    fun getOverdueTasks(
        tasks: List<TaskRuleInput>,
        nowEpochMs: Long
    ): List<TaskRuleInput> {
        return tasks.filter {
            !it.isDeleted &&
            it.status != TaskStatus.DONE &&
            it.status != TaskStatus.CANCELLED &&
            it.dueDate != null &&
            it.dueDate < nowEpochMs
        }
    }
}

data class TaskRuleInput(
    val id: String,
    val projectId: String? = null,
    val title: String,
    val status: TaskStatus = TaskStatus.TODO,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: Long? = null,
    val isDeleted: Boolean = false
)

data class ProjectRuleInput(
    val id: String,
    val title: String,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val nextAction: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
