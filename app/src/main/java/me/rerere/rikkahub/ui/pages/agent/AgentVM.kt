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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.AgentCloudClient
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentStreamEvent
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import java.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AgentVM(
    id: String,
    private val repository: AgentRepository,
    private val cloudClient: AgentCloudClient,
    private val settingsStore: AgentSettingsStore,
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

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val uiSettings: StateFlow<AgentUiSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current)

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = repository.getConversation(conversationId)
            if (loaded != null) {
                _conversation.value = loaded
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun sendMessage(parts: List<UIMessagePart>) {
        if (parts.isEmpty() || _isGenerating.value) return
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
                .ifBlank { "文件转换任务" }
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
            repository.saveConversation(updatedWithUser)
        }

        streamAssistantReply(
            baseConversation = updatedWithUser,
            history = updatedWithUser.currentMessages,
            userMessage = userMessage,
            targetNodeId = null,
        )
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        val snapshot = _conversation.value
        if (!snapshot.newConversation) {
            viewModelScope.launch {
                repository.saveConversation(snapshot)
            }
        }
    }

    fun regenerate(message: UIMessage) {
        if (_isGenerating.value) return
        val current = _conversation.value
        val nodeIndex = current.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == message.id }
        }
        if (nodeIndex < 0) return

        val node = current.messageNodes[nodeIndex]
        val historyBefore = current.messageNodes.take(nodeIndex).map { it.currentMessage }
        val userMsg = historyBefore.lastOrNull { it.role == MessageRole.USER } ?: return

        streamAssistantReply(
            baseConversation = current,
            history = historyBefore,
            userMessage = userMsg,
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

    private fun streamAssistantReply(
        baseConversation: Conversation,
        history: List<UIMessage>,
        userMessage: UIMessage,
        targetNodeId: Uuid?,
    ) {
        generationJob?.cancel()
        _isGenerating.value = true

        val assistantMsgId = Uuid.random()
        val nodeId = targetNodeId ?: Uuid.random()
        val startMs = System.currentTimeMillis()
        val reasoningBuilder = StringBuilder()
        val textBuilder = StringBuilder()

        fun buildAssistantMessage(finished: Boolean, usage: me.rerere.ai.core.TokenUsage? = null): UIMessage {
            val parts = buildList {
                if (reasoningBuilder.isNotEmpty()) {
                    add(
                        UIMessagePart.Reasoning(
                            reasoning = reasoningBuilder.toString(),
                            finishedAt = if (finished) Clock.System.now() else null,
                        )
                    )
                }
                if (textBuilder.isNotEmpty() || !finished) {
                    add(UIMessagePart.Text(textBuilder.toString()))
                }
            }
            return UIMessage(
                id = assistantMsgId,
                role = MessageRole.ASSISTANT,
                parts = parts,
                finishedAt = if (finished) Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) else null,
                modelSnapshot = ModelSnapshot(
                    displayName = "Antigravity Agent",
                    modelId = "antigravity-file-converter",
                ),
                usage = usage,
                generationTimeMs = if (finished) System.currentTimeMillis() - startMs else null,
            )
        }

        val initialConv = if (targetNodeId != null) {
            baseConversation.upsertMessage(nodeId, buildAssistantMessage(finished = false))
        } else {
            val newNode = MessageNode(
                id = nodeId,
                messages = listOf(buildAssistantMessage(finished = false)),
                selectIndex = 0,
            )
            baseConversation.copy(messageNodes = baseConversation.messageNodes + newNode)
        }
        _conversation.value = initialConv

        generationJob = viewModelScope.launch {
            try {
                cloudClient.streamReply(conversationId, history, userMessage).collect { event ->
                    when (event) {
                        is AgentStreamEvent.ReasoningDelta -> {
                            reasoningBuilder.append(event.text)
                            _conversation.value = _conversation.value.upsertMessage(
                                nodeId,
                                buildAssistantMessage(finished = false),
                            )
                        }
                        is AgentStreamEvent.TextDelta -> {
                            textBuilder.append(event.text)
                            _conversation.value = _conversation.value.upsertMessage(
                                nodeId,
                                buildAssistantMessage(finished = false),
                            )
                        }
                        is AgentStreamEvent.Completed -> {
                            val finalConv = _conversation.value.upsertMessage(
                                nodeId,
                                buildAssistantMessage(finished = true, usage = event.usage),
                            ).copy(updateAt = Instant.now())
                            _conversation.value = finalConv
                            repository.saveConversation(finalConv)
                        }
                        is AgentStreamEvent.Error -> {
                            _errorMessage.value = event.message
                            val finalConv = _conversation.value.upsertMessage(
                                nodeId,
                                buildAssistantMessage(finished = true),
                            ).copy(updateAt = Instant.now())
                            _conversation.value = finalConv
                            repository.saveConversation(finalConv)
                        }
                    }
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }
}
