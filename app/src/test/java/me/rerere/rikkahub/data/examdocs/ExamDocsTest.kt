package me.rerere.rikkahub.data.examdocs

import me.rerere.bettertext.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamDocsTest {
    @Test
    fun markdownUrlsUseTheSiteAndAdvertisedProfile() {
        val base = "https://reader.example/study"
        assertEquals(
            "$base/android/docs/math/%E7%AC%AC%E4%B8%80%E8%8A%82.md",
            ExamDocsRepository.buildMarkdownUrl(base, "/docs/math/第一节/#习题", android = true),
        )
        assertEquals("$base/docs/intro.md", ExamDocsRepository.buildMarkdownUrl(base, "docs/intro.md#section"))
        assertEquals(base, ExamDocsRepository.parseBaseUrl("reader.example/study/"))
        assertNull(ExamDocsRepository.parseBaseUrl("https://user:password@reader.example"))
        assertNull(ExamDocsRepository.parseBaseUrl("file:///docs"))
    }

    @Test
    fun flattenPagesCollectsBreadcrumbsAndPreservesOrder() {
        val modules = listOf(
            ExamDocNode.Folder(
                name = "数学",
                children = listOf(
                    ExamDocNode.Page("数学", "/docs/math"),
                    ExamDocNode.Folder("高数", listOf(ExamDocNode.Page("第一节", "/docs/math/01", 10))),
                ),
            ),
        )
        val flat = ExamDocsRepository.flattenPages(modules)
        assertEquals(listOf("/docs/math", "/docs/math/01"), flat.map { it.page.url })
        assertEquals(listOf("数学", "高数"), flat[1].breadcrumb)
        assertEquals(10, flat[1].page.questions)
    }

    @Test
    fun parsedBlocksGiveHeadingsExactIndexesAndKeepCodeAndContainersSeparate() {
        val raw = """
            # 函数 (/docs/math/01)

            # 函数

            ## 题目

            :::solution 解析
            :::tip 提示
            注意奇偶性。
            :::
            :::

            ~~~~md
            ~~~
            # Code example
            :::warning Not a container
            ~~~~

            ## 题目
        """.trimIndent()
        val doc = prepareExamDocsDocument(raw, "https://reader.example/docs/math/01.md", legacy = true)
        assertEquals(listOf("函数", "题目", "题目"), doc.headings.map { it.title })
        assertEquals(listOf("函数", "题目", "题目-1"), doc.headings.map { it.anchor })
        assertEquals(listOf(0, 1, 4), doc.headings.map { it.blockIndex })
        val solution = doc.blocks[2] as ExamDocsSegment.CustomBlock
        assertEquals("solution", solution.type)
        val callout = solution.blocks.single() as ExamDocsSegment.CustomBlock
        assertEquals("tip", callout.type)
        assertEquals("提示", callout.title)
        val code = (doc.blocks[3] as ExamDocsSegment.Markdown).document.blocks.single()
        assertTrue(code is MarkdownBlock.CodeBlock)
    }

    @Test
    fun referenceLinksAreParsedAcrossVisibleMarkdownBlocks() {
        val doc = prepareExamDocsDocument(
            "# Title\n\n[Home][destination]\n\n## Second\n\n[destination]: /docs/home",
            "https://reader.example/android/docs/intro.md",
            legacy = false,
        )
        val paragraph = (doc.blocks[1] as ExamDocsSegment.Markdown).document.blocks.single() as MarkdownBlock.Paragraph
        assertTrue(paragraph.content.any { it is me.rerere.bettertext.markdown.MarkdownInline.Link })
    }

    @Test
    fun linksKeepSitePrefixAndResolveFragmentsAndUnicodePaths() {
        val base = "https://reader.example/study"
        assertEquals("$base/images/pic.png", resolveExamDocsUrl(base, "/docs/math/01", "/images/pic.png"))
        assertEquals("$base/docs/math/fig.png", resolveExamDocsUrl(base, "/docs/math/01", "fig.png"))
        assertEquals(
            ExamDocsLink("/docs/math/01", "求解"),
            getExamDocsLink(base, "/docs/math/01", "#求解"),
        )
        assertEquals(
            ExamDocsLink("/docs/math/%E7%AC%AC%E4%B8%80%E8%8A%82", null),
            getExamDocsLink(base, "/docs/math/01", "第一节.md"),
        )
        assertNull(getExamDocsLink(base, "/docs/math/01", "https://other.example/docs/math"))
    }
}
