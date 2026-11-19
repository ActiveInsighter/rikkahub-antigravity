package me.rerere.rikkahub.data.examdocs

import me.rerere.rikkahub.ui.pages.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.pages.examdocs.extractDocHeadings
import me.rerere.rikkahub.ui.pages.examdocs.parseExamDocsSegments
import me.rerere.rikkahub.ui.pages.examdocs.preprocessExamDocsMarkdown
import me.rerere.rikkahub.ui.pages.examdocs.resolveExamDocsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamDocsTest {

    @Test
    fun buildMarkdownUrl_normalizesPaths() {
        assertEquals(
            "https://exam-docs.2212148739lbw.workers.dev/docs/math.md",
            ExamDocsRepository.buildMarkdownUrl(
                "https://exam-docs.2212148739lbw.workers.dev/",
                "/docs/math/",
            ),
        )
        assertEquals(
            "https://exam-docs.2212148739lbw.workers.dev/docs/intro.md",
            ExamDocsRepository.buildMarkdownUrl(
                "exam-docs.2212148739lbw.workers.dev",
                "docs/intro#section",
            ),
        )
    }

    @Test
    fun flattenPages_collectsAllPagesWithBreadcrumbs() {
        val modules = listOf(
            ExamDocNode.Folder(
                name = "数学",
                questions = 10,
                children = listOf(
                    ExamDocNode.Page(name = "数学", url = "/docs/math", questions = 0),
                    ExamDocNode.Folder(
                        name = "高数",
                        questions = 10,
                        children = listOf(
                            ExamDocNode.Page(name = "第一节", url = "/docs/math/01", questions = 10),
                        ),
                    ),
                ),
            ),
        )
        val flat = ExamDocsRepository.flattenPages(modules)
        assertEquals(2, flat.size)
        assertEquals(listOf("数学"), flat[0].breadcrumb)
        assertEquals(listOf("数学", "高数"), flat[1].breadcrumb)
        assertEquals(10, flat[1].page.questions)
    }

    @Test
    fun preprocessAndSegments_handlesTitleStripHeadingsAndCallouts() {
        val raw = """
            # 第一节 函数的基本性质 (/docs/math/01)

            # 第一节 函数的基本性质

            ## 题目 1

            :::tip 提示
            注意奇偶性定义域对称。
            :::

            ```kotlin
            // # not a heading
            ```
        """.trimIndent()

        val processed = preprocessExamDocsMarkdown(raw)
        assertTrue(processed.startsWith("# 第一节 函数的基本性质\n"))

        val headings = extractDocHeadings(processed)
        assertEquals(2, headings.size)
        assertEquals("第一节 函数的基本性质", headings[0].title)
        assertEquals("题目 1", headings[1].title)

        val segments = parseExamDocsSegments(processed)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is ExamDocsSegment.Markdown)
        val custom = segments[1] as ExamDocsSegment.CustomBlock
        assertEquals("tip", custom.type)
        assertEquals("提示", custom.title)
        assertEquals("注意奇偶性定义域对称。", custom.body)
        assertTrue(segments[2] is ExamDocsSegment.Markdown)
    }

    @Test
    fun resolveExamDocsUrl_resolvesRelativeAndRootPaths() {
        val base = "https://exam-docs.2212148739lbw.workers.dev"
        assertEquals(
            "$base/images/pic.png",
            resolveExamDocsUrl(base, "/docs/math/01", "/images/pic.png"),
        )
        assertEquals(
            "$base/docs/math/fig.png",
            resolveExamDocsUrl(base, "/docs/math/01", "fig.png"),
        )
    }
}
