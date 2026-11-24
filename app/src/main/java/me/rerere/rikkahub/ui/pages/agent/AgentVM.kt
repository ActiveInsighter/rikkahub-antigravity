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
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentAssistantEntity
import me.rerere.rikkahub.data.agent.AGENT_PROVIDER_TYPE_ANTHROPIC
import me.rerere.rikkahub.data.agent.AGENT_PROVIDER_TYPE_GEMINI
import me.rerere.rikkahub.data.agent.AgentConversationSnapshot
import me.rerere.rikkahub.data.agent.AgentGatewayClient
import me.rerere.rikkahub.data.agent.AgentModelCategory
import me.rerere.rikkahub.data.agent.AgentModelEntity
import me.rerere.rikkahub.data.agent.ANTIGRAVITY_CLI_MODEL
import me.rerere.rikkahub.data.agent.CLAUDE_CODE_MODEL
import me.rerere.rikkahub.data.agent.COMMAND_CODE_MODEL
import me.rerere.rikkahub.data.agent.isAntigravityAgentModel
import me.rerere.rikkahub.data.agent.isAntigravityCliModel
import me.rerere.rikkahub.data.agent.isCodexAgentModel
import me.rerere.rikkahub.data.agent.isCommandCodeModel
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
import me.rerere.rikkahub.data.agent.DEFAULT_GEMINI_GATEWAY_URL
import me.rerere.rikkahub.data.agent.MAX_AGENT_IMAGES
import me.rerere.rikkahub.data.agent.RemoteAgentSourceInput
import me.rerere.rikkahub.data.agent.RemoteHistoryTurn
import me.rerere.rikkahub.data.agent.isAgentImageUrl
import me.rerere.rikkahub.data.datastore.ANTIGRAVITY_CLI_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.ANTIGRAVITY_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.isClaudeCodeProvider
import me.rerere.rikkahub.data.datastore.CODEX_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.COMMAND_CODE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.TOP_AGENT_PROVIDER_IDS
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
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
    private val gatewayClient: AgentGatewayClient,
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
    private val localGeneration: StateFlow<me.rerere.rikkahub.data.agent.LocalGenerationState?> =
        repository.localGenerations
            .map { it[conversationId] }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _pendingRemoteSubmission = MutableStateFlow<Pair<Uuid, Long>?>(null)

    val isGenerating: StateFlow<Boolean> = combine(
        snapshot,
        repository.submittingIds,
        localGeneration,
    ) { cached, submitting, localGen ->
        cached?.isActive == true || conversationId in submitting || localGen != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = combine(_isSyncing, snapshot, workflowRepository.session) { manual, cached, session ->
        manual || (session != null && cached?.waitingForMessages == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStatusText: StateFlow<String?> = combine(
        isGenerating,
        snapshot,
        localGeneration,
        repository.submittingStartedAt,
        _pendingRemoteSubmission,
    ) { generating, cached, localGen, submittingMap, pendingRemote ->
        if (!generating) {
            _pendingRemoteSubmission.value = null
            null
        } else {
            val submittingStart = submittingMap[conversationId]
            val pendingHasLanded = pendingRemote != null && cached?.conversation?.messageNodes?.any { node ->
                node.messages.any { it.id == pendingRemote.first }
            } == true
            when {
                localGen != null -> localGen.startedAt
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

    private val allowedModelTypes = setOf(ModelType.CHAT, ModelType.AGENT, ModelType.DECISION)

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
            .firstOrNull { it.type in allowedModelTypes }
        byAssistant ?: byManual ?: fallback
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedModelProvider: StateFlow<ProviderSetting?> = combine(
        resolvedChatModel,
        globalSettingsStore.settingsFlow,
    ) { model, settings ->
        model?.findProvider(providers = settings.providers, checkOverwrite = false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedModelCategory: StateFlow<AgentModelCategory> = resolvedChatModel
        .map { model ->
            when (model?.type) {
                ModelType.AGENT -> AgentModelCategory.AGENT
                ModelType.DECISION -> AgentModelCategory.DECISION
                ModelType.CHAT -> AgentModelCategory.CHAT
                else -> AgentModelCategory.AGENT
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentModelCategory.AGENT)

    private val _decisionSchemaJson = MutableStateFlow("")
    val decisionSchemaJson: StateFlow<String> = _decisionSchemaJson.asStateFlow()

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
        if (_initialDraft.value == text) return
        _initialDraft.value = text
        repository.saveDraft(conversationId, text)
    }

    private var submission: Deferred<Result<Unit>>? = null
    private val isBusy: Boolean
        get() = snapshot.value?.isActive == true || conversationId in repository.submittingIds.value ||
            repository.localGenerations.value[conversationId] != null || _isInitialLoading.value ||
            (session.value != null && snapshot.value?.waitingForMessages == true)

    init {
        viewModelScope.launch {
            combine(
                repository.observeConversation(conversationId),
                localGeneration,
            ) { cached, localGen ->
                cached to localGen
            }.collect { (cached, localGen) ->
                if (localGen != null) {
                    _conversation.value = localGen.conversation
                } else if (cached != null) {
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
            repository.ensureDefaultModels()
        }
    }

    fun updateDecisionSchemaJson(schema: String) {
        _decisionSchemaJson.value = schema
    }

    fun updateGatewayConfig(baseUrl: String, apiKey: String) {
        settingsStore.update {
            it.copy(
                gatewayBaseUrl = baseUrl.trim().ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
                gatewayApiKey = apiKey.trim(),
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

    private fun currentOrFallbackModelEntity(): AgentModelEntity {
        val model = resolvedChatModel.value
        val settings = globalSettingsStore.settingsFlow.value
        val effectiveProvider = model?.findProvider(providers = settings.providers, checkOverwrite = true)
        val apiKeyProvider = effectiveProvider as? ProviderSetting.ApiKey
        val baseUrl = apiKeyProvider?.baseUrl?.ifBlank { null }
            ?: settingsStore.current.gatewayBaseUrl.ifBlank { DEFAULT_GEMINI_GATEWAY_URL }
        val apiKey = apiKeyProvider?.apiKey?.ifBlank { null }
            ?: settingsStore.current.gatewayApiKey
        val now = System.currentTimeMillis()
        val modelId = model?.modelId?.ifBlank { null } ?: "Antigravity"
        val displayName = model?.displayName?.ifBlank { modelId } ?: modelId
        val category = when (model?.type) {
            ModelType.AGENT -> AgentModelCategory.AGENT
            ModelType.DECISION -> AgentModelCategory.DECISION
            ModelType.CHAT -> AgentModelCategory.CHAT
            else -> AgentModelCategory.inferFromModelId(modelId)
        }
        // Custom Anthropic chat providers use /v1/messages;
        // everything else keeps using the Gemini-native /v1beta gateway.
        val providerType = if (apiKeyProvider is ProviderSetting.Claude) {
            AGENT_PROVIDER_TYPE_ANTHROPIC
        } else {
            AGENT_PROVIDER_TYPE_GEMINI
        }
        return AgentModelEntity(
            id = model?.id?.toString() ?: "fallback-$modelId",
            modelId = modelId,
            displayName = displayName,
            category = category.name,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun validateInput(parts: List<UIMessagePart>, category: AgentModelCategory): Boolean {
        val images = extractImagesFromParts(parts)
        if (images.isNotEmpty()) {
            _errorMessage.value = when {
                category == AgentModelCategory.DECISION -> "当前决策接口仅接收文本。请选择支持图片的聊天或 Agent 模型。"
                category != AgentModelCategory.AGENT -> null
                session.value == null -> "请先连接 PocketBase 账号，再发送 Agent 图片。"
                images.size > MAX_AGENT_IMAGES -> "每条消息最多添加 6 张图片。"
                images.any { !it.isAgentImageUrl() } -> "图片须先上传至资料库。可点击失败的附件重试，或重新添加图片。"
                else -> null
            }
            if (_errorMessage.value != null) return false
        }

        val providerId = selectedModelProvider.value?.id
        val modelId = resolvedChatModel.value?.modelId.orEmpty()
        val name = when {
            isClaudeCodeProvider(providerId) -> "Claude Code"
            providerId == COMMAND_CODE_PROVIDER_ID || isCommandCodeModel(modelId) -> "Command Code"
            providerId == ANTIGRAVITY_CLI_PROVIDER_ID || isAntigravityCliModel(modelId) -> "Antigravity CLI"
            else -> return true
        }
        _errorMessage.value = when {
            session.value == null -> "请先连接 PocketBase 账号，再使用 $name。"
            parts.any { it !is UIMessagePart.Text && it !is UIMessagePart.Image &&
                !(it is UIMessagePart.Document && it.fileName.endsWith(".md", ignoreCase = true))
            } -> "$name 支持文本、图片和 Markdown 附件。"
            else -> null
        }
        return _errorMessage.value == null
    }

    fun sendMessage(parts: List<UIMessagePart>): Boolean {
        if (parts.isEmpty() || isBusy) return false
        _errorMessage.value = null
        val modelEntity = currentOrFallbackModelEntity()
        val category = modelEntity.modelCategory
        if (!validateInput(parts, category)) return false

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = parts,
        )

        val current = _conversation.value
        val nextTitle = if (current.title.isBlank()) {
            userMessage.toText().trim()
                .replace('\n', ' ')
                .take(36)
                .ifBlank { modelEntity.displayName.ifBlank { "Agent" } }
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
        val images = extractImagesFromParts(parts)

        when (category) {
            AgentModelCategory.CHAT -> {
                repository.startLocalChat(
                    baseConversation = updatedWithUser,
                    modelEntity = modelEntity,
                    systemPrompt = _agentAssistant.value.systemPrompt,
                    reasoningLevel = _agentAssistant.value.reasoningLevel,
                    temperature = _agentAssistant.value.temperature,
                    fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                    fallbackApiKey = settingsStore.current.gatewayApiKey,
                    targetNodeId = null,
                    onError = { msg -> _errorMessage.value = msg },
                )
            }
            AgentModelCategory.DECISION -> {
                repository.startLocalDecision(
                    baseConversation = updatedWithUser,
                    modelEntity = modelEntity,
                    prompt = prompt,
                    historyMessages = updatedWithUser.messageNodes.map { it.currentMessage },
                    systemPrompt = _agentAssistant.value.systemPrompt,
                    reasoningLevel = _agentAssistant.value.reasoningLevel,
                    temperature = _agentAssistant.value.temperature,
                    schemaJson = _decisionSchemaJson.value.takeIf { it.isNotBlank() },
                    fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                    fallbackApiKey = settingsStore.current.gatewayApiKey,
                    targetNodeId = null,
                    onError = { msg -> _errorMessage.value = msg },
                )
            }
            AgentModelCategory.AGENT -> {
                if (session.value != null) {
                    submitRemote(
                        baseConversation = updatedWithUser,
                        prompt = prompt,
                        sources = sources,
                        images = images,
                        parentMessageId = snapshot.value?.lastRemoteMessageId,
                        replacesMessageId = null,
                        title = if (current.title.isBlank()) nextTitle else "",
                        targetNodeId = null,
                    )
                } else {
                    repository.startGatewayAgentDispatch(
                        baseConversation = updatedWithUser,
                        modelEntity = modelEntity,
                        prompt = prompt,
                        reasoningLevel = _agentAssistant.value.reasoningLevel,
                        fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                        fallbackApiKey = settingsStore.current.gatewayApiKey,
                        targetNodeId = null,
                        onError = { msg -> _errorMessage.value = msg },
                    )
                }
            }
        }

        saveDraft("")
        return true
    }

    fun stopGeneration() {
        if (repository.localGenerations.value[conversationId] != null) {
            repository.cancelLocalGeneration(conversationId)
            return
        }
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
        val nodeIndex = current.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == message.id }
        }
        if (nodeIndex < 0) return

        val node = current.messageNodes[nodeIndex]
        val historyBefore = current.messageNodes.take(nodeIndex).map { it.currentMessage }
        val userMsg = historyBefore.lastOrNull { it.role == MessageRole.USER } ?: return
        val modelEntity = currentOrFallbackModelEntity()
        if (!validateInput(userMsg.parts, modelEntity.modelCategory)) return
        val prompt = buildPromptFromParts(userMsg.parts)
        val sources = extractSourcesFromParts(userMsg.parts)
        val images = extractImagesFromParts(userMsg.parts)

        when (modelEntity.modelCategory) {
            AgentModelCategory.CHAT -> {
                repository.startLocalChat(
                    baseConversation = current,
                    modelEntity = modelEntity,
                    systemPrompt = _agentAssistant.value.systemPrompt,
                    reasoningLevel = _agentAssistant.value.reasoningLevel,
                    temperature = _agentAssistant.value.temperature,
                    fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                    fallbackApiKey = settingsStore.current.gatewayApiKey,
                    targetNodeId = node.id,
                    historyOverride = historyBefore,
                    onError = { msg -> _errorMessage.value = msg },
                )
            }
            AgentModelCategory.DECISION -> {
                repository.startLocalDecision(
                    baseConversation = current,
                    modelEntity = modelEntity,
                    prompt = prompt,
                    historyMessages = historyBefore,
                    systemPrompt = _agentAssistant.value.systemPrompt,
                    reasoningLevel = _agentAssistant.value.reasoningLevel,
                    temperature = _agentAssistant.value.temperature,
                    schemaJson = _decisionSchemaJson.value.takeIf { it.isNotBlank() },
                    fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                    fallbackApiKey = settingsStore.current.gatewayApiKey,
                    targetNodeId = node.id,
                    onError = { msg -> _errorMessage.value = msg },
                )
            }
            AgentModelCategory.AGENT -> {
                if (session.value != null) {
                    submitRemote(
                        baseConversation = current,
                        prompt = prompt,
                        sources = sources,
                        images = images,
                        parentMessageId = null,
                        replacesMessageId = snapshot.value?.lastRemoteMessageId,
                        title = "",
                        targetNodeId = node.id,
                    )
                } else {
                    repository.startGatewayAgentDispatch(
                        baseConversation = current,
                        modelEntity = modelEntity,
                        prompt = prompt,
                        reasoningLevel = _agentAssistant.value.reasoningLevel,
                        fallbackBaseUrl = settingsStore.current.gatewayBaseUrl,
                        fallbackApiKey = settingsStore.current.gatewayApiKey,
                        targetNodeId = node.id,
                        onError = { msg -> _errorMessage.value = msg },
                    )
                }
            }
        }
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
        images: List<String> = emptyList(),
        parentMessageId: String?,
        replacesMessageId: String?,
        title: String,
        targetNodeId: Uuid?,
    ) {
        val selectedModel = resolvedChatModel.value
        val rawProvider = selectedModelProvider.value
        val rawModelId = selectedModel?.modelId?.ifBlank { null } ?: "Antigravity"
        val isAntigravityCli = rawProvider?.id == ANTIGRAVITY_CLI_PROVIDER_ID ||
            isAntigravityCliModel(rawModelId)
        val isClaudeCode = isClaudeCodeProvider(rawProvider?.id)
        val isCommandCode = rawProvider?.id == COMMAND_CODE_PROVIDER_ID || isCommandCodeModel(rawModelId)
        val isCodex = rawProvider?.id == CODEX_PROVIDER_ID ||
            rawProvider?.name.equals("Codex", ignoreCase = true) ||
            isCodexAgentModel(rawModelId)
        val isAntigravity = rawProvider?.id == ANTIGRAVITY_PROVIDER_ID ||
            rawProvider?.name.equals("Antigravity", ignoreCase = true) ||
            isAntigravityAgentModel(rawModelId)
        val modelName = when {
            isClaudeCode -> CLAUDE_CODE_MODEL
            isCommandCode -> COMMAND_CODE_MODEL
            isAntigravityCli -> ANTIGRAVITY_CLI_MODEL
            isCodex -> "Codex"
            isAntigravity -> "Antigravity"
            else -> rawModelId
        }
        val agentModel = when {
            isClaudeCode -> rawModelId
            isAntigravityCli -> rawModelId.takeUnless(::isAntigravityCliModel)?.trim()
            isCodex && !rawModelId.equals("Codex", ignoreCase = true) -> rawModelId.trim()
            isAntigravity && !rawModelId.equals("Antigravity", ignoreCase = true) -> rawModelId.trim()
            else -> null
        }
        val displayLabel = selectedModel?.displayName?.ifBlank { rawModelId } ?: modelName
        val reasoningLevel = _agentAssistant.value.reasoningLevel
        val thinkingLevel = when {
            isClaudeCode -> when (reasoningLevel) {
                ReasoningLevel.AUTO -> ""
                else -> reasoningLevel.name.lowercase()
            }
            isAntigravityCli && agentModel != null && reasoningLevel == ReasoningLevel.MAX -> "max"
            isAntigravityCli && agentModel != null && reasoningLevel == ReasoningLevel.XHIGH -> "xhigh"
            isAntigravityCli -> reasoningLevel.toGeminiThinkingLevel()
            isCodex && reasoningLevel in listOf(ReasoningLevel.XHIGH, ReasoningLevel.MAX) -> "xhigh"
            isCodex && reasoningLevel == ReasoningLevel.OFF -> "off"
            isAntigravity || isCommandCode -> ""
            else -> reasoningLevel.toGeminiThinkingLevel()
        }
        val systemPrompt = _agentAssistant.value.systemPrompt.takeIf { it.isNotBlank() }
        val assistantMsgId = Uuid.random()
        val nodeId = targetNodeId ?: Uuid.random()
        val placeholderMsg = UIMessage(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("")),
            modelSnapshot = ModelSnapshot(
                displayName = displayLabel,
                modelId = rawModelId,
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
        val initialHistory = if (isClaudeCode && parentMessageId == null && replacesMessageId == null &&
            snapshot.value?.remoteStatus == null
        ) {
            val priorNodes = baseConversation.messageNodes.takeWhile { it.id != targetNodeId }.dropLast(1)
            priorNodes.takeLast(40).mapNotNull { node ->
                val message = node.currentMessage
                val text = message.toText()
                val historyImages = extractImagesFromParts(message.parts).filter { it.isAgentImageUrl() }.take(MAX_AGENT_IMAGES)
                when {
                    message.role == MessageRole.USER && (text.isNotBlank() || historyImages.isNotEmpty()) ->
                        RemoteHistoryTurn("user", text, historyImages)
                    message.role == MessageRole.ASSISTANT && text.isNotBlank() -> RemoteHistoryTurn("model", text)
                    else -> null
                }
            }
        } else {
            emptyList()
        }
        _pendingRemoteSubmission.value = assistantMsgId to System.currentTimeMillis()
        val task = repository.submitMessage(
            conversation = initialConv,
            prompt = prompt,
            sources = sources,
            images = images,
            parentMessageId = parentMessageId,
            replacesMessageId = replacesMessageId,
            title = title,
            model = modelName,
            agentModel = agentModel,
            thinkingLevel = thinkingLevel,
            systemPrompt = systemPrompt,
            initialHistory = initialHistory,
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
