package me.rerere.rikkahub.data.examdocs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_EXAM_DOCS_BASE_URL = "https://exam-docs.2212148739lbw.workers.dev"
private const val PREFS_NAME = "exam_docs_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_LAST_PAGE_URL = "last_page_url"

@Serializable
data class ExamDocSourceTreeDto(
    val modules: List<ExamDocRawNodeDto> = emptyList(),
)

@Serializable
data class ExamDocRawNodeDto(
    val kind: String = "page",
    val name: String = "",
    val url: String? = null,
    val questions: Int = 0,
    val children: List<ExamDocRawNodeDto> = emptyList(),
)

sealed interface ExamDocNode {
    val name: String
    val questions: Int

    data class Page(
        override val name: String,
        val url: String,
        override val questions: Int = 0,
    ) : ExamDocNode

    data class Folder(
        override val name: String,
        val children: List<ExamDocNode> = emptyList(),
        override val questions: Int = 0,
    ) : ExamDocNode
}

data class ExamDocFlatEntry(
    val page: ExamDocNode.Page,
    val moduleIndex: Int,
    val moduleName: String,
    val breadcrumb: List<String>,
)

class ExamDocsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val markdownCache = ConcurrentHashMap<String, String>()
    private var cachedTreeUrl: String? = null
    private var cachedModules: List<ExamDocNode.Folder>? = null

    fun getSavedBaseUrl(): String {
        val raw = prefs.getString(KEY_BASE_URL, null)
        return normalizeBaseUrl(raw ?: DEFAULT_EXAM_DOCS_BASE_URL)
    }

    fun saveBaseUrl(url: String): String {
        val normalized = normalizeBaseUrl(url)
        prefs.edit { putString(KEY_BASE_URL, normalized) }
        if (cachedTreeUrl != normalized) {
            cachedTreeUrl = null
            cachedModules = null
            markdownCache.clear()
        }
        return normalized
    }

    fun getLastPageUrl(): String? = prefs.getString(KEY_LAST_PAGE_URL, null)

    fun saveLastPageUrl(pageUrl: String) {
        prefs.edit { putString(KEY_LAST_PAGE_URL, normalizeDocPath(pageUrl)) }
    }

    suspend fun fetchSourceTree(
        baseUrl: String = getSavedBaseUrl(),
        forceRefresh: Boolean = false,
    ): Result<List<ExamDocNode.Folder>> = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        if (!forceRefresh && cachedTreeUrl == normalizedBase) {
            cachedModules?.let { return@withContext Result.success(it) }
        }

        runCatching {
            val request = Request.Builder()
                .url("$normalizedBase/doc-sources.json")
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} ${response.message}".trim())
                }
                val body = response.body.string()
                val dto = json.decodeFromString(ExamDocSourceTreeDto.serializer(), body)
                val modules = dto.modules.mapNotNull { raw -> convertFolder(raw) }
                cachedTreeUrl = normalizedBase
                cachedModules = modules
                modules
            }
        }
    }

    suspend fun fetchMarkdown(
        baseUrl: String,
        pageUrl: String,
        forceRefresh: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        val mdUrl = buildMarkdownUrl(baseUrl, pageUrl)
        if (!forceRefresh) {
            markdownCache[mdUrl]?.let { return@withContext Result.success(it) }
        }

        runCatching {
            val request = Request.Builder()
                .url(mdUrl)
                .header("Accept", "text/markdown, text/plain, */*")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} ${response.message}".trim())
                }
                val text = response.body.string()
                markdownCache[mdUrl] = text
                text
            }
        }
    }

    private fun convertFolder(raw: ExamDocRawNodeDto): ExamDocNode.Folder? {
        if (raw.kind != "folder") return null
        val children = raw.children.mapNotNull { convertNode(it) }
        return ExamDocNode.Folder(
            name = raw.name.ifBlank { "未命名目录" },
            children = children,
            questions = raw.questions,
        )
    }

    private fun convertNode(raw: ExamDocRawNodeDto): ExamDocNode? {
        return when (raw.kind) {
            "folder" -> convertFolder(raw)
            "page" -> {
                val url = raw.url?.takeIf { it.isNotBlank() } ?: return null
                ExamDocNode.Page(
                    name = raw.name.ifBlank { url.substringAfterLast('/') },
                    url = normalizeDocPath(url),
                    questions = raw.questions,
                )
            }
            else -> null
        }
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return DEFAULT_EXAM_DOCS_BASE_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        fun normalizeDocPath(rawPath: String): String {
            val bare = rawPath.trim()
                .substringBefore('#')
                .substringBefore('?')
                .trimEnd('/')
            if (bare.isEmpty()) return "/docs"
            return if (bare.startsWith('/')) bare else "/$bare"
        }

        fun buildMarkdownUrl(baseUrl: String, pageUrl: String): String {
            val normalizedBase = normalizeBaseUrl(baseUrl)
            val trimmed = pageUrl.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val withoutQuery = trimmed.substringBefore('#').substringBefore('?')
                return if (withoutQuery.endsWith(".md")) {
                    withoutQuery
                } else {
                    "${withoutQuery.trimEnd('/')}.md"
                }
            }
            val normalizedPath = normalizeDocPath(trimmed)
            val mdPath = if (normalizedPath.endsWith(".md")) {
                normalizedPath
            } else {
                "$normalizedPath.md"
            }
            return "$normalizedBase$mdPath"
        }

        fun flattenPages(modules: List<ExamDocNode.Folder>): List<ExamDocFlatEntry> {
            val entries = mutableListOf<ExamDocFlatEntry>()
            fun walk(
                nodes: List<ExamDocNode>,
                moduleIndex: Int,
                moduleName: String,
                path: List<String>,
            ) {
                for (node in nodes) {
                    when (node) {
                        is ExamDocNode.Page -> {
                            entries.add(
                                ExamDocFlatEntry(
                                    page = node,
                                    moduleIndex = moduleIndex,
                                    moduleName = moduleName,
                                    breadcrumb = path,
                                )
                            )
                        }
                        is ExamDocNode.Folder -> {
                            walk(
                                nodes = node.children,
                                moduleIndex = moduleIndex,
                                moduleName = moduleName,
                                path = path + node.name,
                            )
                        }
                    }
                }
            }

            modules.forEachIndexed { index, module ->
                walk(
                    nodes = module.children,
                    moduleIndex = index,
                    moduleName = module.name,
                    path = listOf(module.name),
                )
            }
            return entries
        }
    }
}
