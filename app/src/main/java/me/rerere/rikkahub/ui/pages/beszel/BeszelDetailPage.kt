package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.beszel.BeszelChartRange
import me.rerere.rikkahub.data.beszel.BeszelContainer
import me.rerere.rikkahub.data.beszel.BeszelFormatters
import me.rerere.rikkahub.data.beszel.BeszelSmartDevice
import me.rerere.rikkahub.data.beszel.BeszelSystemdService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class BeszelDetailTab(
    override val icon: ImageVector,
    val title: String,
) : PagerTab {
    OVERVIEW(HugeIcons.ChartLineData01, "监控"),
    CONTAINERS(HugeIcons.Package01, "容器"),
    SERVICES(HugeIcons.Tools, "服务"),
    DISKS(HugeIcons.Database02, "磁盘");

    override val label: String
        @Composable get() = title
}

@Composable
fun BeszelDetailPage(
    systemId: String,
    vm: BeszelDetailVM = koinViewModel(key = systemId) { parametersOf(systemId) },
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val tabs = remember { BeszelDetailTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val system = state.system
    val statusColor = when (system?.status) {
        "up" -> Color(0xFF16A34A)
        "down" -> MaterialTheme.colorScheme.error
        "paused" -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.outline
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = system?.name?.ifBlank { system.host } ?: "服务器详情",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (system != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor),
                                )
                            }
                        }
                        if (system != null) {
                            Text(
                                text = buildList {
                                    if (system.host.isNotBlank()) add(system.host)
                                    add(BeszelFormatters.statusDisplayName(system.status))
                                    if (system.uptimeSeconds > 0) {
                                        add("运行 ${BeszelFormatters.formatUptime(system.uptimeSeconds)}")
                                    }
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = HugeIcons.Refresh01,
                                contentDescription = "刷新",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                PagerTabs(
                    pagerState = pagerState,
                    tabs = tabs,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (tabs[page]) {
                        BeszelDetailTab.OVERVIEW -> OverviewTab(
                            state = state,
                            onSelectRange = vm::selectRange,
                        )
                        BeszelDetailTab.CONTAINERS -> ContainersTab(
                            containers = state.containers,
                            search = state.containerSearch,
                            onSearchChange = vm::setContainerSearch,
                            onOpenLogs = vm::openContainerLogs,
                            onOpenInfo = vm::openContainerInfo,
                        )
                        BeszelDetailTab.SERVICES -> ServicesTab(
                            services = state.services,
                            onOpenLogs = vm::openServiceLogs,
                        )
                        BeszelDetailTab.DISKS -> SmartDisksTab(
                            devices = state.smartDevices,
                            isRefreshing = state.isRefreshingSmart,
                            onRefreshSmart = vm::refreshSmartDevices,
                        )
                    }
                }
            }
        }
    }

    state.inspectorSheet?.let { sheet ->
        InspectorBottomSheet(
            sheet = sheet,
            onDismiss = vm::closeInspectorSheet,
        )
    }
}

