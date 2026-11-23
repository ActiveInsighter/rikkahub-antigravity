package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.data.beszel.BeszelChartRange
import me.rerere.rikkahub.data.beszel.BeszelContainer
import me.rerere.rikkahub.data.beszel.BeszelFormatter
import me.rerere.rikkahub.data.beszel.BeszelStatsPoint
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.data.beszel.BeszelSystemDetails
import me.rerere.rikkahub.data.beszel.BeszelSystemdService
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelDetailPage(
    systemId: String,
    vm: BeszelDetailVM = koinViewModel(key = systemId) { parametersOf(systemId) },
) {
    val navController = LocalNavController.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val system = uiState.system
    val latestStats = uiState.stats.lastOrNull()

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                colors = CustomColors.topBarColors,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = HugeIcons.ArrowLeft01,
                            contentDescription = "返回",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = system?.name ?: "服务器详情",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (system != null) {
                            Text(
                                text = "${system.host} · ${system.os.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = vm::refresh,
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Refresh01,
                            contentDescription = "刷新",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (system == null && uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (system == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "未找到服务器信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Snapshot Hero
                    item(key = "snapshot") {
                        SnapshotHeroCard(
                            system = system,
                            latest = latestStats,
                        )
                    }

                    // Time Range Selector
                    item(key = "range_selector") {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BeszelChartRange.entries.forEachIndexed { index, range ->
                                SegmentedButton(
                                    selected = uiState.range == range,
                                    onClick = { vm.selectRange(range) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = BeszelChartRange.entries.size,
                                    ),
                                    label = { Text(range.label) },
                                )
                            }
                        }
                    }

                    // Charts
                    item(key = "charts") {
                        val timestamps = remember(uiState.stats) {
                            uiState.stats.map { it.timestampMillis }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BeszelMetricChartCard(
                                title = "CPU 使用率",
                                subtitle = if (uiState.isChartLoading) "加载中…" else "采样区间 ${uiState.range.label}",
                                timestamps = timestamps,
                                series = listOf(
                                    ChartSeries(
                                        label = "CPU",
                                        color = MaterialTheme.colorScheme.primary,
                                        values = uiState.stats.map { it.cpu },
                                    ),
                                ),
                                valueFormatter = BeszelFormatter::formatPercent,
                                fixedMaxValue = 100.0,
                            )

                            BeszelMetricChartCard(
                                title = "内存使用率",
                                subtitle = latestStats?.let {
                                    "${BeszelFormatter.formatGigabytes(it.memUsedGb)} / ${BeszelFormatter.formatGigabytes(it.memTotalGb)}"
                                },
                                timestamps = timestamps,
                                series = listOf(
                                    ChartSeries(
                                        label = "内存",
                                        color = MaterialTheme.colorScheme.secondary,
                                        values = uiState.stats.map { it.memPercent },
                                    ),
                                ),
                                valueFormatter = BeszelFormatter::formatPercent,
                                fixedMaxValue = 100.0,
                            )

                            BeszelMetricChartCard(
                                title = "网络吞吐",
                                subtitle = "实时上传 / 下载速率",
                                timestamps = timestamps,
                                series = listOf(
                                    ChartSeries(
                                        label = "↑",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        values = uiState.stats.map { it.netSentBytesPerSec },
                                    ),
                                    ChartSeries(
                                        label = "↓",
                                        color = MaterialTheme.colorScheme.primary,
                                        values = uiState.stats.map { it.netRecvBytesPerSec },
                                    ),
                                ),
                                valueFormatter = BeszelFormatter::formatBytesPerSec,
                            )

                            BeszelMetricChartCard(
                                title = "磁盘 I/O",
                                subtitle = "读写吞吐速率",
                                timestamps = timestamps,
                                series = listOf(
                                    ChartSeries(
                                        label = "读",
                                        color = MaterialTheme.colorScheme.primary,
                                        values = uiState.stats.map { it.diskReadBytesPerSec },
                                    ),
                                    ChartSeries(
                                        label = "写",
                                        color = MaterialTheme.colorScheme.error,
                                        values = uiState.stats.map { it.diskWriteBytesPerSec },
                                    ),
                                ),
                                valueFormatter = BeszelFormatter::formatBytesPerSec,
                            )
                        }
                    }

                    // Extra filesystems & GPUs
                    if (latestStats != null && (latestStats.extraFs.isNotEmpty() || latestStats.gpus.isNotEmpty())) {
                        item(key = "extra_resources") {
                            ExtraResourcesCard(latest = latestStats)
                        }
                    }

                    // Docker / Podman Containers
                    if (uiState.containers.isNotEmpty()) {
                        item(key = "containers_header") {
                            SectionTitle("容器 (${uiState.containers.size})")
                        }
                        items(
                            items = uiState.containers,
                            key = { "container_${it.id}" },
                        ) { container ->
                            ContainerCard(container = container)
                        }
                    }

                    // Systemd Services
                    if (uiState.services.isNotEmpty()) {
                        item(key = "services_header") {
                            SectionTitle("Systemd 服务 (${uiState.services.size})")
                        }
                        items(
                            items = uiState.services.take(30),
                            key = { "service_${it.id}" },
                        ) { service ->
                            SystemdServiceRow(service = service)
                        }
                    }

                    // Hardware / System Info
                    item(key = "hardware_info") {
                        HardwareInfoGroup(
                            system = system,
                            details = uiState.details,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun SnapshotHeroCard(
    system: BeszelSystem,
    latest: BeszelStatsPoint?,
) {
    val cpu = latest?.cpu ?: system.cpuPercent
    val memPct = latest?.memPercent ?: system.memoryPercent
    val diskPct = latest?.diskPercent ?: system.diskPercent
    val statusColor = if (system.isUp) Color(0xFF22C55E) else MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = if (system.isUp) "运行正常" else system.status.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }

                Text(
                    text = "已运行 ${BeszelFormatter.formatUptime(system.uptimeSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 3 Resource bars
            ResourceBarRow(
                title = "CPU",
                detail = if (system.loadAvg.isNotEmpty()) {
                    "负载 ${system.loadAvg.joinToString(" / ") { String.format(Locale.US, "%.2f", it) }}"
                } else {
                    ""
                },
                percent = cpu,
            )
            ResourceBarRow(
                title = "内存",
                detail = latest?.let {
                    "${BeszelFormatter.formatGigabytes(it.memUsedGb)} / ${BeszelFormatter.formatGigabytes(it.memTotalGb)}"
                } ?: "",
                percent = memPct,
            )
            ResourceBarRow(
                title = "根磁盘",
                detail = latest?.let {
                    "${BeszelFormatter.formatGigabytes(it.diskUsedGb)} / ${BeszelFormatter.formatGigabytes(it.diskTotalGb)}"
                } ?: "",
                percent = diskPct,
            )

            if (latest != null && latest.temperatures.isNotEmpty()) {
                val maxTemp = latest.temperatures.values.maxOrNull() ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "最高温度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f°C", maxTemp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceBarRow(
    title: String,
    detail: String,
    percent: Double,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = BeszelFormatter.formatPercent(percent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        BeszelExpressiveBar(
            progress = (percent / 100.0).toFloat(),
            color = usageSemanticColor(percent),
        )
    }
}

@Composable
private fun ExtraResourcesCard(latest: BeszelStatsPoint) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (latest.gpus.isNotEmpty()) {
                Text(
                    text = "GPU",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    fontWeight = FontWeight.Bold,
                )
                latest.gpus.values.forEach { gpu ->
                    val detail = buildString {
                        if (gpu.memUsedMb != null && gpu.memTotalMb != null) {
                            append("${gpu.memUsedMb!!.toInt()} / ${gpu.memTotalMb!!.toInt()} MB")
                        }
                        gpu.powerWatts?.let {
                            if (isNotEmpty()) append(" · ")
                            append(String.format(Locale.US, "%.0fW", it))
                        }
                    }
                    ResourceBarRow(
                        title = gpu.name,
                        detail = detail,
                        percent = gpu.usage,
                    )
                }
            }

            if (latest.extraFs.isNotEmpty()) {
                Text(
                    text = "挂载磁盘",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    fontWeight = FontWeight.Bold,
                )
                latest.extraFs.forEach { (name, fs) ->
                    ResourceBarRow(
                        title = name,
                        detail = "${BeszelFormatter.formatGigabytes(fs.usedGb)} / ${BeszelFormatter.formatGigabytes(fs.totalGb)}",
                        percent = fs.percent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerCard(container: BeszelContainer) {
    val statusColor = if (container.isRunning) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = container.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = container.image,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "CPU ${BeszelFormatter.formatPercent(container.cpu)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "内存 ${String.format(Locale.US, "%.0f MB", container.memoryMb)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "网络 ${BeszelFormatter.formatBytesPerSec(container.netBytesPerSec)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SystemdServiceRow(service: BeszelSystemdService) {
    val statusColor = when {
        service.isFailed -> MaterialTheme.colorScheme.error
        service.isActive -> Color(0xFF22C55E)
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${service.stateName} (${service.subName}) · ${BeszelFormatter.formatBytes(service.memoryBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HardwareInfoGroup(
    system: BeszelSystem,
    details: BeszelSystemDetails?,
) {
    val hostname = details?.hostname ?: system.hostname ?: "-"
    val osText = details?.osName ?: system.os.displayName
    val kernel = details?.kernel ?: system.kernel ?: "-"
    val cpuModel = details?.cpuModel ?: system.cpuModel ?: "-"
    val cores = details?.cores?.takeIf { it > 0 } ?: system.cores
    val threads = details?.threads?.takeIf { it > 0 } ?: system.threads

    CardGroup(
        title = { Text("系统与硬件信息") },
    ) {
        item(trailingContent = { Text(hostname) }) {
            Text("主机名")
        }
        item(trailingContent = { Text(osText) }) {
            Text("操作系统")
        }
        item(trailingContent = { Text(kernel, maxLines = 1, overflow = TextOverflow.Ellipsis) }) {
            Text("内核版本")
        }
        item(
            supportingContent = { Text(cpuModel) },
            trailingContent = {
                if (cores > 0) Text("${cores}C / ${threads}T")
            },
        ) {
            Text("处理器")
        }
        system.agentVersion?.let { ver ->
            item(trailingContent = { Text("v$ver") }) {
                Text("Agent 版本")
            }
        }
    }
}
