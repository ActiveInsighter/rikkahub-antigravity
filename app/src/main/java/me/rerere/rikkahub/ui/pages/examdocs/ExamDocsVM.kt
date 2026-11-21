package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.examdocs.ExamDocFlatEntry
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsDirectory
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsLink
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.data.examdocs.ExamDocsUiSettings
import me.rerere.rikkahub.data.examdocs.builtInDocsSites
import me.rerere.rikkahub.data.examdocs.getExamDocsLink
import okhttp3.HttpUrl.Companion.toHttpUrl

@Immutable
data class ExamDocsUiState(
    val sites: List<ExamDocsSite> = builtInDocsSites,
    val activeSite: ExamDocsSite = builtInDocsSites.first(),
    val sitesReady: Boolean = false,
    val uiSettings: ExamDocsUiSettings = ExamDocsUiSettings(),
    val isLoadingTree: Boolean = true,
    val treeError: String? = null,
    val modules: List<ExamDocNode.Folder> = emptyList(),
    val selectedModuleIndex: Int = 0,
    val currentPage: ExamDocNode.Page? = null,
    val currentBreadcrumb: List<String> = emptyList(),
    val isLoadingContent: Boolean = false,
    val contentError: String? = null,
    val document: ExamDocsDocument? = null,
    val pendingAnchor: String? = null,
    val prevPage: ExamDocNode.Page? = null,
    val nextPage: ExamDocNode.Page? = null,
    val searchQuery: String = "",
    val searchResults: List<ExamDocFlatEntry> = emptyList(),
)

