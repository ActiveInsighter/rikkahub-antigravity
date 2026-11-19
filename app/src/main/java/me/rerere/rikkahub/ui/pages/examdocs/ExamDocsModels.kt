package me.rerere.rikkahub.ui.pages.examdocs

import kotlinx.serialization.Serializable

const val DEFAULT_EXAM_DOCS_BASE_URL = "https://exam.any1.tech"

@Serializable
data class ExamDocSourcesResponse(
    val modules: List<ExamDocNode> = emptyList(),
)

@Serializable
data class ExamDocNode(
    val kind: String,
    val name: String,
    val url: String? = null,
    val questions: Int = 0,
    val children: List<ExamDocNode> = emptyList(),
) {
    val isFolder: Boolean get() = kind == "folder"
    val isPage: Boolean get() = kind == "page"
}

data class ExamDocPageSummary(
    val name: String,
    val url: String,
    val questions: Int,
    val breadcrumb: List<String>,
)

data class ExamDocsUiState(
    val siteBaseUrl: String = DEFAULT_EXAM_DOCS_BASE_URL,
    val modules: List<ExamDocNode> = emptyList(),
    val flatPages: List<ExamDocPageSummary> = emptyList(),
    val selectedPage: ExamDocPageSummary? = null,
    val markdownContent: String? = null,
    val isLoadingTree: Boolean = false,
    val isLoadingPage: Boolean = false,
    val treeError: String? = null,
    val pageError: String? = null,
    val isCatalogExpanded: Boolean = false,
    val searchQuery: String = "",
    val expandedFolders: Set<String> = emptySet(),
) {
    val currentPageIndex: Int
        get() = selectedPage?.let { current ->
            flatPages.indexOfFirst { it.url == current.url }
        } ?: -1

    val previousPage: ExamDocPageSummary?
        get() = currentPageIndex.takeIf { it > 0 }?.let { flatPages[it - 1] }

    val nextPage: ExamDocPageSummary?
        get() = currentPageIndex.takeIf { it in 0 until flatPages.lastIndex }?.let { flatPages[it + 1] }
}
