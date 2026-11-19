package me.rerere.rikkahub.data.agent

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

data class AgentConversationSnapshot(
    val conversation: Conversation,
    val remoteStatus: String? = null,
    val lastRemoteMessageId: String? = null,
    val isActive: Boolean = false,
    val waitingForMessages: Boolean = false,
)

data class AgentConversationStatusInfo(
    val remoteStatus: String?,
    val hasUnread: Boolean,
)

/**
 * Repository backed by [AgentDatabase], synchronized with the remote
 * AnyWorkflow / PocketBase database as the source of truth.
 */
class AgentRepository(
    private val database: AgentDatabase,
    private val cloudClient: AgentCloudClient,
    private val workflowRepository: WorkflowRepository,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
) {
    private val conversationDao = database.conversationDao()
    private val messageNodeDao = database.messageNodeDao()
    private val assistantDao = database.assistantDao()
    private val folderDao = database.folderDao()
    private val usageRecordDao = database.usageRecordDao()
    private val draftDao = database.draftDao()
    private val modelDao = database.modelDao()
    @Volatile
    private var visibleConversation: Uuid? = null

    // A job creation event can arrive before its HTTP response binds the remote root to the local ID.
    private val draftMutex = Mutex()
    private val threadSyncMutex = Mutex()
    private val conversationLocks = List(16) { Mutex() }
    private val _submittingIds = MutableStateFlow<Set<Uuid>>(emptySet())
    val submittingIds = _submittingIds.asStateFlow()

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

    fun observeConversationStatusMap(): Flow<Map<Uuid, AgentConversationStatusInfo>> {
        return conversationDao.getAllSortedByUpdateFlow().map { list ->
            buildMap {
                for (entity in list) {
                    val uuid = runCatching { Uuid.parse(entity.id) }.getOrNull() ?: continue
                    put(
                        uuid,
                        AgentConversationStatusInfo(
                            remoteStatus = entity.remoteStatus,
                            hasUnread = entity.hasUnread,
                        )
                    )
                }
            }
        }
    }

    internal fun observeRemoteConversations(): Flow<List<AgentConversationEntity>> =
        conversationDao.getAllSortedByUpdateFlow()

    fun observeConversation(id: Uuid): Flow<AgentConversationSnapshot?> = database.invalidationTracker
        .createFlow("agent_conversation", "agent_message_node")
        .map { getConversationSnapshot(id) }
        .distinctUntilChanged()

    private suspend fun getConversationSnapshot(id: Uuid): AgentConversationSnapshot? {
        val (entity, storedNodes) = database.withTransaction {
            val entity = conversationDao.getById(id.toString()) ?: return@withTransaction null
            entity to messageNodeDao.getByConversationId(entity.id)
        } ?: return null
        val nodes = storedNodes.map { nodeEntity ->
            val messages = runCatching {
                JsonInstant.decodeFromString<List<UIMessage>>(nodeEntity.messages)
            }.getOrDefault(emptyList())
            MessageNode(
                id = Uuid.parse(nodeEntity.id),
                messages = messages,
                selectIndex = nodeEntity.selectIndex.coerceIn(0, (messages.size - 1).coerceAtLeast(0)),
            )
        }
        return AgentConversationSnapshot(
            conversation = Conversation(
                id = id,
                assistantId = entity.assistantId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    ?: DEFAULT_ASSISTANT_ID,
                folderId = entity.folderId?.let { runCatching { Uuid.parse(it) }.getOrNull() },
                title = entity.title,
                messageNodes = nodes,
                isPinned = entity.isPinned,
                createAt = Instant.ofEpochMilli(entity.createAt),
                updateAt = Instant.ofEpochMilli(entity.updateAt),
            ),
            remoteStatus = entity.remoteStatus,
            lastRemoteMessageId = entity.lastRemoteMessageId,
            isActive = entity.remoteStatus?.let { PocketBaseAgentCloudClient.isActiveStatus(it) } == true,
            waitingForMessages = entity.remoteJobId != null && entity.lastRemoteMessageId == null &&
                (entity.lastSyncedAt == 0L || nodes.isEmpty()),
        )
    }

    suspend fun syncThreadsFromRemote(): Result<Unit> = runCatching {
        val session = workflowRepository.session.value ?: return@runCatching
        syncFoldersWithRemote()
        syncThreads(session)
    }.onFailure { if (it is CancellationException) throw it }

    internal suspend fun syncThreads(session: WorkflowSession, root: String? = null): Unit = threadSyncMutex.withLock {
        if (workflowRepository.session.value != session) return@withLock
        val remoteThreads = cloudClient.listRemoteThreads(root)
        database.withTransaction {
            if (workflowRepository.session.value != session) return@withTransaction
            for (thread in remoteThreads) {
                val fallbackId = PocketBaseAgentCloudClient.stableUuid("aw-agent:${thread.threadRoot}").toString()
                val existing = conversationDao.getByRemoteThreadRoot(thread.threadRoot)
                    ?: conversationDao.getById(fallbackId)
                val now = System.currentTimeMillis()
                val created = existing?.createAt ?: remoteTime(thread.created, now)
                val sameJob = existing?.remoteJobId == thread.jobId
                val folderId = if (thread.hasFolderIdField) {
                    thread.folderId?.let { folderDao.getByRemoteId(it)?.id ?: it }
                } else {
                    existing?.folderId
                }
                val storedStatus = existing?.remoteStatus
                val previousStatus = storedStatus.takeIf { sameJob }
                val repaired = previousStatus in setOf("failed", "partial") && thread.status == "succeeded"
                val status = if (repaired) thread.status else previousStatus ?: thread.status
                val isVisible = (existing?.id ?: fallbackId) == visibleConversation?.toString()
                val updated = AgentConversationEntity(
                    id = existing?.id ?: fallbackId,
                    title = thread.title.ifBlank { existing?.title ?: "Agent" },
                    createAt = created,
                    updateAt = maxOf(existing?.updateAt ?: 0L, remoteTime(thread.updated, created)),
                    isPinned = thread.pinned,
                    folderId = folderId,
                    assistantId = existing?.assistantId,
                    remoteJobId = thread.jobId,
                    remoteThreadRoot = thread.threadRoot,
                    // Only the message sync can mark a tracked job terminal, after saving its final parts.
                    remoteStatus = status,
                    lastRemoteMessageId = existing?.lastRemoteMessageId?.takeIf { sameJob },
                    lastSyncedAt = existing?.lastSyncedAt?.takeIf { sameJob } ?: 0L,
                    hasUnread = !isVisible && (repaired || existing?.hasUnread == true),
                )
                if (updated != existing) conversationDao.upsert(updated)
            }
            if (root != null) {
                if (remoteThreads.isEmpty()) {
                    conversationDao.getByRemoteThreadRoot(root)?.let { conversationDao.deleteById(it.id) }
                }
            } else {
                val roots = remoteThreads.mapTo(HashSet()) { it.threadRoot }
                for (local in conversationDao.getAll()) {
                    if (local.remoteThreadRoot != null && local.remoteThreadRoot !in roots) {
                        conversationDao.deleteById(local.id)
                    }
                }
            }
        }
    }

    suspend fun syncConversationFromRemote(id: Uuid): Result<Unit> = runCatching {
        val session = workflowRepository.session.value ?: return@runCatching
        val jobId = conversationDao.getById(id.toString())?.remoteJobId ?: return@runCatching
        syncRemoteJob(session, id, jobId, previous = null)
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    internal suspend fun syncRemoteJob(
        session: WorkflowSession,
        id: Uuid,
        jobId: String,
        previous: RemoteThreadConversation?,
        refreshJob: Boolean = true,
    ): RemoteThreadConversation? = conversationLock(id).withLock {
        if (workflowRepository.session.value != session || id in submittingIds.value) return@withLock null
        val stored = conversationDao.getById(id.toString()) ?: return@withLock null
        if (stored.remoteJobId != jobId) return@withLock null
        // A manual refresh can have committed a newer job status since this worker last fetched it.
        val remote = cloudClient.fetchThreadConversation(jobId, previous, refreshJob || stored.remoteStatus != previous?.job?.status)
        // Room observers can stop this worker as soon as the terminal status is committed.
        withContext(NonCancellable) {
            val completion = database.withTransaction {
                val existing = conversationDao.getById(id.toString()) ?: return@withTransaction null
                if (existing.remoteJobId != jobId || workflowRepository.session.value != session || id in submittingIds.value) {
                    return@withTransaction null
                }

                val status = if (remote.isActive && !PocketBaseAgentCloudClient.isActiveStatus(remote.job.status)) {
                    remote.message?.status ?: remote.job.status
                } else {
                    remote.job.status
                }
                val finished = !remote.isActive && PocketBaseAgentCloudClient.isActiveStatus(existing.remoteStatus.orEmpty())
                val succeeded = status == "succeeded"
                val becameReadable = succeeded && (finished || existing.remoteStatus in setOf("failed", "partial"))
                val updated = existing.copy(
                    title = remote.job.title.ifBlank { existing.title },
                    updateAt = maxOf(
                        existing.updateAt,
                        remoteTime(remote.job.updated, existing.updateAt),
                        remoteTime(remote.message?.updated.orEmpty(), existing.updateAt),
                    ),
                    remoteStatus = status,
                    lastRemoteMessageId = remote.message?.id,
                    hasUnread = id != visibleConversation && (existing.hasUnread || becameReadable),
                )
                saveNodesForConversation(existing.id, remote.messageNodes)
                val usage = if (finished) saveCompletedUsage(existing, remote) else null
                val event = if (finished && (succeeded || status == "failed")) {
                    val preview = if (succeeded) {
                        remote.messageNodes.lastOrNull()?.currentMessage?.toText().orEmpty()
                    } else {
                        remote.message?.error.orEmpty().ifBlank { remote.job.error.ifBlank { "任务执行失败" } }
                    }
                    AppEvent.AgentJobFinished(id, updated.title, succeeded, preview.trim().replace('\n', ' ').take(60))
                } else {
                    null
                }
                if (updated != existing || existing.lastSyncedAt == 0L) {
                    conversationDao.upsert(updated.copy(lastSyncedAt = System.currentTimeMillis()))
                }
                event to usage
            }
            if (completion != null && workflowRepository.session.value == session) {
                val (event, usage) = completion
                event?.let(appEventBus::tryEmit)
                if (usage != null) appScope.launch {
                    if (workflowRepository.session.value == session) uploadUsage(usage)
                }
            }
        }
        remote
    }

    fun submitMessage(
        conversation: Conversation,
        prompt: String,
        sources: List<RemoteAgentSourceInput>,
        parentMessageId: String?,
        replacesMessageId: String?,
        title: String,
        model: String? = null,
        thinkingLevel: String? = null,
        systemPrompt: String? = null,
    ): Deferred<Result<Unit>> {
        val session = workflowRepository.session.value
        _submittingIds.update { it + conversation.id }
        // Commit the job-to-conversation mapping even if navigation clears the submitting ViewModel.
        return appScope.async {
            try {
                runCatching {
                    threadSyncMutex.withLock {
                        check(session != null && workflowRepository.session.value == session) { "请先连接 AnyWorkflow" }
                        val submitted = cloudClient.submitMessage(
                            prompt = prompt,
                            sources = sources,
                            parentMessageId = parentMessageId,
                            replacesMessageId = replacesMessageId,
                            title = title,
                            modelOverride = model,
                            thinkingLevelOverride = thinkingLevel,
                            systemPromptOverride = systemPrompt,
                        )
                        if (workflowRepository.session.value == session) {
                            saveConversation(conversation, submitted.jobId, submitted.threadRoot, submitted.status)
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            } finally {
                _submittingIds.update { it - conversation.id }
            }
        }
    }

    suspend fun cancelConversationJob(id: Uuid): Result<Unit> = runCatching {
        val existing = conversationDao.getById(id.toString()) ?: return@runCatching
        val jobId = existing.remoteJobId ?: return@runCatching
        if (PocketBaseAgentCloudClient.isActiveStatus(existing.remoteStatus.orEmpty())) {
            cloudClient.cancelJob(jobId)
            syncConversationFromRemote(id).getOrThrow()
        }
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun saveConversation(
        conversation: Conversation,
        remoteJobId: String? = null,
        remoteThreadRoot: String? = null,
        remoteStatus: String? = null,
    ) = conversationLock(conversation.id).withLock {
        database.withTransaction {
            val convId = conversation.id.toString()
            val existing = conversationDao.getById(convId)
            val entity = AgentConversationEntity(
                id = convId,
                title = conversation.title,
                createAt = conversation.createAt.toEpochMilli(),
                updateAt = conversation.updateAt.toEpochMilli(),
                isPinned = existing?.isPinned ?: conversation.isPinned,
                folderId = existing?.folderId ?: conversation.folderId?.toString(),
                assistantId = conversation.assistantId.toString(),
                remoteJobId = remoteJobId ?: existing?.remoteJobId,
                remoteThreadRoot = remoteThreadRoot ?: existing?.remoteThreadRoot,
                remoteStatus = remoteStatus ?: existing?.remoteStatus,
                lastRemoteMessageId = existing?.lastRemoteMessageId?.takeIf { remoteJobId == null || remoteJobId == existing.remoteJobId },
                lastSyncedAt = existing?.lastSyncedAt?.takeIf { remoteJobId == null || remoteJobId == existing.remoteJobId } ?: 0L,
                hasUnread = existing?.hasUnread == true,
            )
            if (entity != existing) conversationDao.upsert(entity)
            saveNodesForConversation(convId, conversation.messageNodes)
        }
    }

    internal suspend fun showConversation(id: Uuid?) {
        visibleConversation = id
        if (id != null) conversationDao.updateUnread(id.toString(), false)
    }

    suspend fun getDraft(conversationId: Uuid): String? = draftMutex.withLock {
        draftDao.getByConversationId(conversationId.toString())?.text
    }

    fun saveDraft(conversationId: Uuid, text: String) = appScope.launch(start = CoroutineStart.UNDISPATCHED) {
        draftMutex.withLock {
            val id = conversationId.toString()
            if (text.isEmpty()) {
                draftDao.deleteByConversationId(id)
            } else {
                draftDao.upsert(
                    AgentDraftEntity(
                        conversationId = id,
                        text = text,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private fun conversationLock(id: Uuid): Mutex = conversationLocks[(id.hashCode() and Int.MAX_VALUE) % conversationLocks.size]

    private fun remoteTime(value: String, fallback: Long): Long =
        PocketBaseAgentCloudClient.parseIsoMillis(value).takeIf { it > 0 } ?: fallback

    private suspend fun saveNodesForConversation(convId: String, nodes: List<MessageNode>) {
        val existing = messageNodeDao.getByConversationId(convId).associateByTo(HashMap()) { it.id }
        val changed = nodes.mapIndexedNotNull { index, node ->
            val entity = AgentMessageNodeEntity(
                id = node.id.toString(),
                conversationId = convId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex,
            )
            entity.takeUnless { it == existing.remove(it.id) }
        }
        if (existing.isNotEmpty()) messageNodeDao.deleteNodes(existing.values.toList())
        if (changed.isNotEmpty()) messageNodeDao.upsertAll(changed)
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

    private suspend fun saveCompletedUsage(
        conversation: AgentConversationEntity,
        remote: RemoteThreadConversation,
    ): AgentUsageRecordEntity? {
        val message = remote.message ?: return null
        val usage = message.usage
        if (usage.totalTokenCount <= 0 && usage.promptTokenCount <= 0 && usage.candidatesTokenCount <= 0) return null
        val id = PocketBaseAgentCloudClient.stableUuid("aw-agent-usage:${message.id}").toString()
        if (usageRecordDao.getById(id) != null) return null
        val model = remote.job.model.ifBlank { "Antigravity" }
        return AgentUsageRecordEntity(
            id = id,
            conversationId = conversation.id,
            assistantId = conversation.assistantId ?: DEFAULT_ASSISTANT_ID.toString(),
            modelId = model,
            modelName = model,
            providerId = "anyworkflow",
            promptTokens = usage.promptTokenCount.toLong(),
            completionTokens = usage.candidatesTokenCount.toLong(),
            cachedTokens = usage.cachedContentTokenCount.toLong(),
            reasoningTokens = usage.thoughtsTokenCount.toLong(),
            totalTokens = usage.totalTokenCount.takeIf { it > 0 }?.toLong()
                ?: (usage.promptTokenCount.toLong() + usage.candidatesTokenCount),
            durationMs = remote.messageNodes.lastOrNull()?.currentMessage?.generationTimeMs ?: 0L,
            source = "agent",
            syncStatus = SyncStatus.PENDING,
            createdAt = System.currentTimeMillis(),
        ).also { usageRecordDao.upsert(it) }
    }

    private suspend fun uploadUsage(item: AgentUsageRecordEntity) {
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

    suspend fun syncUsageWithRemote(): Result<Unit> = runCatching {
        if (!cloudClient.hasSession()) return@runCatching
        val pending = usageRecordDao.getPendingSync()
        for (item in pending) uploadUsage(item)
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

    // ---- Local-only Agent Models (Room DB, no cloud sync) ----

    fun observeModels(): Flow<List<AgentModelEntity>> = modelDao.observeAll()

    suspend fun ensureDefaultModels(): List<AgentModelEntity> {
        val existing = modelDao.getAll()
        if (existing.isNotEmpty()) return existing
        val now = System.currentTimeMillis()
        val defaults = listOf(
            AgentModelEntity(
                id = "builtin-antigravity",
                modelId = "Antigravity",
                displayName = "Antigravity",
                category = AgentModelCategory.AGENT.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
            ),
            AgentModelEntity(
                id = "builtin-gemini-2.5-flash",
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                category = AgentModelCategory.CHAT.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                sortOrder = 1,
                createdAt = now,
                updatedAt = now,
            ),
            AgentModelEntity(
                id = "builtin-gemini-2.5-pro",
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                category = AgentModelCategory.CHAT.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                sortOrder = 2,
                createdAt = now,
                updatedAt = now,
            ),
            AgentModelEntity(
                id = "builtin-gemini-3-flash-preview",
                modelId = "gemini-3-flash-preview",
                displayName = "Gemini 3 Flash Preview",
                category = AgentModelCategory.CHAT.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                sortOrder = 3,
                createdAt = now,
                updatedAt = now,
            ),
            AgentModelEntity(
                id = "builtin-gemini-decision",
                modelId = "gemini-decision",
                displayName = "Gemini Decision",
                category = AgentModelCategory.DECISION.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                sortOrder = 4,
                createdAt = now,
                updatedAt = now,
            ),
        )
        modelDao.insertAllIgnore(defaults)
        return modelDao.getAll()
    }

    suspend fun saveLocalModel(model: AgentModelEntity): AgentModelEntity {
        val now = System.currentTimeMillis()
        val normalized = model.copy(
            modelId = model.modelId.trim(),
            displayName = model.displayName.trim().ifBlank { model.modelId.trim() },
            baseUrl = model.baseUrl.trim().ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
            apiKey = model.apiKey.trim(),
            updatedAt = now,
        )
        modelDao.upsert(normalized)
        return normalized
    }

    suspend fun deleteLocalModel(id: String) {
        modelDao.deleteById(id)
    }
}
