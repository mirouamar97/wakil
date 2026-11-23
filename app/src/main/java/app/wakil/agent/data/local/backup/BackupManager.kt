package app.wakil.agent.data.local.backup

import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.GoalEntity
import app.wakil.agent.data.local.entities.NoteEntity
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.data.local.entities.UserMemoryEntity
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
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
    val updatedAt: Long
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
    val updatedAt: Long
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
    val updatedAt: Long
)

@Serializable
data class NoteBackupItem(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class MemoryBackupItem(
    val id: String,
    val factText: String,
    val source: String = "chat",
    val approvalStatus: String = "APPROVED",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class WakilBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val app: String = "Wakil",
    val goals: List<GoalBackupItem> = emptyList(),
    val projects: List<ProjectBackupItem> = emptyList(),
    val tasks: List<TaskBackupItem> = emptyList(),
    val notes: List<NoteBackupItem> = emptyList(),
    val memories: List<MemoryBackupItem> = emptyList()
)

data class ImportPreview(
    val goalsCount: Int,
    val projectsCount: Int,
    val tasksCount: Int,
    val notesCount: Int,
    val memoriesCount: Int,
    val schemaVersion: Int,
    val isValid: Boolean,
    val errorMessage: String? = null
)

class BackupManager(private val database: WakilDatabase) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val goals = database.goalDao().getAllGoals().map {
            GoalBackupItem(it.id, it.title, it.description, it.status.name, it.timeframe, it.progress, it.createdAt, it.updatedAt)
        }
        val projects = database.projectDao().getAllProjects().map {
            ProjectBackupItem(it.id, it.goalId, it.title, it.description, it.status.name, it.dueDate, it.budget, it.nextAction, it.createdAt, it.updatedAt)
        }
        val tasks = database.taskDao().getAllTasks().map {
            TaskBackupItem(it.id, it.projectId, it.goalId, it.title, it.description, it.status.name, it.priority.name, it.dueDate, it.createdAt, it.updatedAt)
        }
        val notes = database.noteDao().getAllNotes().map {
            NoteBackupItem(it.id, it.title, it.content, it.category, it.createdAt, it.updatedAt)
        }
        val memories = database.userMemoryDao().getApprovedMemories().map {
            MemoryBackupItem(it.id, it.factText, it.source, it.approvalStatus.name, it.createdAt, it.updatedAt)
        }

        val payload = WakilBackupPayload(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            goals = goals,
            projects = projects,
            tasks = tasks,
            notes = notes,
            memories = memories
        )

        return json.encodeToString(payload)
    }

    fun inspectBackup(jsonString: String): ImportPreview {
        return try {
            val payload = json.decodeFromString<WakilBackupPayload>(jsonString)
            if (payload.schemaVersion > 1) {
                ImportPreview(0, 0, 0, 0, 0, payload.schemaVersion, false, "Unsupported schema version")
            } else {
                ImportPreview(
                    goalsCount = payload.goals.size,
                    projectsCount = payload.projects.size,
                    tasksCount = payload.tasks.size,
                    notesCount = payload.notes.size,
                    memoriesCount = payload.memories.size,
                    schemaVersion = payload.schemaVersion,
                    isValid = true
                )
            }
        } catch (e: Exception) {
            ImportPreview(0, 0, 0, 0, 0, 0, false, e.localizedMessage ?: "Invalid JSON")
        }
    }

    suspend fun importFromJson(jsonString: String): Boolean {
        return try {
            val payload = json.decodeFromString<WakilBackupPayload>(jsonString)
            for (g in payload.goals) {
                database.goalDao().insertGoal(
                    GoalEntity(
                        id = g.id,
                        title = g.title,
                        description = g.description,
                        status = try { GoalStatus.valueOf(g.status) } catch (_: Exception) { GoalStatus.ACTIVE },
                        timeframe = g.timeframe,
                        progress = g.progress,
                        createdAt = g.createdAt,
                        updatedAt = g.updatedAt
                    )
                )
            }
            for (p in payload.projects) {
                database.projectDao().insertProject(
                    ProjectEntity(
                        id = p.id,
                        goalId = p.goalId,
                        title = p.title,
                        description = p.description,
                        status = try { ProjectStatus.valueOf(p.status) } catch (_: Exception) { ProjectStatus.ACTIVE },
                        dueDate = p.dueDate,
                        budget = p.budget,
                        nextAction = p.nextAction,
                        createdAt = p.createdAt,
                        updatedAt = p.updatedAt
                    )
                )
            }
            for (t in payload.tasks) {
                database.taskDao().insertTask(
                    TaskEntity(
                        id = t.id,
                        projectId = t.projectId,
                        goalId = t.goalId,
                        title = t.title,
                        description = t.description,
                        status = try { TaskStatus.valueOf(t.status) } catch (_: Exception) { TaskStatus.TODO },
                        priority = try { TaskPriority.valueOf(t.priority) } catch (_: Exception) { TaskPriority.MEDIUM },
                        dueDate = t.dueDate,
                        createdAt = t.createdAt,
                        updatedAt = t.updatedAt
                    )
                )
            }
            for (n in payload.notes) {
                database.noteDao().insertNote(
                    NoteEntity(
                        id = n.id,
                        title = n.title,
                        content = n.content,
                        category = n.category,
                        createdAt = n.createdAt,
                        updatedAt = n.updatedAt
                    )
                )
            }
            for (m in payload.memories) {
                database.userMemoryDao().insertMemory(
                    UserMemoryEntity(
                        id = m.id,
                        factText = m.factText,
                        source = m.source,
                        approvalStatus = try { ApprovalStatus.valueOf(m.approvalStatus) } catch (_: Exception) { ApprovalStatus.APPROVED },
                        createdAt = m.createdAt,
                        updatedAt = m.updatedAt
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
