package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.beszel.BeszelAlertEvent
import me.rerere.rikkahub.data.beszel.BeszelFormatter
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelPage(
    vm: BeszelVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val config by vm.config.collectAsStateWithLifecycle()
    val systems by vm.systems.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var showAlertsSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val isConfigured = config?.isConfigured == true

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
                            text = "服务器状况",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isConfigured) {
                            Text(
                                text = config?.serverUrl.orEmpty()
                                    .removePrefix("https://")
                                    .removePrefix("http://"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (isConfigured) {
                        Surface(
                            onClick = vm::toggleAutoRefresh,
                            shape = CircleShape,
                            color = if (uiState.autoRefresh) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (uiState.autoRefresh) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        ),
                                )
                                Text(
                                    text = if (uiState.autoRefresh) "实时" else "暂停",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.autoRefresh) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                vm.loadAlerts()
                                showAlertsSheet = true
                            },
                        ) {
                            Icon(
                                imageVector = HugeIcons.Notification01,
                                contentDescription = "告警历史",
                            )
                        }

                        IconButton(
                            onClick = { vm.refreshSystems() },
                            enabled = !uiState.isRefreshing,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Refresh01,
                                contentDescription = "刷新",
                            )
                        }

                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = HugeIcons.Settings03,
                                contentDescription = "切换服务器",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!isConfigured) {
            BeszelLoginContent(
                uiState = uiState,
                onUrlChange = vm::updateServerUrl,
                onIdentityChange = vm::updateIdentity,
                onPasswordChange = vm::updatePassword,
                onInsecureTlsChange = vm::updateAllowInsecureTls,
                onConnect = vm::connect,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            BeszelDashboardContent(
                systems = systems,
                uiState = uiState,
                onSearchChange = vm::updateSearchQuery,
                onFilterChange = vm::updateStatusFilter,
                onRefresh = { vm.refreshSystems() },
                onSystemClick = { sys ->
                    navController.navigate(Screen.BeszelDetail(systemId = sys.id))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("切换或断开服务器") },
            text = { Text("确定要断开当前 Beszel 服务器连接并重新配置链接与密码吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout()
                    },
                ) {
                    Text("断开连接")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showAlertsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlertsSheet = false },
        ) {
            BeszelAlertsSheetContent(
                alerts = uiState.alerts,
                isLoading = uiState.isLoadingAlerts,
            )
        }
    }
}

