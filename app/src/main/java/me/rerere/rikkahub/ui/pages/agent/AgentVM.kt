package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentAssistantEntity
import me.rerere.rikkahub.data.agent.AgentConversationSnapshot
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
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

    private val snapshot = MutableStateFlow<AgentConversationSnapshot?>(null)

    val isGenerating: StateFlow<Boolean> = combine(snapshot, repository.submittingIds) { cached, submitting ->
        cached?.isActive == true || conversationId in submitting
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = combine(_isSyncing, snapshot, workflowRepository.session) { manual, cached, session ->
        manual || (session != null && cached?.waitingForMessages == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStatusText: StateFlow<String?> = combine(isGenerating, snapshot) { generating, cached ->
        if (!generating) {
            null
        } else {
            val latestAssistant = cached?.conversation?.messageNodes?.lastOrNull()?.currentMessage
                ?.takeIf { it.role == MessageRole.ASSISTANT }
            val createdMs = latestAssistant?.createdAt
                ?.toInstant(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds()
                ?.takeIf { it > 0L && it <= System.currentTimeMillis() }
            createdMs ?: cached?.conversation?.updateAt?.toEpochMilli()
                ?.takeIf { it > 0L && it <= System.currentTimeMillis() }
                ?: System.currentTimeMillis()
        }
    }.flatMapLatest { startMs ->
        if (startMs == null) {
            flowOf<String?>(null)
        } else {
            flow {
                while (true) {
                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
                    emit(formatAgentElapsed(elapsedSec))
                    delay(1000L)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    val selectedAgentModelName: StateFlow<String> = settingsStore.settingsFlow
        .map { it.selectedAgentModel.ifBlank { "Antigravity" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.selectedAgentModel.ifBlank { "Antigravity" })

    val customGeminiModels: StateFlow<List<String>> = settingsStore.settingsFlow
        .map { it.customGeminiModels }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.customGeminiModels)

    val availableAgentModels: StateFlow<List<String>> = settingsStore.settingsFlow
        .map { (DEFAULT_AGENT_MODELS + it.customGeminiModels).distinct() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (DEFAULT_AGENT_MODELS + settingsStore.current.customGeminiModels).distinct(),
        )

    private val _initialDraft = MutableStateFlow<String?>(null)
    val initialDraft: StateFlow<String?> = _initialDraft.asStateFlow()

    fun saveDraft(text: String) {
        _initialDraft.value = text
        repository.saveDraft(conversationId, text)
    }

    private var submission: Deferred<Result<Unit>>? = null
    private val isBusy: Boolean
        get() = snapshot.value?.isActive == true || conversationId in repository.submittingIds.value ||
            _isInitialLoading.value || (session.value != null && snapshot.value?.waitingForMessages == true)

    init {
        viewModelScope.launch {
            repository.observeConversation(conversationId).collect { cached ->
                if (cached != null) {
                    _conversation.value = cached.conversation
                } else if (snapshot.value != null) {
                    _conversation.value = Conversation(
                        id = conversationId,
                        assistantId = DEFAULT_ASSISTANT_ID,
                        title = "",
                        messageNodes = emptyList(),
                        newConversation = true,
                    )
                }
                snapshot.value = cached
                _isInitialLoading.value = false
            }
        }
        viewModelScope.launch {
            _initialDraft.value = repository.getDraft(conversationId).orEmpty()
            _agentAssistant.value = repository.ensureDefaultAssistant().toDomainAssistant()
        }
    }

    fun selectAgentModel(modelName: String) {
        val normalized = modelName.trim()
        if (normalized.isEmpty()) return
        settingsStore.update { it.copy(selectedAgentModel = normalized) }
    }

    fun addCustomGeminiModel(modelName: String) {
        val normalized = modelName.trim()
        if (normalized.isEmpty()) return
        settingsStore.update { current ->
            val updatedCustom = if (normalized in DEFAULT_AGENT_MODELS || normalized in current.customGeminiModels) {
                current.customGeminiModels
            } else {
                current.customGeminiModels + normalized
            }
            current.copy(
                selectedAgentModel = normalized,
                customGeminiModels = updatedCustom,
            )
        }
    }

    fun removeCustomGeminiModel(modelName: String) {
        settingsStore.update { current ->
            val updatedCustom = current.customGeminiModels.filterNot { it == modelName }
            val nextSelected = if (current.selectedAgentModel == modelName) "Antigravity" else current.selectedAgentModel
            current.copy(
                selectedAgentModel = nextSelected,
                customGeminiModels = updatedCustom,
            )
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
                repository.syncUsageWithRemote()
            }.onFailure { err ->
                _connectError.value = err.message ?: "连接失败"
            }
        }
    }

    fun syncFromRemote() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                repository.syncConversationFromRemote(conversationId).onFailure {
                    _errorMessage.value = it.message ?: "同步 Agent 步骤失败"
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun sendMessage(parts: List<UIMessagePart>): Boolean {
        if (parts.isEmpty() || isBusy) return false
        if (session.value == null) {
            openConnectDialog()
            return false
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
        val prompt = buildPromptFromParts(parts)
        val sources = extractSourcesFromParts(parts)

        submitRemote(
            baseConversation = updatedWithUser,
            prompt = prompt,
            sources = sources,
            parentMessageId = snapshot.value?.lastRemoteMessageId,
            replacesMessageId = null,
            title = if (current.title.isBlank()) nextTitle else "",
            targetNodeId = null,
        )
        saveDraft("")
        return true
    }

    fun stopGeneration() {
        viewModelScope.launch {
            if (submission?.await()?.isFailure == true) return@launch
            repository.cancelConversationJob(conversationId).onFailure {
                _errorMessage.value = it.message ?: "取消 Agent 任务失败"
            }
        }
    }

    fun regenerate(message: UIMessage) {
        if (isBusy) return
        if (session.value == null) {
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

        submitRemote(
            baseConversation = current,
            prompt = prompt,
            sources = sources,
            parentMessageId = null,
            replacesMessageId = snapshot.value?.lastRemoteMessageId,
            title = "",
            targetNodeId = node.id,
        )
    }

    fun editMessage(message: UIMessage, newParts: List<UIMessagePart>) {
        if (isBusy || newParts.isEmpty()) return
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
        viewModelScope.launch {
            repository.saveConversation(updated)
        }
    }

    fun deleteMessage(message: UIMessage) {
        if (isBusy) return
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
        viewModelScope.launch {
            repository.saveConversation(updated)
        }
    }

    fun updateMessageNode(node: MessageNode) {
        if (isBusy) return
        val current = _conversation.value
        val newNodes = current.messageNodes.map {
            if (it.id == node.id) node else it
        }
        val updated = current.copy(
            messageNodes = newNodes,
            updateAt = Instant.now(),
        )
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
        if (updated.newConversation) {
            _conversation.value = updated
        } else {
            viewModelScope.launch {
                repository.renameConversation(conversationId, trimmed)
            }
        }
    }

    fun clearMessages() {
        if (isBusy) return
        val updated = _conversation.value.copy(
            messageNodes = emptyList(),
            updateAt = Instant.now(),
        )
        if (!updated.newConversation) {
            viewModelScope.launch {
                repository.saveConversation(updated)
            }
        }
    }

    private fun ReasoningLevel.toGeminiThinkingLevel(): String = when (this) {
        ReasoningLevel.OFF, ReasoningLevel.AUTO -> ""
        ReasoningLevel.LOW -> "low"
        ReasoningLevel.MEDIUM -> "medium"
        ReasoningLevel.HIGH, ReasoningLevel.XHIGH, ReasoningLevel.MAX -> "high"
    }

    private fun submitRemote(
        baseConversation: Conversation,
        prompt: String,
        sources: List<RemoteAgentSourceInput>,
        parentMessageId: String?,
        replacesMessageId: String?,
        title: String,
        targetNodeId: Uuid?,
    ) {
        val modelName = selectedAgentModelName.value.ifBlank { "Antigravity" }
        val thinkingLevel = _agentAssistant.value.reasoningLevel.toGeminiThinkingLevel()
        val systemPrompt = _agentAssistant.value.systemPrompt.takeIf { it.isNotBlank() }
        val assistantMsgId = Uuid.random()
        val nodeId = targetNodeId ?: Uuid.random()
        val placeholderMsg = UIMessage(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("")),
            modelSnapshot = ModelSnapshot(
                displayName = modelName,
                modelId = modelName,
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
        val task = repository.submitMessage(
            conversation = initialConv,
            prompt = prompt,
            sources = sources,
            parentMessageId = parentMessageId,
            replacesMessageId = replacesMessageId,
            title = title,
            model = modelName,
            thinkingLevel = thinkingLevel,
            systemPrompt = systemPrompt,
        )
        submission = task
        viewModelScope.launch {
            task.await().onFailure { error ->
                val message = error.message ?: "触发 Agent Action 失败"
                _errorMessage.value = message
                val errorMessage = placeholderMsg.copy(parts = listOf(UIMessagePart.Notice(title = "请求失败", text = message)))
                repository.saveConversation(initialConv.upsertMessage(nodeId, errorMessage))
            }
            if (submission === task) submission = null
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
