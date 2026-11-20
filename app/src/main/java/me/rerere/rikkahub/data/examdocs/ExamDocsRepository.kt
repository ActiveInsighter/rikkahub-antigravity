package me.rerere.rikkahub.data.examdocs

import android.content.Context
import android.util.LruCache
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
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
private const val ANDROID_MARKDOWN_VERSION = 1
private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024

@Serializable
data class ExamDocsSite(val name: String, val baseUrl: String)

val builtInDocsSites = listOf(
    ExamDocsSite("Exam Docs", DEFAULT_EXAM_DOCS_BASE_URL),
    ExamDocsSite("Study Docs", DEFAULT_STUDY_DOCS_BASE_URL),
)

data class ExamDocsSiteSettings(val sites: List<ExamDocsSite>, val active: ExamDocsSite)

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

class ExamDocsRepository(
    context: Context,
    okHttpClient: OkHttpClient,
    json: Json,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val json = Json(from = json) { ignoreUnknownKeys = true }
    private val client = okHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
    private val directories = LruCache<String, ExamDocsDirectory>(4)
    private val documents = object : LruCache<String, ExamDocsDocument>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ExamDocsDocument) = value.sourceBytes
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

    fun saveSiteSettings(sites: List<ExamDocsSite>, active: ExamDocsSite) {
        prefs.edit {
            putString(KEY_SITES, json.encodeToString(sites))
            putString(KEY_ACTIVE_SITE, active.baseUrl)
            putBoolean(KEY_SITES_V2_MIGRATED, true)
        }
    }

    fun getLastPageUrl(site: ExamDocsSite): String? = prefs.getString(lastPageKey(site.baseUrl), null)

    fun saveLastPageUrl(site: ExamDocsSite, pageUrl: String) {
        prefs.edit { putString(lastPageKey(site.baseUrl), pageUrl) }
    }

    private fun lastPageKey(baseUrl: String) = "last_page:$baseUrl"

    suspend fun fetchSourceTree(site: ExamDocsSite, refresh: Boolean): ExamDocsDirectory {
        if (!refresh) directories.get(site.baseUrl)?.let { return it }
        val body = fetchText("${site.baseUrl}/doc-sources.json", "application/json")
            ?: throw IOException("站点缺少 /doc-sources.json")
        return withContext(Dispatchers.Default) {
            val tree = json.decodeFromString<ExamDocSourceTreeDto>(body)
            val modules = tree.modules.mapNotNull(::convertFolder)
            val directory = ExamDocsDirectory(
                modules,
                flattenPages(modules).distinctBy { it.page.url },
                tree.androidMarkdownVersion == ANDROID_MARKDOWN_VERSION,
            )
            ensureActive()
            directories.put(site.baseUrl, directory)
            directory
        }
    }

    suspend fun fetchDocument(
        site: ExamDocsSite,
        pageUrl: String,
        usesAndroidMarkdown: Boolean,
        refresh: Boolean,
    ): ExamDocsDocument {
        val legacyUrl = buildMarkdownUrl(site.baseUrl, pageUrl)
        val preferredUrl = buildMarkdownUrl(site.baseUrl, pageUrl, usesAndroidMarkdown)
        if (!refresh) documents.get(preferredUrl)?.let { return it }
        var sourceUrl = preferredUrl
        val accept = "text/markdown, text/plain;q=0.9, */*;q=0.5"
        var markdown = fetchText(sourceUrl, accept)
        if (markdown == null && sourceUrl != legacyUrl) {
            sourceUrl = legacyUrl
            markdown = fetchText(sourceUrl, accept)
        }
        val content = markdown ?: throw IOException("未找到文档")
        return withContext(Dispatchers.Default) {
            val document = prepareExamDocsDocument(content, sourceUrl, legacy = sourceUrl == legacyUrl)
            ensureActive()
            documents.put(preferredUrl, document)
            document
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
