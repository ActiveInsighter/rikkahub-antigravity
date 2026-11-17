package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

class AgentDrawerVM(
    private val repository: AgentRepository,
    private val settingsStore: AgentSettingsStore,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    val sortOrder: StateFlow<ConversationSortOrder> = settingsStore.settingsFlow
        .map { it.conversationSortOrder }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.conversationSortOrder)

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<Conversation>> = combine(
        sortOrder.flatMapLatest { repository.observeConversations(it) },
        searchQuery,
    ) { list, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            list
        } else {
            list.filter { it.title.contains(trimmed, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
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
}
