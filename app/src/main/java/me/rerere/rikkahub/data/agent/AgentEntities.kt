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
