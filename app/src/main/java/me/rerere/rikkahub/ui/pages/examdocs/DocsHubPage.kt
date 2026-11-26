package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import me.rerere.hugeicons.stroke.Bookmark02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.data.examdocs.RecentDocEntry
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.util.concurrent.TimeUnit

@Composable
fun DocsHubPage(
    repository: ExamDocsRepository = koinInject(),
) {
    val navController = LocalNavController.current
    val siteSettings by repository.siteSettingsFlow.collectAsStateWithLifecycle()
    val recentDocs by repository.recentDocsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun openDirectory(site: ExamDocsSite = siteSettings.active) {
        navController.navigate(
            Screen.ExamDocs(
                initialUrl = null,
                initialSiteBaseUrl = site.baseUrl,
                restoreScrollPosition = false,
            )
        )
    }

    fun openRecentDoc(entry: RecentDocEntry) {
        navController.navigate(
            Screen.ExamDocs(
                initialUrl = entry.url,
                initialSiteBaseUrl = entry.siteBaseUrl,
                restoreScrollPosition = true,
            )
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("文档") },
                subtitle = {
                    Text(
                        text = "${siteSettings.active.name} · 最近阅读与目录",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ExamDocsStats) }) {
                        Icon(HugeIcons.ChartColumn, contentDescription = "刷题统计")
                    }
                    IconButton(onClick = { navController.navigate(Screen.ExamDocsAccount) }) {
                        Icon(HugeIcons.UserCircle, contentDescription = "账号与同步")
                    }
                    IconButton(onClick = { navController.navigate(Screen.ExamDocsSettings) }) {
                        Icon(HugeIcons.Settings03, contentDescription = "文档设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CustomColors.topBarColors.containerColor,
                    scrolledContainerColor = CustomColors.topBarColors.scrolledContainerColor,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "reader_hero") {
                DocsDirectoryHeroCard(
                    activeSite = siteSettings.active,
                    recentCount = recentDocs.size,
                    onOpenReader = { openDirectory(siteSettings.active) },
                )
            }

            item(key = "recent_docs") {
                if (recentDocs.isEmpty()) {
                    DocsEmptyRecentCard(
                        onStartReading = { openDirectory(siteSettings.active) },
                    )
                } else {
                    CardGroup(
                        title = { Text("最近阅读") },
                    ) {
                        recentDocs.forEach { entry ->
                            val subtitleParts = buildList {
                                add(entry.siteName)
                                if (entry.breadcrumb.isNotEmpty()) {
                                    add(entry.breadcrumb.joinToString(" / "))
                                }
                            }
                            val progressLabel = formatReadingProgress(entry)
                            val timeLabel = formatRelativeTime(entry.lastReadAt)
                            item(
                                onClick = { openRecentDoc(entry) },
                                leadingContent = {
                                    CardGroupIcon(
                                        icon = if (entry.scrollIndex > 0) HugeIcons.Bookmark02 else HugeIcons.Book02,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = subtitleParts.joinToString(" · "),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (progressLabel != null) {
                                                "$progressLabel · $timeLabel"
                                            } else {
                                                timeLabel
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        IconButton(
                                            onClick = { repository.removeRecentDoc(entry.siteBaseUrl, entry.url) },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Delete02,
                                                contentDescription = "移除记录",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
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
                                    text = entry.title.ifBlank { entry.url },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (siteSettings.sites.isNotEmpty()) {
                item(key = "sites_directory") {
                    CardGroup(
                        title = { Text("从目录打开") },
                    ) {
                        siteSettings.sites.forEach { site ->
                            val isActive = site.baseUrl == siteSettings.active.baseUrl
                            item(
                                onClick = { openDirectory(site) },
                                leadingContent = {
                                    CardGroupIcon(
                                        icon = HugeIcons.Folder01,
                                        containerColor = if (isActive) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        contentColor = if (isActive) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (isActive) "当前站点 · 从头浏览目录" else "切换站点并浏览目录",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = HugeIcons.ArrowRight01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            ) {
                                Text(
                                    text = site.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocsDirectoryHeroCard(
    activeSite: ExamDocsSite,
    recentCount: Int,
    onOpenReader: () -> Unit,
) {
    Surface(
        onClick = onOpenReader,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "文档阅读器",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (recentCount > 0) {
                        "${activeSite.name} · 浏览目录或从下方继续阅读"
                    } else {
                        "${activeSite.name} · 打开目录开始阅读"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DocsEmptyRecentCard(
    onStartReading: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Clock01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = "暂无最近阅读记录",
                style = MaterialTheme.typography.titleSmallEmphasized,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "从目录打开任意文档后，这里会记录最近阅读的文档和位置，方便下次直接继续。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onStartReading,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(HugeIcons.Book02, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("打开文档目录")
            }
        }
    }
}

private fun formatReadingProgress(entry: RecentDocEntry): String? {
    if (entry.scrollIndex <= 0 && entry.scrollOffset <= 0) return null
    if (entry.totalBlocks > 0) {
        val percent = ((entry.scrollIndex + 1).toFloat() / entry.totalBlocks.toFloat() * 100f)
            .toInt()
            .coerceIn(1, 100)
        return "上次读至 $percent%（第 ${entry.scrollIndex + 1} 段）"
    }
    return "上次读至第 ${entry.scrollIndex + 1} 段"
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "最近"
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> "${days / 7} 周前"
    }
}
