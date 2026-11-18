package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

data class AgentConversationSnapshot(
    val conversation: Conversation,
    val remoteJobId: String? = null,
    val remoteThreadRoot: String? = null,
    val remoteStatus: String? = null,
    val lastRemoteMessageId: String? = null,
    val isActive: Boolean = false,
)

/**
 * Repository backed by [AgentDatabase], synchronized with the remote
 * AnyWorkflow / PocketBase database as the source of truth.
 */
class AgentRepository(
    private val conversationDao: AgentConversationDao,
    private val messageNodeDao: AgentMessageNodeDao,
    private val cloudClient: AgentCloudClient,
) {
    fun observeConversations(sortOrder: ConversationSortOrder): Flow<List<Conversation>> {
        val source = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> conversationDao.getAllSortedByUpdateFlow()
            ConversationSortOrder.CREATE_TIME -> conversationDao.getAllSortedByCreateFlow()
        }
        return source.map { list ->
            list.map { entity ->
                Conversation(
                    id = Uuid.parse(entity.id),
                    assistantId = DEFAULT_ASSISTANT_ID,
                    title = entity.title,
                    messageNodes = emptyList(),
                    isPinned = entity.isPinned,
                    createAt = Instant.ofEpochMilli(entity.createAt),
                    updateAt = Instant.ofEpochMilli(entity.updateAt),
                )
            }
        }
    }

    suspend fun getConversation(id: Uuid): Conversation? {
        return getConversationSnapshot(id)?.conversation
    }

    suspend fun getConversationSnapshot(id: Uuid): AgentConversationSnapshot? {
        val entity = conversationDao.getById(id.toString()) ?: return null
        val nodes = messageNodeDao.getByConversationId(entity.id).map { nodeEntity ->
            val messages = runCatching {
                JsonInstant.decodeFromString<List<UIMessage>>(nodeEntity.messages)
            }.getOrDefault(emptyList())
            MessageNode(
                id = Uuid.parse(nodeEntity.id),
                messages = messages,
                selectIndex = nodeEntity.selectIndex.coerceIn(0, (messages.size - 1).coerceAtLeast(0)),
            )
        }
        val conversation = Conversation(
            id = Uuid.parse(entity.id),
            assistantId = DEFAULT_ASSISTANT_ID,
            title = entity.title,
            messageNodes = nodes,
            isPinned = entity.isPinned,
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
        )
        return AgentConversationSnapshot(
            conversation = conversation,
            remoteJobId = entity.remoteJobId,
            remoteThreadRoot = entity.remoteThreadRoot,
            remoteStatus = entity.remoteStatus,
            lastRemoteMessageId = entity.lastRemoteMessageId,
            isActive = entity.remoteStatus?.let { PocketBaseAgentCloudClient.isActiveStatus(it) } == true,
        )
    }

    suspend fun syncThreadsFromRemote(): Result<Unit> = runCatching {
        if (!cloudClient.hasSession()) return@runCatching
        val remoteThreads = cloudClient.listRemoteThreads()
        val now = System.currentTimeMillis()
        val remoteRoots = HashSet<String>(remoteThreads.size)

        for (thread in remoteThreads) {
            remoteRoots += thread.threadRoot
            val existing = conversationDao.getByRemoteThreadRoot(thread.threadRoot)
                ?: conversationDao.getById(
                    PocketBaseAgentCloudClient.stableUuid("aw-agent:${thread.threadRoot}").toString()
                )
            val localId = existing?.id
                ?: PocketBaseAgentCloudClient.stableUuid("aw-agent:${thread.threadRoot}").toString()

            val createdMs = PocketBaseAgentCloudClient.parseIsoMillis(thread.created)
                .takeIf { it > 0L } ?: existing?.createAt ?: now
            val updatedMs = PocketBaseAgentCloudClient.parseIsoMillis(thread.updated)
                .takeIf { it > 0L } ?: existing?.updateAt ?: createdMs

            conversationDao.upsert(
                AgentConversationEntity(
                    id = localId,
                    title = thread.title.ifBlank { existing?.title ?: "Antigravity Agent" },
                    createAt = createdMs,
                    updateAt = updatedMs,
                    isPinned = thread.pinned,
                    remoteJobId = thread.jobId,
                    remoteThreadRoot = thread.threadRoot,
                    remoteStatus = thread.status,
                    lastRemoteMessageId = existing?.lastRemoteMessageId,
                    lastSyncedAt = now,
                )
            )
        }

        // Remote database is authoritative: remove synced local threads no longer present remotely.
        val localAll = conversationDao.getAll()
        for (local in localAll) {
            val root = local.remoteThreadRoot
            if (root != null && root !in remoteRoots) {
                conversationDao.deleteById(local.id)
            }
        }
    }

    suspend fun syncConversationFromRemote(id: Uuid): Result<AgentConversationSnapshot?> = runCatching {
        if (!cloudClient.hasSession()) return@runCatching getConversationSnapshot(id)
        val existing = conversationDao.getById(id.toString()) ?: return@runCatching null
        val jobId = existing.remoteJobId ?: return@runCatching getConversationSnapshot(id)
        val remote = cloudClient.fetchThreadConversation(jobId) ?: return@runCatching getConversationSnapshot(id)

        val now = System.currentTimeMillis()
        val threadRoot = remote.job.threadRoot.ifBlank { remote.job.id }
        val updatedMs = PocketBaseAgentCloudClient.parseIsoMillis(remote.job.updated)
            .takeIf { it > 0L } ?: now
        val createdMs = PocketBaseAgentCloudClient.parseIsoMillis(remote.job.created)
            .takeIf { it > 0L } ?: existing.createAt

        val title = remote.job.title.ifBlank {
            remote.job.prompt.trim().replace('\n', ' ').take(36)
                .ifBlank { existing.title.ifBlank { "Antigravity Agent" } }
        }

        val updatedEntity = existing.copy(
            title = title,
            createAt = createdMs,
            updateAt = updatedMs,
            remoteJobId = remote.job.id,
            remoteThreadRoot = threadRoot,
            remoteStatus = remote.job.status,
            lastRemoteMessageId = remote.lastRemoteMessageId ?: existing.lastRemoteMessageId,
            lastSyncedAt = now,
        )
        conversationDao.upsert(updatedEntity)
        saveNodesForConversation(existing.id, remote.messageNodes)

        val conv = Conversation(
            id = id,
            assistantId = DEFAULT_ASSISTANT_ID,
            title = title,
            messageNodes = remote.messageNodes,
            isPinned = updatedEntity.isPinned,
            createAt = Instant.ofEpochMilli(createdMs),
            updateAt = Instant.ofEpochMilli(updatedMs),
        )
        AgentConversationSnapshot(
            conversation = conv,
            remoteJobId = updatedEntity.remoteJobId,
            remoteThreadRoot = updatedEntity.remoteThreadRoot,
            remoteStatus = updatedEntity.remoteStatus,
            lastRemoteMessageId = updatedEntity.lastRemoteMessageId,
            isActive = remote.isActive,
        )
    }

    suspend fun saveConversation(
        conversation: Conversation,
        remoteJobId: String? = null,
        remoteThreadRoot: String? = null,
        remoteStatus: String? = null,
        lastRemoteMessageId: String? = null,
    ) {
        val convId = conversation.id.toString()
        val existing = conversationDao.getById(convId)
        val entity = AgentConversationEntity(
            id = convId,
            title = conversation.title,
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            isPinned = conversation.isPinned,
            remoteJobId = remoteJobId ?: existing?.remoteJobId,
            remoteThreadRoot = remoteThreadRoot ?: existing?.remoteThreadRoot,
            remoteStatus = remoteStatus ?: existing?.remoteStatus,
            lastRemoteMessageId = lastRemoteMessageId ?: existing?.lastRemoteMessageId,
            lastSyncedAt = System.currentTimeMillis(),
        )
        conversationDao.upsert(entity)
        saveNodesForConversation(convId, conversation.messageNodes)
    }

    private suspend fun saveNodesForConversation(convId: String, nodes: List<MessageNode>) {
        val nodeEntities = nodes.mapIndexed { index, node ->
            AgentMessageNodeEntity(
                id = node.id.toString(),
                conversationId = convId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex,
            )
        }
        messageNodeDao.replaceForConversation(convId, nodeEntities)
    }

    suspend fun renameConversation(id: Uuid, title: String) {
        val existing = conversationDao.getById(id.toString())
        val remoteJobId = existing?.remoteJobId
        if (!remoteJobId.isNullOrBlank() && cloudClient.hasSession()) {
            runCatching { cloudClient.updateThreadMetadata(remoteJobId, title = title) }
        }
        conversationDao.updateTitle(
            id = id.toString(),
            title = title,
            updateAt = Instant.now().toEpochMilli(),
        )
    }

    suspend fun setPinned(id: Uuid, pinned: Boolean) {
        val existing = conversationDao.getById(id.toString())
        val remoteJobId = existing?.remoteJobId
        if (!remoteJobId.isNullOrBlank() && cloudClient.hasSession()) {
            runCatching { cloudClient.updateThreadMetadata(remoteJobId, pinned = pinned) }
        }
        conversationDao.updatePinned(id = id.toString(), isPinned = pinned)
    }

    suspend fun deleteConversation(id: Uuid) {
        val existing = conversationDao.getById(id.toString())
        val remoteJobId = existing?.remoteJobId
        if (!remoteJobId.isNullOrBlank() && cloudClient.hasSession()) {
            runCatching { cloudClient.deleteThread(remoteJobId) }
        }
        conversationDao.deleteById(id.toString())
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAll()
    }
}
