package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun ExamDocsSettingsPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.ExamDocsAccount) },
                        leadingContent = { CardGroupIcon(
                            HugeIcons.UserCircle, containerColor = containerColor, contentColor = contentColor,
                        ) },
                        headlineContent = { Text("Exam Docs 登录与同步") },
                        supportingContent = { Text("邮箱登录、同步状态和学习记录") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.ExamDocsSiteSettings) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.Internet,
                                containerColor = containerColor,
                                contentColor = contentColor,
                            )
                        },
                        headlineContent = { Text("站点设置") },
                        supportingContent = { Text("管理文档站点、切换数据源与恢复默认") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.ExamDocsUISettings) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.PaintBoard,
                                containerColor = containerColor,
                                contentColor = contentColor,
                            )
                        },
                        headlineContent = { Text("UI 设置") },
                        supportingContent = { Text("背景模糊/玻璃效果与阅读字体大小") },
                    )
                }
            }
        }
    }
}

@Composable
fun ExamDocsSiteSettingsPage(repository: ExamDocsRepository = koinInject()) {
    val siteSettings by repository.siteSettingsFlow.collectAsStateWithLifecycle()
    val sites = siteSettings.sites
    val activeSite = siteSettings.active
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("站点设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(title = { Text("已配置站点") }) {
                    for (site in sites) {
                        item(
                            onClick = { repository.switchSite(site) },
                            leadingContent = { Icon(HugeIcons.Folder01, null) },
                            supportingContent = {
                                Text(site.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (site == activeSite) {
                                        Text(
                                            "当前",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(top = 14.dp, end = 4.dp),
                                        )
                                    }
                                    TextButton(onClick = {
                                        editingUrl = site.baseUrl
                                        showEditor = true
                                    }) { Text("编辑") }
                                    if (sites.size > 1) {
                                        IconButton(onClick = { repository.removeSite(site) }) {
                                            Icon(HugeIcons.Delete01, "移除 ${site.name}")
                                        }
                                    }
                                }
                            },
                        ) { Text(site.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            item {
                CardGroup {
                    item(
                        onClick = {
                            editingUrl = null
                            showEditor = true
                        },
                        leadingContent = { Icon(HugeIcons.Add01, null) },
                    ) { Text("添加站点") }
                    item(
                        onClick = repository::restoreDefaultSites,
                        leadingContent = { Icon(HugeIcons.Refresh03, null) },
                    ) { Text("恢复默认站点") }
                }
            }
        }
    }

    if (showEditor) {
        val editing = sites.firstOrNull { it.baseUrl == editingUrl }
        ExamDocsSiteEditor(editing, repository::saveSite) { showEditor = false }
    }
}

@Composable
fun ExamDocsUISettingsPage(repository: ExamDocsRepository = koinInject()) {
    val uiSettings by repository.uiSettingsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("UI 设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(title = { Text("背景与视觉效果") }) {
                    switchItem(
                        checked = uiSettings.enableBlurEffect,
                        onCheckedChange = { enabled ->
                            repository.saveUiSettings(uiSettings.copy(enableBlurEffect = enabled))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_background_effect_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_title)) },
                    )
                    if (uiSettings.enableBlurEffect) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_type)) },
                        ) {
                            ConnectedChoiceButtons(
                                options = BackgroundEffectType.entries,
                                selected = uiSettings.backgroundEffectType,
                                onSelect = { type ->
                                    repository.saveUiSettings(uiSettings.copy(backgroundEffectType = type))
                                },
                            ) { type, selected ->
                                ConnectedChoiceLabel(
                                    text = when (type) {
                                        BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                        BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                    },
                                    selected = selected,
                                )
                            }
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text("阅读排版") }) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                        trailingContent = { CardGroupValue("${(uiSettings.fontSizeRatio * 100).roundToInt()}%") },
                    ) {
                        Slider(
                            value = uiSettings.fontSizeRatio,
                            onValueChange = { ratio ->
                                repository.saveUiSettings(uiSettings.copy(fontSizeRatio = ratio))
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            MarkdownBlock(
                                content = stringResource(R.string.setting_display_page_font_size_preview),
                                modifier = Modifier.padding(12.dp),
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * uiSettings.fontSizeRatio,
                                    lineHeight = LocalTextStyle.current.lineHeight * uiSettings.fontSizeRatio,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamDocsSiteEditor(
    site: ExamDocsSite?,
    onSave: (ExamDocsSite?, String, String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(site?.baseUrl) { mutableStateOf(site?.name.orEmpty()) }
    var url by rememberSaveable(site?.baseUrl) { mutableStateOf(site?.baseUrl.orEmpty()) }
    var error by rememberSaveable(site?.baseUrl) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (site == null) "添加站点" else "编辑站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text("站点地址") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(site, name, url)
                if (error == null) onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
