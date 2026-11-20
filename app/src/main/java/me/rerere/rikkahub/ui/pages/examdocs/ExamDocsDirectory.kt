package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
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
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("文档目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        DocsSiteSelector(state.sites, state.activeSite, onSelectSite)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("搜索章节或路径") },
            leadingIcon = { Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(HugeIcons.Cancel01, "清除搜索", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.isLoadingTree) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("重试") }
        } else if (state.searchQuery.isNotBlank()) {
            if (state.searchResults.isEmpty()) {
                Text(
                    "未找到匹配的文档",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
            ModuleTabs(
                modules = state.modules,
                selectedIndex = state.selectedModuleIndex,
                onSelectModule = onSelectModule,
            )
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
private fun ModuleTabs(
    modules: List<ExamDocNode.Folder>,
    selectedIndex: Int,
    onSelectModule: (Int) -> Unit,
) {
    if (modules.isEmpty()) return
    val requesters = remember(modules) { modules.map { BringIntoViewRequester() } }
    val safeIndex = selectedIndex.coerceIn(0, modules.lastIndex)

    LaunchedEffect(safeIndex) {
        requesters[safeIndex].bringIntoView()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modules.fastForEachIndexed { index, module ->
            val selected = index == safeIndex
            ToggleButton(
                checked = selected,
                onCheckedChange = { if (!selected) onSelectModule(index) },
                modifier = Modifier
                    .bringIntoViewRequester(requesters[index])
                    .semantics { role = Role.Tab },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                    exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                ) {
                    Row {
                        Icon(
                            imageVector = HugeIcons.Book02,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(
                    text = module.name,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun DocsSiteSelector(sites: List<ExamDocsSite>, selected: ExamDocsSite, onSelect: (ExamDocsSite) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    HugeIcons.Book02,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    selected.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    HugeIcons.ArrowDown01,
                    contentDescription = "切换站点",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (site in sites) {
                DropdownMenuItem(
                    text = {
                        Text(
                            site.name,
                            fontWeight = if (site == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (site == selected) colors.primary else colors.onSurface,
                        )
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
        shape = RoundedCornerShape(12.dp),
        color = if (active) colors.primary.copy(alpha = 0.11f) else Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (folder) {
                val rotation = animateFloatAsState(if (expanded) 90f else 0f, label = "directoryChevron")
                Icon(
                    HugeIcons.ArrowRight01,
                    if (expanded) "收起" else "展开",
                    Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = rotation.value },
                    tint = colors.onSurfaceVariant,
                )
            } else if (subtitle == null) {
                Spacer(Modifier.size(14.dp))
            }
            Icon(
                if (folder) HugeIcons.Folder01 else HugeIcons.File02,
                null,
                Modifier.size(17.dp),
                tint = when {
                    active -> colors.primary
                    folder -> colors.primary.copy(alpha = 0.85f)
                    else -> colors.onSurfaceVariant
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (folder || active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) colors.primary else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (node.questions > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) {
                        colors.primary.copy(alpha = 0.16f)
                    } else {
                        colors.secondaryContainer.copy(alpha = 0.65f)
                    },
                ) {
                    Text(
                        "${node.questions} 题",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) {
                            colors.primary
                        } else {
                            colors.onSecondaryContainer.copy(alpha = 0.85f)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
