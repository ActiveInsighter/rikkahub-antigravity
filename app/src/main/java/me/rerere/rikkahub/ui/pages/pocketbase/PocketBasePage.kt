package me.rerere.rikkahub.ui.pages.pocketbase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.DatabaseRestore
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.FileZip
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.pocketbase.PbBackup
import me.rerere.rikkahub.data.pocketbase.PbCollection
import me.rerere.rikkahub.data.pocketbase.PbCron
import me.rerere.rikkahub.data.pocketbase.PbLogEntry
import me.rerere.rikkahub.data.pocketbase.PocketBaseAuthMode
import me.rerere.rikkahub.data.pocketbase.PocketBaseConnection
import me.rerere.rikkahub.data.pocketbase.PocketBaseSession
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalExtendColors
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.androidx.compose.koinViewModel

@Composable
fun PocketBasePage(vm: PocketBaseVM = koinViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showConnectionSheet by remember { mutableStateOf(false) }
    var showAddConnectionMode by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<PbLogEntry?>(null) }

    LaunchedEffect(state.errorMessage, state.actionMessage) {
        val msg = state.actionMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessages()
        }
    }

    val session = state.session
    if (session == null || showAddConnectionMode) {
        PocketBaseConnectScreen(
            state = state,
            canCancelToDashboard = session != null,
            onCancel = { showAddConnectionMode = false },
            onLoginPassword = { url, name, id, pass ->
                vm.loginWithPassword(url, name, id, pass)
                showAddConnectionMode = false
            },
            onConnectToken = { url, name, token ->
                vm.connectWithToken(url, name, token)
                showAddConnectionMode = false
            },
            onSwitchConnection = { connId ->
                vm.switchConnection(connId)
                showAddConnectionMode = false
            },
            onDeleteConnection = vm::deleteConnection,
        )
    } else {
        PocketBaseConsoleScaffold(
            session = session,
            state = state,
            snackbarHostState = snackbarHostState,
            onSelectTab = vm::selectTab,
            onRefresh = vm::refreshAll,
            onOpenConnections = { showConnectionSheet = true },
            onCollectionClick = { col ->
                navController.navigate(Screen.PocketBaseCollection(col.name))
            },
            onCollectionSearchChange = vm::setCollectionSearch,
            onCollectionFilterChange = vm::setCollectionFilter,
            onToggleSystemCollections = vm::toggleShowSystemCollections,
            onLogPresetChange = vm::setLogPreset,
            onCustomLogFilterChange = vm::setCustomLogFilter,
            onApplyLogSearch = vm::applyLogSearch,
            onLogsPageChange = vm::loadLogsPage,
            onLogClick = { selectedLog = it },
            onSqlQueryChange = vm::setSqlQuery,
            onRunSql = vm::runSql,
            onCreateBackup = vm::createBackup,
            onDeleteBackup = vm::deleteBackup,
            onRunCron = vm::runCron,
            onReloadOps = { vm.loadOps() },
        )
    }

    if (showConnectionSheet && session != null) {
        PocketBaseConnectionSheet(
            activeSession = session,
            connections = state.connections,
            autoRefreshEnabled = state.autoRefreshEnabled,
            realtimeEnabled = state.realtimeEnabled,
            onToggleAutoRefresh = vm::toggleAutoRefresh,
            onToggleRealtime = vm::toggleRealtime,
            onSwitchConnection = { id ->
                vm.switchConnection(id)
                showConnectionSheet = false
            },
            onDeleteConnection = vm::deleteConnection,
            onAddConnection = {
                showConnectionSheet = false
                showAddConnectionMode = true
            },
            onDisconnect = {
                showConnectionSheet = false
                vm.disconnect()
            },
            onDismiss = { showConnectionSheet = false },
        )
    }

    selectedLog?.let { log ->
        PocketBaseLogDetailSheet(
            log = log,
            onDismiss = { selectedLog = null },
        )
    }
}

