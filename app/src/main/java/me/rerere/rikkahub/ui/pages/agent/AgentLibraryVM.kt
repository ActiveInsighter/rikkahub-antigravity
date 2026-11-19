package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.workflow.LibraryImageItem
import me.rerere.rikkahub.data.workflow.LibraryImageRepository
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LibraryImageGroup(
    val label: String,
    val items: List<LibraryImageItem>,
)

data class AgentLibraryUiState(
    val isSignedIn: Boolean = false,
    val items: List<LibraryImageItem> = emptyList(),
    val groups: List<LibraryImageGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val cursor: String = "",
    val searchQuery: String = "",
    val sortNewestFirst: Boolean = true,
    val selectedUrls: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

class AgentLibraryVM(
    private val libraryImageRepository: LibraryImageRepository,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AgentLibraryUiState(isSignedIn = libraryImageRepository.isSignedIn)
    )
    val uiState: StateFlow<AgentLibraryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            workflowRepository.session.collect { session ->
                val signedIn = session != null
                _uiState.update { it.copy(isSignedIn = signedIn) }
                if (signedIn) {
                    refresh()
                } else {
                    _uiState.update {
                        it.copy(
                            items = emptyList(),
                            groups = emptyList(),
                            cursor = "",
                            selectedUrls = emptySet(),
                            errorMessage = null,
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        if (!libraryImageRepository.isSignedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val query = _uiState.value.searchQuery
            libraryImageRepository.listImages(cursor = "", query = query, limit = 100)
                .onSuccess { page ->
                    _uiState.update { state ->
                        val groups = groupImagesByTime(page.items, state.sortNewestFirst)
                        state.copy(
                            items = page.items,
                            groups = groups,
                            cursor = page.cursor,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "加载图片资料库失败",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.isSignedIn || state.isLoading || state.isLoadingMore || state.cursor.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            libraryImageRepository.listImages(
                cursor = state.cursor,
                query = state.searchQuery,
                limit = 100,
            ).onSuccess { page ->
                _uiState.update { current ->
                    val merged = (current.items + page.items).distinctBy { it.id }
                    current.copy(
                        items = merged,
                        groups = groupImagesByTime(merged, current.sortNewestFirst),
                        cursor = page.cursor,
                        isLoadingMore = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        errorMessage = error.message ?: "加载更多图片失败",
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun toggleSortOrder() {
        _uiState.update { state ->
            val newestFirst = !state.sortNewestFirst
            state.copy(
                sortNewestFirst = newestFirst,
                groups = groupImagesByTime(state.items, newestFirst),
            )
        }
    }

    fun toggleSelection(sourceUrl: String) {
        _uiState.update { state ->
            val next = if (sourceUrl in state.selectedUrls) {
                state.selectedUrls - sourceUrl
            } else {
                state.selectedUrls + sourceUrl
            }
            state.copy(selectedUrls = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedUrls = emptySet()) }
    }

    fun reuseImages(urls: List<String>) {
        libraryImageRepository.queueReuseImages(urls)
        clearSelection()
    }

    fun deleteImage(item: LibraryImageItem) {
        viewModelScope.launch {
            libraryImageRepository.deleteImage(item.id)
                .onSuccess {
                    _uiState.update { state ->
                        val remaining = state.items.filterNot { it.id == item.id }
                        state.copy(
                            items = remaining,
                            groups = groupImagesByTime(remaining, state.sortNewestFirst),
                            selectedUrls = state.selectedUrls - item.sourceUrl,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "删除图片失败")
                    }
                }
        }
    }

    private fun groupImagesByTime(
        items: List<LibraryImageItem>,
        newestFirst: Boolean,
    ): List<LibraryImageGroup> {
        val sorted = if (newestFirst) {
            items.sortedByDescending { it.createdAt }
        } else {
            items.sortedBy { it.createdAt }
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)
        val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月")

        val grouped = linkedMapOf<String, MutableList<LibraryImageItem>>()
        for (item in sorted) {
            val date = runCatching {
                Instant.parse(item.createdAt).atZone(zone).toLocalDate()
            }.getOrNull()
            val label = when {
                date == null -> "未知时间"
                date == today -> "今天"
                date == yesterday -> "昨天"
                date.isAfter(weekAgo) -> "近 7 天"
                else -> date.format(monthFormatter)
            }
            grouped.getOrPut(label) { mutableListOf() }.add(item)
        }
        return grouped.map { (label, list) -> LibraryImageGroup(label = label, items = list) }
    }
}
