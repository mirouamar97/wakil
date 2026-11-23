package app.wakil.agent.data.local.backup

import androidx.room.withTransaction
import app.wakil.agent.data.local.WakilDatabase
import app.wakil.agent.data.local.entities.ActionLogEntity
import app.wakil.agent.data.local.entities.ConversationEntity
import app.wakil.agent.data.local.entities.GoalEntity
import app.wakil.agent.data.local.entities.MessageEntity
import app.wakil.agent.data.local.entities.MilestoneEntity
import app.wakil.agent.data.local.entities.NoteEntity
import app.wakil.agent.data.local.entities.NoteLinkEntity
import app.wakil.agent.data.local.entities.ProjectEntity
import app.wakil.agent.data.local.entities.TaskEntity
import app.wakil.agent.data.local.entities.UserMemoryEntity
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.domain.rules.GoalStatus
import app.wakil.agent.domain.rules.PermissionLevel
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus

class BackupManager(private val database: WakilDatabase) {

    suspend fun exportToJson(): String = database.withTransaction {
        val goals = database.goalDao().getAllGoalsForBackup()
        val projects = database.projectDao().getAllProjectsForBackup()
        val milestones = database.projectDao().getAllMilestones()
        val tasks = database.taskDao().getAllTasksForBackup()
        val notes = database.noteDao().getAllNotesForBackup()
        val noteLinks = database.noteDao().getAllNoteLinks()
        val conversations = database.conversationDao().getAllConversationsForBackup()
        val messages = database.conversationDao().getAllMessages()
        val actionLogs = database.actionLogDao().getAllActionLogsForBackup()
        val memories = database.userMemoryDao().getAllMemoriesForBackup()

        BackupCodec.encode(
            WakilBackupPayload(
                goals = goals.map {
                    GoalBackupItem(
                        it.id, it.title, it.description, it.status.name, it.timeframe,
                        it.progress, it.createdAt, it.updatedAt, it.isDeleted
                    )
                },
                projects = projects.map {
                    ProjectBackupItem(
                        it.id, it.goalId, it.title, it.description, it.status.name, it.dueDate,
                        it.budget, it.nextAction, it.createdAt, it.updatedAt, it.isDeleted
                    )
                },
                milestones = milestones.map {
                    MilestoneBackupItem(
                        it.id, it.projectId, it.title, it.orderIndex, it.dueDate,
                        it.isCompleted, it.createdAt, it.updatedAt
                    )
                },
                tasks = tasks.map {
                    TaskBackupItem(
                        it.id, it.projectId, it.goalId, it.title, it.description, it.status.name,
                        it.priority.name, it.dueDate, it.createdAt, it.updatedAt, it.isDeleted
                    )
                },
                notes = notes.map {
                    NoteBackupItem(
                        it.id, it.title, it.content, it.category, it.createdAt, it.updatedAt, it.isDeleted
                    )
                },
                noteLinks = noteLinks.map {
                    NoteLinkBackupItem(it.id, it.noteId, it.targetType, it.targetId, it.createdAt)
                },
                conversations = conversations.map {
                    ConversationBackupItem(it.id, it.title, it.createdAt, it.updatedAt, it.isArchived)
                },
                messages = messages.map {
                    MessageBackupItem(
                        it.id, it.conversationId, it.role, it.content, it.providerId,
                        it.model, it.status, it.createdAt
                    )
                },
                actionLogs = actionLogs.map {
                    ActionLogBackupItem(
                        it.id, it.actionType, it.recordType, it.recordId, it.reason, it.result,
                        it.permissionLevel.name, it.approvalStatus.name, it.undoDataJson,
                        it.canUndo, it.createdAt
                    )
                },
                memories = memories.map {
                    MemoryBackupItem(
                        it.id, it.factText, it.source, it.approvalStatus.name, it.createdAt, it.updatedAt
                    )
                },
            )
        )
    }

    suspend fun inspectBackup(jsonString: String): ImportPreview = database.withTransaction {
        when (val decoded = BackupCodec.decode(jsonString)) {
            is BackupDecodeResult.Invalid -> ImportPreview(
                counts = BackupCounts(),
                conflictCount = 0,
                schemaVersion = 0,
                isValid = false,
                error = decoded.error,
            )
            is BackupDecodeResult.Valid -> {
                val payload = decoded.payload
                val existing = recordIds()
                val referencesError = BackupCodec.validateReferences(payload, existing)
                ImportPreview(
                    counts = BackupCounts.from(payload),
                    conflictCount = BackupCounts.from(payload).collisionsWith(payload, existing),
                    schemaVersion = payload.schemaVersion,
                    isValid = referencesError == null,
                    error = referencesError,
                )
            }
        }
    }

