package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.examdocs.prepareExamDocsDocument
import me.rerere.rikkahub.ui.components.richtext.SvgPreview
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamDocsReaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun answersAndOpensSolutionContainingStepsCalloutAndWideMath() {
        render(
            """
            :::question {id="sample" type="single" number="1" points="2" tags="来源:模拟试卷"}
            求分段函数 ${'$'} f(x)${'$'} 的原函数。

            :::option {label="A" correct="true"}
            ${'$'}F(x)=\begin{cases}x^2,&x\le0,\\ x+2,&x>0\end{cases}${'$'}
            :::

            :::option {label="B"}
            另一个选项
            :::

            :::solution 本题解析
            :::steps
            :::step
            ### 检查连续性

            :::idea 对照参考条件
            ${'$'}${'$'}
            \lim_{x\to0}F(x)=\frac12
            ${'$'}${'$'}
            :::

            | 条件 | 结论 |
            | --- | --- |
            | 连续 | 可积 |

            ~~~kotlin
            val answer = 1
            ~~~

            :::
            :::
            :::
            :::
            """.trimIndent(),
            modifier = Modifier.height(IntrinsicSize.Min),
        )
        val kind = compose.onNodeWithText("单选题").fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithText("模拟试卷").fetchSemanticsNode().boundsInRoot
        assertTrue("Question metadata should share one line", source.top < kind.bottom && source.bottom > kind.top)
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("回答正确").assertIsDisplayed()
        val retry = compose.onNodeWithText("重做").fetchSemanticsNode().boundsInRoot
        val solution = compose.onNodeWithText("查看解答")
        assertTrue("Retry should sit to the left of the solution link", retry.right <= solution.fetchSemanticsNode().boundsInRoot.left)
        solution.performClick()
        compose.onNodeWithText("本题解析").assertIsDisplayed()
        compose.onNodeWithText("检查连续性").assertIsDisplayed()
        compose.onNodeWithText("对照参考条件").assertIsDisplayed()
        compose.onNodeWithText("可积").assertExists()
        compose.onNodeWithText("kotlin").assertExists()
    }

    @Test
    fun multipleChoiceShowsMissedAnswersAndResetsInDarkTheme() {
        render(
            """
            :::question {id="multi" type="multiple"}
            选择所有质数。

            :::option {label="A" correct="true"}
            质数 2
            :::

            :::option {label="B"}
            合数 4
            :::

            :::option {label="C" correct="true"}
            质数 5
            :::
            :::
            """.trimIndent(),
            dark = true,
        )
        val first = compose.onNodeWithText("A")
        val submit = compose.onNodeWithText("提交答案")
        submit.assertIsNotEnabled()
        first.performClick()
        first.assertIsOn()
        submit.assertIsEnabled().performClick()
        compose.onNodeWithText("漏选").assertIsDisplayed()
        compose.onNodeWithText("部分正确，有漏选").assertIsDisplayed()
        first.assertIsNotEnabled()

        compose.onNodeWithText("重做").performClick()
        first.assertIsOff().assertIsEnabled()
        submit.assertIsNotEnabled()
        compose.onNodeWithText("漏选").assertDoesNotExist()

        compose.onNodeWithText("B").performClick()
        submit.performClick()
        val wrong = compose.onNodeWithContentDescription("选错", useUnmergedTree = true)
        val optionText = compose.onNodeWithText("合数 4", useUnmergedTree = true)
        wrong.assertIsDisplayed()
        assertTrue(
            "The verdict should share the leading option marker",
            wrong.fetchSemanticsNode().boundsInRoot.right <= optionText.fetchSemanticsNode().boundsInRoot.left,
        )
        compose.onNodeWithText("回答错误").assertIsDisplayed()
    }

    @Test
    fun submitsAndResetsRoundedBlankField() {
        render(
            """
            :::question {id="blank" type="blank" blanks="[{\"answer\":\"2\"}]"}
            填写 1 + 1 的结果。
            :::
            """.trimIndent(),
        )
        val submit = compose.onNodeWithText("提交答案")
        submit.assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("2")
        submit.assertIsEnabled().performClick()
        compose.onNodeWithText("与参考答案一致").assertIsDisplayed()
        compose.onNodeWithText("回答正确").assertIsDisplayed()
        compose.onNodeWithText("重做").performClick()
        submit.assertIsNotEnabled()
    }

    @Test
    fun opensWordDetailsWithExampleAndTranslation() {
        render(
            """
            :::wordcards
            ~~~json
            [{"word":"measure","phonetic":"/ˈmeʒə/","meanings":[{"text":"衡量"}],"senses":[{"gloss":"测量","count":2,"examples":[{"text":"Measure the distance.","translation":"测量距离。"}]}]},{"word":"compare","meanings":[{"text":"比较"}]}]
            ~~~
            :::
            """.trimIndent()
        )
        compose.onNodeWithContentDescription("查看 measure 的详细释义和例句").performClick()
        compose.onNodeWithText("Measure the distance.").assertIsDisplayed()
        compose.onNodeWithText("Measure the distance.").performClick()
        compose.onNodeWithText("测量距离。").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一个单词").performClick()
        compose.onNodeWithText("Measure the distance.").assertDoesNotExist()
        compose.onNodeWithContentDescription("上一个单词").performClick()
        compose.onNodeWithText("Measure the distance.").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭词卡").performClick()
        compose.onNodeWithText("Measure the distance.").assertDoesNotExist()
    }

    @Test
    fun rendersSvgBytesAsAnImage() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50">
                <rect width="100" height="50" fill="#d02030"/>
            </svg>
        """.trimIndent().toByteArray()
        compose.setContent {
            MaterialTheme {
                SvgPreview(svg, description = "SVG test image", modifier = Modifier.size(160.dp, 80.dp))
            }
        }
        val image = compose.onNodeWithContentDescription("SVG test image")
        compose.waitUntil(timeoutMillis = 10_000) {
            val pixels = image.captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2] == Color(0xFFD02030)
        }
    }

    @Test
    fun rendersLinkedAdjacencyDiagramAtNaturalAspectRatioWithoutCaption() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("exam-docs-diagram", ".svg", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("examdocs/adjacency-list.svg").use { asset ->
                file.outputStream().use { asset.copyTo(it) }
            }
            render("![卷三_Q07_邻接表](/img/adjacency-list.svg)", imageUrlResolver = { file.toUri().toString() })
            val picture = compose.onNodeWithContentDescription("卷三_Q07_邻接表")
            compose.waitUntil(timeoutMillis = 10_000) {
                if (picture.fetchSemanticsNode().boundsInRoot.height < 1f) {
                    false
                } else {
                    val pixels = picture.captureToImage().toPixelMap()
                    (0 until pixels.height step 2).any { y ->
                        (0 until pixels.width step 2).any { x ->
                            val color = pixels[x, y]
                            color.red < 0.2f && color.green < 0.2f && color.blue < 0.2f
                        }
                    }
                }
            }
            val bounds = picture.fetchSemanticsNode().boundsInRoot
            assertTrue("The diagram should keep its 2:1 aspect ratio", bounds.width / bounds.height in 1.94f..2.06f)
            compose.onNodeWithText("卷三_Q07_邻接表").assertDoesNotExist()
        } finally {
            file.delete()
        }
    }

    private fun render(
        markdown: String,
        modifier: Modifier = Modifier.fillMaxSize(),
        dark: Boolean = false,
        imageUrlResolver: (String) -> String = { it },
    ) {
        val document = prepareExamDocsDocument(markdown, "https://example.test/android/docs/sample.md", legacy = false)
        val registry = ExamDocsComponentRegistry()
        compose.setContent {
            MaterialExpressiveTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalSettings provides Settings.dummy(), LocalDarkMode provides dark) {
                    val renderer = rememberExamDocsRenderer(document, rememberLazyListState(), {}, imageUrlResolver)
                    SharedTransitionLayout(Modifier.fillMaxSize().padding(12.dp)) {
                        Column(modifier) {
                            document.blocks.forEach { ExamDocsMarkdownBlock(it, registry, renderer) }
                        }
                    }
                }
            }
        }
    }
}
