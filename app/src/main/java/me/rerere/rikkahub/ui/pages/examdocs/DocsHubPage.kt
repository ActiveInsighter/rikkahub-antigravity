package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.RecentExamDoc
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject

@Composable
fun DocsHubPage() {
    val navController = LocalNavController.current
    val repository: ExamDocsRepository = koinInject()
    val recentDocs by repository.recentDocsFlow.collectAsStateWithLifecycle()
    val siteSettings by repository.siteSettingsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("文档") },
                subtitle = {
                    Text(
                        text = "当前站点 · ${siteSettings.active.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ExamDocsStats) }) {
                        Icon(HugeIcons.ChartColumn, contentDescription = "答题统计")
                    }
                    IconButton(onClick = { navController.navigate(Screen.ExamDocsSettings) }) {
                        Icon(HugeIcons.Settings03, contentDescription = "文档设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "browse_entry") {
                Surface(
                    onClick = {
                        navController.navigate(
                            Screen.ExamDocs(
                                initialUrl = null,
                                siteBaseUrl = siteSettings.active.baseUrl,
                                restoreScroll = false,
                            )
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Book02,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "浏览文档目录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "从目录进入将从头阅读，不影响最近阅读进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (siteSettings.sites.size > 1) {
                item(key = "site_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        siteSettings.sites.forEach { site ->
                            FilterChip(
                                selected = site == siteSettings.active,
                                onClick = {
                                    repository.switchSite(site)
                                    navController.navigate(
                                        Screen.ExamDocs(
                                            initialUrl = null,
                                            siteBaseUrl = site.baseUrl,
                                            restoreScroll = false,
                                        )
                                    )
                                },
                                label = { Text(site.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }

            if (recentDocs.isEmpty()) {
                item(key = "recent_empty") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CustomColors.listItemColors.containerColor,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Clock01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = "暂无最近阅读记录",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "打开任意文档后，这里会保留最近读过的文档与精确阅读位置，方便下次直接续读。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    navController.navigate(
                                        Screen.ExamDocs(
                                            initialUrl = null,
                                            siteBaseUrl = siteSettings.active.baseUrl,
                                            restoreScroll = false,
                                        )
                                    )
                                }
                            ) {
                                Text("打开目录")
                            }
                        }
                    }
                }
            } else {
                item(key = "recent_docs") {
                    RecentDocsGroup(
                        docs = recentDocs.take(8),
                        onOpenDoc = { doc ->
                            navController.navigate(
                                Screen.ExamDocs(
                                    initialUrl = doc.pageUrl,
                                    siteBaseUrl = doc.siteBaseUrl,
                                    restoreScroll = true,
                                )
                            )
                        },
                        onRemoveDoc = { doc ->
                            repository.removeRecentDoc(doc.siteBaseUrl, doc.pageUrl)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentDocsGroup(
    docs: List<RecentExamDoc>,
    onOpenDoc: (RecentExamDoc) -> Unit,
    onRemoveDoc: (RecentExamDoc) -> Unit,
) {
    CardGroup(
        title = { Text("最近阅读 · 点击直接续读") },
    ) {
        docs.forEach { doc ->
            val pathText = buildString {
                append(doc.siteName)
                if (doc.breadcrumb.isNotEmpty()) {
                    append(" · ")
                    append(doc.breadcrumb.joinToString(" / "))
                }
            }
            val progressText = if (doc.scrollIndex > 0) {
                "上次读至第 ${doc.scrollIndex + 1} 段"
            } else {
                "已打开"
            }
            val timeText = java.time.Instant.ofEpochMilli(doc.lastReadAt).toLocalDateTime()

            item(
                onClick = { onOpenDoc(doc) },
                leadingContent = {
                    CardGroupIcon(
                        icon = HugeIcons.Book02,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = pathText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$progressText · $timeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onRemoveDoc(doc) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Delete02,
                                contentDescription = "移除记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                Text(
                    text = doc.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