@Composable
private fun OverviewTab(
    state: BeszelDetailUiState,
    onSelectRange: (BeszelChartRange) -> Unit,
) {
    val times = remember(state.stats) { state.stats.map { it.time } }
    val latest = state.latestPoint
    val system = state.system
    val details = state.details

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Time range selector
        item(key = "range_selector") {
            ConnectedChoiceButtons(
                options = BeszelChartRange.entries,
                selected = state.range,
                onSelect = onSelectRange,
                minHeight = 40.dp,
            ) { option, _ ->
                Text(option.label, maxLines = 1)
            }
        }

        // Quick 4-stat summary grid
        item(key = "quick_stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickMetricTile(
                    label = "CPU",
                    value = BeszelFormatters.formatPercent(latest?.cpu ?: system?.cpuPercent ?: 0.0),
                    sub = system?.loadAvg?.takeIf { it.isNotEmpty() }?.let {
                        "负载 ${String.format("%.2f", it[0])}"
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickMetricTile(
                    label = "内存",
                    value = BeszelFormatters.formatPercent(latest?.memPercent ?: system?.memoryPercent ?: 0.0),
                    sub = if (latest != null && latest.memTotalGb > 0) {
                        "${BeszelFormatters.formatGb(latest.memUsedGb)} / ${BeszelFormatters.formatGb(latest.memTotalGb)}"
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // CPU Chart
        item(key = "chart_cpu") {
            BeszelMetricChartCard(
                title = "CPU 使用率",
                subtitle = details?.cpu?.takeIf { it.isNotBlank() },
                times = times,
                series = listOf(
                    BeszelChartSeries(
                        label = "CPU",
                        color = MaterialTheme.colorScheme.primary,
                        values = state.stats.map { it.cpu },
                    ),
                ),
                range = state.range,
                fixedMax = 100.0,
                valueFormatter = { BeszelFormatters.formatPercent(it) },
            )
        }

        // Memory Chart
        item(key = "chart_mem") {
            BeszelMetricChartCard(
                title = "内存使用率",
                subtitle = if (latest != null && latest.memTotalGb > 0) {
                    "已用 ${BeszelFormatters.formatGb(latest.memUsedGb)} · 缓存 ${BeszelFormatters.formatGb(latest.memBuffCacheGb)}" +
                        if (latest.swapTotalGb > 0) " · Swap ${BeszelFormatters.formatGb(latest.swapUsedGb)}/${BeszelFormatters.formatGb(latest.swapTotalGb)}" else ""
                } else {
                    null
                },
                times = times,
                series = listOf(
                    BeszelChartSeries(
                        label = "内存",
                        color = MaterialTheme.colorScheme.secondary,
                        values = state.stats.map { it.memPercent },
                    ),
                ),
                range = state.range,
                fixedMax = 100.0,
                valueFormatter = { BeszelFormatters.formatPercent(it) },
            )
        }

        // Network Chart
        item(key = "chart_net") {
            BeszelMetricChartCard(
                title = "网络吞吐",
                times = times,
                series = listOf(
                    BeszelChartSeries(
                        label = "上传",
                        color = Color(0xFF16A34A),
                        values = state.stats.map { it.netSentBytesPerSec },
                    ),
                    BeszelChartSeries(
                        label = "下载",
                        color = MaterialTheme.colorScheme.primary,
                        values = state.stats.map { it.netRecvBytesPerSec },
                    ),
                ),
                range = state.range,
                valueFormatter = { BeszelFormatters.formatBytesPerSec(it) },
            )
        }

        // Disk I/O Chart
        item(key = "chart_disk") {
            BeszelMetricChartCard(
                title = "磁盘 I/O",
                subtitle = if (latest != null && latest.diskTotalGb > 0) {
                    "根分区已用 ${BeszelFormatters.formatGb(latest.diskUsedGb)} / ${BeszelFormatters.formatGb(latest.diskTotalGb)} (${BeszelFormatters.formatPercent(latest.diskPercent)})"
                } else {
                    null
                },
                times = times,
                series = listOf(
                    BeszelChartSeries(
                        label = "读取",
                        color = MaterialTheme.colorScheme.tertiary,
                        values = state.stats.map { it.diskReadBytesPerSec },
                    ),
                    BeszelChartSeries(
                        label = "写入",
                        color = Color(0xFFD97706),
                        values = state.stats.map { it.diskWriteBytesPerSec },
                    ),
                ),
                range = state.range,
                valueFormatter = { BeszelFormatters.formatBytesPerSec(it) },
            )
        }

        // GPUs if present
        val gpus = latest?.gpus.orEmpty()
        if (gpus.isNotEmpty()) {
            item(key = "gpus") {
                CardGroup(title = { Text("GPU") }) {
                    gpus.forEach { gpu ->
                        val memText = if (gpu.memUsedMb != null && gpu.memTotalMb != null) {
                            " · 显存 ${gpu.memUsedMb.toInt()}/${gpu.memTotalMb.toInt()} MB"
                        } else {
                            ""
                        }
                        val powerText = gpu.powerWatts?.let { " · ${String.format("%.1f", it)}W" }.orEmpty()
                        item(
                            supportingContent = {
                                Text("利用率 ${BeszelFormatters.formatPercent(gpu.usage)}$memText$powerText")
                            },
                        ) {
                            Text(gpu.name)
                        }
                    }
                }
            }
        }

        // Temperatures if present
        val temps = latest?.temperatures.orEmpty()
        if (temps.isNotEmpty()) {
            item(key = "temps") {
                CardGroup(title = { Text("温度传感器") }) {
                    temps.entries.sortedBy { it.key }.forEach { (sensor, temp) ->
                        item(
                            trailingContent = {
                                Text(
                                    text = BeszelFormatters.formatTemp(temp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (temp >= 80.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        ) {
                            Text(sensor)
                        }
                    }
                }
            }
        }

        // Extra filesystems if present
        val extraFs = latest?.extraFs.orEmpty()
        if (extraFs.isNotEmpty()) {
            item(key = "extra_fs") {
                CardGroup(title = { Text("挂载分区") }) {
                    extraFs.forEach { fs ->
                        item(
                            supportingContent = {
                                Text("${BeszelFormatters.formatGb(fs.usedGb)} / ${BeszelFormatters.formatGb(fs.totalGb)}")
                            },
                            trailingContent = {
                                Text(
                                    text = BeszelFormatters.formatPercent(fs.percent),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                        ) {
                            Text(fs.name)
                        }
                    }
                }
            }
        }

        // System Details
        if (details != null || system != null) {
            item(key = "system_info") {
                CardGroup(title = { Text("系统信息") }) {
                    val hostname = details?.hostname?.ifBlank { system?.hostname.orEmpty() }.orEmpty()
                    if (hostname.isNotBlank()) {
                        item(supportingContent = { Text(hostname) }) { Text("主机名") }
                    }
                    val osLabel = details?.osName?.ifBlank { BeszelFormatters.osDisplayName(details.os) }
                        ?: system?.let { BeszelFormatters.osDisplayName(it.os) }
                        ?: ""
                    if (osLabel.isNotBlank()) {
                        val archSuffix = details?.arch?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        item(supportingContent = { Text("$osLabel$archSuffix") }) { Text("操作系统") }
                    }
                    val kernel = details?.kernel?.ifBlank { system?.kernel.orEmpty() }.orEmpty()
                    if (kernel.isNotBlank()) {
                        item(supportingContent = { Text(kernel) }) { Text("内核版本") }
                    }
                    val cpu = details?.cpu?.ifBlank { system?.cpuModel.orEmpty() }.orEmpty()
                    if (cpu.isNotBlank()) {
                        val cores = details?.cores ?: system?.cores ?: 0
                        val threads = details?.threads ?: system?.threads ?: 0
                        val coreText = if (cores > 0) " (${cores}核 ${threads}线程)" else ""
                        item(supportingContent = { Text("$cpu$coreText") }) { Text("处理器") }
                    }
                    system?.agentVersion?.let { ver ->
                        item(supportingContent = { Text("v$ver") }) { Text("Agent 版本") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMetricTile(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.Bold,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContainersTab(
    containers: List<BeszelContainer>,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpenLogs: (BeszelContainer) -> Unit,
    onOpenInfo: (BeszelContainer) -> Unit,
) {
    val filtered = remember(containers, search) {
        val q = search.trim()
        if (q.isEmpty()) containers
        else containers.filter {
            it.name.contains(q, ignoreCase = true) || it.image.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "container_search") {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("搜索容器名称或镜像 (${containers.size})…") },
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        if (filtered.isEmpty()) {
            item(key = "empty_containers") {
                EmptyTabState(
                    icon = HugeIcons.Package01,
                    title = if (containers.isEmpty()) "暂无容器数据" else "没有匹配的容器",
                    subtitle = "Agent 未开启 Docker/Podman 监控或当前无运行容器",
                )
            }
        } else {
            items(filtered, key = { it.id }) { container ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = container.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = container.image,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val badgeColor = if (container.isRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline
                            Surface(
                                shape = CircleShape,
                                color = badgeColor.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = container.healthLabel ?: if (container.isRunning) "运行中" else container.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "CPU ${BeszelFormatters.formatPercent(container.cpu)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "内存 ${BeszelFormatters.formatBytes(container.memoryMb * 1024 * 1024)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "网络 ${BeszelFormatters.formatBytesPerSec(container.netBytesPerSec)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenLogs(container) },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.ComputerTerminal01,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("日志")
                            }
                            FilledTonalButton(
                                onClick = { onOpenInfo(container) },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.FileView,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("详情")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesTab(
    services: List<BeszelSystemdService>,
    onOpenLogs: (BeszelSystemdService) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (services.isEmpty()) {
            item(key = "empty_services") {
                EmptyTabState(
                    icon = HugeIcons.Tools,
                    title = "暂无 Systemd 服务",
                    subtitle = "未配置服务监控或当前系统不支持 Systemd",
                )
            }
        } else {
            items(services, key = { it.id }) { svc ->
                val stateColor = when {
                    svc.isFailed -> MaterialTheme.colorScheme.error
                    svc.isActive -> Color(0xFF16A34A)
                    else -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    onClick = { onOpenLogs(svc) },
                    shape = RoundedCornerShape(20.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(stateColor),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = svc.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${svc.stateName} (${svc.subName}) · CPU ${BeszelFormatters.formatPercent(svc.cpu)} · 内存 ${BeszelFormatters.formatBytes(svc.memoryBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = HugeIcons.ComputerTerminal01,
                            contentDescription = "查看日志",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartDisksTab(
    devices: List<BeszelSmartDevice>,
    isRefreshing: Boolean,
    onRefreshSmart: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "smart_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S.M.A.R.T. 磁盘健康 (${devices.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onRefreshSmart,
                    enabled = !isRefreshing,
                    shape = CircleShape,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = HugeIcons.Refresh01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("检测刷新")
                }
            }
        }

        if (devices.isEmpty()) {
            item(key = "empty_smart") {
                EmptyTabState(
                    icon = HugeIcons.Database02,
                    title = "暂无 S.M.A.R.T. 设备",
                    subtitle = "Agent 未上报磁盘健康数据",
                )
            }
        } else {
            items(devices, key = { it.id }) { dev ->
                val healthColor = when {
                    dev.failed -> MaterialTheme.colorScheme.error
                    dev.passed -> Color(0xFF16A34A)
                    else -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.model.ifBlank { dev.name },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${dev.name} · ${dev.type.uppercase()} · ${BeszelFormatters.formatBytes(dev.capacityBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = healthColor.copy(alpha = 0.14f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (dev.failed) HugeIcons.Alert01 else HugeIcons.CheckmarkCircle02,
                                        contentDescription = null,
                                        tint = healthColor,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = dev.state.ifBlank { "UNKNOWN" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = healthColor,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            dev.tempCelsius?.let { t ->
                                Text(
                                    text = "温度 ${BeszelFormatters.formatTemp(t)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (dev.hours > 0) {
                                Text(
                                    text = "通电 ${dev.hours} 小时",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (dev.cycles > 0) {
                                Text(
                                    text = "通断电 ${dev.cycles} 次",
                                    style = MaterialTheme.typography.labelMedium,
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
private fun EmptyTabState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InspectorBottomSheet(
    sheet: BeszelInspectorSheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sheet.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sheet.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!sheet.isLoading && sheet.content.isNotBlank()) {
                    IconButton(
                        onClick = {
                            context.copyText(sheet.content)
                            toaster.show("已复制到剪贴板", ToastType.SUCCESS)
                        },
                    ) {
                        Icon(
                            imageVector = HugeIcons.Copy01,
                            contentDescription = "复制",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (sheet.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else if (!sheet.error.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = sheet.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 420.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = sheet.content,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}
