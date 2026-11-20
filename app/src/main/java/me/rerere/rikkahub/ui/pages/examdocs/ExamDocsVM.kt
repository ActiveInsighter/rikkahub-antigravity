package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.examdocs.DocsSite
import me.rerere.rikkahub.data.examdocs.ExamDocFlatEntry
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import java.util.UUID

@Immutable
data class ExamDocsUiState(
    val sites: List<DocsSite> = emptyList(),
    val activeSite: DocsSite = ExamDocsRepository.defaultSites().first(),
    val baseUrl: String = activeSite.baseUrl,
    val isLoadingTree: Boolean = true,
    val treeError: String? = null,
    val modules: List<ExamDocNode.Folder> = emptyList(),
    val selectedModuleIndex: Int = 0,
    val currentPage: ExamDocNode.Page? = null,
    val currentBreadcrumb: List<String> = emptyList(),
    val isLoadingContent: Boolean = false,
    val contentError: String? = null,
    val markdownContent: String? = null,
    val loadedSourceUrl: String? = null,
    val isAndroidFormat: Boolean = false,
    val renderItems: List<ExamDocsRenderItem> = emptyList(),
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
        run {
            val sites = repository.getSites()
            val active = repository.getActiveSite()
            ExamDocsUiState(
                sites = sites,
                activeSite = active,
                baseUrl = active.baseUrl,
            )
        }
    )
    val uiState = _uiState.asStateFlow()

    private var flatEntries: List<ExamDocFlatEntry> = emptyList()
    private var loadTreeJob: Job? = null
    private var loadContentJob: Job? = null

    init {
        loadTree(forceRefresh = false, targetUrl = initialUrl)
    }

    fun loadTree(
        forceRefresh: Boolean = false,
        targetUrl: String? = null,
    ) {
        loadTreeJob?.cancel()
        val site = _uiState.value.activeSite
        loadTreeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeError = null) }
            repository.fetchSourceTree(baseUrl = site.baseUrl, forceRefresh = forceRefresh)
                .onSuccess { modules ->
                    val flattened = withContext(Dispatchers.Default) {
                        ExamDocsRepository.flattenPages(modules)
                    }
                    flatEntries = flattened

                    val desiredUrl = targetUrl
                        ?: _uiState.value.currentPage?.url
                        ?: repository.getLastPageUrl(site.id)

                    val matchedEntry = desiredUrl?.let { raw ->
                        val normalized = ExamDocsRepository.normalizeDocPath(raw)
                        flattened.firstOrNull { it.page.url == normalized }
                    } ?: flattened.firstOrNull()

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

    fun selectSite(siteId: String) {
        if (siteId == _uiState.value.activeSite.id) return
        val active = repository.setActiveSite(siteId)
        val sites = repository.getSites()
        switchToSite(sites, active, forceRefresh = false)
    }

    fun saveSite(
        existingId: String?,
        name: String,
        baseUrl: String,
        preferAndroidMd: Boolean,
        activate: Boolean = true,
    ) {
        val id = existingId?.takeIf { it.isNotBlank() } ?: "site_${UUID.randomUUID().toString().take(8)}"
        val site = DocsSite(
            id = id,
            name = name.trim().ifBlank { ExamDocsRepository.hostLabel(baseUrl) },
            baseUrl = ExamDocsRepository.normalizeBaseUrl(baseUrl),
            preferAndroidMd = preferAndroidMd,
        )
        val sites = repository.upsertSite(site)
        if (activate || id == _uiState.value.activeSite.id) {
            val active = repository.setActiveSite(id)
            switchToSite(sites, active, forceRefresh = true)
        } else {
            _uiState.update { it.copy(sites = sites) }
        }
    }

    fun deleteSite(siteId: String) {
        val remaining = repository.deleteSite(siteId)
        val active = repository.getActiveSite()
        if (active.id != _uiState.value.activeSite.id) {
            switchToSite(remaining, active, forceRefresh = false)
        } else {
            _uiState.update { it.copy(sites = remaining) }
        }
    }

    fun resetDefaultSites() {
        val defaults = repository.resetDefaultSites()
        val active = repository.getActiveSite()
        switchToSite(defaults, active, forceRefresh = false)
    }

    private fun switchToSite(
        sites: List<DocsSite>,
        active: DocsSite,
        forceRefresh: Boolean,
    ) {
        loadContentJob?.cancel()
        flatEntries = emptyList()
        _uiState.update {
            it.copy(
                sites = sites,
                activeSite = active,
                baseUrl = active.baseUrl,
                modules = emptyList(),
                selectedModuleIndex = 0,
                currentPage = null,
                currentBreadcrumb = emptyList(),
                markdownContent = null,
                loadedSourceUrl = null,
                isAndroidFormat = false,
                renderItems = emptyList(),
                headings = emptyList(),
                prevPage = null,
                nextPage = null,
                contentError = null,
                isLoadingContent = false,
                searchQuery = "",
                searchResults = emptyList(),
            )
        }
        loadTree(forceRefresh = forceRefresh)
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
        val site = _uiState.value.activeSite

        repository.saveLastPageUrl(entry.page.url, site.id)
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
        val site = _uiState.value.activeSite
        repository.saveLastPageUrl(normalized, site.id)
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
        val site = _uiState.value.activeSite
        loadContentJob = viewModelScope.launch {
            repository.fetchMarkdown(
                baseUrl = site.baseUrl,
                pageUrl = pageUrl,
                preferAndroidMd = site.preferAndroidMd,
                forceRefresh = forceRefresh,
            )
                .onSuccess { loaded ->
                    val (items, headings) = withContext(Dispatchers.Default) {
                        val processed = preprocessExamDocsMarkdown(loaded.content)
                        buildExamDocsRenderPlan(processed)
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingContent = false,
                            contentError = null,
                            markdownContent = loaded.content,
                            loadedSourceUrl = loaded.sourceUrl,
                            isAndroidFormat = loaded.isAndroidFormat,
                            renderItems = items,
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
