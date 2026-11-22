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
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentAssistantEntity
import me.rerere.rikkahub.data.agent.AgentConversationSnapshot
import me.rerere.rikkahub.data.agent.AgentGatewayClient
import me.rerere.rikkahub.data.agent.AgentModelCategory
import me.rerere.rikkahub.data.agent.AgentModelEntity
import me.rerere.rikkahub.data.agent.isAntigravityAgentModel
import me.rerere.rikkahub.data.agent.isCodexAgentModel
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
import me.rerere.rikkahub.data.agent.DEFAULT_GEMINI_GATEWAY_URL
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

    val localModels: StateFlow<List<AgentModelEntity>> = repository.observeModels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedModelEntity: StateFlow<AgentModelEntity?> = combine(
        localModels,
        settingsStore.settingsFlow,
    ) { models, settings ->
        models.firstOrNull { it.id == settings.selectedAgentModelId }
            ?: models.firstOrNull { it.modelId.equals(settings.selectedAgentModel, ignoreCase = true) }
            ?: models.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedAgentModelName: StateFlow<String> = combine(
        selectedModelEntity,
        settingsStore.settingsFlow,
    ) { entity, settings ->
        entity?.displayName?.ifBlank { entity.modelId }
            ?: settings.selectedAgentModel.ifBlank { "Antigravity" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.selectedAgentModel.ifBlank { "Antigravity" })

    val selectedModelCategory: StateFlow<AgentModelCategory> = combine(
        selectedModelEntity,
        selectedAgentModelName,
    ) { entity, name ->
        entity?.modelCategory ?: AgentModelCategory.inferFromModelId(name)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AgentModelCategory.AGENT)

    val resolvedChatModel: StateFlow<Model> = combine(
        selectedModelEntity,
        selectedAgentModelName,
        _selectedModel,
    ) { entity, name, manual ->
        val modelId = entity?.modelId ?: manual?.modelId ?: name
        val displayName = entity?.displayName?.ifBlank { modelId } ?: manual?.displayName ?: name
        Model(
            modelId = modelId,
            displayName = displayName,
            abilities = if (
                AgentModelCategory.inferFromModelId(modelId) == AgentModelCategory.AGENT &&
                !isCodexAgentModel(modelId)
            ) {
                emptyList()
            } else {
                listOf(ModelAbility.REASONING)
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Model(modelId = "Antigravity", displayName = "Antigravity"),
    )

    val antigravitySubModel: StateFlow<String> = settingsStore.settingsFlow
        .map { it.antigravitySubModel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.antigravitySubModel)

    val codexSubModel: StateFlow<String> = settingsStore.settingsFlow
        .map { it.codexSubModel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.codexSubModel)

    private val _decisionSchemaJson = MutableStateFlow("")
    val decisionSchemaJson: StateFlow<String> = _decisionSchemaJson.asStateFlow()

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

    fun selectLocalModel(model: AgentModelEntity) {
        settingsStore.update {
            it.copy(
                selectedAgentModelId = model.id,
                selectedAgentModel = model.modelId,
            )
        }
    }

    fun saveLocalModel(
        id: String? = null,
        modelId: String,
        displayName: String,
        category: AgentModelCategory,
        baseUrl: String,
        apiKey: String,
        selectAfterSave: Boolean = true,
    ) {
        val cleanModelId = modelId.trim()
        if (cleanModelId.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = id?.let { targetId -> localModels.value.firstOrNull { it.id == targetId } }
            val entity = AgentModelEntity(
                id = existing?.id ?: "local-${Uuid.random()}",
                modelId = cleanModelId,
                displayName = displayName.trim().ifBlank { cleanModelId },
                category = category.name,
                providerType = "GEMINI",
                baseUrl = baseUrl.trim().ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
                apiKey = apiKey.trim(),
                isBuiltin = existing?.isBuiltin ?: false,
                sortOrder = existing?.sortOrder ?: (localModels.value.size + 10),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val saved = repository.saveLocalModel(entity)
            if (selectAfterSave) {
                selectLocalModel(saved)
            }
        }
    }

    fun deleteLocalModel(id: String) {
        viewModelScope.launch {
            repository.deleteLocalModel(id)
            if (settingsStore.current.selectedAgentModelId == id) {
                val fallback = localModels.value.firstOrNull { it.id != id }
                settingsStore.update {
                    it.copy(
                        selectedAgentModelId = fallback?.id.orEmpty(),
                        selectedAgentModel = fallback?.modelId ?: "Antigravity",
                    )
                }
            }
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

    fun selectAgentModel(modelName: String) {
        val normalized = modelName.trim()
        if (normalized.isEmpty()) return
        val matched = localModels.value.firstOrNull {
            it.modelId.equals(normalized, ignoreCase = true) ||
                it.displayName.equals(normalized, ignoreCase = true)
        }
        if (matched != null) {
            selectLocalModel(matched)
        } else {
            settingsStore.update { it.copy(selectedAgentModel = normalized) }
        }
    }

    fun addCustomGeminiModel(modelName: String) {
        val normalized = modelName.trim()
        if (normalized.isEmpty()) return
        saveLocalModel(
            modelId = normalized,
            displayName = normalized,
            category = AgentModelCategory.inferFromModelId(normalized),
            baseUrl = settingsStore.current.gatewayBaseUrl,
            apiKey = settingsStore.current.gatewayApiKey,
            selectAfterSave = true,
        )
    }

    fun removeCustomGeminiModel(modelName: String) {
        val matched = localModels.value.firstOrNull {
            !it.isBuiltin && it.modelId.equals(modelName, ignoreCase = true)
        }
        if (matched != null) {
            deleteLocalModel(matched.id)
        }
    }

    fun updateAgentModel(model: Model) {
        _selectedModel.value = model
        val updated = _agentAssistant.value.copy(chatModelId = model.id)
        _agentAssistant.value = updated
        persistAssistant(updated)
    }

    fun updateAgentSubModel(isCodex: Boolean, subModel: String) {
        val clean = subModel.trim()
        settingsStore.update { current ->
            if (isCodex) {
                current.copy(codexSubModel = clean)
            } else {
                current.copy(antigravitySubModel = clean)
            }
        }
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
        val selected = selectedModelEntity.value
        if (selected != null) return selected
        val now = System.currentTimeMillis()
        val name = selectedAgentModelName.value.ifBlank { "Antigravity" }
        return AgentModelEntity(
            id = "fallback-$name",
            modelId = name,
            displayName = name,
            category = AgentModelCategory.inferFromModelId(name).name,
            baseUrl = settingsStore.current.gatewayBaseUrl.ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
            apiKey = settingsStore.current.gatewayApiKey,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun sendMessage(parts: List<UIMessagePart>): Boolean {
        if (parts.isEmpty() || isBusy) return false
        _errorMessage.value = null

        val modelEntity = currentOrFallbackModelEntity()
        val category = modelEntity.modelCategory

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
        val modelName = selectedModelEntity.value?.modelId?.ifBlank { null }
            ?: selectedAgentModelName.value.ifBlank { "Antigravity" }
        val agentModel = when {
            isCodexAgentModel(modelName) -> settingsStore.current.codexSubModel.trim().takeIf { it.isNotBlank() }
            isAntigravityAgentModel(modelName) -> settingsStore.current.antigravitySubModel.trim().takeIf { it.isNotBlank() }
            else -> null
        }
        val thinkingLevel = if (modelName.isManagedAgentModel()) "" else _agentAssistant.value.reasoningLevel.toGeminiThinkingLevel()
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
        val hasImages = parts.any { it is UIMessagePart.Image }
        return if (hasImages) "请查看并处理图片" else ""
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
                    id = doc.url.hashCode().toUInt().toString(16),
                    name = doc.fileName.ifBlank { "document.md" },
                    content = content,
                )
            }
        }
    }
}

internal fun String.isManagedAgentModel(): Boolean =
    equals("Antigravity", ignoreCase = true) || startsWith("antigravity-", ignoreCase = true)
