package app.wakil.agent.tools

import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.domain.rules.LifeRules
import app.wakil.agent.domain.rules.ProjectRuleInput
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskRuleInput

data class DailyBriefingData(
    val topPriorities: List<String>,
    val overdueCount: Int,
    val activeProjectsCount: Int,
    val activeProjectsWithoutNextAction: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object DailyBriefing {

    suspend fun generate(database: WakilDatabase): DailyBriefingData {
        val now = System.currentTimeMillis()
        val allTasks = database.taskDao().getAllTasks().map {
            TaskRuleInput(
                id = it.id,
                projectId = it.projectId,
                title = it.title,
                status = it.status,
                priority = it.priority,
                dueDate = it.dueDate,
                isDeleted = it.isDeleted
            )
        }
        val allProjects = database.projectDao().getAllProjects().map {
            ProjectRuleInput(
                id = it.id,
                title = it.title,
                status = it.status,
                nextAction = it.nextAction,
                updatedAt = it.updatedAt,
                isDeleted = it.isDeleted
            )
        }

        val activeProjects = allProjects.filter { it.status == ProjectStatus.ACTIVE && !it.isDeleted }
        val top3 = LifeRules.calculateTop3Priorities(allTasks, activeProjects, now)
        val overdue = LifeRules.getOverdueTasks(allTasks, now)
        val nextActionsValidation = LifeRules.validateActiveProjectsNextActions(activeProjects)
        val missingNextActionCount = nextActionsValidation.values.count { !it }

        return DailyBriefingData(
            topPriorities = top3.map { it.title },
            overdueCount = overdue.size,
            activeProjectsCount = activeProjects.size,
            activeProjectsWithoutNextAction = missingNextActionCount
        )
    }
}