    /**
     * Imports atomically. Existing IDs are preserved and skipped, so restore never silently
     * replaces records that may be newer on this device.
     */
    suspend fun importFromJson(jsonString: String): ImportResult = try {
        database.withTransaction {
            when (val decoded = BackupCodec.decode(jsonString)) {
                is BackupDecodeResult.Invalid -> ImportResult(0, 0, decoded.error)
                is BackupDecodeResult.Valid -> {
                    val payload = decoded.payload
                    val existing = recordIds()
                    val referenceError = BackupCodec.validateReferences(payload, existing)
                    if (referenceError != null) {
                        ImportResult(0, 0, referenceError)
                    } else {
                        val conflicts = BackupCounts.from(payload).collisionsWith(payload, existing)
                        var imported = 0

                        payload.goals.filterNot { it.id in existing.goals }.forEach {
                            database.goalDao().insertGoal(
                                GoalEntity(
                                    id = it.id,
                                    title = it.title,
                                    description = it.description,
                                    status = GoalStatus.valueOf(it.status),
                                    timeframe = it.timeframe,
                                    progress = it.progress,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                    isDeleted = it.isDeleted,
                                )
                            )
                            imported++
                        }
                        payload.projects.filterNot { it.id in existing.projects }.forEach {
                            database.projectDao().insertProject(
                                ProjectEntity(
                                    id = it.id,
                                    goalId = it.goalId,
                                    title = it.title,
                                    description = it.description,
                                    status = ProjectStatus.valueOf(it.status),
                                    dueDate = it.dueDate,
                                    budget = it.budget,
                                    nextAction = it.nextAction,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                    isDeleted = it.isDeleted,
                                )
                            )
                            imported++
                        }
                        payload.milestones.filterNot { it.id in existing.milestones }.forEach {
                            database.projectDao().insertMilestone(
                                MilestoneEntity(
                                    id = it.id,
                                    projectId = it.projectId,
                                    title = it.title,
                                    orderIndex = it.orderIndex,
                                    dueDate = it.dueDate,
                                    isCompleted = it.isCompleted,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                )
                            )
                            imported++
                        }
                        payload.tasks.filterNot { it.id in existing.tasks }.forEach {
                            database.taskDao().insertTask(
                                TaskEntity(
                                    id = it.id,
                                    projectId = it.projectId,
                                    goalId = it.goalId,
                                    title = it.title,
                                    description = it.description,
                                    status = TaskStatus.valueOf(it.status),
                                    priority = TaskPriority.valueOf(it.priority),
                                    dueDate = it.dueDate,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                    isDeleted = it.isDeleted,
                                )
                            )
                            imported++
                        }
                        payload.notes.filterNot { it.id in existing.notes }.forEach {
                            database.noteDao().insertNote(
                                NoteEntity(
                                    id = it.id,
                                    title = it.title,
                                    content = it.content,
                                    category = it.category,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                    isDeleted = it.isDeleted,
                                )
                            )
                            imported++
                        }
                        payload.noteLinks.filterNot { it.id in existing.noteLinks }.forEach {
                            database.noteDao().insertNoteLink(
                                NoteLinkEntity(it.id, it.noteId, it.targetType, it.targetId, it.createdAt)
                            )
                            imported++
                        }
                        payload.conversations.filterNot { it.id in existing.conversations }.forEach {
                            database.conversationDao().insertConversation(
                                ConversationEntity(it.id, it.title, it.createdAt, it.updatedAt, it.isArchived)
                            )
                            imported++
                        }
                        payload.messages.filterNot { it.id in existing.messages }.forEach {
                            database.conversationDao().insertMessage(
                                MessageEntity(
                                    it.id, it.conversationId, it.role, it.content, it.providerId,
                                    it.model, it.status, it.createdAt
                                )
                            )
                            imported++
                        }
                        payload.actionLogs.filterNot { it.id in existing.actionLogs }.forEach {
                            database.actionLogDao().insertActionLog(
                                ActionLogEntity(
                                    id = it.id,
                                    actionType = it.actionType,
                                    recordType = it.recordType,
                                    recordId = it.recordId,
                                    reason = it.reason,
                                    result = it.result,
                                    permissionLevel = PermissionLevel.valueOf(it.permissionLevel),
                                    approvalStatus = ApprovalStatus.valueOf(it.approvalStatus),
                                    undoDataJson = it.undoDataJson,
                                    canUndo = it.canUndo,
                                    createdAt = it.createdAt,
                                )
                            )
                            imported++
                        }
                        payload.memories.filterNot { it.id in existing.memories }.forEach {
                            database.userMemoryDao().insertMemory(
                                UserMemoryEntity(
                                    id = it.id,
                                    factText = it.factText,
                                    source = it.source,
                                    approvalStatus = ApprovalStatus.valueOf(it.approvalStatus),
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                )
                            )
                            imported++
                        }
                        ImportResult(importedCount = imported, preservedConflictCount = conflicts)
                    }
                }
            }
        }
    } catch (_: Exception) {
        ImportResult(0, 0, BackupError.INVALID_DATA)
    }

    private suspend fun recordIds(): BackupRecordIds = BackupRecordIds(
        goals = database.goalDao().getAllGoalsForBackup().map { it.id }.toSet(),
        projects = database.projectDao().getAllProjectsForBackup().map { it.id }.toSet(),
        milestones = database.projectDao().getAllMilestones().map { it.id }.toSet(),
        tasks = database.taskDao().getAllTasksForBackup().map { it.id }.toSet(),
        notes = database.noteDao().getAllNotesForBackup().map { it.id }.toSet(),
        noteLinks = database.noteDao().getAllNoteLinks().map { it.id }.toSet(),
        conversations = database.conversationDao().getAllConversationsForBackup().map { it.id }.toSet(),
        messages = database.conversationDao().getAllMessages().map { it.id }.toSet(),
        actionLogs = database.actionLogDao().getAllActionLogsForBackup().map { it.id }.toSet(),
        memories = database.userMemoryDao().getAllMemoriesForBackup().map { it.id }.toSet(),
    )
}