@Composable
private fun BeszelLoginContent(
    uiState: BeszelUiState,
    onUrlChange: (String) -> Unit,
    onIdentityChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onInsecureTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = HugeIcons.DashboardSpeed02,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Beszel 监控面板",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "输入您的 Beszel Hub 地址与账号密码，实时掌握服务器 CPU、内存、磁盘、网络与 Docker 容器状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        AnimatedVisibility(visible = uiState.error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Alert01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = uiState.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        CardGroup(
            title = { Text("服务器连接配置") },
        ) {
            formItem(
                headlineContent = { Text("服务器地址") },
                supportingContent = { Text("支持 http:// 或 https://，默认端口通常为 8090") },
            ) {
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text("https://beszel.example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.Link01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            formItem(
                headlineContent = { Text("账号 / 邮箱") },
                supportingContent = { Text("Beszel Hub 登录邮箱或用户名") },
            ) {
                OutlinedTextField(
                    value = uiState.identity,
                    onValueChange = onIdentityChange,
                    placeholder = { Text("admin@example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            formItem(
                headlineContent = { Text("密码") },
            ) {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    placeholder = { Text("输入登录密码") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onConnect() },
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = HugeIcons.View,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            item(
                onClick = { onInsecureTlsChange(!uiState.allowInsecureTls) },
                supportingContent = { Text("内网使用自签名 HTTPS 证书时可开启") },
                trailingContent = {
                    Switch(
                        checked = uiState.allowInsecureTls,
                        onCheckedChange = onInsecureTlsChange,
                    )
                },
            ) {
                Text("忽略 SSL 证书校验")
            }
        }

        Button(
            onClick = onConnect,
            enabled = !uiState.isConnecting,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (uiState.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("正在连接…")
            } else {
                Text(
                    text = "连接并查看数据",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeszelDashboardContent(
    systems: List<BeszelSystem>,
    uiState: BeszelUiState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (BeszelStatusFilter) -> Unit,
    onRefresh: () -> Unit,
    onSystemClick: (BeszelSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredSystems = remember(systems, uiState.searchQuery, uiState.statusFilter) {
        systems.filter { sys ->
            val matchesQuery = uiState.searchQuery.isBlank() ||
                sys.name.contains(uiState.searchQuery, ignoreCase = true) ||
                sys.host.contains(uiState.searchQuery, ignoreCase = true)
            val matchesStatus = when (uiState.statusFilter) {
                BeszelStatusFilter.ALL -> true
                BeszelStatusFilter.UP -> sys.isUp
                BeszelStatusFilter.DOWN -> !sys.isUp
            }
            matchesQuery && matchesStatus
        }
    }

    val upSystems = remember(systems) { systems.filter { it.isUp } }
    val avgCpu = remember(upSystems) {
        if (upSystems.isEmpty()) 0.0 else upSystems.sumOf { it.cpuPercent } / upSystems.size
    }
    val avgMem = remember(upSystems) {
        if (upSystems.isEmpty()) 0.0 else upSystems.sumOf { it.memoryPercent } / upSystems.size
    }
    val totalBandwidth = remember(upSystems) {
        upSystems.sumOf { it.bandwidthBytesPerSec }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Error banner if refresh failed
            if (uiState.error != null) {
                item(key = "error_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Alert01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Summary Overview Strip
            item(key = "overview_strip") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatTile(
                            label = "在线节点",
                            value = "${upSystems.size} / ${systems.size}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatTile(
                            label = "总网络吞吐",
                            value = BeszelFormatter.formatBytesPerSec(totalBandwidth),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatTile(
                            label = "平均 CPU",
                            value = BeszelFormatter.formatPercent(avgCpu),
                            containerColor = CustomColors.listItemColors.containerColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatTile(
                            label = "平均内存",
                            value = BeszelFormatter.formatPercent(avgMem),
                            containerColor = CustomColors.listItemColors.containerColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Filter chips + Search
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (systems.size > 3) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("搜索服务器名称或 Host") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Search01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(
                                            imageVector = HugeIcons.Cancel01,
                                            contentDescription = "清除",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = uiState.statusFilter == BeszelStatusFilter.ALL,
                            onClick = { onFilterChange(BeszelStatusFilter.ALL) },
                            label = { Text("全部 (${systems.size})") },
                        )
                        FilterChip(
                            selected = uiState.statusFilter == BeszelStatusFilter.UP,
                            onClick = { onFilterChange(BeszelStatusFilter.UP) },
                            label = { Text("在线 (${upSystems.size})") },
                        )
                        val downCount = systems.size - upSystems.size
                        FilterChip(
                            selected = uiState.statusFilter == BeszelStatusFilter.DOWN,
                            onClick = { onFilterChange(BeszelStatusFilter.DOWN) },
                            label = { Text("离线/异常 ($downCount)") },
                        )
                    }
                }
            }

            if (filteredSystems.isEmpty()) {
                item(key = "empty_systems") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = CustomColors.listItemColors.containerColor,
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (systems.isEmpty()) "暂无服务器节点" else "没有匹配的服务器",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (systems.isEmpty()) {
                                    "请先在 Beszel Hub 网页端添加 Agent 节点"
                                } else {
                                    "尝试调整搜索关键词或状态筛选"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredSystems,
                    key = { it.id },
                ) { system ->
                    BeszelSystemCard(
                        system = system,
                        onClick = { onSystemClick(system) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BeszelSystemCard(
    system: BeszelSystem,
    onClick: () -> Unit,
) {
    val statusColor = when {
        system.isUp -> Color(0xFF22C55E)
        system.isDown -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusText = when (system.status) {
        "up" -> "在线"
        "down" -> "离线"
        "paused" -> "已暂停"
        "pending" -> "等待中"
        else -> system.status
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = system.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append(system.host)
                                if (system.port.isNotBlank()) append(":${system.port}")
                                append(" · ")
                                append(system.os.displayName)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    system.temperature?.let { temp ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.0f°C", temp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }

                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Metrics grid (CPU, Memory, Disk)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricMiniColumn(
                    label = "CPU",
                    valueText = BeszelFormatter.formatPercent(system.cpuPercent),
                    percent = system.cpuPercent,
                    modifier = Modifier.weight(1f),
                )
                MetricMiniColumn(
                    label = "内存",
                    valueText = BeszelFormatter.formatPercent(system.memoryPercent),
                    percent = system.memoryPercent,
                    modifier = Modifier.weight(1f),
                )
                MetricMiniColumn(
                    label = "磁盘",
                    valueText = BeszelFormatter.formatPercent(system.diskPercent),
                    percent = system.diskPercent,
                    modifier = Modifier.weight(1f),
                )
            }

            // Footer strip: Network, Load Avg, Uptime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "网络 ${BeszelFormatter.formatBytesPerSec(system.bandwidthBytesPerSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (system.loadAvg.isNotEmpty()) {
                    Text(
                        text = "负载 ${system.loadAvg.joinToString(" / ") { String.format(Locale.US, "%.2f", it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "运行 ${BeszelFormatter.formatUptime(system.uptimeSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricMiniColumn(
    label: String,
    valueText: String,
    percent: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        BeszelExpressiveBar(
            progress = (percent / 100.0).toFloat(),
            color = usageSemanticColor(percent),
        )
    }
}

@Composable
private fun BeszelAlertsSheetContent(
    alerts: List<BeszelAlertEvent>,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "告警历史",
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.Bold,
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无告警记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(alerts, key = { it.id }) { alert ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = if (alert.isResolved) HugeIcons.Tick01 else HugeIcons.Alert01,
                                contentDescription = null,
                                tint = if (alert.isResolved) {
                                    Color(0xFF22C55E)
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${alert.systemName} · ${alert.name.ifBlank { alert.monitorName }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "触发值: ${String.format(Locale.US, "%.1f", alert.value)} · ${alert.created.take(19)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
