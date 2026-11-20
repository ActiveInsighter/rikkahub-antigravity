package me.rerere.rikkahub.data.examdocs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_EXAM_DOCS_BASE_URL = "https://exam-docs.2212148739lbw.workers.dev"
const val DEFAULT_STUDY_DOCS_BASE_URL = "https://study-docs.any1.tech"
private const val PREFS_NAME = "exam_docs_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_LAST_PAGE_URL = "last_page_url"
private const val KEY_SITES_JSON = "sites_json"
private const val KEY_ACTIVE_SITE_ID = "active_site_id"

@Serializable
data class ExamDocSite(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lastPageUrl: String? = null,
    val isBuiltIn: Boolean = false,
)

data class MarkdownFetchResult(
    val content: String,
    val resolvedUrl: String,
    val isAndroidFormat: Boolean,
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

class ExamDocsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val treeCache = ConcurrentHashMap<String, List<ExamDocNode.Folder>>()
    private val markdownCache = ConcurrentHashMap<String, MarkdownFetchResult>()

    fun getSites(): List<ExamDocSite> {
        val rawJson = prefs.getString(KEY_SITES_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString(ListSerializer(ExamDocSite.serializer()), rawJson)
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                return parsed.map { it.copy(baseUrl = normalizeBaseUrl(it.baseUrl)) }
            }
        }

        // Migrate legacy single-site prefs if present.
        val legacyBase = prefs.getString(KEY_BASE_URL, null)?.let(::normalizeBaseUrl)
        val legacyLastPage = prefs.getString(KEY_LAST_PAGE_URL, null)
        val defaults = defaultSites().toMutableList()
        if (legacyBase != null && legacyBase != DEFAULT_EXAM_DOCS_BASE_URL) {
            defaults.add(
                ExamDocSite(
                    id = "custom-migrated",
                    name = deriveSiteName(legacyBase),
                    baseUrl = legacyBase,
                    lastPageUrl = legacyLastPage,
                    isBuiltIn = false,
                )
            )
        } else if (legacyLastPage != null) {
            defaults[0] = defaults[0].copy(lastPageUrl = legacyLastPage)
        }
        saveSitesInternal(defaults)
        if (legacyBase != null && legacyBase != DEFAULT_EXAM_DOCS_BASE_URL) {
            prefs.edit { putString(KEY_ACTIVE_SITE_ID, "custom-migrated") }
        }
        return defaults
    }

    fun getActiveSite(): ExamDocSite {
        val sites = getSites()
        val activeId = prefs.getString(KEY_ACTIVE_SITE_ID, null)
        return sites.firstOrNull { it.id == activeId } ?: sites.first()
    }

    fun selectSite(siteId: String): ExamDocSite {
        val sites = getSites()
        val target = sites.firstOrNull { it.id == siteId } ?: sites.first()
        prefs.edit {
            putString(KEY_ACTIVE_SITE_ID, target.id)
            putString(KEY_BASE_URL, target.baseUrl)
        }
        return target
    }

    fun upsertSite(
        id: String?,
        name: String,
        baseUrl: String,
    ): ExamDocSite {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val cleanName = name.trim().ifBlank { deriveSiteName(normalizedUrl) }
        val current = getSites().toMutableList()
        val existingIndex = if (id != null) current.indexOfFirst { it.id == id } else -1

        val savedSite = if (existingIndex >= 0) {
            val old = current[existingIndex]
            val updated = old.copy(name = cleanName, baseUrl = normalizedUrl)
            current[existingIndex] = updated
            if (old.baseUrl != normalizedUrl) {
                treeCache.remove(old.baseUrl)
            }
            updated
        } else {
            val existingByUrl = current.firstOrNull { it.baseUrl == normalizedUrl }
            if (existingByUrl != null) {
                val idx = current.indexOf(existingByUrl)
                val updated = existingByUrl.copy(name = cleanName)
                current[idx] = updated
                updated
            } else {
                ExamDocSite(
                    id = "site-${UUID.randomUUID().toString().take(8)}",
                    name = cleanName,
                    baseUrl = normalizedUrl,
                    isBuiltIn = false,
                ).also { current.add(it) }
            }
        }

        saveSitesInternal(current)
        prefs.edit {
            putString(KEY_ACTIVE_SITE_ID, savedSite.id)
            putString(KEY_BASE_URL, savedSite.baseUrl)
        }
        return savedSite
    }

    fun deleteSite(siteId: String): List<ExamDocSite> {
        val current = getSites().toMutableList()
        val target = current.firstOrNull { it.id == siteId }
        if (target != null && !target.isBuiltIn && current.size > 1) {
            current.remove(target)
            treeCache.remove(target.baseUrl)
            saveSitesInternal(current)
            if (prefs.getString(KEY_ACTIVE_SITE_ID, null) == siteId) {
                val fallback = current.first()
                prefs.edit {
                    putString(KEY_ACTIVE_SITE_ID, fallback.id)
                    putString(KEY_BASE_URL, fallback.baseUrl)
                }
            }
        }
        return current
    }

    fun resetDefaultSites(): List<ExamDocSite> {
        val defaults = defaultSites()
        saveSitesInternal(defaults)
        prefs.edit {
            putString(KEY_ACTIVE_SITE_ID, defaults.first().id)
            putString(KEY_BASE_URL, defaults.first().baseUrl)
        }
        return defaults
    }

    fun getSavedBaseUrl(): String = getActiveSite().baseUrl

    fun saveBaseUrl(url: String): String {
        val active = getActiveSite()
        val updated = upsertSite(id = active.id, name = active.name, baseUrl = url)
        return updated.baseUrl
    }

    fun getLastPageUrl(siteId: String = getActiveSite().id): String? {
        return getSites().firstOrNull { it.id == siteId }?.lastPageUrl
            ?: prefs.getString(KEY_LAST_PAGE_URL, null)
    }

    fun saveLastPageUrl(pageUrl: String, siteId: String = getActiveSite().id) {
        val normalized = normalizeDocPath(pageUrl)
        val sites = getSites().map { site ->
            if (site.id == siteId) site.copy(lastPageUrl = normalized) else site
        }
        saveSitesInternal(sites)
        prefs.edit { putString(KEY_LAST_PAGE_URL, normalized) }
    }

    private fun saveSitesInternal(sites: List<ExamDocSite>) {
        val encoded = json.encodeToString(ListSerializer(ExamDocSite.serializer()), sites)
        prefs.edit { putString(KEY_SITES_JSON, encoded) }
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
        forceRefresh: Boolean = false,
    ): Result<MarkdownFetchResult> = withContext(Dispatchers.IO) {
        val androidUrl = buildAndroidMarkdownUrl(baseUrl, pageUrl)
        val fallbackUrl = buildMarkdownUrl(baseUrl, pageUrl)
        val cacheKey = "$baseUrl::$pageUrl"

        if (!forceRefresh) {
            markdownCache[cacheKey]?.let { return@withContext Result.success(it) }
        }

        runCatching {
            // Prefer Android-optimized `.android.md` export, fall back to standard `.md` on 404.
            val androidAttempt = executeMarkdownRequest(androidUrl)
            val result = if (androidAttempt != null) {
                MarkdownFetchResult(
                    content = androidAttempt,
                    resolvedUrl = androidUrl,
                    isAndroidFormat = true,
                )
            } else {
                val standardBody = executeMarkdownRequest(fallbackUrl)
                    ?: throw IOException("HTTP 404 Not Found")
                MarkdownFetchResult(
                    content = standardBody,
                    resolvedUrl = fallbackUrl,
                    isAndroidFormat = false,
                )
            }
            markdownCache[cacheKey] = result
            result
        }
    }

    private fun executeMarkdownRequest(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/markdown, text/plain, */*")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 404) {
                return null
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message}".trim())
            }
            return response.body.string()
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
        fun defaultSites(): List<ExamDocSite> = listOf(
            ExamDocSite(
                id = "exam-docs",
                name = "Exam Docs",
                baseUrl = DEFAULT_EXAM_DOCS_BASE_URL,
                isBuiltIn = true,
            ),
            ExamDocSite(
                id = "study-docs",
                name = "Study Docs",
                baseUrl = DEFAULT_STUDY_DOCS_BASE_URL,
                isBuiltIn = true,
            ),
        )

        fun deriveSiteName(baseUrl: String): String {
            return baseUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore('.')
                .replaceFirstChar { it.uppercase() }
                .ifBlank { "Custom Docs" }
        }

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
            val standard = buildMarkdownUrl(baseUrl, pageUrl)
            return if (standard.endsWith(".android.md")) {
                standard
            } else if (standard.endsWith(".md")) {
                standard.removeSuffix(".md") + ".android.md"
            } else {
                "$standard.android.md"
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
