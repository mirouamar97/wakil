package app.wakil.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllGoalsFlow(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE isDeleted = 0")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("UPDATE goals SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteGoal(id: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isDeleted = 0")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteProject(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM milestones WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getMilestonesForProject(projectId: String): Flow<List<MilestoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: MilestoneEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY dueDate ASC, priority DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0")
    fun getTasksByProject(projectId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTask(id: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: String, timestamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteLink(link: NoteLinkEntity)

    @Query("SELECT * FROM note_links WHERE noteId = :noteId")
    suspend fun getLinksForNote(noteId: String): List<NoteLinkEntity>
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY createdAt DESC LIMIT 50")
    fun getRecentActionLogsFlow(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs ORDER BY createdAt DESC")
    suspend fun getAllActionLogs(): List<ActionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(actionLog: ActionLogEntity)

    @Query("SELECT * FROM action_logs WHERE canUndo = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastUndoableAction(): ActionLogEntity?

    @Query("UPDATE action_logs SET canUndo = 0 WHERE id = :id")
    suspend fun markUndone(id: String)
}

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY createdAt DESC")
    fun getAllMemoriesFlow(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories WHERE approvalStatus = 'APPROVED' ORDER BY createdAt DESC")
    suspend fun getApprovedMemories(): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity)

    @Update
    suspend fun updateMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)
}
