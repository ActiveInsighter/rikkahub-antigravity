package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.stats.HeatmapCard
import me.rerere.rikkahub.ui.pages.stats.StatCard
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
internal fun ExamDocsStatsPage(vm: ExamDocsStatsVM = koinViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("答题统计") },
                subtitle = state.siteName.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    HeatmapCard(
                        requestsPerDay = state.attemptsPerDay,
                        title = "近两个月答题记录",
                        weeksCount = 9,
                        cellSize = 18.dp,
                        cellSpacing = 4.dp,
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = HugeIcons.CheckmarkCircle02,
                                label = "已答题目",
                                value = if (state.totalQuestions > 0) {
                                    "${state.answeredQuestions} / ${state.totalQuestions}"
                                } else {
                                    state.answeredQuestions.toString()
                                },
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = HugeIcons.CheckList,
                                label = "累计提交",
                                value = state.totalAttempts.toString(),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = HugeIcons.Calendar03,
                                label = "活跃天数",
                                value = state.activeDays.toString(),
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = HugeIcons.Book02,
                                label = "涉及文档",
                                value = state.touchedPages.toString(),
                            )
                        }
                    }
                }

                if (state.moduleStats.isNotEmpty()) {
                    item {
                        ModuleStatsCard(state.moduleStats)
                    }
                }

                if (state.recentPages.isNotEmpty()) {
                    item {
                        Text(
                            text = "最近作答",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    item {
                        CardGroup {
                            state.recentPages.forEach { item ->
                                val subtitle = listOfNotNull(
                                    item.moduleName,
                                    formatRelativeDay(item.lastTimestamp),
                                ).joinToString(" · ")
                                item(
                                    onClick = {
                                        navController.navigate(Screen.ExamDocs(initialUrl = item.path))
                                    },
                                    supportingContent = subtitle.takeIf { it.isNotEmpty() }?.let {
                                        {
                                            Text(
                                                text = it,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ) {
                                            Text(
                                                text = "${item.answeredCount} 题",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    },
                                ) {
                                    Text(
                                        text = item.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else if (state.answeredQuestions == 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CustomColors.cardColorsOnSurfaceContainer,
                        ) {
                            Text(
                                text = "还没有答题记录，在文档中完成题目后会自动记录在这里。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleStatsCard(modules: List<ExamDocsModuleStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("模块进度", style = MaterialTheme.typography.titleMedium)
            modules.forEach { module ->
                val progress = if (module.totalQuestions > 0) {
                    (module.answered.toFloat() / module.totalQuestions).coerceIn(0f, 1f)
                } else if (module.answered > 0) 1f else 0f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (module.totalQuestions > 0) {
                                "${module.answered} / ${module.totalQuestions}"
                            } else {
                                "${module.answered} 题"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

private fun formatRelativeDay(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val diff = ChronoUnit.DAYS.between(date, today)
    return when {
        diff <= 0L -> "今天"
        diff == 1L -> "昨天"
        diff in 2L..6L -> "$diff 天前"
        date.year == today.year -> date.format(monthDayFormatter)
        else -> date.format(fullDateFormatter)
    }
}
