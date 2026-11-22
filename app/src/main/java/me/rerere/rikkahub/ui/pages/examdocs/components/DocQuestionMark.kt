package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tag01
import me.rerere.rikkahub.data.examdocs.DocsMark
import me.rerere.rikkahub.data.examdocs.DocsPageKey
import me.rerere.rikkahub.data.examdocs.DocsQuestionKey
import me.rerere.rikkahub.ui.pages.examdocs.DocsReadingProgress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
internal fun DocQuestionMark(reading: DocsReadingProgress, page: DocsPageKey, question: DocsQuestionKey) {
    val snapshot = reading.snapshot ?: return
    val mark = snapshot.marks[question.markId] ?: DocsMark(question.documentPath, question.questionId)
    var editing by remember(question) { mutableStateOf(false) }

    IconButton(onClick = { editing = true }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            HugeIcons.Tag01,
            contentDescription = "收藏与笔记",
            tint = if (mark.favorite || mark.note.isNotBlank() || mark.tagIds.isNotEmpty()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    if (editing) {
        var favorite by remember { mutableStateOf(mark.favorite) }
        var note by remember { mutableStateOf(mark.note) }
        var tagIds by remember { mutableStateOf(mark.tagIds.toSet()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("收藏与笔记") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("收藏这道题")
                        Switch(checked = favorite, onCheckedChange = { favorite = it })
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(4000) },
                        label = { Text("笔记") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (snapshot.tags.isNotEmpty()) {
                        Text("标签", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            snapshot.tags.forEach { tag ->
                                val selected = tag.tagId in tagIds
                                FilterChip(
                                    selected = selected,
                                    enabled = selected || tagIds.size < 20,
                                    onClick = {
                                        tagIds = if (selected) tagIds - tag.tagId else tagIds + tag.tagId
                                    },
                                    label = { Text(tag.name) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val validIds = snapshot.tags.map { it.tagId }.toSet()
                    reading.repository.saveMark(
                        page,
                        mark.copy(favorite = favorite, note = note, tagIds = tagIds.filter { it in validIds }),
                        syncable = !question.questionId.startsWith("local:"),
                    )
                    editing = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
}
