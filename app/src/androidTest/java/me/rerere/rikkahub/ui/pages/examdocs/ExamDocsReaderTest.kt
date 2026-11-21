package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.examdocs.prepareExamDocsDocument
import me.rerere.rikkahub.ui.context.LocalSettings
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
            :::question {id="sample" type="single" number="1"}
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

            :::
            :::
            :::
            :::
            """.trimIndent(),
            modifier = Modifier.height(IntrinsicSize.Min),
        )
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("回答正确").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看解答").performClick()
        compose.onNodeWithText("本题解析").assertIsDisplayed()
        compose.onNodeWithText("检查连续性").assertIsDisplayed()
        compose.onNodeWithText("对照参考条件").assertIsDisplayed()
        compose.onNodeWithText("可积").assertExists()
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

    private fun render(markdown: String, modifier: Modifier = Modifier.fillMaxSize()) {
        val document = prepareExamDocsDocument(markdown, "https://example.test/android/docs/sample.md", legacy = false)
        val registry = ExamDocsComponentRegistry()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettings provides Settings.dummy()) {
                    val renderer = rememberExamDocsRenderer(document, rememberLazyListState(), {}, { it })
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
