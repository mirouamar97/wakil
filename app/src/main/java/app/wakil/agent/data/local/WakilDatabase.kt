package app.wakil.agent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.wakil.agent.data.local.dao.ActionLogDao
import app.wakil.agent.data.local.dao.ConversationDao
import app.wakil.agent.data.local.dao.GoalDao
import app.wakil.agent.data.local.dao.NoteDao
import app.wakil.agent.data.local.dao.ProjectDao
import app.wakil.agent.data.local.dao.TaskDao
import app.wakil.agent.data.local.dao.UserMemoryDao
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

@Database(
    entities = [
        GoalEntity::class,
        ProjectEntity::class,
        MilestoneEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        NoteLinkEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ActionLogEntity::class,
        UserMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WakilDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun conversationDao(): ConversationDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun userMemoryDao(): UserMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: WakilDatabase? = null

        fun getInstance(context: Context): WakilDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WakilDatabase::class.java,
                    "wakil.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
