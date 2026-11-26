package me.rerere.rikkahub.data.examdocs

import android.content.Context
import android.util.LruCache
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val DEFAULT_EXAM_DOCS_BASE_URL = "https://exam.any1.tech"
private const val LEGACY_EXAM_DOCS_BASE_URL = "https://exam-docs.2212148739lbw.workers.dev"
const val DEFAULT_STUDY_DOCS_BASE_URL = "https://study-docs.any1.tech"
private const val PREFS_NAME = "exam_docs_prefs"
private const val KEY_SITES = "sites"
private const val KEY_SITES_V2_MIGRATED = "sites_v2_migrated"
private const val KEY_ACTIVE_SITE = "active_site"
private const val KEY_BASE_URL = "base_url"
private const val KEY_LAST_PAGE_URL = "last_page_url"
private const val KEY_UI_SETTINGS = "ui_settings"
private const val KEY_RECENT_DOCS = "recent_docs_v1"
private const val MAX_RECENT_DOCS = 12
private const val ANDROID_MARKDOWN_VERSION = 2
private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024

@Serializable
data class ExamDocsSite(val name: String, val baseUrl: String)

@Serializable
data class RecentExamDoc(
    val siteBaseUrl: String,
    val siteName: String,
    val pageUrl: String,
    val title: String,
    val breadcrumb: List<String> = emptyList(),
    val lastReadAt: Long = System.currentTimeMillis(),
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

val builtInDocsSites = listOf(
    ExamDocsSite("Exam Docs", DEFAULT_EXAM_DOCS_BASE_URL),
    ExamDocsSite("Study Docs", DEFAULT_STUDY_DOCS_BASE_URL),
)

data class ExamDocsSiteSettings(val sites: List<ExamDocsSite>, val active: ExamDocsSite)

@Serializable
data class ExamDocsUiSettings(
    val enableBlurEffect: Boolean = true,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val fontSizeRatio: Float = 1.0f,
)

@Serializable
data class ExamDocSourceTreeDto(
    val modules: List<ExamDocRawNodeDto>,
    val androidMarkdownVersion: Int? = null,
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

data class ExamDocsDirectory(
    val modules: List<ExamDocNode.Folder>,
    val entries: List<ExamDocFlatEntry>,
    val usesAndroidMarkdown: Boolean,
) {
    val pageIndexes = entries.mapIndexed { index, entry -> entry.page.url to index }.toMap()
}

class ExamDocsRepository internal constructor(
    context: Context,
    okHttpClient: OkHttpClient,
    json: Json,
    database: DocsDatabase,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val json = Json(from = json) { ignoreUnknownKeys = true }
    private val client = okHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
    private val cacheDao = database.dao()
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap.newKeySet<Pair<String, String>>()
    private val directories = LruCache<String, CachedValue<ExamDocsDirectory>>(4)
    private val documents = object : LruCache<String, CachedValue<ExamDocsDocument>>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedValue<ExamDocsDocument>) = value.value.sourceBytes
    }

    private data class CachedValue<T>(val value: T, val fetchedAt: Long) {
        val fresh: Boolean get() = System.currentTimeMillis() - fetchedAt < TimeUnit.HOURS.toMillis(12)
    }

    fun loadSiteSettings(): ExamDocsSiteSettings {
        val saved = prefs.getString(KEY_SITES, null)
        if (saved == null) {
            val rawLegacy = parseBaseUrl(prefs.getString(KEY_BASE_URL, DEFAULT_EXAM_DOCS_BASE_URL).orEmpty())
            val legacy = if (rawLegacy == LEGACY_EXAM_DOCS_BASE_URL) DEFAULT_EXAM_DOCS_BASE_URL else rawLegacy
            val active = builtInDocsSites.firstOrNull { it.baseUrl == legacy }
                ?: legacy?.let { ExamDocsSite(it.toHttpUrl().host, it) }
                ?: builtInDocsSites.first()
            val sites = (builtInDocsSites + active).distinctBy(ExamDocsSite::baseUrl)
            val lastPage = prefs.getString(KEY_LAST_PAGE_URL, null)
            prefs.edit {
                putString(KEY_SITES, json.encodeToString(sites))
                putString(KEY_ACTIVE_SITE, active.baseUrl)
                putBoolean(KEY_SITES_V2_MIGRATED, true)
                lastPage?.let { putString(lastPageKey(active.baseUrl), normalizeDocPath(it)) }
            }
            return ExamDocsSiteSettings(sites, active)
        }

        val decoded = runCatching { json.decodeFromString<List<ExamDocsSite>>(saved) }.getOrDefault(emptyList())
        val rawActiveUrl = prefs.getString(KEY_ACTIVE_SITE, null)
        if (!prefs.getBoolean(KEY_SITES_V2_MIGRATED, false)) {
            val migratedCustom = decoded.map { site ->
                if (site.baseUrl == LEGACY_EXAM_DOCS_BASE_URL) site.copy(baseUrl = DEFAULT_EXAM_DOCS_BASE_URL) else site
            }
            val sites = (builtInDocsSites + migratedCustom).distinctBy(ExamDocsSite::baseUrl)
            val migratedActiveUrl = if (rawActiveUrl == LEGACY_EXAM_DOCS_BASE_URL) DEFAULT_EXAM_DOCS_BASE_URL else rawActiveUrl
            val active = sites.firstOrNull { it.baseUrl == migratedActiveUrl } ?: sites.first()
            prefs.edit {
                putString(KEY_SITES, json.encodeToString(sites))
                putString(KEY_ACTIVE_SITE, active.baseUrl)
                putBoolean(KEY_SITES_V2_MIGRATED, true)
            }
            return ExamDocsSiteSettings(sites, active)
        }

        val sites = decoded.distinctBy(ExamDocsSite::baseUrl).ifEmpty { builtInDocsSites }
        val active = sites.firstOrNull { it.baseUrl == rawActiveUrl } ?: sites.first()
        return ExamDocsSiteSettings(sites, active)
    }

    private val _siteSettingsFlow by lazy { MutableStateFlow(loadSiteSettings()) }
    val siteSettingsFlow: StateFlow<ExamDocsSiteSettings> get() = _siteSettingsFlow.asStateFlow()

    private val _uiSettingsFlow by lazy { MutableStateFlow(loadUiSettings()) }
    val uiSettingsFlow: StateFlow<ExamDocsUiSettings> get() = _uiSettingsFlow.asStateFlow()

    fun loadUiSettings(): ExamDocsUiSettings {
        val raw = prefs.getString(KEY_UI_SETTINGS, null) ?: return ExamDocsUiSettings()
        return runCatching { json.decodeFromString<ExamDocsUiSettings>(raw) }
            .getOrDefault(ExamDocsUiSettings())
    }

    fun saveUiSettings(settings: ExamDocsUiSettings) {
        val clamped = settings.copy(fontSizeRatio = settings.fontSizeRatio.coerceIn(0.5f, 2.0f))
        prefs.edit { putString(KEY_UI_SETTINGS, json.encodeToString(clamped)) }
        _uiSettingsFlow.value = clamped
    }

    fun saveSiteSettings(sites: List<ExamDocsSite>, active: ExamDocsSite) {
        prefs.edit {
            putString(KEY_SITES, json.encodeToString(sites))
            putString(KEY_ACTIVE_SITE, active.baseUrl)
            putBoolean(KEY_SITES_V2_MIGRATED, true)
        }
        _siteSettingsFlow.value = ExamDocsSiteSettings(sites, active)
    }

    fun switchSite(site: ExamDocsSite) {
        val current = _siteSettingsFlow.value
        if (site == current.active) return
        saveSiteSettings(current.sites, site)
    }

    fun saveSite(original: ExamDocsSite?, name: String, rawUrl: String): String? {
        val baseUrl = parseBaseUrl(rawUrl) ?: return "请输入有效的 HTTP 或 HTTPS 站点地址"
        val current = _siteSettingsFlow.value
        if (current.sites.any { it != original && it.baseUrl == baseUrl }) return "这个站点已经添加"
        val site = ExamDocsSite(name.trim().ifBlank { baseUrl.toHttpUrl().host }, baseUrl)
        val sites = if (original == null) current.sites + site else current.sites.map { if (it == original) site else it }
        val active = if (original == null || original == current.active) site else current.active
        saveSiteSettings(sites, active)
        return null
    }

    fun removeSite(site: ExamDocsSite) {
        val current = _siteSettingsFlow.value
        if (current.sites.size <= 1) return
        val sites = current.sites - site
        val active = current.active.takeUnless { it == site } ?: sites.first()
        saveSiteSettings(sites, active)
    }

    fun restoreDefaultSites() {
        val current = _siteSettingsFlow.value
        val merged = (current.sites + builtInDocsSites).distinctBy(ExamDocsSite::baseUrl)
        saveSiteSettings(merged, current.active)
    }

    fun getLastPageUrl(site: ExamDocsSite): String? = prefs.getString(lastPageKey(site.baseUrl), null)

    fun saveLastPageUrl(site: ExamDocsSite, pageUrl: String) {
        prefs.edit { putString(lastPageKey(site.baseUrl), pageUrl) }
    }

    private fun lastPageKey(baseUrl: String) = "last_page:$baseUrl"

    private val _recentDocsFlow by lazy { MutableStateFlow(loadRecentDocs()) }
    val recentDocsFlow: StateFlow<List<RecentExamDoc>> get() = _recentDocsFlow.asStateFlow()

    private fun loadRecentDocs(): List<RecentExamDoc> {
        val raw = prefs.getString(KEY_RECENT_DOCS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentExamDoc>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun saveRecentDocs(items: List<RecentExamDoc>) {
        val trimmed = items.take(MAX_RECENT_DOCS)
        prefs.edit { putString(KEY_RECENT_DOCS, json.encodeToString(trimmed)) }
        _recentDocsFlow.value = trimmed
    }

    fun recordRecentDoc(
        site: ExamDocsSite,
        pageUrl: String,
        title: String,
        breadcrumb: List<String>,
    ) {
        val normalized = normalizeDocPath(pageUrl)
        val current = _recentDocsFlow.value
        val existing = current.firstOrNull {
            it.siteBaseUrl == site.baseUrl && normalizeDocPath(it.pageUrl) == normalized
        }
        val updated = RecentExamDoc(
            siteBaseUrl = site.baseUrl,
            siteName = site.name,
            pageUrl = normalized,
            title = title.ifBlank { existing?.title ?: normalized },
            breadcrumb = breadcrumb.ifEmpty { existing?.breadcrumb.orEmpty() },
            lastReadAt = System.currentTimeMillis(),
            scrollIndex = existing?.scrollIndex ?: 0,
            scrollOffset = existing?.scrollOffset ?: 0,
        )
        val rest = current.filterNot {
            it.siteBaseUrl == site.baseUrl && normalizeDocPath(it.pageUrl) == normalized
        }
        saveRecentDocs(listOf(updated) + rest)
    }

    fun updateDocScrollPosition(
        siteBaseUrl: String,
        pageUrl: String,
        scrollIndex: Int,
        scrollOffset: Int,
    ) {
        val normalized = normalizeDocPath(pageUrl)
        val current = _recentDocsFlow.value
        val index = current.indexOfFirst {
            it.siteBaseUrl == siteBaseUrl && normalizeDocPath(it.pageUrl) == normalized
        }
        if (index == -1) return
        val existing = current[index]
        if (existing.scrollIndex == scrollIndex && existing.scrollOffset == scrollOffset) return
        val updated = existing.copy(
            scrollIndex = scrollIndex.coerceAtLeast(0),
            scrollOffset = scrollOffset.coerceAtLeast(0),
            lastReadAt = System.currentTimeMillis(),
        )
        val mutable = current.toMutableList()
        mutable[index] = updated
        saveRecentDocs(mutable)
    }

    fun getRecentDocScrollPosition(siteBaseUrl: String, pageUrl: String): Pair<Int, Int>? {
        val normalized = normalizeDocPath(pageUrl)
        val doc = _recentDocsFlow.value.firstOrNull {
            it.siteBaseUrl == siteBaseUrl && normalizeDocPath(it.pageUrl) == normalized
        } ?: return null
        return if (doc.scrollIndex > 0 || doc.scrollOffset > 0) {
            doc.scrollIndex to doc.scrollOffset
        } else {
            null
        }
    }

    fun removeRecentDoc(siteBaseUrl: String, pageUrl: String) {
        val normalized = normalizeDocPath(pageUrl)
        val updated = _recentDocsFlow.value.filterNot {
            it.siteBaseUrl == siteBaseUrl && normalizeDocPath(it.pageUrl) == normalized
        }
        saveRecentDocs(updated)
    }

    suspend fun fetchSourceTree(site: ExamDocsSite, refresh: Boolean): ExamDocsDirectory {
        if (!refresh) directories.get(site.baseUrl)?.takeIf { it.fresh }?.let { return it.value }
        val url = "${site.baseUrl}/doc-sources.json"
        val loaded = loadResource(site, "directory", refresh, fetch = {
            url to (fetchText(url, "application/json") ?: throw IOException("站点缺少 /doc-sources.json"))
        }) { _, body ->
            val tree = json.decodeFromString<ExamDocSourceTreeDto>(body)
            val modules = tree.modules.mapNotNull(::convertFolder)
            ExamDocsDirectory(
                modules,
                flattenPages(modules).distinctBy { it.page.url },
                tree.androidMarkdownVersion == ANDROID_MARKDOWN_VERSION,
            )
        }
        directories.put(site.baseUrl, loaded)
        return loaded.value
    }

    suspend fun fetchDocument(
        site: ExamDocsSite,
        pageUrl: String,
        usesAndroidMarkdown: Boolean,
        refresh: Boolean,
    ): ExamDocsDocument {
        val legacyUrl = buildMarkdownUrl(site.baseUrl, pageUrl)
        val preferredUrl = buildMarkdownUrl(site.baseUrl, pageUrl, usesAndroidMarkdown)
        if (!refresh) documents.get(preferredUrl)?.takeIf { it.fresh }?.let { return it.value }
        val loaded = loadResource(site, preferredUrl, refresh, fetch = {
            val accept = "text/markdown, text/plain;q=0.9, */*;q=0.5"
            val preferred = fetchText(preferredUrl, accept)
            if (preferred != null) preferredUrl to preferred
            else if (preferredUrl != legacyUrl) legacyUrl to (fetchText(legacyUrl, accept) ?: throw IOException("未找到文档"))
            else throw IOException("未找到文档")
        }) { url, body ->
            prepareExamDocsDocument(body, url, legacy = url == legacyUrl)
        }
        documents.put(preferredUrl, loaded)
        return loaded.value
    }

    private suspend fun <T> loadResource(
        site: ExamDocsSite,
        resource: String,
        refresh: Boolean,
        fetch: suspend () -> Pair<String, String>,
        parse: (String, String) -> T,
    ): CachedValue<T> = withContext(Dispatchers.IO) {
        val stored = runCatching { cacheDao.resource(site.baseUrl, resource) }.getOrNull()
        suspend fun readStored(): CachedValue<T>? = stored?.let { row ->
            try {
                val body = GZIPInputStream(row.compressed.inputStream()).bufferedReader().use { it.readText() }
                withContext(Dispatchers.Default) { CachedValue(parse(row.sourceUrl, body), row.fetchedAt) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

        suspend fun download(): CachedValue<T> {
            val (url, body) = fetch()
            val value = withContext(Dispatchers.Default) { parse(url, body) }
            val fetchedAt = System.currentTimeMillis()
            try {
                val bytes = ByteArrayOutputStream().also { buffer ->
                    GZIPOutputStream(buffer).use { it.write(body.toByteArray()) }
                }.toByteArray()
                // Keep each Room result comfortably within a CursorWindow on older devices.
                if (bytes.size <= 1024 * 1024) {
                    cacheDao.putResource(DocsCachedResource(site.baseUrl, resource, url, bytes, fetchedAt))
                    if (resource != "directory") cacheDao.pruneResources()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Caching is optional for the current online read.
            }
            return CachedValue(value, fetchedAt)
        }

        val cached = readStored()
        if (!refresh && cached != null) {
            val key = site.baseUrl to resource
            if (!cached.fresh && refreshing.add(key)) {
                cacheScope.launch {
                    try {
                        download()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The existing copy remains available offline.
                    } finally {
                        refreshing.remove(key)
                    }
                }
            }
            return@withContext cached
        }

        try {
            download()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            cached ?: throw error
        }
    }

    private suspend fun fetchText(url: String, accept: String): String? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("Accept", accept).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (it.code == 404 || it.code == 410) return@use null
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code} ${it.message}".trim())
                            if (it.body.contentType()?.subtype?.contains("html") == true) {
                                throw IOException("服务器返回了网页")
                            }
                            val source = it.body.source()
                            if (source.request(MAX_RESPONSE_BYTES + 1)) throw IOException("文档超过大小限制")
                            it.body.string()
                        }
                        continuation.resume(text)
                    } catch (error: IOException) {
                        continuation.resumeWithException(error)
                    }
                }
            })
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
                val url = raw.url?.takeIf { it.startsWith("/docs/") || it == "/docs" } ?: return null
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
        fun parseBaseUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val hasScheme = trimmed.contains("://")
            val url = (if (hasScheme) trimmed else "https://$trimmed").toHttpUrlOrNull() ?: return null
            if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty() ||
                url.query != null || url.fragment != null
            ) return null
            return url.toString().trimEnd('/')
        }

        fun normalizeDocPath(raw: String): String {
            val bare = raw.trim().substringBefore('#').substringBefore('?').trim('/')
            val path = "/" + bare.ifEmpty { "docs" }
            return ("https://docs.invalid$path").toHttpUrl().encodedPath
                .let { if (it.startsWith("/android/docs")) it.removePrefix("/android") else it }
                .removeSuffix(".md")
        }

        fun buildMarkdownUrl(baseUrl: String, pageUrl: String, android: Boolean = false): String {
            val base = requireNotNull(parseBaseUrl(baseUrl))
            val prefix = if (android) "/android" else ""
            return "$base$prefix${normalizeDocPath(pageUrl)}.md"
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
