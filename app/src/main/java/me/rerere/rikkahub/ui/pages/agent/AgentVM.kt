package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentAssistantEntity
import me.rerere.rikkahub.data.agent.AgentCloudClient
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.PocketBaseAgentCloudClient
import me.rerere.rikkahub.data.agent.RemoteAgentSourceInput
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import java.io.File
import java.net.URI
import java.time.Instant
import kotlin.uuid.Uuid

class AgentVM(
    id: String,
    private val repository: AgentRepository,
    private val cloudClient: AgentCloudClient,
    private val settingsStore: AgentSettingsStore,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)

    private val _conversation = MutableStateFlow(
        Conversation(
            id = conversationId,
            assistantId = DEFAULT_ASSISTANT_ID,
            title = "",
            messageNodes = emptyList(),
            newConversation = true,
        )
    )
    val conversation: StateFlow<Conversation> = _conversation.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _agentAssistant = MutableStateFlow(
        Assistant(
            id = DEFAULT_ASSISTANT_ID,
            name = "Agent",
        )
    )
    val agentAssistant: StateFlow<Assistant> = _agentAssistant.asStateFlow()

    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _activeStatusText = MutableStateFlow<String?>(null)
    val activeStatusText: StateFlow<String?> = _activeStatusText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showConnectDialog = MutableStateFlow(false)
    val showConnectDialog: StateFlow<Boolean> = _showConnectDialog.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    val session: StateFlow<WorkflowSession?> = workflowRepository.session

    val uiSettings: StateFlow<AgentUiSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current)

    private var generationJob: Job? = null
    private var activeJobId: String? = null
    private var activeThreadRoot: String? = null
    private var lastRemoteMessageId: String? = null

    init {
        viewModelScope.launch {
            try {
                val localAssistant = repository.ensureDefaultAssistant()
                _agentAssistant.value = localAssistant.toDomainAssistant()

                val snapshot = repository.getConversationSnapshot(conversationId)
                if (snapshot != null) {
                    _conversation.value = snapshot.conversation
                    activeJobId = snapshot.remoteJobId
                    activeThreadRoot = snapshot.remoteThreadRoot
                    lastRemoteMessageId = snapshot.lastRemoteMessageId
                }
                if (cloudClient.hasSession()) {
                    syncFromRemoteInternal()
                }
            } finally {
                _isInitialLoading.value = false
            }
        }
    }

    fun updateAgentModel(model: Model) {
        _selectedModel.value = model
        val updated = _agentAssistant.value.copy(chatModelId = model.id)
        _agentAssistant.value = updated
        persistAssistant(updated)
    }

    fun updateAgentAssistant(assistant: Assistant) {
        _agentAssistant.value = assistant
        persistAssistant(assistant)
    }

    private fun persistAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertAssistant(
                AgentAssistantEntity(
                    id = assistant.id.toString(),
                    name = assistant.name.ifBlank { "Agent" },
                    systemPrompt = assistant.systemPrompt,
                    modelId = assistant.chatModelId?.toString(),
                    reasoningLevel = assistant.reasoningLevel.name,
                    temperature = assistant.temperature,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    private fun AgentAssistantEntity.toDomainAssistant(): Assistant {
        val parsedId = runCatching { Uuid.parse(id) }.getOrDefault(DEFAULT_ASSISTANT_ID)
        val parsedModelId = modelId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val parsedReasoning = runCatching { ReasoningLevel.valueOf(reasoningLevel) }
            .getOrDefault(ReasoningLevel.OFF)
        return Assistant(
            id = parsedId,
            name = name,
            systemPrompt = systemPrompt,
            chatModelId = parsedModelId,
            reasoningLevel = parsedReasoning,
            temperature = temperature,
        )
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun openConnectDialog() {
        _connectError.value = null
        _showConnectDialog.value = true
    }

    fun dismissConnectDialog() {
        _showConnectDialog.value = false
        _connectError.value = null
    }

    fun connectAccount(
        identity: String,
        password: String,
        baseUrl: String = DEFAULT_POCKETBASE_URL,
    ) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        _connectError.value = null
        viewModelScope.launch {
            val result = workflowRepository.login(identity, password, baseUrl)
            _isConnecting.value = false
            result.onSuccess {
                _showConnectDialog.value = false
                repository.syncThreadsFromRemote()
                syncFromRemoteInternal()
            }.onFailure { err ->
                _connectError.value = err.message ?: "连接失败"
            }
        }
    }

    fun syncFromRemote() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            syncFromRemoteInternal()
        }
    }

    private suspend fun syncFromRemoteInternal() {
        if (!cloudClient.hasSession()) return
        _isSyncing.value = true
        try {
            val synced = repository.syncConversationFromRemote(conversationId).getOrNull()
            if (synced != null) {
                _conversation.value = synced.conversation
                activeJobId = synced.remoteJobId
                activeThreadRoot = synced.remoteThreadRoot
                lastRemoteMessageId = synced.lastRemoteMessageId
                if (synced.isActive && !synced.remoteJobId.isNullOrBlank()) {
                    val lastNodeId = synced.conversation.messageNodes.lastOrNull()?.id
                    observeRemoteJob(
                        jobId = synced.remoteJobId,
                        targetNodeId = lastNodeId,
                        existingAssistantId = synced.conversation.messageNodes.lastOrNull()?.currentMessage?.id,
                    )
                }
            }
        } finally {
            _isSyncing.value = false
        }
    }

    fun sendMessage(parts: List<UIMessagePart>) {
        if (parts.isEmpty() || _isGenerating.value) return
        if (!cloudClient.hasSession()) {
            openConnectDialog()
            return
        }
        _errorMessage.value = null

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = parts,
        )

        val current = _conversation.value
        val nextTitle = if (current.title.isBlank()) {
            userMessage.toText().trim()
                .replace('\n', ' ')
                .take(36)
                .ifBlank { "Antigravity Agent" }
        } else {
            current.title
        }

        val updatedWithUser = current.copy(
            title = nextTitle,
            messageNodes = current.messageNodes + userMessage.toMessageNode(),
            updateAt = Instant.now(),
            newConversation = false,
        )
        _conversation.value = updatedWithUser

        viewModelScope.launch {
            repository.saveConversation(
                conversation = updatedWithUser,
                remoteJobId = activeJobId,
                remoteThreadRoot = activeThreadRoot,
                lastRemoteMessageId = lastRemoteMessageId,
            )
        }

        val prompt = buildPromptFromParts(parts)
        val sources = extractSourcesFromParts(parts)

        submitAndObserveRemote(
            baseConversation = updatedWithUser,
            prompt = prompt,
            sources = sources,
            parentMessageId = lastRemoteMessageId,
            replacesMessageId = null,
            title = if (current.title.isBlank()) nextTitle else "",
            targetNodeId = null,
        )
    }

    fun stopGeneration() {
        val jobIdToCancel = activeJobId
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _activeStatusText.value = null
        if (!jobIdToCancel.isNullOrBlank() && cloudClient.hasSession()) {
            viewModelScope.launch {
                runCatching { cloudClient.cancelJob(jobIdToCancel) }
                syncFromRemoteInternal()
            }
        } else {
            val snapshot = _conversation.value
            if (!snapshot.newConversation) {
                viewModelScope.launch {
                    repository.saveConversation(snapshot)
                }
            }
        }
    }

    fun regenerate(message: UIMessage) {
        if (_isGenerating.value) return
        if (!cloudClient.hasSession()) {
            openConnectDialog()
            return
        }
        val current = _conversation.value
        val nodeIndex = current.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == message.id }
        }
        if (nodeIndex < 0) return

        val node = current.messageNodes[nodeIndex]
        val historyBefore = current.messageNodes.take(nodeIndex).map { it.currentMessage }
        val userMsg = historyBefore.lastOrNull { it.role == MessageRole.USER } ?: return

        val prompt = buildPromptFromParts(userMsg.parts)
        val sources = extractSourcesFromParts(userMsg.parts)

        submitAndObserveRemote(
            baseConversation = current,
            prompt = prompt,
            sources = sources,
            parentMessageId = null,
            replacesMessageId = lastRemoteMessageId,
            title = "",
            targetNodeId = node.id,
        )
    }

    fun editMessage(message: UIMessage, newParts: List<UIMessagePart>) {
        if (_isGenerating.value || newParts.isEmpty()) return
        val current = _conversation.value
        val newNodes = current.messageNodes.map { node ->
            if (node.messages.any { it.id == message.id }) {
                node.copy(
                    messages = node.messages.map { msg ->
                        if (msg.id == message.id) msg.copy(parts = newParts) else msg
                    }
                )
            } else {
                node
            }
        }
        val updated = current.copy(
            messageNodes = newNodes,
            updateAt = Instant.now(),
        )
        _conversation.value = updated
        viewModelScope.launch {
            repository.saveConversation(updated)
        }
    }

    fun deleteMessage(message: UIMessage) {
        if (_isGenerating.value) return
        val current = _conversation.value
        val newNodes = current.messageNodes.mapNotNull { node ->
            if (node.messages.any { it.id == message.id }) {
                val remaining = node.messages.filterNot { it.id == message.id }
                if (remaining.isEmpty()) {
                    null
                } else {
                    node.copy(
                        messages = remaining,
                        selectIndex = node.selectIndex.coerceIn(0, remaining.lastIndex),
                    )
                }
            } else {
                node
            }
        }
        val updated = current.copy(
            messageNodes = newNodes,
            updateAt = Instant.now(),
        )
        _conversation.value = updated
        viewModelScope.launch {
            repository.saveConversation(updated)
        }
    }

    fun updateMessageNode(node: MessageNode) {
        val current = _conversation.value
        val newNodes = current.messageNodes.map {
            if (it.id == node.id) node else it
        }
        val updated = current.copy(
            messageNodes = newNodes,
            updateAt = Instant.now(),
        )
        _conversation.value = updated
        viewModelScope.launch {
            repository.saveConversation(updated)
        }
    }

    fun renameConversation(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val updated = _conversation.value.copy(
            title = trimmed,
            updateAt = Instant.now(),
        )
        _conversation.value = updated
        viewModelScope.launch {
            if (!updated.newConversation) {
                repository.renameConversation(conversationId, trimmed)
            }
        }
    }

    fun clearMessages() {
        if (_isGenerating.value) return
        val updated = _conversation.value.copy(
            messageNodes = emptyList(),
            updateAt = Instant.now(),
        )
        _conversation.value = updated
        viewModelScope.launch {
            if (!updated.newConversation) {
                repository.saveConversation(updated)
            }
        }
    }

    private fun submitAndObserveRemote(
        baseConversation: Conversation,
        prompt: String,
        sources: List<RemoteAgentSourceInput>,
        parentMessageId: String?,
        replacesMessageId: String?,
        title: String,
        targetNodeId: Uuid?,
    ) {
        generationJob?.cancel()
        _isGenerating.value = true
        _activeStatusText.value = "正在触发云端 Action..."

        val assistantMsgId = Uuid.random()
        val nodeId = targetNodeId ?: Uuid.random()
        val placeholderMsg = UIMessage(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("")),
            modelSnapshot = ModelSnapshot(
                displayName = "Antigravity Agent",
                modelId = "Antigravity",
            ),
        )

        val initialConv = if (targetNodeId != null) {
            baseConversation.upsertMessage(nodeId, placeholderMsg)
        } else {
            val newNode = MessageNode(
                id = nodeId,
                messages = listOf(placeholderMsg),
                selectIndex = 0,
            )
            baseConversation.copy(messageNodes = baseConversation.messageNodes + newNode)
        }
        _conversation.value = initialConv

        generationJob = viewModelScope.launch {
            try {
                val submitted = cloudClient.submitMessage(
                    prompt = prompt,
                    sources = sources,
                    parentMessageId = parentMessageId,
                    replacesMessageId = replacesMessageId,
                    title = title,
                )
                activeJobId = submitted.jobId
                activeThreadRoot = submitted.threadRoot
                _activeStatusText.value = "Action 已触发 · 等待步骤写入"

                repository.saveConversation(
                    conversation = _conversation.value,
                    remoteJobId = submitted.jobId,
                    remoteThreadRoot = submitted.threadRoot,
                    remoteStatus = submitted.status,
                )

                collectJobUpdates(
                    jobId = submitted.jobId,
                    nodeId = nodeId,
                    assistantMsgId = assistantMsgId,
                )
            } catch (e: Throwable) {
                _errorMessage.value = e.message ?: "触发 Agent Action 失败"
                val errConv = _conversation.value.upsertMessage(
                    nodeId,
                    UIMessage(
                        id = assistantMsgId,
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Notice(
                                title = "请求失败",
                                text = e.message ?: "无法连接到远端数据库",
                            )
                        ),
                        modelSnapshot = ModelSnapshot(
                            displayName = "Antigravity Agent",
                            modelId = "Antigravity",
                        ),
                    ),
                ).copy(updateAt = Instant.now())
                _conversation.value = errConv
                repository.saveConversation(errConv)
            } finally {
                _isGenerating.value = false
                _activeStatusText.value = null
            }
        }
    }

    private fun observeRemoteJob(
        jobId: String,
        targetNodeId: Uuid?,
        existingAssistantId: Uuid?,
    ) {
        generationJob?.cancel()
        _isGenerating.value = true
        _activeStatusText.value = "正在同步远端步骤..."
        val nodeId = targetNodeId ?: Uuid.random()
        val assistantMsgId = existingAssistantId ?: Uuid.random()

        generationJob = viewModelScope.launch {
            try {
                collectJobUpdates(
                    jobId = jobId,
                    nodeId = nodeId,
                    assistantMsgId = assistantMsgId,
                )
            } catch (e: Throwable) {
                _errorMessage.value = e.message ?: "同步 Agent 步骤失败"
            } finally {
                _isGenerating.value = false
                _activeStatusText.value = null
            }
        }
    }

    private suspend fun collectJobUpdates(
        jobId: String,
        nodeId: Uuid,
        assistantMsgId: Uuid,
    ) {
        cloudClient.observeJobUpdates(jobId).collect { update ->
            val msg = update.message
            if (msg != null) {
                lastRemoteMessageId = msg.id
            }
            val statusLabel = when (update.job.status) {
                "queued" -> "排队中 · 等待执行器领取"
                "running" -> "执行中 · 已同步 ${update.parts.size} 个步骤"
                "completed" -> null
                "failed" -> "执行失败"
                "canceled" -> "已取消"
                else -> update.job.status
            }
            _activeStatusText.value = statusLabel

            val uiMessage = cloudClient.buildAssistantUIMessage(
                messageId = assistantMsgId,
                job = update.job,
                message = msg,
                parts = update.parts,
            )

            val currentConv = _conversation.value
            val existingMsg = currentConv.messageNodes
                .firstOrNull { it.id == nodeId }
                ?.currentMessage
            val nextTitle = update.job.title.ifBlank { currentConv.title }
            val hasMessageChanged = existingMsg == null ||
                existingMsg.parts != uiMessage.parts ||
                existingMsg.annotations != uiMessage.annotations ||
                existingMsg.usage != uiMessage.usage ||
                existingMsg.stepCount != uiMessage.stepCount ||
                existingMsg.generationTimeMs != uiMessage.generationTimeMs ||
                (existingMsg.finishedAt == null) != (uiMessage.finishedAt == null)
            val hasTitleChanged = nextTitle != currentConv.title

            if (hasMessageChanged || hasTitleChanged || update.isTerminal) {
                val updatedConv = if (hasMessageChanged || hasTitleChanged) {
                    currentConv
                        .upsertMessage(nodeId, uiMessage)
                        .copy(
                            title = nextTitle,
                            updateAt = Instant.now(),
                        ).also { _conversation.value = it }
                } else {
                    currentConv
                }

                repository.saveConversation(
                    conversation = updatedConv,
                    remoteJobId = update.job.id,
                    remoteThreadRoot = update.job.threadRoot.ifBlank { update.job.id },
                    remoteStatus = update.job.status,
                    lastRemoteMessageId = msg?.id,
                )

                if (update.isTerminal && msg != null && (msg.usage.totalTokenCount > 0 || msg.usage.promptTokenCount > 0)) {
                    repository.recordUsage(
                        conversationId = updatedConv.id.toString(),
                        assistantId = _agentAssistant.value.id.toString(),
                        modelId = update.job.model.ifBlank { "Antigravity" },
                        modelName = update.job.model.ifBlank { "Antigravity" },
                        providerId = "anyworkflow",
                        promptTokens = msg.usage.promptTokenCount.toLong(),
                        completionTokens = msg.usage.candidatesTokenCount.toLong(),
                        cachedTokens = msg.usage.cachedContentTokenCount.toLong(),
                        reasoningTokens = msg.usage.thoughtsTokenCount.toLong(),
                        durationMs = uiMessage.generationTimeMs ?: 0L,
                    )
                }
            }
        }
    }

    private fun buildPromptFromParts(parts: List<UIMessagePart>): String {
        val text = parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
            .trim()
        if (text.isNotEmpty()) return text
        val docNames = parts.filterIsInstance<UIMessagePart.Document>().map { it.fileName }
        return if (docNames.isNotEmpty()) {
            "请处理附件文件：${docNames.joinToString(", ")}"
        } else {
            ""
        }
    }

    private fun extractSourcesFromParts(parts: List<UIMessagePart>): List<RemoteAgentSourceInput> {
        return parts.filterIsInstance<UIMessagePart.Document>().mapNotNull { doc ->
            val content = runCatching {
                val path = URI(doc.url).path
                if (path != null) File(path).readText(Charsets.UTF_8) else ""
            }.getOrDefault("")
            if (content.isBlank()) {
                null
            } else {
                RemoteAgentSourceInput(
                    id = doc.url.hashCode().toUInt().toString(16),
                    name = doc.fileName.ifBlank { "document.md" },
                    content = content,
                )
            }
        }
    }
}
