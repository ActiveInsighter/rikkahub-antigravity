package me.rerere.rikkahub.ui.pages.examdocs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.data.examdocs.ExamDocsUiSettings
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.formItem
import me.rerere.rikkahub.ui.components.ui.switchItem
import kotlin.math.roundToInt

private enum class DocsSettingsPage { Hub, Sites, Ui }

@Composable
internal fun ExamDocsSiteSettings(
    sites: List<ExamDocsSite>,
    activeSite: ExamDocsSite,
    uiSettings: ExamDocsUiSettings,
    onSelectSite: (ExamDocsSite) -> Unit,
    onSaveSite: (ExamDocsSite?, String, String) -> String?,
    onRemoveSite: (ExamDocsSite) -> Unit,
    onRestoreDefaults: () -> Unit,
    onUpdateUiSettings: (ExamDocsUiSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(DocsSettingsPage.Hub) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingUrl by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = page != DocsSettingsPage.Hub) {
        page = DocsSettingsPage.Hub
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != DocsSettingsPage.Hub
                if (forward) {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 6 } + fadeOut())
                }
            },
            label = "ExamDocsSettingsNavigation",
        ) { currentPage ->
            when (currentPage) {
                DocsSettingsPage.Hub -> DocsSettingsHub(
                    activeSite = activeSite,
                    uiSettings = uiSettings,
                    onOpenSites = { page = DocsSettingsPage.Sites },
                    onOpenUi = { page = DocsSettingsPage.Ui },
                )
                DocsSettingsPage.Sites -> DocsSitesSubPage(
                    sites = sites,
                    activeSite = activeSite,
                    onBack = { page = DocsSettingsPage.Hub },
                    onSelectSite = onSelectSite,
                    onEditSite = { site ->
                        editingUrl = site?.baseUrl
                        showEditor = true
                    },
                    onRemoveSite = onRemoveSite,
                    onRestoreDefaults = onRestoreDefaults,
                )
                DocsSettingsPage.Ui -> DocsUiSubPage(
                    uiSettings = uiSettings,
                    onBack = { page = DocsSettingsPage.Hub },
                    onUpdateUiSettings = onUpdateUiSettings,
                )
            }
        }
    }
    if (showEditor) {
        val editing = sites.firstOrNull { it.baseUrl == editingUrl }
        ExamDocsSiteEditor(editing, onSaveSite) { showEditor = false }
    }
}

@Composable
private fun DocsSettingsHub(
    activeSite: ExamDocsSite,
    uiSettings: ExamDocsUiSettings,
    onOpenSites: () -> Unit,
    onOpenUi: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val effectLabel = if (uiSettings.enableBlurEffect) {
        when (uiSettings.backgroundEffectType) {
            BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
            BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
        }
    } else {
        "关闭"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        CardGroup {
            item(
                onClick = onOpenSites,
                leadingContent = {
                    CardGroupIcon(HugeIcons.Folder01, containerColor = containerColor, contentColor = contentColor)
                },
                headlineContent = { Text("站点设置") },
                supportingContent = { Text("当前站点：${activeSite.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            item(
                onClick = onOpenUi,
                leadingContent = {
                    CardGroupIcon(HugeIcons.PaintBoard, containerColor = containerColor, contentColor = contentColor)
                },
                headlineContent = { Text("UI设置") },
                supportingContent = {
                    Text("背景效果：$effectLabel · 字体 ${(uiSettings.fontSizeRatio * 100).roundToInt()}%")
                },
            )
        }
    }
}

@Composable
private fun DocsSitesSubPage(
    sites: List<ExamDocsSite>,
    activeSite: ExamDocsSite,
    onBack: () -> Unit,
    onSelectSite: (ExamDocsSite) -> Unit,
    onEditSite: (ExamDocsSite?) -> Unit,
    onRemoveSite: (ExamDocsSite) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.76f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubPageHeader(title = "站点设置", onBack = onBack)
        CardGroup {
            for (site in sites) {
                item(
                    onClick = { onSelectSite(site) },
                    leadingContent = { Icon(HugeIcons.Folder01, null) },
                    supportingContent = {
                        Text(site.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (site == activeSite) Text("当前", Modifier.padding(top = 12.dp))
                            TextButton(onClick = { onEditSite(site) }) { Text("编辑") }
                            if (sites.size > 1) {
                                IconButton(onClick = { onRemoveSite(site) }) {
                                    Icon(HugeIcons.Delete01, "移除 ${site.name}")
                                }
                            }
                        }
                    },
                ) { Text(site.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            item(
                onClick = { onEditSite(null) },
                leadingContent = { Icon(HugeIcons.Add01, null) },
            ) { Text("添加站点") }
            item(
                onClick = onRestoreDefaults,
            ) { Text("恢复默认站点") }
        }
    }
}

@Composable
private fun DocsUiSubPage(
    uiSettings: ExamDocsUiSettings,
    onBack: () -> Unit,
    onUpdateUiSettings: (ExamDocsUiSettings) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.76f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubPageHeader(title = "UI设置", onBack = onBack)
        CardGroup {
            switchItem(
                checked = uiSettings.enableBlurEffect,
                onCheckedChange = { onUpdateUiSettings(uiSettings.copy(enableBlurEffect = it)) },
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
                        onSelect = { onUpdateUiSettings(uiSettings.copy(backgroundEffectType = it)) },
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
        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                trailingContent = { CardGroupValue("${(uiSettings.fontSizeRatio * 100).roundToInt()}%") },
            ) {
                Slider(
                    value = uiSettings.fontSizeRatio,
                    onValueChange = { onUpdateUiSettings(uiSettings.copy(fontSizeRatio = it)) },
                    valueRange = 0.5f..2f,
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
        CardGroup {
            item(
                onClick = { onUpdateUiSettings(ExamDocsUiSettings()) },
            ) {
                Text("恢复默认 UI 设置", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
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
