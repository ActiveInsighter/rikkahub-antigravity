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
        AgentAssistantEntity::class,
        AgentFolderEntity::class,
        AgentUsageRecordEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun conversationDao(): AgentConversationDao
    abstract fun messageNodeDao(): AgentMessageNodeDao
    abstract fun assistantDao(): AgentAssistantDao
    abstract fun folderDao(): AgentFolderDao
    abstract fun usageRecordDao(): AgentUsageRecordDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN folder_id TEXT")
                db.execSQL("ALTER TABLE agent_conversation ADD COLUMN assistant_id TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_assistant` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `avatar` TEXT,
                        `system_prompt` TEXT NOT NULL DEFAULT '',
                        `model_id` TEXT,
                        `reasoning_level` TEXT NOT NULL DEFAULT 'OFF',
                        `temperature` REAL,
                        `is_default` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_folder` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `remote_id` TEXT,
                        `sync_status` TEXT NOT NULL DEFAULT 'PENDING',
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_folder_remote_id` ON `agent_folder` (`remote_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_folder_sync_status` ON `agent_folder` (`sync_status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_usage_record` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `conversation_id` TEXT,
                        `assistant_id` TEXT,
                        `model_id` TEXT,
                        `model_name` TEXT,
                        `provider_id` TEXT,
                        `prompt_tokens` INTEGER NOT NULL DEFAULT 0,
                        `completion_tokens` INTEGER NOT NULL DEFAULT 0,
                        `cached_tokens` INTEGER NOT NULL DEFAULT 0,
                        `reasoning_tokens` INTEGER NOT NULL DEFAULT 0,
                        `total_tokens` INTEGER NOT NULL DEFAULT 0,
                        `duration_ms` INTEGER NOT NULL DEFAULT 0,
                        `source` TEXT NOT NULL DEFAULT 'agent',
                        `remote_id` TEXT,
                        `sync_status` TEXT NOT NULL DEFAULT 'PENDING',
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_usage_record_created_at` ON `agent_usage_record` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_usage_record_conversation_id` ON `agent_usage_record` (`conversation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_usage_record_sync_status` ON `agent_usage_record` (`sync_status`)")
            }
        }

        fun create(context: Context): AgentDatabase =
            Room.databaseBuilder(context, AgentDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
