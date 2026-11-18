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

    @Query("SELECT * FROM agent_conversation WHERE remote_thread_root = :threadRoot LIMIT 1")
    suspend fun getByRemoteThreadRoot(threadRoot: String): AgentConversationEntity?

    @Query("SELECT * FROM agent_conversation")
    suspend fun getAll(): List<AgentConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentConversationEntity)

    @Query("UPDATE agent_conversation SET title = :title, update_at = :updateAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updateAt: Long)

    @Query("UPDATE agent_conversation SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)

    @Query("UPDATE agent_conversation SET folder_id = :folderId, update_at = :updateAt WHERE id = :id")
    suspend fun updateFolderId(id: String, folderId: String?, updateAt: Long)

    @Query("UPDATE agent_conversation SET folder_id = NULL WHERE folder_id = :folderId")
    suspend fun clearFolderId(folderId: String)

    @Query("UPDATE agent_conversation SET assistant_id = :assistantId WHERE id = :id")
    suspend fun updateAssistantId(id: String, assistantId: String?)

    @Query("UPDATE agent_conversation SET has_unread = :hasUnread WHERE id = :id")
    suspend fun updateUnread(id: String, hasUnread: Boolean)

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

@Dao
interface AgentAssistantDao {
    @Query("SELECT * FROM agent_assistant ORDER BY is_default DESC, created_at ASC")
    fun getAllFlow(): Flow<List<AgentAssistantEntity>>

    @Query("SELECT * FROM agent_assistant ORDER BY is_default DESC, created_at ASC")
    suspend fun getAll(): List<AgentAssistantEntity>

    @Query("SELECT * FROM agent_assistant WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentAssistantEntity?

    @Query("SELECT * FROM agent_assistant WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): AgentAssistantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentAssistantEntity)

    @Query("UPDATE agent_assistant SET is_default = 0")
    suspend fun clearDefault()

    @Query("DELETE FROM agent_assistant WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AgentFolderDao {
    @Query("SELECT * FROM agent_folder WHERE sync_status != 'DELETED' ORDER BY sort_order ASC, created_at ASC")
    fun getAllFlow(): Flow<List<AgentFolderEntity>>

    @Query("SELECT * FROM agent_folder WHERE sync_status != 'DELETED' ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAll(): List<AgentFolderEntity>

    @Query("SELECT * FROM agent_folder WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentFolderEntity?

    @Query("SELECT * FROM agent_folder WHERE remote_id = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): AgentFolderEntity?

    @Query("SELECT * FROM agent_folder WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSync(): List<AgentFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentFolderEntity)

    @Query("DELETE FROM agent_folder WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AgentUsageRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AgentUsageRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<AgentUsageRecordEntity>)

    @Query("SELECT * FROM agent_usage_record WHERE sync_status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingSync(): List<AgentUsageRecordEntity>

    @Query("SELECT * FROM agent_usage_record WHERE remote_id = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): AgentUsageRecordEntity?

    @Query(
        "SELECT COALESCE(SUM(prompt_tokens), 0) AS promptTokens, " +
            "COALESCE(SUM(completion_tokens), 0) AS completionTokens, " +
            "COALESCE(SUM(cached_tokens), 0) AS cachedTokens, " +
            "COALESCE(SUM(reasoning_tokens), 0) AS reasoningTokens, " +
            "COALESCE(SUM(total_tokens), 0) AS totalTokens " +
            "FROM agent_usage_record WHERE created_at >= :since"
    )
    suspend fun getTotals(since: Long = 0L): AgentUsageTotals

    @Query(
        "SELECT COALESCE(SUM(prompt_tokens), 0) AS promptTokens, " +
            "COALESCE(SUM(completion_tokens), 0) AS completionTokens, " +
            "COALESCE(SUM(cached_tokens), 0) AS cachedTokens, " +
            "COALESCE(SUM(reasoning_tokens), 0) AS reasoningTokens, " +
            "COALESCE(SUM(total_tokens), 0) AS totalTokens " +
            "FROM agent_usage_record WHERE created_at >= :since"
    )
    fun observeTotals(since: Long = 0L): Flow<AgentUsageTotals>

    @Query("SELECT * FROM agent_usage_record WHERE created_at >= :since ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(since: Long = 0L, limit: Int = 100): Flow<List<AgentUsageRecordEntity>>
}

data class AgentUsageTotals(
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val cachedTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val totalTokens: Long = 0L,
)

@Dao
interface AgentDraftDao {
    @Query("SELECT * FROM agent_draft WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun getByConversationId(conversationId: String): AgentDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentDraftEntity)

    @Query("DELETE FROM agent_draft WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)
}
