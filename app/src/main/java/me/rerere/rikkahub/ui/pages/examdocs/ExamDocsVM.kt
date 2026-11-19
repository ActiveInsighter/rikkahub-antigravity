package me.rerere.rikkahub.ui.pages.examdocs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExamDocsVM(
    private val repository: ExamDocsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamDocsUiState())
    val uiState = _uiState.asStateFlow()

    private var treeJob: Job? = null
    private var pageJob: Job? = null

    init {
        loadTree()
    }

    fun loadTree(forceRefresh: Boolean = false) {
        treeJob?.cancel()
        treeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingTree = true,
                    treeError = null,
                )
            }
            try {
                val baseUrl = _uiState.value.siteBaseUrl
                val response = repository.fetchSourceTree(baseUrl)
                val flatPages = flattenPages(response.modules)
                val currentSelected = _uiState.value.selectedPage
                val targetPage = currentSelected
                    ?.let { prev -> flatPages.firstOrNull { it.url == prev.url } }
                    ?: flatPages.firstOrNull()

                val defaultExpanded = _uiState.value.expandedFolders.ifEmpty {
                    buildInitialExpandedFolders(response.modules, targetPage)
                }

                _uiState.update {
                    it.copy(
                        modules = response.modules,
                        flatPages = flatPages,
                        selectedPage = targetPage,
                        isLoadingTree = false,
                        expandedFolders = defaultExpanded,
                    )
                }

                if (targetPage != null) {
                    loadPageMarkdown(targetPage, forceRefresh = forceRefresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingTree = false,
                        treeError = e.message ?: "获取目录失败",
                    )
                }
            }
        }
    }

    fun selectPage(page: ExamDocPageSummary, forceRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                selectedPage = page,
                isCatalogExpanded = false,
                expandedFolders = it.expandedFolders + ancestorPaths(page.breadcrumb),
            )
        }
        loadPageMarkdown(page, forceRefresh = forceRefresh)
    }

    fun refreshCurrentPage() {
        val page = _uiState.value.selectedPage
        if (page != null) {
            loadPageMarkdown(page, forceRefresh = true)
        } else {
            loadTree(forceRefresh = true)
        }
    }

    fun toggleCatalog(expanded: Boolean? = null) {
        _uiState.update { state ->
            state.copy(isCatalogExpanded = expanded ?: !state.isCatalogExpanded)
        }
    }

    fun toggleFolder(folderPath: String) {
        _uiState.update { state ->
            val updated = if (folderPath in state.expandedFolders) {
                state.expandedFolders - folderPath
            } else {
                state.expandedFolders + folderPath
            }
            state.copy(expandedFolders = updated)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun navigatePrevious() {
        _uiState.value.previousPage?.let { selectPage(it) }
    }

    fun navigateNext() {
        _uiState.value.nextPage?.let { selectPage(it) }
    }

    fun currentMarkdownUrl(): String? {
        val state = _uiState.value
        val page = state.selectedPage ?: return null
        return repository.buildMarkdownUrl(state.siteBaseUrl, page.url)
    }

    fun currentWebPageUrl(): String? {
        val state = _uiState.value
        val page = state.selectedPage ?: return null
        return repository.buildWebPageUrl(state.siteBaseUrl, page.url)
    }

    private fun loadPageMarkdown(page: ExamDocPageSummary, forceRefresh: Boolean) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingPage = true,
                    pageError = null,
                )
            }
            try {
                val baseUrl = _uiState.value.siteBaseUrl
                val markdown = repository.fetchMarkdown(
                    pageUrl = page.url,
                    baseUrl = baseUrl,
                    forceRefresh = forceRefresh,
                )
                _uiState.update {
                    it.copy(
                        markdownContent = markdown,
                        isLoadingPage = false,
                        pageError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        pageError = e.message ?: "加载文档失败",
                    )
                }
            }
        }
    }

    private fun flattenPages(
        nodes: List<ExamDocNode>,
        breadcrumb: List<String> = emptyList(),
    ): List<ExamDocPageSummary> {
        val result = mutableListOf<ExamDocPageSummary>()
        for (node in nodes) {
            if (node.isPage && !node.url.isNullOrBlank()) {
                result += ExamDocPageSummary(
                    name = node.name,
                    url = node.url,
                    questions = node.questions,
                    breadcrumb = breadcrumb,
                )
            } else if (node.isFolder) {
                result += flattenPages(node.children, breadcrumb + node.name)
            }
        }
        return result
    }

    private fun buildInitialExpandedFolders(
        modules: List<ExamDocNode>,
        targetPage: ExamDocPageSummary?,
    ): Set<String> {
        val expanded = mutableSetOf<String>()
        modules.firstOrNull { it.isFolder }?.let { expanded += it.name }
        if (targetPage != null) {
            expanded += ancestorPaths(targetPage.breadcrumb)
        }
        return expanded
    }

    private fun ancestorPaths(breadcrumb: List<String>): Set<String> {
        val paths = mutableSetOf<String>()
        val builder = StringBuilder()
        for (segment in breadcrumb) {
            if (builder.isNotEmpty()) builder.append('/')
            builder.append(segment)
            paths += builder.toString()
        }
        return paths
    }
}
