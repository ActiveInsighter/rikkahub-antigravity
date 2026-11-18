package me.rerere.rikkahub.data.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AgentConversationEntity::class,
        AgentMessageNodeEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun conversationDao(): AgentConversationDao
    abstract fun messageNodeDao(): AgentMessageNodeDao

    companion object {
        const val DATABASE_NAME = "agent.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN remote_job_id TEXT")
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN remote_thread_root TEXT")
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN remote_status TEXT")
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN last_remote_message_id TEXT")
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN last_synced_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AgentDatabase =
            Room.databaseBuilder(context, AgentDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
