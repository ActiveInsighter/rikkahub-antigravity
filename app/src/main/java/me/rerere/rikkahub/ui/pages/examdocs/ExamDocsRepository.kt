package me.rerere.rikkahub.ui.pages.examdocs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class ExamDocsRepository(
    private val okHttpClient: OkHttpClient,
) {
    private val pageCache = ConcurrentHashMap<String, String>()

    suspend fun fetchSourceTree(
        baseUrl: String = DEFAULT_EXAM_DOCS_BASE_URL,
    ): ExamDocSourcesResponse = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$normalizedBase/doc-sources.json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from doc-sources.json")
            JsonInstant.decodeFromString<ExamDocSourcesResponse>(body)
        }
    }

    suspend fun fetchMarkdown(
        pageUrl: String,
        baseUrl: String = DEFAULT_EXAM_DOCS_BASE_URL,
        forceRefresh: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val mdUrl = buildMarkdownUrl(baseUrl, pageUrl)
        if (!forceRefresh) {
            pageCache[mdUrl]?.let { return@withContext it }
        }

        val request = Request.Builder()
            .url(mdUrl)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val rawMarkdown = response.body?.string()
                ?: throw IOException("Empty markdown response")
            val cleaned = cleanExamMarkdown(rawMarkdown, baseUrl)
            pageCache[mdUrl] = cleaned
            cleaned
        }
    }

    fun buildMarkdownUrl(baseUrl: String, pageUrl: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = pageUrl.trim()
            .let { if (it.startsWith("/")) it else "/$it" }
            .trimEnd('/')
            .let { if (it.endsWith(".md")) it else "$it.md" }
        return "$normalizedBase$normalizedPath"
    }

    fun buildWebPageUrl(baseUrl: String, pageUrl: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = pageUrl.trim()
            .let { if (it.startsWith("/")) it else "/$it" }
            .removeSuffix(".md")
        return "$normalizedBase$normalizedPath"
    }

    /**
     * 对 llms.mdx 返回的 Markdown 做轻量清理：
     * 1. 去掉首行重复的 `# 标题 (/docs/...)` 路由头；
     * 2. 把相对图片路径 `](/...` 补全为绝对地址，方便 Coil 加载。
     */
    private fun cleanExamMarkdown(raw: String, baseUrl: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val lines = raw.lines()
        val bodyLines = if (
            lines.isNotEmpty() &&
            lines.first().startsWith("# ") &&
            lines.first().contains("(/docs/")
        ) {
            lines.drop(1).dropWhile { it.isBlank() }
        } else {
            lines
        }
        return bodyLines
            .joinToString("\n")
            .replace(Regex("""!\[([^\]]*)\]\((/[^)]+)\)""")) { match ->
                "![${match.groupValues[1]}]($normalizedBase${match.groupValues[2]})"
            }
    }
}
