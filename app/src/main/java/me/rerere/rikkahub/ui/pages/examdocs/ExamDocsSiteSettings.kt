package me.rerere.rikkahub.ui.pages.examdocs

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.data.examdocs.builtInDocsSites
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
internal fun ExamDocsSiteSettings(
    sites: List<ExamDocsSite>,
    activeSite: ExamDocsSite,
    onSelectSite: (ExamDocsSite) -> Unit,
    onSaveSite: (ExamDocsSite?, String, String) -> String?,
    onRemoveSite: (ExamDocsSite) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingUrl by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("站点设置", style = MaterialTheme.typography.titleLarge)
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
                                TextButton(onClick = {
                                    editingUrl = site.baseUrl
                                    showEditor = true
                                }) { Text("编辑") }
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
                    onClick = {
                        editingUrl = null
                        showEditor = true
                    },
                    leadingContent = { Icon(HugeIcons.Add01, null) },
                ) { Text("添加站点") }
                item(
                    onClick = onRestoreDefaults,
                ) { Text("恢复默认站点") }
            }
        }
    }
    if (showEditor) {
        val editing = sites.firstOrNull { it.baseUrl == editingUrl }
        ExamDocsSiteEditor(editing, onSaveSite) { showEditor = false }
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
