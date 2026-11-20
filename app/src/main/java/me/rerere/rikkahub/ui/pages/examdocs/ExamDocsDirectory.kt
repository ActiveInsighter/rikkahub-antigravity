package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsSite

@Composable
internal fun ExamDocsDirectorySheet(
    state: ExamDocsUiState,
    onSelectSite: (ExamDocsSite) -> Unit,
    onSelectModule: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelectPage: (ExamDocNode.Page) -> Unit,
) {
    val currentUrl = state.currentPage?.url
    val error = state.treeError
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.86f).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("文档目录", style = MaterialTheme.typography.titleLarge)
        DocsSiteSelector(state.sites, state.activeSite, onSelectSite)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("搜索章节或路径") },
            leadingIcon = { Icon(HugeIcons.Search01, null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) { Icon(HugeIcons.Cancel01, "清除搜索") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.isLoadingTree) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("重试") }
        } else if (state.searchQuery.isNotBlank()) {
            if (state.searchResults.isEmpty()) {
                Text("未找到匹配的文档", Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.searchResults, key = { it.page.url }) { entry ->
                    DirectoryRow(
                        node = entry.page,
                        active = currentUrl == entry.page.url,
                        subtitle = entry.breadcrumb.joinToString(" › "),
                        onClick = { onSelectPage(entry.page) },
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.modules) { index, module ->
                    FilterChip(
                        selected = index == state.selectedModuleIndex,
                        onClick = { onSelectModule(index) },
                        label = { Text(module.name) },
                    )
                }
            }
            key(state.activeSite.baseUrl, state.selectedModuleIndex) {
                DirectoryTree(
                    module = state.modules.getOrNull(state.selectedModuleIndex),
                    currentUrl = currentUrl,
                    onSelectPage = onSelectPage,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DocsSiteSelector(sites: List<ExamDocsSite>, selected: ExamDocsSite, onSelect: (ExamDocsSite) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(HugeIcons.ArrowDown01, "切换站点", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (site in sites) {
                DropdownMenuItem(
                    text = {
                        Text(site.name, fontWeight = if (site == selected) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = {
                        expanded = false
                        onSelect(site)
                    },
                )
            }
        }
    }
}

private data class DirectoryEntry(val node: ExamDocNode, val key: String, val depth: Int, val expanded: Boolean)

@Composable
private fun DirectoryTree(
    module: ExamDocNode.Folder?,
    currentUrl: String?,
    onSelectPage: (ExamDocNode.Page) -> Unit,
    modifier: Modifier,
) {
    val overrides = remember { mutableStateMapOf<String, Boolean>() }
    val nodes = module?.children.orEmpty()
    val activeFolders = remember(nodes, currentUrl) {
        mutableSetOf<String>().also { result ->
            fun visit(children: List<ExamDocNode>, parent: String): Boolean {
                return children.withIndex().any { (index, node) ->
                    when (node) {
                        is ExamDocNode.Page -> node.url == currentUrl
                        is ExamDocNode.Folder -> {
                            val key = "$parent/$index"
                            visit(node.children, key).also { if (it) result.add(key) }
                        }
                    }
                }
            }
            if (currentUrl != null) visit(nodes, "")
        }
    }
    val rows by remember(nodes, activeFolders) {
        derivedStateOf {
            buildList {
                fun flatten(children: List<ExamDocNode>, parent: String, depth: Int) {
                    children.forEachIndexed { index, node ->
                        val key = "$parent/$index"
                        val expanded = node is ExamDocNode.Folder && (overrides[key] ?: (key in activeFolders))
                        add(DirectoryEntry(node, key, depth, expanded))
                        if (node is ExamDocNode.Folder && expanded) flatten(node.children, key, depth + 1)
                    }
                }
                flatten(nodes, "", 0)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(rows, key = DirectoryEntry::key, contentType = { it.node is ExamDocNode.Folder }) { entry ->
            val node = entry.node
            DirectoryRow(
                node = node,
                active = node is ExamDocNode.Page && node.url == currentUrl,
                expanded = entry.expanded,
                modifier = Modifier
                    .animateItem()
                    .padding(start = (entry.depth.coerceAtMost(10) * 12).dp),
                onClick = {
                    when (node) {
                        is ExamDocNode.Folder -> overrides[entry.key] = !entry.expanded
                        is ExamDocNode.Page -> onSelectPage(node)
                    }
                },
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    node: ExamDocNode,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expanded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val folder = node is ExamDocNode.Folder
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (active) colors.primaryContainer else Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (folder) {
                val rotation = animateFloatAsState(if (expanded) 90f else 0f, label = "directoryChevron")
                Icon(
                    HugeIcons.ArrowRight01,
                    if (expanded) "收起" else "展开",
                    Modifier.size(14.dp).graphicsLayer { rotationZ = rotation.value },
                    tint = colors.onSurfaceVariant,
                )
            } else if (subtitle == null) {
                Spacer(Modifier.size(14.dp))
            }
            Icon(
                if (folder) HugeIcons.Folder01 else HugeIcons.File02,
                null,
                Modifier.size(17.dp),
                tint = if (folder || active) colors.primary else colors.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (folder || active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (node.questions > 0) {
                Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                    Text(
                        "${node.questions} 题",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
