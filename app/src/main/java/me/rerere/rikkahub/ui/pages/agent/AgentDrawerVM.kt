package me.rerere.rikkahub.ui.pages.agent

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.PocketBaseAgentCloudClient
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.pages.chat.ConversationListItem
import me.rerere.rikkahub.ui.pages.chat.ConversationStatusBadge
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

class AgentDrawerVM(
    private val context: Application,
    private val repository: AgentRepository,
    private val settingsStore: AgentSettingsStore,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    val sortOrder: StateFlow<ConversationSortOrder> = settingsStore.settingsFlow
        .map { it.conversationSortOrder }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.conversationSortOrder)

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConversationIds: StateFlow<Set<Uuid>> = repository.observeActiveConversationIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val conversationStatuses: StateFlow<Map<Uuid, ConversationStatusBadge>> =
        repository.observeConversationStatusMap()
            .map { map ->
                map.mapValues { (_, info) ->
                    val status = info.remoteStatus
                    when {
                        status != null && PocketBaseAgentCloudClient.isActiveStatus(status) ->
                            ConversationStatusBadge.RUNNING
                        status.equals("failed", ignoreCase = true) && info.hasUnread ->
                            ConversationStatusBadge.FAILED
                        info.hasUnread ->
                            ConversationStatusBadge.COMPLETED_UNREAD
                        else ->
                            ConversationStatusBadge.NONE
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allConversations: StateFlow<List<Conversation>> = repository.observeConversations(ConversationSortOrder.UPDATE_TIME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversationsPaging: Flow<PagingData<ConversationListItem>> = combine(
        sortOrder.flatMapLatest { repository.observeConversations(it) },
        _selectedFolderId,
        searchQuery,
        sortOrder,
    ) { list, folderId, query, order ->
        val trimmed = query.trim()
        val filtered = list.filter { conv ->
            val matchesFolder = if (folderId == null) {
                conv.folderId == null
            } else {
                conv.folderId == folderId
            }
            val matchesQuery = trimmed.isEmpty() || conv.title.contains(trimmed, ignoreCase = true)
            matchesFolder && matchesQuery
        }
        PagingData.from(filtered)
            .map { ConversationListItem.Item(it) }
            .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                when {
                    after == null -> null
                    after.conversation.isPinned -> {
                        if (before == null) ConversationListItem.PinnedHeader else null
                    }
                    else -> {
                        val afterDate = after.conversation.sortDate(order)
                        val sameGroup = before != null &&
                            !before.conversation.isPinned &&
                            before.conversation.sortDate(order) == afterDate
                        if (sameGroup) {
                            null
                        } else {
                            ConversationListItem.DateHeader(
                                date = afterDate,
                                label = getDateLabel(afterDate),
                                sortOrder = order,
                            )
                        }
                    }
                }
            }
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultAssistant()
            repository.syncUsageWithRemote()
        }
    }

    fun refreshFromRemote() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncThreadsFromRemote()
                repository.syncUsageWithRemote()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createFolder(trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameFolder(folderId, trimmed)
        }
    }

    fun deleteFolder(folderId: Uuid) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            repository.moveConversationToFolder(conversationId, folderId)
        }
    }

    fun updateSortOrder(order: ConversationSortOrder) {
        settingsStore.update { it.copy(conversationSortOrder = order) }
    }

    fun setPinned(id: Uuid, pinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(id, pinned)
        }
    }

    fun rename(id: Uuid, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameConversation(id, trimmed)
        }
    }

    fun delete(id: Uuid) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    private fun Conversation.sortDate(sortOrder: ConversationSortOrder): LocalDate {
        val instant = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> updateAt
            ConversationSortOrder.CREATE_TIME -> createAt
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun getDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> context.getString(R.string.chat_page_today)
            yesterday -> context.getString(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        }
    }
}