class ExamDocsVM(
    private val initialUrl: String?,
    private val repository: ExamDocsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamDocsUiState())
    val uiState = _uiState.asStateFlow()

    private var directory: ExamDocsDirectory? = null
    private var directoryJob: Job? = null
    private var documentJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val (settings, uiSettings) = withContext(Dispatchers.IO) {
                repository.siteSettingsFlow.value to repository.uiSettingsFlow.value
            }
            val target = initialUrl?.let { getExamDocsLink(settings.active.baseUrl, "/docs", it) }
            _uiState.value = ExamDocsUiState(
                sites = settings.sites,
                activeSite = settings.active,
                sitesReady = true,
                uiSettings = uiSettings,
            )
            loadTree(target = target)

            launch {
                repository.uiSettingsFlow.drop(1).collectLatest { updatedUi ->
                    _uiState.update { it.copy(uiSettings = updatedUi) }
                }
            }
            launch {
                repository.siteSettingsFlow.drop(1).collectLatest { updatedSites ->
                    applySiteSettings(updatedSites.sites, updatedSites.active)
                }
            }
        }
    }

    fun reload() {
        val current = _uiState.value.currentPage
        val target = current?.let { ExamDocsLink(it.url, _uiState.value.pendingAnchor) }
        loadTree(refresh = true, target = target)
    }

    private fun loadTree(refresh: Boolean = false, target: ExamDocsLink? = null) {
        val site = _uiState.value.activeSite
        directoryJob?.cancel()
        documentJob?.cancel()
        directoryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeError = null, isLoadingContent = false, document = null) }
            try {
                val index = repository.fetchSourceTree(site, refresh)
                val preferred = target?.path ?: withContext(Dispatchers.IO) { repository.getLastPageUrl(site) }
                directory = index
                val entry = preferred?.let(index.pageIndexes::get)?.let(index.entries::get) ?: index.entries.firstOrNull()
                val page = if (target != null && target.path !in index.pageIndexes) {
                    ExamDocNode.Page(documentTitle(target.path), target.path)
                } else {
                    entry?.page
                }
                _uiState.update {
                    it.copy(isLoadingTree = false, modules = index.modules)
                }
                updateSearchQuery(_uiState.value.searchQuery)
                if (page != null) openPage(page, refresh, target?.fragment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoadingTree = false, treeError = error.message ?: "无法加载文档目录") }
            }
        }
    }

    fun switchSite(site: ExamDocsSite) {
        repository.switchSite(site)
    }

    fun saveSite(original: ExamDocsSite?, name: String, rawUrl: String): String? =
        repository.saveSite(original, name, rawUrl)

    fun removeSite(site: ExamDocsSite) {
        repository.removeSite(site)
    }

    fun restoreDefaultSites() {
        repository.restoreDefaultSites()
    }

    private fun applySiteSettings(sites: List<ExamDocsSite>, active: ExamDocsSite) {
        val state = _uiState.value
        if (state.activeSite.baseUrl == active.baseUrl) {
            _uiState.update { it.copy(sites = sites, activeSite = active) }
            return
        }
        searchJob?.cancel()
        directory = null
        _uiState.value = ExamDocsUiState(
            sites = sites,
            activeSite = active,
            sitesReady = true,
            uiSettings = state.uiSettings,
            searchQuery = state.searchQuery,
        )
        loadTree()
    }

    fun selectModule(index: Int) {
        _uiState.update { if (index in it.modules.indices) it.copy(selectedModuleIndex = index) else it }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchResults = emptyList()) }
        val trimmed = query.trim()
        val entries = directory?.entries ?: return
        if (trimmed.isEmpty()) return
        searchJob = viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                entries.asSequence().filter { entry ->
                    entry.page.name.contains(trimmed, ignoreCase = true) ||
                        entry.breadcrumb.any { it.contains(trimmed, ignoreCase = true) } ||
                        documentTitle(entry.page.url).contains(trimmed, ignoreCase = true) ||
                        entry.page.url.contains(trimmed, ignoreCase = true)
                }.take(60).toList()
            }
            _uiState.update { it.copy(searchResults = matches) }
        }
    }

    fun openLink(link: ExamDocsLink) {
        val entry = directory?.let { index -> index.pageIndexes[link.path]?.let(index.entries::get) }
        openPage(entry?.page ?: ExamDocNode.Page(documentTitle(link.path), link.path), anchor = link.fragment)
    }

    fun openPage(page: ExamDocNode.Page) = openPage(page, refresh = false)

    private fun openPage(page: ExamDocNode.Page, refresh: Boolean = false, anchor: String? = null) {
        val state = _uiState.value
        if (!refresh && state.currentPage?.url == page.url && state.document != null) {
            _uiState.update { it.copy(pendingAnchor = anchor) }
            return
        }

        documentJob?.cancel()
        val index = directory
        val pageIndex = index?.pageIndexes?.get(page.url) ?: -1
        val entry = index?.entries?.getOrNull(pageIndex)
        _uiState.update {
            it.copy(
                currentPage = page,
                selectedModuleIndex = entry?.moduleIndex ?: it.selectedModuleIndex,
                currentBreadcrumb = entry?.breadcrumb.orEmpty(),
                prevPage = index?.entries?.getOrNull(pageIndex - 1)?.page,
                nextPage = if (pageIndex >= 0) index?.entries?.getOrNull(pageIndex + 1)?.page else null,
                isLoadingContent = true,
                contentError = null,
                document = null,
                pendingAnchor = anchor,
            )
        }

        documentJob = viewModelScope.launch {
            try {
                val document = repository.fetchDocument(
                    state.activeSite, page.url, index?.usesAndroidMarkdown == true, refresh,
                )
                repository.saveLastPageUrl(state.activeSite, page.url)
                _uiState.update { it.copy(isLoadingContent = false, document = document) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoadingContent = false, contentError = error.message ?: "无法加载文档") }
            }
        }
    }

    fun consumeAnchor(anchor: String) {
        _uiState.update { if (it.pendingAnchor == anchor) it.copy(pendingAnchor = null) else it }
    }

    private fun documentTitle(path: String): String =
        ("https://docs.invalid$path").toHttpUrl().pathSegments.last().ifBlank { "文档" }
}
