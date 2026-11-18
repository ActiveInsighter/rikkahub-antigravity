package me.rerere.rikkahub.data.agent

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "agent_conversation")
data class AgentConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("create_at")
    val createAt: Long,
    @ColumnInfo("update_at")
    val updateAt: Long,
    @ColumnInfo("is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo("folder_id")
    val folderId: String? = null,
    @ColumnInfo("assistant_id")
    val assistantId: String? = null,
    @ColumnInfo("remote_job_id")
    val remoteJobId: String? = null,
    @ColumnInfo("remote_thread_root")
    val remoteThreadRoot: String? = null,
    @ColumnInfo("remote_status")
    val remoteStatus: String? = null,
    @ColumnInfo("last_remote_message_id")
    val lastRemoteMessageId: String? = null,
    @ColumnInfo("last_synced_at", defaultValue = "0")
    val lastSyncedAt: Long = 0L,
    @ColumnInfo("has_unread", defaultValue = "0")
    val hasUnread: Boolean = false,
)

@Entity(
    tableName = "agent_message_node",
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversation_id", "node_index")],
)
data class AgentMessageNodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("node_index")
    val nodeIndex: Int,
    @ColumnInfo("messages")
    val messages: String, // JSON serialized List<UIMessage>
    @ColumnInfo("select_index")
    val selectIndex: Int,
)

/**
 * Local-only Agent Assistant entity (isolated from Chat's assistants).
 */
@Entity(tableName = "agent_assistant")
data class AgentAssistantEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("avatar")
    val avatar: String? = null,
    @ColumnInfo("system_prompt", defaultValue = "''")
    val systemPrompt: String = "",
    @ColumnInfo("model_id")
    val modelId: String? = null,
    @ColumnInfo("reasoning_level", defaultValue = "'OFF'")
    val reasoningLevel: String = "OFF",
    @ColumnInfo("temperature")
    val temperature: Float? = null,
    @ColumnInfo("is_default", defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

/**
 * Agent conversation folder entity (stored locally AND synced to cloud).
 */
@Entity(
    tableName = "agent_folder",
    indices = [Index("remote_id"), Index("sync_status")],
)
data class AgentFolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo("remote_id")
    val remoteId: String? = null,
    @ColumnInfo("sync_status", defaultValue = "'PENDING'")
    val syncStatus: String = SyncStatus.PENDING,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

/**
 * Agent usage record entity (stored locally AND synced to cloud).
 */
@Entity(
    tableName = "agent_usage_record",
    indices = [Index("created_at"), Index("conversation_id"), Index("sync_status")],
)
data class AgentUsageRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String? = null,
    @ColumnInfo("assistant_id")
    val assistantId: String? = null,
    @ColumnInfo("model_id")
    val modelId: String? = null,
    @ColumnInfo("model_name")
    val modelName: String? = null,
    @ColumnInfo("provider_id")
    val providerId: String? = null,
    @ColumnInfo("prompt_tokens", defaultValue = "0")
    val promptTokens: Long = 0L,
    @ColumnInfo("completion_tokens", defaultValue = "0")
    val completionTokens: Long = 0L,
    @ColumnInfo("cached_tokens", defaultValue = "0")
    val cachedTokens: Long = 0L,
    @ColumnInfo("reasoning_tokens", defaultValue = "0")
    val reasoningTokens: Long = 0L,
    @ColumnInfo("total_tokens", defaultValue = "0")
    val totalTokens: Long = 0L,
    @ColumnInfo("duration_ms", defaultValue = "0")
    val durationMs: Long = 0L,
    @ColumnInfo("source", defaultValue = "'agent'")
    val source: String = "agent",
    @ColumnInfo("remote_id")
    val remoteId: String? = null,
    @ColumnInfo("sync_status", defaultValue = "'PENDING'")
    val syncStatus: String = SyncStatus.PENDING,
    @ColumnInfo("created_at")
    val createdAt: Long,
)

object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING = "PENDING"
    const val DELETED = "DELETED"
}

/**
 * Local-only per-conversation input draft cache (supports both new and existing conversations).
 */
@Entity(tableName = "agent_draft")
data class AgentDraftEntity(
    @PrimaryKey
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("text")
    val text: String,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
