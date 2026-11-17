package me.rerere.rikkahub.data.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode

@Database(
    entities = [
        AgentConversationEntity::class,
        AgentMessageNodeEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun conversationDao(): AgentConversationDao
    abstract fun messageNodeDao(): AgentMessageNodeDao

    companion object {
        const val DATABASE_NAME = "agent.db"

        fun create(context: Context): AgentDatabase =
            Room.databaseBuilder(context, AgentDatabase::class.java, DATABASE_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
