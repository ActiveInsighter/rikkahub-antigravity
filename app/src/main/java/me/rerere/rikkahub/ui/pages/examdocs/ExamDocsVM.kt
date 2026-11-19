package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.examdocs.ExamDocFlatEntry
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository

@Immutable
data class ExamDocsUiState(
    val baseUrl: String = "",
    val isLoadingTree: Boolean = true,
    val treeError: String? = null,
    val modules: List<ExamDocNode.Folder> = emptyList(),
    val selectedModuleIndex: Int = 0,
    val currentPage: ExamDocNode.Page? = null,
    val currentBreadcrumb: List<String> = emptyList(),
    val isLoadingContent: Boolean = false,
    val contentError: String? = null,
    val markdownContent: String? = null,
    val headings: List<DocHeading> = emptyList(),
    val prevPage: ExamDocNode.Page? = null,
    val nextPage: ExamDocNode.Page? = null,
    val searchQuery: String = "",
    val searchResults: List<ExamDocFlatEntry> = emptyList(),
)

class ExamDocsVM(
    private val initialUrl: String?,
    private val repository: ExamDocsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExamDocsUiState(baseUrl = repository.getSavedBaseUrl())
    )
    val uiState = _uiState.asStateFlow()

    private var flatEntries: List<ExamDocFlatEntry> = emptyList()
    private var loadContentJob: Job? = null

    init {
        loadTree(forceRefresh = false, targetUrl = initialUrl)
    }

    fun loadTree(
        forceRefresh: Boolean = false,
        targetUrl: String? = null,
    ) {
        val baseUrl = _uiState.value.baseUrl
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeError = null) }
            repository.fetchSourceTree(baseUrl = baseUrl, forceRefresh = forceRefresh)
                .onSuccess { modules ->
                    flatEntries = ExamDocsRepository.flattenPages(modules)
                    val desiredUrl = targetUrl
                        ?: _uiState.value.currentPage?.url
                        ?: repository.getLastPageUrl()

                    val matchedEntry = desiredUrl?.let { raw ->
                        val normalized = ExamDocsRepository.normalizeDocPath(raw)
                        flatEntries.firstOrNull { it.page.url == normalized }
                    } ?: flatEntries.firstOrNull()

                    _uiState.update { state ->
                        state.copy(
                            isLoadingTree = false,
                            treeError = null,
                            modules = modules,
                            selectedModuleIndex = matchedEntry?.moduleIndex
                                ?: state.selectedModuleIndex.coerceIn(0, (modules.size - 1).coerceAtLeast(0)),
                        )
                    }
                    updateSearchResults(_uiState.value.searchQuery)

                    if (matchedEntry != null) {
                        openPageInternal(matchedEntry, forceRefresh = forceRefresh)
                    } else if (desiredUrl != null) {
                        openRawPath(desiredUrl, forceRefresh = forceRefresh)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingTree = false,
                            treeError = error.message ?: "无法加载文档目录",
                        )
                    }
                }
        }
    }

    fun selectModule(index: Int) {
        val modules = _uiState.value.modules
        if (index in modules.indices) {
            _uiState.update { it.copy(selectedModuleIndex = index) }
        }
    }

    fun openPage(page: ExamDocNode.Page, forceRefresh: Boolean = false) {
        val entryIndex = flatEntries.indexOfFirst { it.page.url == page.url }
        if (entryIndex >= 0) {
            openPageInternal(flatEntries[entryIndex], forceRefresh = forceRefresh)
        } else {
            openRawPath(page.url, titleFallback = page.name, forceRefresh = forceRefresh)
        }
    }

    fun openByUrl(url: String) {
        val normalized = ExamDocsRepository.normalizeDocPath(url)
        val entry = flatEntries.firstOrNull { it.page.url == normalized }
        if (entry != null) {
            openPageInternal(entry, forceRefresh = false)
        } else {
            openRawPath(normalized, forceRefresh = false)
        }
    }

    fun refreshCurrentPage() {
        val page = _uiState.value.currentPage
        if (page != null) {
            openPage(page, forceRefresh = true)
        } else {
            loadTree(forceRefresh = true)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateSearchResults(query)
    }

    fun updateBaseUrl(newBaseUrl: String) {
        val saved = repository.saveBaseUrl(newBaseUrl)
        _uiState.update {
            it.copy(
                baseUrl = saved,
                modules = emptyList(),
                currentPage = null,
                currentBreadcrumb = emptyList(),
                markdownContent = null,
                headings = emptyList(),
                prevPage = null,
                nextPage = null,
            )
        }
        loadTree(forceRefresh = true)
    }

    private fun updateSearchResults(query: String) {
        val trimmed = query.trim()
        val results = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            flatEntries.filter { entry ->
                entry.page.name.contains(trimmed, ignoreCase = true) ||
                    entry.breadcrumb.any { it.contains(trimmed, ignoreCase = true) } ||
                    entry.page.url.contains(trimmed, ignoreCase = true)
            }.take(60)
        }
        _uiState.update { it.copy(searchResults = results) }
    }

    private fun openPageInternal(entry: ExamDocFlatEntry, forceRefresh: Boolean) {
        val index = flatEntries.indexOf(entry)
        val prev = if (index > 0) flatEntries[index - 1].page else null
        val next = if (index >= 0 && index + 1 < flatEntries.size) flatEntries[index + 1].page else null

        repository.saveLastPageUrl(entry.page.url)
        _uiState.update {
            it.copy(
                selectedModuleIndex = entry.moduleIndex,
                currentPage = entry.page,
                currentBreadcrumb = entry.breadcrumb,
                prevPage = prev,
                nextPage = next,
                isLoadingContent = true,
                contentError = null,
            )
        }

        fetchMarkdownContent(entry.page.url, forceRefresh)
    }

    private fun openRawPath(
        rawPath: String,
        titleFallback: String? = null,
        forceRefresh: Boolean = false,
    ) {
        val normalized = ExamDocsRepository.normalizeDocPath(rawPath)
        val page = ExamDocNode.Page(
            name = titleFallback ?: normalized.substringAfterLast('/').ifBlank { "文档" },
            url = normalized,
        )
        repository.saveLastPageUrl(normalized)
        _uiState.update {
            it.copy(
                currentPage = page,
                currentBreadcrumb = emptyList(),
                prevPage = null,
                nextPage = null,
                isLoadingContent = true,
                contentError = null,
            )
        }
        fetchMarkdownContent(normalized, forceRefresh)
    }

    private fun fetchMarkdownContent(pageUrl: String, forceRefresh: Boolean) {
        loadContentJob?.cancel()
        val baseUrl = _uiState.value.baseUrl
        loadContentJob = viewModelScope.launch {
            repository.fetchMarkdown(baseUrl = baseUrl, pageUrl = pageUrl, forceRefresh = forceRefresh)
                .onSuccess { rawMarkdown ->
                    val processed = preprocessExamDocsMarkdown(rawMarkdown)
                    val headings = extractDocHeadings(processed)
                    _uiState.update {
                        it.copy(
                            isLoadingContent = false,
                            contentError = null,
                            markdownContent = rawMarkdown,
                            headings = headings,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingContent = false,
                            contentError = error.message ?: "无法加载文档内容",
                        )
                    }
                }
        }
    }
}
