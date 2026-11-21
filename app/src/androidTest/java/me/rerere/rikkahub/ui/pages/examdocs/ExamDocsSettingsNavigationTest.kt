package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamDocsSettingsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<RouteActivity>()

    @Test
    fun opensBothSettingsPagesFromReaderMenu() {
        compose.onNodeWithText("文档阅读器").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasContentDescription("文档目录") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.more_options)).performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("站点设置").performClick()
        compose.onNodeWithText("已配置站点").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("UI 设置").performClick()
        compose.onNodeWithText("阅读排版").assertIsDisplayed()
    }
}
