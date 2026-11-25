package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentApproval
import me.rerere.rikkahub.data.agent.acpAgentName
import me.rerere.rikkahub.data.agent.AgentAssistantEntity
import me.rerere.rikkahub.data.agent.AgentConversationSnapshot
import me.rerere.rikkahub.data.agent.isAntigravityAgentModel
import me.rerere.rikkahub.data.agent.isAntigravityCliModel
import me.rerere.rikkahub.data.agent.isCodexAgentModel
import me.rerere.rikkahub.data.agent.isCommandCodeModel
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.RemoteAgentSourceInput
import me.rerere.rikkahub.data.agent.RemoteHistoryTurn
import me.rerere.rikkahub.data.agent.isAgentImageUrl
import me.rerere.rikkahub.data.datastore.ANTIGRAVITY_CLI_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.ANTIGRAVITY_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.CLAUDE_CODE_GPT_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.CODEX_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.COMMAND_CODE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.OPENCODE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.TOP_AGENT_PROVIDER_IDS
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.isClaudeCodeProvider
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
    private val globalSettingsStore: SettingsStore,
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
    private val _pendingRemoteSubmission = MutableStateFlow<Pair<Uuid, Long>?>(null)

    val isGenerating: StateFlow<Boolean> = combine(
        snapshot,
        repository.submittingIds,
    ) { cached, submitting ->
        cached?.isActive == true || conversationId in submitting
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = combine(_isSyncing, snapshot, workflowRepository.session) { manual, cached, session ->
        manual || (session != null && cached?.waitingForMessages == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStatusText: StateFlow<String?> = combine(
        isGenerating,
        snapshot,
        repository.submittingStartedAt,
        _pendingRemoteSubmission,
    ) { generating, cached, submittingMap, pendingRemote ->
        if (!generating) {
            _pendingRemoteSubmission.value = null
            null
        } else {
            val submittingStart = submittingMap[conversationId]
            val pendingHasLanded = pendingRemote != null && cached?.conversation?.messageNodes?.any { node ->
                node.messages.any { it.id == pendingRemote.first }
            } == true
            when {
                submittingStart != null -> submittingStart
                pendingRemote != null && !pendingHasLanded -> pendingRemote.second
                else -> {
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
            }
        }
    }.scan(null as Long?) { prevStartMs, candidateStartMs ->
        when {
            candidateStartMs == null -> null
            prevStartMs != null -> prevStartMs
            else -> candidateStartMs
        }
    }.distinctUntilChanged().flatMapLatest { startMs ->
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

    private val allowedModelTypes = setOf(ModelType.AGENT)

    val resolvedChatModel: StateFlow<Model?> = combine(
        _agentAssistant,
        globalSettingsStore.settingsFlow,
        _selectedModel,
    ) { assistant, settings, manual ->
        val byAssistant = assistant.chatModelId?.let { id ->
            settings.providers.findModelById(id)?.takeIf { it.type in allowedModelTypes }
        }
        val byManual = manual?.let { m ->
            settings.providers.findModelById(m.id)?.takeIf { it.type in allowedModelTypes }
        }
        val fallback = settings.providers
            .asSequence()
            .filter { it.enabled }
            .sortedBy { provider ->
                val topIdx = TOP_AGENT_PROVIDER_IDS.indexOf(provider.id)
                if (topIdx >= 0) topIdx else Int.MAX_VALUE
            }
            .flatMap { it.models.asSequence() }
            .firstOrNull { it.type in allowedModelTypes && it.modelId == "Antigravity" }
        byAssistant ?: byManual ?: fallback
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedModelProvider: StateFlow<ProviderSetting?> = combine(
        resolvedChatModel,
        globalSettingsStore.settingsFlow,
    ) { model, settings ->
        model?.findProvider(providers = settings.providers, checkOverwrite = false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessionAgentName = snapshot.map { cached ->
        if (cached?.remoteJobId == null) null else cached.conversation.messageNodes.lastOrNull()
            ?.currentMessage?.modelSnapshot?.displayName
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pendingApproval = repository.approvals.map { it[conversationId] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun approve(approval: AgentApproval, optionId: String) {
        viewModelScope.launch {
            repository.approveConversationJob(conversationId, approval, optionId).onFailure {
                _errorMessage.value = it.message
            }
        }
    }

    private val _initialDraft = MutableStateFlow<String?>(null)
    val initialDraft: StateFlow<String?> = _initialDraft.asStateFlow()

    fun saveDraft(text: String) {
        if (_initialDraft.value == text) return
        _initialDraft.value = text
        repository.saveDraft(conversationId, text)
    }

    private var submission: Deferred<Result<Unit>>? = null
    private val isBusy: Boolean
        get() = snapshot.value?.isActive == true || conversationId in repository.submittingIds.value ||
            _isInitialLoading.value ||
            (session.value != null && snapshot.value?.waitingForMessages == true)

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

    private fun validateInput(parts: List<UIMessagePart>, sources: List<RemoteAgentSourceInput>): Boolean {
        val documents = parts.filterIsInstance<UIMessagePart.Document>()
        _errorMessage.value = when {
            session.value == null -> "请先连接 AnyWorkflow 账号，再使用 ACP Agent。"
            snapshot.value?.remoteStatus == "unavailable" -> "此会话没有可用的 ACP 记录，请新建对话。"
            parts.any { it !is UIMessagePart.Text && it !is UIMessagePart.Image && it !is UIMessagePart.Document } ->
                "ACP Agent 支持文本、图片链接和 Markdown 附件。"
            documents.any { !it.fileName.endsWith(".md", ignoreCase = true) } -> "附件须为 Markdown 文件。"
            sources.size != documents.size -> "无法读取 Markdown 附件。"
            extractImagesFromParts(parts).any { !it.isAgentImageUrl() } -> "请先将图片上传到资料库。"
            else -> null
        }
        return _errorMessage.value == null
    }

    fun sendMessage(parts: List<UIMessagePart>, onSubmitted: () -> Unit) {
        if (parts.isEmpty() || isBusy) return
        _errorMessage.value = null
        val sources = extractSourcesFromParts(parts)
        if (!validateInput(parts, sources)) return

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = parts,
        )

        val current = _conversation.value
        val nextTitle = if (current.title.isBlank()) {
            userMessage.toText().trim()
                .replace('\n', ' ')
                .take(36)
                .ifBlank { resolvedChatModel.value?.displayName ?: "Agent" }
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
        val images = extractImagesFromParts(parts)
        submitRemote(updatedWithUser, prompt, sources, images, nextTitle, onSubmitted = onSubmitted)
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
        val current = _conversation.value
        val jobId = snapshot.value?.remoteJobId
        if (jobId == null || snapshot.value?.remoteStatus !in setOf("failed", "canceled") ||
            current.messageNodes.lastOrNull()?.currentMessage?.id != message.id
        ) {
            _errorMessage.value = "可重试失败、取消或中断的最新 ACP 轮次。"
            return
        }
        if (session.value == null) {
            _errorMessage.value = "请先连接 AnyWorkflow 账号。"
            return
        }
        submitRemote(current, "", emptyList(), emptyList(), current.title, retryJobId = jobId)
    }

    fun editMessage(message: UIMessage, newParts: List<UIMessagePart>) {
        if (isBusy || newParts.isEmpty()) return
        if (snapshot.value?.remoteJobId != null) {
            _errorMessage.value = "ACP 会话使用服务端记录。继续发送新消息，或新建会话。"
            return
        }
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
        if (snapshot.value?.remoteJobId != null) {
            _errorMessage.value = "ACP 会话使用服务端记录。继续发送新消息，或新建会话。"
            return
        }
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
        if (snapshot.value?.remoteJobId != null) {
            _errorMessage.value = "ACP 会话使用服务端记录。继续发送新消息，或新建会话。"
            return
        }
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
                try {
                    repository.renameConversation(conversationId, trimmed)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _errorMessage.value = error.message ?: "无法重命名会话"
                }
            }
        }
    }

    fun clearMessages() {
        if (isBusy) return
        if (snapshot.value?.remoteJobId != null) {
            _errorMessage.value = "ACP 会话使用服务端记录。继续发送新消息，或新建会话。"
            return
        }
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

    private fun submitRemote(
        conversation: Conversation,
        prompt: String,
        sources: List<RemoteAgentSourceInput>,
        images: List<String>,
        title: String,
        retryJobId: String? = null,
        onSubmitted: () -> Unit = {},
    ) {
        val selected = resolvedChatModel.value
        val rawModel = selected?.modelId ?: "Antigravity"
        val provider = selectedModelProvider.value?.id
        val agent = when {
            // GPT-6.2 models are only offered by the Codex adapter; the Claude Code
            // adapter's picker is limited to Claude aliases and rejects them.
            provider == CLAUDE_CODE_GPT_PROVIDER_ID -> "codex"
            isClaudeCodeProvider(provider) -> "claude-code"
            provider == COMMAND_CODE_PROVIDER_ID || isCommandCodeModel(rawModel) -> "command-code"
            provider == OPENCODE_PROVIDER_ID || rawModel.equals("OpenCode", ignoreCase = true) -> "opencode"
            provider == CODEX_PROVIDER_ID || isCodexAgentModel(rawModel) -> "codex"
            provider == ANTIGRAVITY_PROVIDER_ID || provider == ANTIGRAVITY_CLI_PROVIDER_ID ||
                isAntigravityAgentModel(rawModel) || isAntigravityCliModel(rawModel) -> "antigravity"
            else -> {
                _errorMessage.value = "所选模型没有 ACP 适配器。"
                return
            }
        }
        val model = rawModel.takeUnless {
            it.equals(acpAgentName(agent), ignoreCase = true) ||
                isAntigravityCliModel(it) || isCommandCodeModel(it)
        }
        val effort = _agentAssistant.value.reasoningLevel.takeIf {
            selected?.abilities?.contains(ModelAbility.REASONING) == true &&
                it != ReasoningLevel.AUTO && it != ReasoningLevel.OFF
        }?.let {
            if (agent == "codex" && it == ReasoningLevel.MAX) "xhigh" else it.name.lowercase()
        }
        val assistant = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("")),
            modelSnapshot = ModelSnapshot(rawModel, selected?.displayName ?: acpAgentName(agent)),
        )
        val pending = if (retryJobId == null) {
            conversation.copy(messageNodes = conversation.messageNodes + assistant.toMessageNode())
        } else {
            conversation
        }
        val priorNodes = if (snapshot.value?.remoteJobId == null) conversation.messageNodes.dropLast(1) else emptyList()
        val history = priorNodes.takeLast(40).mapNotNull { node ->
            val message = node.currentMessage
            val text = message.toText()
            if (text.isBlank()) return@mapNotNull null
            when (message.role) {
                MessageRole.USER -> RemoteHistoryTurn("user", text)
                MessageRole.ASSISTANT -> RemoteHistoryTurn("model", text)
                else -> null
            }
        }
        _pendingRemoteSubmission.value = assistant.id to System.currentTimeMillis()
        val task = repository.submitMessage(
            conversation = pending,
            prompt = prompt,
            sources = sources,
            images = images,
            replacesMessageId = retryJobId,
            title = title,
            model = model,
            agentId = agent,
            thinkingLevel = effort,
            systemPrompt = _agentAssistant.value.systemPrompt.takeIf { it.isNotBlank() },
            initialHistory = history,
        )
        submission = task
        viewModelScope.launch {
            task.await().onSuccess { onSubmitted() }.onFailure { error ->
                _errorMessage.value = error.message ?: "ACP 任务提交失败"
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
        if (docNames.isNotEmpty()) {
            return "请处理附件文件：${docNames.joinToString(", ")}"
        }
        return ""
    }

    private fun extractImagesFromParts(parts: List<UIMessagePart>): List<String> {
        return parts.filterIsInstance<UIMessagePart.Image>()
            .map { it.url.trim() }
            .filter { it.isNotBlank() }
            .distinct()
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
                    id = java.util.UUID.nameUUIDFromBytes(doc.url.toByteArray()).toString() + ".md",
                    name = doc.fileName.ifBlank { "document.md" },
                    content = content,
                )
            }
        }
    }
}

internal fun String.isManagedAgentModel(): Boolean =
    equals("Antigravity", ignoreCase = true) || startsWith("antigravity-", ignoreCase = true)
