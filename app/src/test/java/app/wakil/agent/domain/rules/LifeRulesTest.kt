package app.wakil.agent.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun top3PrioritiesOrdersCorrectlyByScore() {
        val p1 = ProjectRuleInput("p1", "Project 1", ProjectStatus.ACTIVE)
        val tasks = listOf(
            TaskRuleInput("t1", "p1", "Overdue Task", TaskStatus.TODO, TaskPriority.MEDIUM, dueDate = now - 1000), // +1000 (overdue) + 150 + 200 = 1350
            TaskRuleInput("t2", "p1", "Due Today High", TaskStatus.TODO, TaskPriority.HIGH, dueDate = now + 10_000), // +500 (due today) + 300 + 200 = 1000
            TaskRuleInput("t3", null, "Low Random", TaskStatus.TODO, TaskPriority.LOW, dueDate = now + 100_000), // +50
            TaskRuleInput("t4", "p1", "High Normal", TaskStatus.TODO, TaskPriority.HIGH, dueDate = now + 100_000), // +300 + 200 = 500
            TaskRuleInput("t5", null, "Done Task", TaskStatus.DONE, TaskPriority.HIGH, dueDate = now - 1000) // ignored because DONE
        )

        val top3 = LifeRules.calculateTop3Priorities(tasks, listOf(p1), now)
        assertEquals(3, top3.size)
        assertEquals("t1", top3[0].taskId)
        assertEquals("reason_overdue", top3[0].reasonKey)
        assertEquals("t2", top3[1].taskId)
        assertEquals("reason_due_today", top3[1].reasonKey)
        assertEquals("t4", top3[2].taskId)
    }

    @Test
    fun activeProjectsLimitWarnsWhenExceededAndSuggestsPause() {
        val projects = listOf(
            ProjectRuleInput("p1", "P1", ProjectStatus.ACTIVE, updatedAt = 1000),
            ProjectRuleInput("p2", "P2", ProjectStatus.ACTIVE, updatedAt = 2000),
            ProjectRuleInput("p3", "P3", ProjectStatus.ACTIVE, updatedAt = 3000),
            ProjectRuleInput("p4", "P4", ProjectStatus.ACTIVE, updatedAt = 4000)
        )

        val check = LifeRules.checkActiveProjectsLimit(projects, maxAllowed = 3)
        assertTrue(check.isExceeded)
        assertEquals(4, check.count)
        assertEquals("p1", check.suggestedToPauseId) // oldest updatedAt
        assertNotNull(check.warningKey)
    }

    @Test
    fun activeProjectsLimitDoesNotWarnWhenWithinLimit() {
        val projects = listOf(
            ProjectRuleInput("p1", "P1", ProjectStatus.ACTIVE),
            ProjectRuleInput("p2", "P2", ProjectStatus.PAUSED),
            ProjectRuleInput("p3", "P3", ProjectStatus.ACTIVE)
        )

        val check = LifeRules.checkActiveProjectsLimit(projects, maxAllowed = 3)
        assertFalse(check.isExceeded)
        assertEquals(2, check.count)
    }

    @Test
    fun validateActiveProjectsNextActionsDetectsMissingAction() {
        val projects = listOf(
            ProjectRuleInput("p1", "P1", ProjectStatus.ACTIVE, nextAction = "Call client"),
            ProjectRuleInput("p2", "P2", ProjectStatus.ACTIVE, nextAction = ""),
            ProjectRuleInput("p3", "P3", ProjectStatus.ACTIVE, nextAction = null)
        )

        val validation = LifeRules.validateActiveProjectsNextActions(projects)
        assertTrue(validation["p1"] == true)
        assertTrue(validation["p2"] == false)
        assertTrue(validation["p3"] == false)
    }

    @Test
    fun getOverdueTasksFiltersCorrectly() {
        val tasks = listOf(
            TaskRuleInput("t1", title = "Overdue", dueDate = now - 5000),
            TaskRuleInput("t2", title = "Future", dueDate = now + 5000),
            TaskRuleInput("t3", title = "Done Overdue", status = TaskStatus.DONE, dueDate = now - 5000),
            TaskRuleInput("t4", title = "No Due Date", dueDate = null)
        )

        val overdue = LifeRules.getOverdueTasks(tasks, now)
        assertEquals(1, overdue.size)
        assertEquals("t1", overdue[0].id)
    }

    @Test
    fun securityPolicyRequiresApprovalForDestructiveActions() {
        assertTrue(SecurityPolicy.requiresUserApproval("DELETE"))
        assertTrue(SecurityPolicy.requiresUserApproval("UPDATE", "dueDate"))
        assertTrue(SecurityPolicy.requiresUserApproval("UPDATE", "deadline"))
        assertFalse(SecurityPolicy.requiresUserApproval("CREATE", "task"))
        assertFalse(SecurityPolicy.requiresUserApproval("UPDATE", "title"))

        assertTrue(SecurityPolicy.isLevelPermittedInRelease1(PermissionLevel.L0))
        assertTrue(SecurityPolicy.isLevelPermittedInRelease1(PermissionLevel.L1))
        assertFalse(SecurityPolicy.isLevelPermittedInRelease1(PermissionLevel.L2))
        assertFalse(SecurityPolicy.isLevelPermittedInRelease1(PermissionLevel.L3))
    }
}
