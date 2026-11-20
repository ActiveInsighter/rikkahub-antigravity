package me.rerere.rikkahub.data.examdocs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_EXAM_DOCS_BASE_URL = "https://exam-docs.2212148739lbw.workers.dev"
const val DEFAULT_STUDY_DOCS_BASE_URL = "https://study-docs.any1.tech"

private const val PREFS_NAME = "exam_docs_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_LAST_PAGE_URL = "last_page_url"
private const val KEY_SITES_JSON = "sites_json"
private const val KEY_ACTIVE_SITE_ID = "active_site_id"
private const val KEY_LAST_PAGE_BY_SITE_JSON = "last_page_by_site_json"

@Serializable
data class DocsSite(
    val id: String,
    val name: String,
    val baseUrl: String,
    val preferAndroidMd: Boolean = true,
)

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

data class LoadedMarkdown(
    val content: String,
    val sourceUrl: String,
    val isAndroidFormat: Boolean,
)

class ExamDocsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val treeCache = ConcurrentHashMap<String, List<ExamDocNode.Folder>>()
    private val markdownCache = ConcurrentHashMap<String, String>()

    fun getSites(): List<DocsSite> {
        val rawJson = prefs.getString(KEY_SITES_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val parsed = json.decodeFromString<List<DocsSite>>(rawJson)
                    .map { it.copy(baseUrl = normalizeBaseUrl(it.baseUrl)) }
                if (parsed.isNotEmpty()) return parsed
            }
        }

        // Migrate legacy single-site preference if present
        val legacyBase = prefs.getString(KEY_BASE_URL, null)?.let { normalizeBaseUrl(it) }
        val defaults = defaultSites().map { site ->
            if (site.id == "exam-docs" && legacyBase != null && legacyBase != DEFAULT_EXAM_DOCS_BASE_URL) {
                site.copy(baseUrl = legacyBase)
            } else {
                site
            }
        }
        saveSitesInternal(defaults)
        return defaults
    }

    fun getActiveSite(): DocsSite {
        val sites = getSites()
        val activeId = prefs.getString(KEY_ACTIVE_SITE_ID, null)
        return sites.firstOrNull { it.id == activeId } ?: sites.first()
    }

    fun setActiveSite(siteId: String): DocsSite {
        val sites = getSites()
        val target = sites.firstOrNull { it.id == siteId } ?: sites.first()
        prefs.edit {
            putString(KEY_ACTIVE_SITE_ID, target.id)
            putString(KEY_BASE_URL, target.baseUrl)
        }
        return target
    }

    fun upsertSite(site: DocsSite): List<DocsSite> {
        val normalized = site.copy(
            name = site.name.trim().ifBlank { hostLabel(site.baseUrl) },
            baseUrl = normalizeBaseUrl(site.baseUrl),
        )
        val current = getSites().toMutableList()
        val existingIdx = current.indexOfFirst { it.id == normalized.id }
        if (existingIdx >= 0) {
            val oldUrl = current[existingIdx].baseUrl
            if (oldUrl != normalized.baseUrl) {
                invalidateSiteCache(oldUrl)
            }
            current[existingIdx] = normalized
        } else {
            current.add(normalized)
        }
        saveSitesInternal(current)
        return current
    }

    fun deleteSite(siteId: String): List<DocsSite> {
        val current = getSites()
        if (current.size <= 1) return current
        val removed = current.firstOrNull { it.id == siteId }
        val remaining = current.filterNot { it.id == siteId }
        if (remaining.isEmpty()) return current
        if (removed != null) {
            invalidateSiteCache(removed.baseUrl)
        }
        saveSitesInternal(remaining)
        if (prefs.getString(KEY_ACTIVE_SITE_ID, null) == siteId) {
            setActiveSite(remaining.first().id)
        }
        return remaining
    }

    fun resetDefaultSites(): List<DocsSite> {
        val defaults = defaultSites()
        saveSitesInternal(defaults)
        setActiveSite(defaults.first().id)
        return defaults
    }

    fun getSavedBaseUrl(): String = getActiveSite().baseUrl

    fun saveBaseUrl(url: String): String {
        val active = getActiveSite()
        val normalized = normalizeBaseUrl(url)
        upsertSite(active.copy(baseUrl = normalized))
        prefs.edit { putString(KEY_BASE_URL, normalized) }
        return normalized
    }

    fun getLastPageUrl(siteId: String = getActiveSite().id): String? {
        val map = getLastPageMap()
        return map[siteId] ?: if (siteId == "exam-docs") prefs.getString(KEY_LAST_PAGE_URL, null) else null
    }

    fun saveLastPageUrl(pageUrl: String, siteId: String = getActiveSite().id) {
        val normalized = normalizeDocPath(pageUrl)
        val map = getLastPageMap().toMutableMap()
        map[siteId] = normalized
        prefs.edit {
            putString(KEY_LAST_PAGE_BY_SITE_JSON, json.encodeToString(map))
            if (siteId == "exam-docs") {
                putString(KEY_LAST_PAGE_URL, normalized)
            }
        }
    }

    private fun getLastPageMap(): Map<String, String> {
        val raw = prefs.getString(KEY_LAST_PAGE_BY_SITE_JSON, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    private fun saveSitesInternal(sites: List<DocsSite>) {
        prefs.edit { putString(KEY_SITES_JSON, json.encodeToString(sites)) }
    }

    private fun invalidateSiteCache(baseUrl: String) {
        val normalized = normalizeBaseUrl(baseUrl)
        treeCache.remove(normalized)
        val keysToRemove = markdownCache.keys.filter { it.startsWith("$normalized/") }
        keysToRemove.forEach { markdownCache.remove(it) }
    }

    suspend fun fetchSourceTree(
        baseUrl: String = getSavedBaseUrl(),
        forceRefresh: Boolean = false,
    ): Result<List<ExamDocNode.Folder>> = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        if (!forceRefresh) {
            treeCache[normalizedBase]?.let { return@withContext Result.success(it) }
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
                treeCache[normalizedBase] = modules
                modules
            }
        }
    }

    suspend fun fetchMarkdown(
        baseUrl: String,
        pageUrl: String,
        preferAndroidMd: Boolean = true,
        forceRefresh: Boolean = false,
    ): Result<LoadedMarkdown> = withContext(Dispatchers.IO) {
        val androidUrl = buildAndroidMarkdownUrl(baseUrl, pageUrl)
        val standardUrl = buildMarkdownUrl(baseUrl, pageUrl)

        if (preferAndroidMd) {
            if (!forceRefresh) {
                markdownCache[androidUrl]?.let {
                    return@withContext Result.success(LoadedMarkdown(it, androidUrl, isAndroidFormat = true))
                }
            }
            val androidAttempt = executeMarkdownRequest(androidUrl)
            if (androidAttempt.isSuccess) {
                val text = androidAttempt.getOrThrow()
                markdownCache[androidUrl] = text
                return@withContext Result.success(LoadedMarkdown(text, androidUrl, isAndroidFormat = true))
            }
            val err = androidAttempt.exceptionOrNull()
            // Fall back to standard .md if .android.md is not deployed yet (HTTP 404)
            if (err !is HttpStatusException || err.code != 404) {
                return@withContext Result.failure(err ?: IOException("Unknown error"))
            }
        }

        if (!forceRefresh) {
            markdownCache[standardUrl]?.let {
                return@withContext Result.success(LoadedMarkdown(it, standardUrl, isAndroidFormat = false))
            }
        }

        executeMarkdownRequest(standardUrl).map { text ->
            markdownCache[standardUrl] = text
            LoadedMarkdown(text, standardUrl, isAndroidFormat = false)
        }
    }

    private fun executeMarkdownRequest(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/markdown, text/plain, */*")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "HTTP ${response.code} ${response.message}".trim())
            }
            response.body.string()
        }
    }

    private class HttpStatusException(val code: Int, message: String) : IOException(message)

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
        fun defaultSites(): List<DocsSite> = listOf(
            DocsSite(
                id = "exam-docs",
                name = "Exam Docs",
                baseUrl = DEFAULT_EXAM_DOCS_BASE_URL,
                preferAndroidMd = true,
            ),
            DocsSite(
                id = "study-docs",
                name = "Study Docs",
                baseUrl = DEFAULT_STUDY_DOCS_BASE_URL,
                preferAndroidMd = true,
            ),
        )

        fun hostLabel(url: String): String = normalizeBaseUrl(url)
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .ifBlank { "Custom Docs" }

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

        fun buildAndroidMarkdownUrl(baseUrl: String, pageUrl: String): String {
            val mdUrl = buildMarkdownUrl(baseUrl, pageUrl)
            return when {
                mdUrl.endsWith(".android.md") -> mdUrl
                mdUrl.endsWith(".md") -> mdUrl.removeSuffix(".md") + ".android.md"
                else -> "$mdUrl.android.md"
            }
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
