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
    private val assistantDao: AgentAssistantDao,
    private val folderDao: AgentFolderDao,
    private val usageRecordDao: AgentUsageRecordDao,
    private val cloudClient: AgentCloudClient,
) {
    fun observeConversations(sortOrder: ConversationSortOrder): Flow<List<Conversation>> {
        val source = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> conversationDao.getAllSortedByUpdateFlow()
            ConversationSortOrder.CREATE_TIME -> conversationDao.getAllSortedByCreateFlow()
        }
        return source.map { list ->
            list.map { entity ->
                val folderUuid = entity.folderId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                val assistantUuid = entity.assistantId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    ?: DEFAULT_ASSISTANT_ID
                Conversation(
                    id = Uuid.parse(entity.id),
                    assistantId = assistantUuid,
                    folderId = folderUuid,
                    title = entity.title,
                    messageNodes = emptyList(),
                    isPinned = entity.isPinned,
                    createAt = Instant.ofEpochMilli(entity.createAt),
                    updateAt = Instant.ofEpochMilli(entity.updateAt),
                )
            }
        }
    }

    fun observeActiveConversationIds(): Flow<Set<Uuid>> {
        return conversationDao.getAllSortedByUpdateFlow().map { list ->
            list.asSequence()
                .filter { it.remoteStatus != null && PocketBaseAgentCloudClient.isActiveStatus(it.remoteStatus) }
                .mapNotNull { runCatching { Uuid.parse(it.id) }.getOrNull() }
                .toSet()
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
                    title = thread.title.ifBlank { existing?.title ?: "Agent" },
                    createAt = createdMs,
                    updateAt = updatedMs,
                    isPinned = thread.pinned,
                    folderId = thread.folderId?.takeIf { it.isNotBlank() } ?: existing?.folderId,
                    assistantId = existing?.assistantId,
                    remoteJobId = thread.jobId,
                    remoteThreadRoot = thread.threadRoot,
                    remoteStatus = thread.status,
                    lastRemoteMessageId = existing?.lastRemoteMessageId,
                    lastSyncedAt = now,
                )
            )
        }
        syncFoldersWithRemote()

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
            folderId = conversation.folderId?.toString() ?: existing?.folderId,
            assistantId = conversation.assistantId.toString(),
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

    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        val convIdStr = conversationId.toString()
        val folderIdStr = folderId?.toString()
        val now = System.currentTimeMillis()
        conversationDao.updateFolderId(convIdStr, folderIdStr, now)
        val existing = conversationDao.getById(convIdStr)
        val remoteJobId = existing?.remoteJobId
        if (!remoteJobId.isNullOrBlank() && cloudClient.hasSession()) {
            runCatching { cloudClient.updateThreadMetadata(remoteJobId, folderId = folderIdStr ?: "") }
        }
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

    // ---- Local-only Agent Assistants ----

    fun observeAssistants(): Flow<List<AgentAssistantEntity>> = assistantDao.getAllFlow()

    suspend fun ensureDefaultAssistant(): AgentAssistantEntity {
        val existing = assistantDao.getDefault() ?: assistantDao.getAll().firstOrNull()
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val defaultAssistant = AgentAssistantEntity(
            id = DEFAULT_ASSISTANT_ID.toString(),
            name = "Agent",
            systemPrompt = "",
            reasoningLevel = "OFF",
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        assistantDao.upsert(defaultAssistant)
        return defaultAssistant
    }

    suspend fun upsertAssistant(assistant: AgentAssistantEntity) {
        if (assistant.isDefault) {
            assistantDao.clearDefault()
        }
        assistantDao.upsert(assistant.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteAssistant(id: String) {
        assistantDao.deleteById(id)
    }

    // ---- Local + Cloud Agent Folders ----

    fun observeFolders(): Flow<List<me.rerere.rikkahub.data.model.Folder>> {
        return folderDao.getAllFlow().map { list ->
            list.mapNotNull { entity ->
                val uuid = runCatching { Uuid.parse(entity.id) }.getOrNull() ?: return@mapNotNull null
                me.rerere.rikkahub.data.model.Folder(
                    id = uuid,
                    assistantId = DEFAULT_ASSISTANT_ID,
                    name = entity.name,
                    sortIndex = entity.sortOrder,
                    createAt = Instant.ofEpochMilli(entity.createdAt),
                )
            }
        }
    }

    suspend fun createFolder(name: String): me.rerere.rikkahub.data.model.Folder {
        val trimmed = name.trim()
        val id = Uuid.random()
        val now = System.currentTimeMillis()
        val existing = folderDao.getAll()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        var entity = AgentFolderEntity(
            id = id.toString(),
            name = trimmed,
            sortOrder = nextOrder,
            syncStatus = SyncStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        folderDao.upsert(entity)
        if (cloudClient.hasSession()) {
            val remote = cloudClient.upsertRemoteFolder(
                RemoteAgentFolder(
                    id = "",
                    clientId = entity.id,
                    name = entity.name,
                    sortOrder = entity.sortOrder,
                )
            )
            if (remote != null) {
                entity = entity.copy(remoteId = remote.id, syncStatus = SyncStatus.SYNCED)
                folderDao.upsert(entity)
            }
        }
        return me.rerere.rikkahub.data.model.Folder(
            id = id,
            assistantId = DEFAULT_ASSISTANT_ID,
            name = trimmed,
            sortIndex = nextOrder,
            createAt = Instant.ofEpochMilli(now),
        )
    }

    suspend fun renameFolder(id: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = folderDao.getById(id.toString()) ?: return
        var updated = existing.copy(
            name = trimmed,
            syncStatus = SyncStatus.PENDING,
            updatedAt = System.currentTimeMillis(),
        )
        folderDao.upsert(updated)
        if (cloudClient.hasSession()) {
            val remote = cloudClient.upsertRemoteFolder(
                RemoteAgentFolder(
                    id = updated.remoteId.orEmpty(),
                    clientId = updated.id,
                    name = updated.name,
                    sortOrder = updated.sortOrder,
                )
            )
            if (remote != null) {
                updated = updated.copy(remoteId = remote.id, syncStatus = SyncStatus.SYNCED)
                folderDao.upsert(updated)
            }
        }
    }

    suspend fun deleteFolder(id: Uuid) {
        val idStr = id.toString()
        val existing = folderDao.getById(idStr)
        conversationDao.clearFolderId(idStr)
        if (existing?.remoteId != null && cloudClient.hasSession()) {
            cloudClient.deleteRemoteFolder(existing.remoteId)
        }
        folderDao.deleteById(idStr)
    }

    suspend fun syncFoldersWithRemote(): Result<Unit> = runCatching {
        if (!cloudClient.hasSession()) return@runCatching
        val pending = folderDao.getPendingSync()
        for (item in pending) {
            if (item.syncStatus == SyncStatus.DELETED) {
                item.remoteId?.let { cloudClient.deleteRemoteFolder(it) }
                folderDao.deleteById(item.id)
            } else {
                val remote = cloudClient.upsertRemoteFolder(
                    RemoteAgentFolder(
                        id = item.remoteId.orEmpty(),
                        clientId = item.id,
                        name = item.name,
                        sortOrder = item.sortOrder,
                    )
                )
                if (remote != null) {
                    folderDao.upsert(item.copy(remoteId = remote.id, syncStatus = SyncStatus.SYNCED))
                }
            }
        }
        val remoteFolders = cloudClient.listRemoteFolders()
        val now = System.currentTimeMillis()
        for (remote in remoteFolders) {
            val local = folderDao.getByRemoteId(remote.id) ?: folderDao.getById(remote.clientId)
            val localId = local?.id ?: remote.clientId
            folderDao.upsert(
                AgentFolderEntity(
                    id = localId,
                    name = remote.name,
                    sortOrder = remote.sortOrder,
                    remoteId = remote.id,
                    syncStatus = SyncStatus.SYNCED,
                    createdAt = local?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    // ---- Local + Cloud Agent Usage Records ----

    fun observeUsageTotals(since: Long = 0L): Flow<AgentUsageTotals> = usageRecordDao.observeTotals(since)

    fun observeRecentUsage(since: Long = 0L, limit: Int = 100): Flow<List<AgentUsageRecordEntity>> =
        usageRecordDao.observeRecent(since, limit)

    suspend fun recordUsage(
        conversationId: String?,
        assistantId: String?,
        modelId: String?,
        modelName: String?,
        providerId: String?,
        promptTokens: Long,
        completionTokens: Long,
        cachedTokens: Long = 0L,
        reasoningTokens: Long = 0L,
        durationMs: Long = 0L,
    ) {
        val now = System.currentTimeMillis()
        val total = promptTokens + completionTokens
        var entity = AgentUsageRecordEntity(
            id = Uuid.random().toString(),
            conversationId = conversationId,
            assistantId = assistantId,
            modelId = modelId,
            modelName = modelName,
            providerId = providerId,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            cachedTokens = cachedTokens,
            reasoningTokens = reasoningTokens,
            totalTokens = total,
            durationMs = durationMs,
            source = "agent",
            syncStatus = SyncStatus.PENDING,
            createdAt = now,
        )
        usageRecordDao.upsert(entity)
        if (cloudClient.hasSession()) {
            val remote = cloudClient.uploadUsageRecord(
                RemoteAgentUsageRecord(
                    clientId = entity.id,
                    conversationId = entity.conversationId,
                    assistantId = entity.assistantId,
                    modelId = entity.modelId,
                    modelName = entity.modelName,
                    providerId = entity.providerId,
                    promptTokens = entity.promptTokens,
                    completionTokens = entity.completionTokens,
                    cachedTokens = entity.cachedTokens,
                    reasoningTokens = entity.reasoningTokens,
                    totalTokens = entity.totalTokens,
                    durationMs = entity.durationMs,
                    source = entity.source,
                    createdAt = entity.createdAt,
                )
            )
            if (remote != null) {
                entity = entity.copy(remoteId = remote.id, syncStatus = SyncStatus.SYNCED)
                usageRecordDao.upsert(entity)
            }
        }
    }

    suspend fun syncUsageWithRemote(): Result<Unit> = runCatching {
        if (!cloudClient.hasSession()) return@runCatching
        val pending = usageRecordDao.getPendingSync()
        for (item in pending) {
            val remote = cloudClient.uploadUsageRecord(
                RemoteAgentUsageRecord(
                    clientId = item.id,
                    conversationId = item.conversationId,
                    assistantId = item.assistantId,
                    modelId = item.modelId,
                    modelName = item.modelName,
                    providerId = item.providerId,
                    promptTokens = item.promptTokens,
                    completionTokens = item.completionTokens,
                    cachedTokens = item.cachedTokens,
                    reasoningTokens = item.reasoningTokens,
                    totalTokens = item.totalTokens,
                    durationMs = item.durationMs,
                    source = item.source,
                    createdAt = item.createdAt,
                )
            )
            if (remote != null) {
                usageRecordDao.upsert(item.copy(remoteId = remote.id, syncStatus = SyncStatus.SYNCED))
            }
        }
        val remoteRecords = cloudClient.listRemoteUsageRecords()
        for (remote in remoteRecords) {
            val existing = usageRecordDao.getByRemoteId(remote.id)
            if (existing == null) {
                usageRecordDao.upsert(
                    AgentUsageRecordEntity(
                        id = remote.clientId.ifBlank { remote.id },
                        conversationId = remote.conversationId,
                        assistantId = remote.assistantId,
                        modelId = remote.modelId,
                        modelName = remote.modelName,
                        providerId = remote.providerId,
                        promptTokens = remote.promptTokens,
                        completionTokens = remote.completionTokens,
                        cachedTokens = remote.cachedTokens,
                        reasoningTokens = remote.reasoningTokens,
                        totalTokens = remote.totalTokens,
                        durationMs = remote.durationMs,
                        source = remote.source,
                        remoteId = remote.id,
                        syncStatus = SyncStatus.SYNCED,
                        createdAt = remote.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    )
                )
            }
        }
    }
}