@Composable
private fun PocketBaseConnectScreen(
    state: PocketBaseUiState,
    canCancelToDashboard: Boolean,
    onCancel: () -> Unit,
    onLoginPassword: (url: String, name: String, identity: String, password: String) -> Unit,
    onConnectToken: (url: String, name: String, token: String) -> Unit,
    onSwitchConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
) {
    var authMode by rememberSaveable { mutableStateOf(PocketBaseAuthMode.SUPERUSER_PASSWORD) }
    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_POCKETBASE_URL) }
    var connectionName by rememberSaveable { mutableStateOf("主 PocketBase") }
    var identity by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var apiToken by rememberSaveable { mutableStateOf("") }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (canCancelToDashboard) {
                        IconButton(onClick = onCancel) {
                            Icon(HugeIcons.Cancel01, contentDescription = "取消")
                        }
                    } else {
                        BackButton()
                    }
                },
                title = { Text("PocketBase 控制台") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Banner
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Database02,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "连接 PocketBase 实例",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "原生管理表结构、数据记录、实时 SSE、系统日志、SQL 控制台与备份任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Saved instances
            if (state.connections.isNotEmpty()) {
                CardGroup(
                    title = { Text("已保存的连接") },
                ) {
                    state.connections.forEach { conn ->
                        item(
                            onClick = { onSwitchConnection(conn.id) },
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.CloudServer,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${conn.baseUrl} · ${conn.identity}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDeleteConnection(conn.id) }) {
                                    Icon(
                                        imageVector = HugeIcons.Delete02,
                                        contentDescription = "删除连接",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        ) {
                            Text(conn.name)
                        }
                    }
                }
            }

            // Auth Mode Switcher
            ConnectedChoiceButtons(
                options = listOf(PocketBaseAuthMode.SUPERUSER_PASSWORD, PocketBaseAuthMode.API_TOKEN),
                selected = authMode,
                onSelect = { authMode = it },
                modifier = Modifier.fillMaxWidth(),
            ) { option, _ ->
                Text(
                    text = when (option) {
                        PocketBaseAuthMode.SUPERUSER_PASSWORD -> "超级管理员登录"
                        PocketBaseAuthMode.API_TOKEN -> "API Token 直连"
                    },
                    maxLines = 1,
                )
            }

            // Form Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = connectionName,
                        onValueChange = { connectionName = it },
                        label = { Text("连接名称") },
                        placeholder = { Text("例如：pb.any1.tech 生产库") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("PocketBase 服务地址") },
                        placeholder = { Text("https://pb.any1.tech") },
                        leadingIcon = { Icon(HugeIcons.Link01, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (authMode == PocketBaseAuthMode.SUPERUSER_PASSWORD) {
                        OutlinedTextField(
                            value = identity,
                            onValueChange = { identity = it },
                            label = { Text("超级管理员邮箱") },
                            placeholder = { Text("admin@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("超级管理员密码") },
                            leadingIcon = { Icon(HugeIcons.Key01, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null,
                                    )
                                }
                            },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onLoginPassword(baseUrl, connectionName, identity, password) },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = { apiToken = it },
                            label = { Text("超级管理员 Token") },
                            placeholder = { Text("eyJhbGciOiJIUzI1NiIs...") },
                            leadingIcon = { Icon(HugeIcons.Key01, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null,
                                    )
                                }
                            },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnimatedVisibility(visible = state.authError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Alert01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = state.authError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (authMode == PocketBaseAuthMode.SUPERUSER_PASSWORD) {
                                onLoginPassword(baseUrl, connectionName, identity, password)
                            } else {
                                onConnectToken(baseUrl, connectionName, apiToken)
                            }
                        },
                        enabled = !state.isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (state.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (state.isConnecting) "正在连接..." else "连接并保存")
                    }

                    Text(
                        text = "凭据通过 Android Keystore 加密存储于本机，不会上传或写入明文仓库。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PocketBaseConsoleScaffold(
    session: PocketBaseSession,
    state: PocketBaseUiState,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (PocketBaseTab) -> Unit,
    onRefresh: () -> Unit,
    onOpenConnections: () -> Unit,
    onCollectionClick: (PbCollection) -> Unit,
    onCollectionSearchChange: (String) -> Unit,
    onCollectionFilterChange: (CollectionFilter) -> Unit,
    onToggleSystemCollections: () -> Unit,
    onLogPresetChange: (LogFilterPreset) -> Unit,
    onCustomLogFilterChange: (String) -> Unit,
    onApplyLogSearch: () -> Unit,
    onLogsPageChange: (Int) -> Unit,
    onLogClick: (PbLogEntry) -> Unit,
    onSqlQueryChange: (String) -> Unit,
    onRunSql: () -> Unit,
    onCreateBackup: (String?) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onRunCron: (String) -> Unit,
    onReloadOps: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val extendColors = LocalExtendColors.current
    val isHealthy = state.health?.code == 200

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = session.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isHealthy) extendColors.green6 else MaterialTheme.colorScheme.outline),
                            )
                        }
                        Text(
                            text = session.baseUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onRefresh) {
                        Icon(HugeIcons.Refresh01, contentDescription = "刷新")
                    }
                    IconButton(onClick = onOpenConnections) {
                        Icon(HugeIcons.Settings02, contentDescription = "连接与设置")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                PocketBaseTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        PocketBaseTab.OVERVIEW -> HugeIcons.ChartLineData01
                        PocketBaseTab.COLLECTIONS -> HugeIcons.Database02
                        PocketBaseTab.LOGS -> HugeIcons.FileView
                        PocketBaseTab.SQL -> HugeIcons.Code
                        PocketBaseTab.OPS -> HugeIcons.ServerStack01
                    }
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(icon, contentDescription = tab.label, modifier = Modifier.size(20.dp)) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
            when (state.selectedTab) {
                PocketBaseTab.OVERVIEW -> OverviewTab(
                    state = state,
                    onOpenCollections = { onSelectTab(PocketBaseTab.COLLECTIONS) },
                    onOpenLogs = { onSelectTab(PocketBaseTab.LOGS) },
                    onOpenSql = { onSelectTab(PocketBaseTab.SQL) },
                    onOpenOps = { onSelectTab(PocketBaseTab.OPS) },
                    onCollectionClick = onCollectionClick,
                )
                PocketBaseTab.COLLECTIONS -> CollectionsTab(
                    state = state,
                    onSearchChange = onCollectionSearchChange,
                    onFilterChange = onCollectionFilterChange,
                    onToggleSystem = onToggleSystemCollections,
                    onCollectionClick = onCollectionClick,
                )
                PocketBaseTab.LOGS -> LogsTab(
                    state = state,
                    onPresetChange = onLogPresetChange,
                    onCustomFilterChange = onCustomLogFilterChange,
                    onApplySearch = onApplyLogSearch,
                    onPageChange = onLogsPageChange,
                    onLogClick = onLogClick,
                )
                PocketBaseTab.SQL -> SqlConsoleTab(
                    state = state,
                    onQueryChange = onSqlQueryChange,
                    onRunSql = onRunSql,
                )
                PocketBaseTab.OPS -> OpsTab(
                    state = state,
                    onCreateBackup = onCreateBackup,
                    onDeleteBackup = onDeleteBackup,
                    onRunCron = onRunCron,
                    onReload = onReloadOps,
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    state: PocketBaseUiState,
    onOpenCollections: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSql: () -> Unit,
    onOpenOps: () -> Unit,
    onCollectionClick: (PbCollection) -> Unit,
) {
    val extendColors = LocalExtendColors.current
    val nonSystemCollections = remember(state.collections) {
        state.collections.filterNot { it.system || it.name.startsWith("_") }
    }
    val totalRequests24h = remember(state.logStats) { state.logStats.sumOf { it.total } }
    val totalErrors24h = remember(state.errorStats) { state.errorStats.sumOf { it.total } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Health Hero Card
        item {
            val health = state.health
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (health?.code == 200) extendColors.green2 else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = HugeIcons.CheckmarkCircle02,
                                        contentDescription = null,
                                        tint = if (health?.code == 200) extendColors.green7 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (health?.code == 200) "服务运行正常" else "等待健康检查",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = health?.message ?: "GET /api/health",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        PbBadgePill(
                            text = if (health?.canBackup == true) "可备份" else "在线",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            label = "业务表",
                            value = "${nonSystemCollections.size}",
                            sub = "共 ${state.collections.size} 张表",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenCollections,
                        )
                        MetricTile(
                            label = "近期请求",
                            value = "$totalRequests24h",
                            sub = "错误 $totalErrors24h",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLogs,
                        )
                        MetricTile(
                            label = "备份 / Cron",
                            value = "${state.backups.size} / ${state.crons.size}",
                            sub = "运维中心",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenOps,
                        )
                    }
                }
            }
        }

        // Request Volume & Error Chart
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "请求量与错误趋势",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onOpenLogs) {
                            Text("查看日志")
                        }
                    }
                    PbLogStatsChart(
                        stats = state.logStats,
                        errorStats = state.errorStats,
                    )
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenSql,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(HugeIcons.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SQL 控制台")
                }
                FilledTonalButton(
                    onClick = onOpenOps,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(HugeIcons.DatabaseRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("备份与任务")
                }
            }
        }

        // Quick Collections Preview
        if (nonSystemCollections.isNotEmpty()) {
            item {
                CardGroup(
                    title = { Text("常用业务数据表") },
                ) {
                    nonSystemCollections.take(6).forEach { col ->
                        item(
                            onClick = { onCollectionClick(col) },
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.Database02,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            },
                            supportingContent = {
                                Text("${col.type} · ${col.fields.size} 个字段")
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = HugeIcons.ArrowRight01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        ) {
                            Text(col.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionsTab(
    state: PocketBaseUiState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (CollectionFilter) -> Unit,
    onToggleSystem: () -> Unit,
    onCollectionClick: (PbCollection) -> Unit,
) {
    val filtered = remember(
        state.collections,
        state.collectionSearch,
        state.collectionFilter,
        state.showSystemCollections,
    ) {
        state.collections.filter { col ->
            val isSys = col.system || col.name.startsWith("_")
            if (!state.showSystemCollections && isSys) return@filter false
            val matchesType = when (state.collectionFilter) {
                CollectionFilter.ALL -> true
                CollectionFilter.BASE -> col.type == "base"
                CollectionFilter.AUTH -> col.type == "auth"
                CollectionFilter.VIEW -> col.type == "view"
            }
            val q = state.collectionSearch.trim()
            val matchesQuery = q.isEmpty() || col.name.contains(q, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.collectionSearch,
                onValueChange = onSearchChange,
                placeholder = { Text("搜索数据表名称...") },
                leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                trailingIcon = {
                    if (state.collectionSearch.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(HugeIcons.Cancel01, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CollectionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.collectionFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                    )
                }
                FilterChip(
                    selected = state.showSystemCollections,
                    onClick = onToggleSystem,
                    label = { Text("含系统表") },
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "没有匹配的数据表",
                    subtitle = "尝试清空搜索词或开启「含系统表」",
                )
            }
        } else {
            items(filtered, key = { it.id.ifEmpty { it.name } }) { col ->
                CollectionCard(
                    collection = col,
                    onClick = { onCollectionClick(col) },
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: PbCollection,
    onClick: () -> Unit,
) {
    val typeContainer = when (collection.type) {
        "auth" -> MaterialTheme.colorScheme.tertiaryContainer
        "view" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val typeContent = when (collection.type) {
        "auth" -> MaterialTheme.colorScheme.onTertiaryContainer
        "view" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = typeContainer,
                        contentColor = typeContent,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(HugeIcons.Database02, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(
                            text = collection.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${collection.fields.size} 字段 · ${collection.indexes.size} 索引",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PbBadgePill(
                        text = collection.type.uppercase(),
                        containerColor = typeContainer,
                        contentColor = typeContent,
                    )
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Preview first few field names
            val previewFields = collection.fields.take(5).joinToString(" · ") { "${it.name}:${it.type}" }
            if (previewFields.isNotEmpty()) {
                Text(
                    text = previewFields,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetbrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogsTab(
    state: PocketBaseUiState,
    onPresetChange: (LogFilterPreset) -> Unit,
    onCustomFilterChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onPageChange: (Int) -> Unit,
    onLogClick: (PbLogEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ConnectedChoiceButtons(
                options = LogFilterPreset.entries,
                selected = state.logPreset,
                onSelect = onPresetChange,
                modifier = Modifier.fillMaxWidth(),
            ) { option, _ ->
                Text(option.label, maxLines = 1)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.customLogFilter,
                    onValueChange = onCustomFilterChange,
                    placeholder = { Text("PB 筛选表达式，如 data.method=\"POST\"") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onApplySearch() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onApplySearch,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("筛选")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    PbLogStatsChart(stats = state.logStats, errorStats = state.errorStats)
                }
            }
        }

        val logsPage = state.logsPage
        if (state.isLoadingLogs) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (logsPage == null || logsPage.items.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "暂无日志记录",
                    subtitle = "当前筛选条件下没有 PocketBase 日志",
                )
            }
        } else {
            item {
                PaginationBar(
                    page = logsPage.page,
                    totalPages = logsPage.totalPages,
                    totalItems = logsPage.totalItems,
                    onPrev = { onPageChange(logsPage.page - 1) },
                    onNext = { onPageChange(logsPage.page + 1) },
                )
            }
            items(logsPage.items, key = { it.id }) { log ->
                LogEntryCard(log = log, onClick = { onLogClick(log) })
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    log: PbLogEntry,
    onClick: () -> Unit,
) {
    val extendColors = LocalExtendColors.current
    val status = log.status
    val statusContainer = when {
        status == null -> MaterialTheme.colorScheme.surfaceContainerHighest
        status >= 500 -> MaterialTheme.colorScheme.errorContainer
        status >= 400 -> extendColors.orange2
        else -> extendColors.green2
    }
    val statusContent = when {
        status == null -> MaterialTheme.colorScheme.onSurfaceVariant
        status >= 500 -> MaterialTheme.colorScheme.onErrorContainer
        status >= 400 -> extendColors.orange7
        else -> extendColors.green7
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (log.method.isNotBlank()) {
                        PbBadgePill(
                            text = log.method,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (status != null) {
                        PbBadgePill(
                            text = "$status",
                            containerColor = statusContainer,
                            contentColor = statusContent,
                        )
                    } else {
                        PbBadgePill(
                            text = "LVL ${log.level}",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    log.execTimeMs?.let { ms ->
                        Text(
                            text = String.format("%.1f ms", ms),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = formatPbTimestamp(log.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = log.url.ifBlank { log.message },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (log.url.isNotBlank()) JetbrainsMono else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (log.url.isNotBlank() && log.message.isNotBlank() && log.message != log.url) {
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SqlConsoleTab(
    state: PocketBaseUiState,
    onQueryChange: (String) -> Unit,
    onRunSql: () -> Unit,
) {
    var showConfirmWriteDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val templates = remember {
        listOf(
            "所有表" to "SELECT name, type FROM sqlite_master WHERE type IN ('table', 'view') ORDER BY name;",
            "表记录数" to "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '_%' AND name NOT LIKE 'sqlite_%';",
            "最近日志" to "SELECT created, level, message FROM _logs ORDER BY created DESC LIMIT 20;",
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.Code, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = "PocketBase v0.39+ 原生 SQL 控制台 (POST /api/sql)。非 SELECT 语句会弹窗二次确认。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                templates.forEach { (label, sql) ->
                    FilterChip(
                        selected = state.sqlQuery.trim() == sql,
                        onClick = { onQueryChange(sql) },
                        label = { Text(label) },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.sqlQuery,
                onValueChange = onQueryChange,
                label = { Text("SQLite 查询语句") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetbrainsMono),
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(
                onClick = {
                    val trimmed = state.sqlQuery.trim().uppercase()
                    val isReadOnly = trimmed.startsWith("SELECT") ||
                        trimmed.startsWith("EXPLAIN") ||
                        trimmed.startsWith("WITH")
                    if (isReadOnly) {
                        onRunSql()
                    } else {
                        showConfirmWriteDialog = true
                    }
                },
                enabled = !state.isRunningSql && state.sqlQuery.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.isRunningSql) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isRunningSql) "执行中..." else "执行 SQL")
            }
        }

        state.sqlError?.let { err ->
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        state.sqlResult?.let { res ->
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val meta = buildString {
                                append("${res.rows.size} 行")
                                res.rowsAffected?.let { append(" · 影响 $it 行") }
                                res.execTimeMs?.let { append(" · ${String.format("%.2f", it)} ms") }
                            }
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(JsonInstantPretty.encodeToString(res.rawJson)))
                                },
                            ) {
                                Icon(HugeIcons.Copy01, contentDescription = "复制 JSON", modifier = Modifier.size(18.dp))
                            }
                        }

                        if (res.columns.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Header row
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    res.columns.forEach { col ->
                                        Text(
                                            text = col,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetbrainsMono,
                                            modifier = Modifier.width(140.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                // Data rows
                                res.rows.take(100).forEach { row ->
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        row.forEach { cell ->
                                            val text = when (cell) {
                                                is JsonNull -> "NULL"
                                                is JsonPrimitive -> cell.contentOrNull ?: cell.toString()
                                                else -> cell.toString()
                                            }
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = JetbrainsMono,
                                                modifier = Modifier.width(140.dp),
                                                maxLines = 2,
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
        }
    }

    if (showConfirmWriteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmWriteDialog = false },
            title = { Text("确认执行非查询语句？") },
            text = { Text("当前 SQL 语句可能会修改数据库结构或数据，请确认无误后继续。") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmWriteDialog = false
                        onRunSql()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("确认执行")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmWriteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun OpsTab(
    state: PocketBaseUiState,
    onCreateBackup: (String?) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onRunCron: (String) -> Unit,
    onReload: () -> Unit,
) {
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var backupToDelete by remember { mutableStateOf<PbBackup?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Backups section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "数据库备份 (${state.backups.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReload) {
                        Text("刷新")
                    }
                    Button(onClick = { showCreateBackupDialog = true }) {
                        Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建备份")
                    }
                }
            }
        }

        if (state.backups.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "暂无备份文件",
                    subtitle = "点击右上角「新建备份」创建 PocketBase ZIP 快照",
                )
            }
        } else {
            items(state.backups, key = { it.key }) { backup ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            CardGroupIcon(
                                icon = HugeIcons.FileZip,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column {
                                Text(
                                    text = backup.key,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${formatBytes(backup.size)} · ${formatPbTimestamp(backup.modified)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { backupToDelete = backup }) {
                            Icon(
                                imageVector = HugeIcons.Delete02,
                                contentDescription = "删除备份",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // Crons section
        item {
            Text(
                text = "定时任务 (${state.crons.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.crons.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "暂无定时任务",
                    subtitle = "服务端未注册 Cron 任务",
                )
            }
        } else {
            items(state.crons, key = { it.id }) { cron ->
                CronCard(cron = cron, onRun = { onRunCron(cron.id) })
            }
        }

        // Server Settings JSON Tree
        state.serverSettings?.let { settings ->
            item {
                Text(
                    text = "服务器配置 (/api/settings)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        JsonTree(json = settings, initialExpandLevel = 1)
                    }
                }
            }
        }
    }

    if (showCreateBackupDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBackupDialog = false },
            title = { Text("创建数据库备份") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("备份文件名（可选）") },
                    placeholder = { Text("留空则由 PocketBase 自动命名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateBackupDialog = false
                        onCreateBackup(nameInput.trim().ifEmpty { null })
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBackupDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    backupToDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("删除备份？") },
            text = { Text("确定删除备份文件 ${backup.key} 吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        backupToDelete = null
                        onDeleteBackup(backup.key)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CronCard(
    cron: PbCron,
    onRun: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                CardGroupIcon(
                    icon = HugeIcons.Clock01,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column {
                    Text(
                        text = cron.id,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = cron.expression,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetbrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(onClick = onRun) {
                Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("触发")
            }
        }
    }
}

@Composable
private fun PocketBaseLogDetailSheet(
    log: PbLogEntry,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "日志详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(JsonInstantPretty.encodeToString(log.rawJson)))
                    },
                ) {
                    Icon(HugeIcons.Copy01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制 JSON")
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DetailRow("ID", log.id)
                        DetailRow("时间", formatPbTimestamp(log.created))
                        DetailRow("级别", "${log.level}")
                        if (log.method.isNotBlank()) DetailRow("方法", log.method)
                        if (log.url.isNotBlank()) DetailRow("路径", log.url)
                        log.status?.let { DetailRow("状态码", "$it") }
                        log.execTimeMs?.let { DetailRow("耗时", String.format("%.2f ms", it)) }
                        if (log.remoteIP.isNotBlank()) DetailRow("IP", log.remoteIP)
                        if (log.auth.isNotBlank()) DetailRow("认证", log.auth)
                        if (log.userAgent.isNotBlank()) DetailRow("User-Agent", log.userAgent)
                        if (log.message.isNotBlank()) DetailRow("消息", log.message)
                    }
                }
            }

            Text(
                text = "原始 JSON",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    JsonTree(json = log.rawJson, initialExpandLevel = 2)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetbrainsMono,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PocketBaseConnectionSheet(
    activeSession: PocketBaseSession,
    connections: List<PocketBaseConnection>,
    autoRefreshEnabled: Boolean,
    realtimeEnabled: Boolean,
    onToggleAutoRefresh: () -> Unit,
    onToggleRealtime: () -> Unit,
    onSwitchConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onAddConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "连接与控制台设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动刷新 (15s)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("定时拉取概览、日志与表列表", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoRefreshEnabled, onCheckedChange = { onToggleAutoRefresh() })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("实时 SSE 订阅", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("进入数据表时自动订阅 /api/realtime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = realtimeEnabled, onCheckedChange = { onToggleRealtime() })
                    }
                }
            }

            CardGroup(
                title = { Text("PocketBase 实例 (${connections.size})") },
            ) {
                connections.forEach { conn ->
                    val isCurrent = conn.id == activeSession.connectionId
                    item(
                        onClick = { if (!isCurrent) onSwitchConnection(conn.id) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.CloudServer,
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent = {
                            Text("${conn.baseUrl}${if (isCurrent) " · 当前已连接" else ""}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteConnection(conn.id) }) {
                                Icon(
                                    imageVector = HugeIcons.Delete02,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    ) {
                        Text(conn.name)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onAddConnection,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增实例")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("断开当前连接")
                }
            }
        }
    }
}

@Composable
fun PaginationBar(
    page: Int,
    totalPages: Int,
    totalItems: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 $page / ${totalPages.coerceAtLeast(1)} 页 · 共 $totalItems 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(
                onClick = onPrev,
                enabled = page > 1,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "上一页", modifier = Modifier.size(16.dp))
            }
            FilledTonalIconButton(
                onClick = onNext,
                enabled = page < totalPages,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(HugeIcons.ArrowRight01, contentDescription = "下一页", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun EmptyPlaceholderCard(
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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
