package me.rerere.rikkahub.data.examdocs

import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
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

    @Test
    fun parsesDirectiveAttributesAndNestedComponentContainers() {
        val raw = """
            # 测试

            :::question {id="q-1" type="single" number="1" tags="来源:408"}
            题干内容
            :::option {label="A" correct="true"}
            选项A
            :::
            :::solution 参考答案与解析
            解析内容
            :::
            :::

            :::cards
            :::card {icon="rocket" tone="info" href="/docs/intro"} 卡片标题
            卡片说明
            :::
            :::
        """.trimIndent()
        val doc = prepareExamDocsDocument(raw, "https://exam.any1.tech/android/docs/test.md", legacy = false)
        val question = doc.blocks[1] as ExamDocsSegment.CustomBlock
        assertEquals("question", question.type)
        assertEquals("q-1", question.attrs["id"])
        assertEquals("single", question.attrs["type"])
        assertEquals("1", question.attrs["number"])
        assertEquals("来源:408", question.attrs["tags"])
        val option = question.blocks[1] as ExamDocsSegment.CustomBlock
        assertEquals("option", option.type)
        assertEquals("A", option.attrs["label"])
        assertEquals("true", option.attrs["correct"])

        val cards = doc.blocks[2] as ExamDocsSegment.CustomBlock
        val card = cards.blocks.single() as ExamDocsSegment.CustomBlock
        assertEquals("card", card.type)
        assertEquals("卡片标题", card.title)
        assertEquals("rocket", card.attrs["icon"])
        assertEquals("info", card.attrs["tone"])
        assertEquals("/docs/intro", card.attrs["href"])
    }
    @Test
    fun normalizesSpacedMathAndLetsWideStandaloneFormulaScroll() {
        val raw = """
            设函数 ${'$'} f(x)${'$'} 在 ${'$'} [a,+\infty)${'$'} 上一阶可导。

            ${'$'}F(x)=\begin{cases}x^2,&x\le0,\\x+2,&x>0\end{cases}${'$'}

            ~~~
            ${'$'} x ${'$'}
            ~~~
        """.trimIndent()
        val blocks = parseExamDocsSegments(raw).filterIsInstance<ExamDocsSegment.Markdown>()
            .flatMap { it.document.blocks }
        val first = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(listOf("f(x)", "[a,+\\infty)"), first.content.filterIsInstance<MarkdownInline.Math>().map { it.formula })
        val wide = (blocks[1] as MarkdownBlock.Paragraph).content.filterIsInstance<MarkdownInline.Math>().single()
        assertTrue(wide.display)
        assertEquals("${'$'} x ${'$'}", (blocks[2] as MarkdownBlock.CodeBlock).code.trim())
    }

    @Test
    fun preservesMathBracesAndEscapesInStructuredAnswerKeys() {
        val raw = """
            :::question {type="blank" blanks="[{\"answer\":\"\\\\frac{1}{2}\",\"aliases\":[\"0.5\"]}]"}
            求值
            :::
        """.trimIndent()
        val question = parseExamDocsSegments(raw).single() as ExamDocsSegment.CustomBlock
        assertEquals("""[{"answer":"\\frac{1}{2}","aliases":["0.5"]}]""", question.attrs["blanks"])
    }

    @Test
    fun normalizesHtmlImagesAndLatexDelimiters() {
        val raw = """
            <img src="/images/diagram.svg" alt="结构图" width="600" />

            行内公式 \(a^2 + b^2 = c^2\) 与块级公式：

            \[ x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \]
        """.trimIndent()
        val blocks = parseExamDocsSegments(raw).filterIsInstance<ExamDocsSegment.Markdown>()
            .flatMap { it.document.blocks }
        val imgPara = blocks[0] as MarkdownBlock.Paragraph
        val image = imgPara.content.filterIsInstance<MarkdownInline.Image>().single()
        assertEquals("/images/diagram.svg", image.url)
        assertEquals("结构图", image.alt)

        val inlinePara = blocks[1] as MarkdownBlock.Paragraph
        assertEquals(listOf("a^2 + b^2 = c^2"), inlinePara.content.filterIsInstance<MarkdownInline.Math>().map { it.formula })

        val displayPara = blocks[2] as MarkdownBlock.Paragraph
        val displayMath = displayPara.content.filterIsInstance<MarkdownInline.Math>().single()
        assertTrue(displayMath.display)
        assertEquals("""x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}""", displayMath.formula)
    }

}
