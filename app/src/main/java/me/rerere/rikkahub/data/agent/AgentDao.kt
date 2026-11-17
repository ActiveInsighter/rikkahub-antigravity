package me.rerere.rikkahub.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentConversationDao {
    @Query("SELECT * FROM agent_conversation ORDER BY is_pinned DESC, update_at DESC")
    fun getAllSortedByUpdateFlow(): Flow<List<AgentConversationEntity>>

    @Query("SELECT * FROM agent_conversation ORDER BY is_pinned DESC, create_at DESC")
    fun getAllSortedByCreateFlow(): Flow<List<AgentConversationEntity>>

    @Query("SELECT * FROM agent_conversation WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentConversationEntity)

    @Query("UPDATE agent_conversation SET title = :title, update_at = :updateAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updateAt: Long)

    @Query("UPDATE agent_conversation SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)

    @Query("DELETE FROM agent_conversation WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM agent_conversation")
    suspend fun deleteAll()
}

@Dao
interface AgentMessageNodeDao {
    @Query("SELECT * FROM agent_message_node WHERE conversation_id = :conversationId ORDER BY node_index ASC")
    suspend fun getByConversationId(conversationId: String): List<AgentMessageNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(nodes: List<AgentMessageNodeEntity>)

    @Query("DELETE FROM agent_message_node WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Transaction
    suspend fun replaceForConversation(conversationId: String, nodes: List<AgentMessageNodeEntity>) {
        deleteByConversationId(conversationId)
        if (nodes.isNotEmpty()) {
            upsertAll(nodes)
        }
    }
}
