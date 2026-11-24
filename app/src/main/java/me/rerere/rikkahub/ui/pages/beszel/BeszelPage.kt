package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import me.rerere.hugeicons.stroke.ArrowDataTransferVertical
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.beszel.BeszelFormatters
import me.rerere.rikkahub.data.beszel.BeszelSession
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun BeszelPage(vm: BeszelVM = koinViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    var showAlertsSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    val session = state.session
    if (session == null) {
        BeszelLoginScreen(
            state = state,
            onLogin = vm::login,
            onVerifyOtp = vm::verifyOtp,
            onCancelMfa = vm::cancelMfa,
        )
    } else {
        BeszelDashboardScreen(
            session = session,
            state = state,
            onRefresh = vm::refresh,
            onToggleAutoRefresh = vm::toggleAutoRefresh,
            onSearchChange = vm::setSearchQuery,
            onFilterChange = vm::setFilter,
            onOpenAlerts = {
                vm.loadAlerts()
                showAlertsSheet = true
            },
            onOpenAccount = { showAccountSheet = true },
            onSystemClick = { system ->
                navController.navigate(Screen.BeszelDetail(systemId = system.id))
            },
        )
    }

    if (showAlertsSheet && session != null) {
        BeszelAlertsSheet(
            state = state,
            onDeleteHistory = vm::deleteAlertHistory,
            onDismiss = { showAlertsSheet = false },
        )
    }

    if (showAccountSheet && session != null) {
        BeszelAccountSheet(
            session = session,
            hubVersion = state.hubInfo?.version,
            autoRefreshEnabled = state.autoRefreshEnabled,
            onToggleAutoRefresh = vm::toggleAutoRefresh,
            onLogout = {
                showAccountSheet = false
                vm.logout()
            },
            onDismiss = { showAccountSheet = false },
        )
    }
}

@Composable
private fun BeszelLoginScreen(
    state: BeszelUiState,
    onLogin: (baseUrl: String, identity: String, password: String) -> Unit,
    onVerifyOtp: (otpCode: String) -> Unit,
    onCancelMfa: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var baseUrl by rememberSaveable(state.initialBaseUrl) { mutableStateOf(state.initialBaseUrl) }
    var identity by rememberSaveable(state.initialIdentity) { mutableStateOf(state.initialIdentity) }
    var password by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("服务器状况") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.CloudServer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "连接 Beszel Hub",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "输入你的 Beszel 实例地址与账号密码，即可实时查看服务器资源、Docker 容器、Systemd 服务与 S.M.A.R.T. 状态。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !state.loginError.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Alert01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = state.loginError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            val mfa = state.mfaState
            if (mfa == null) {
                CardGroup(title = { Text("服务器与凭据") }) {
                    formItem(
                        leadingContent = { CardGroupIcon(HugeIcons.Link01) },
                        headlineContent = { Text("Beszel 链接") },
                        supportingContent = { Text("支持 HTTP / HTTPS 域名或 IP:端口") },
                    ) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("https://beszel.example.com") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
                    formItem(
                        leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                        headlineContent = { Text("账号 / 邮箱") },
                    ) {
                        OutlinedTextField(
                            value = identity,
                            onValueChange = { identity = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("admin@example.com") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
                    formItem(
                        leadingContent = { CardGroupIcon(HugeIcons.Key01) },
                        headlineContent = { Text("密码") },
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("输入登录密码") },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!state.isLoggingIn) {
                                        onLogin(baseUrl, identity, password)
                                    }
                                },
                            ),
                        )
                    }
                }

                Button(
                    onClick = { onLogin(baseUrl, identity, password) },
                    enabled = !state.isLoggingIn && baseUrl.isNotBlank() && identity.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = CircleShape,
                ) {
                    if (state.isLoggingIn) {
                        LoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在连接…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("登录并连接", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                CardGroup(title = { Text("二步验证 (OTP)") }) {
                    formItem(
                        leadingContent = { CardGroupIcon(HugeIcons.Key01) },
                        headlineContent = { Text("输入邮箱验证码") },
                        supportingContent = { Text("已向 ${mfa.identity} 发送一次性验证码") },
                    ) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("6 位验证码") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!state.isLoggingIn && otpCode.isNotBlank()) {
                                        onVerifyOtp(otpCode)
                                    }
                                },
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onCancelMfa,
                        enabled = !state.isLoggingIn,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = CircleShape,
                    ) {
                        Text("返回")
                    }
                    Button(
                        onClick = { onVerifyOtp(otpCode) },
                        enabled = !state.isLoggingIn && otpCode.isNotBlank(),
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        shape = CircleShape,
                    ) {
                        if (state.isLoggingIn) {
                            LoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("验证并登录", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeszelDashboardScreen(
    session: BeszelSession,
    state: BeszelUiState,
    onRefresh: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (BeszelSystemFilter) -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAccount: () -> Unit,
    onSystemClick: (BeszelSystem) -> Unit,
) {
    val filteredSystems = remember(state.systems, state.searchQuery, state.filter) {
        state.systems.filter { s ->
            val matchesFilter = when (state.filter) {
                BeszelSystemFilter.ALL -> true
                BeszelSystemFilter.UP -> s.isUp
                BeszelSystemFilter.DOWN -> !s.isUp
            }
            val q = state.searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                s.name.contains(q, ignoreCase = true) ||
                s.host.contains(q, ignoreCase = true) ||
                (s.hostname?.contains(q, ignoreCase = true) == true)
            matchesFilter && matchesQuery
        }
    }

    val onlineCount = remember(state.systems) { state.systems.count { it.isUp } }
    val offlineCount = remember(state.systems) { state.systems.count { !it.isUp } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "服务器状况",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = session.baseUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = onOpenAlerts) {
                        BadgedBox(
                            badge = {
                                if (state.activeAlertCount > 0) {
                                    Badge {
                                        Text(state.activeAlertCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = HugeIcons.Notification01,
                                contentDescription = "告警",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(onClick = onRefresh) {
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
                    FilledTonalIconButton(
                        onClick = onOpenAccount,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CustomColors.listItemColors.containerColor,
                        ),
                    ) {
                        Icon(
                            imageVector = HugeIcons.UserCircle,
                            contentDescription = "账号与连接",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (state.isLoadingSystems) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator()
                    Text(
                        text = "正在加载服务器列表…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Summary Strip
                item(key = "summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatChip(
                            label = "节点总数",
                            value = state.systems.size.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatChip(
                            label = "在线",
                            value = onlineCount.toString(),
                            containerColor = Color(0xFF16A34A).copy(alpha = 0.14f),
                            contentColor = Color(0xFF15803D),
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatChip(
                            label = "异常/离线",
                            value = offlineCount.toString(),
                            containerColor = if (offlineCount > 0) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                CustomColors.listItemColors.containerColor
                            },
                            contentColor = if (offlineCount > 0) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Search & Filter
                item(key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = CircleShape,
                            placeholder = { Text("搜索服务器名称、IP 或主机名…") },
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Search01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ConnectedChoiceButtons(
                                options = BeszelSystemFilter.entries,
                                selected = state.filter,
                                onSelect = onFilterChange,
                                minHeight = 40.dp,
                                modifier = Modifier.weight(1f),
                            ) { option, _ ->
                                Text(option.label, maxLines = 1)
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                onClick = onToggleAutoRefresh,
                                shape = CircleShape,
                                color = if (state.autoRefreshEnabled) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    CustomColors.listItemColors.containerColor
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (state.autoRefreshEnabled) {
                                                    Color(0xFF16A34A)
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                },
                                            ),
                                    )
                                    Text(
                                        text = if (state.autoRefreshEnabled) "5s 刷新" else "已暂停",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (state.autoRefreshEnabled) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (!state.systemsError.isNullOrBlank()) {
                    item(key = "error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Alert01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = state.systemsError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                if (filteredSystems.isEmpty()) {
                    item(key = "empty") {
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
                                    imageVector = HugeIcons.ServerStack01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = if (state.systems.isEmpty()) "暂无受监控的服务器" else "没有匹配的服务器",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (state.systems.isEmpty()) {
                                        "请在 Beszel Web 端添加服务器节点后刷新"
                                    } else {
                                        "尝试修改搜索关键词或筛选条件"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredSystems, key = { it.id }) { system ->
                        BeszelSystemCard(
                            system = system,
                            onClick = { onSystemClick(system) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatChip(
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BeszelSystemCard(
    system: BeszelSystem,
    onClick: () -> Unit,
) {
    val statusColor = when (system.status) {
        "up" -> Color(0xFF16A34A)
        "down" -> MaterialTheme.colorScheme.error
        "paused" -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header row
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = system.name.ifBlank { system.host },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildList {
                            if (system.host.isNotBlank()) add(system.host)
                            add(BeszelFormatters.osDisplayName(system.os))
                            system.agentVersion?.let { add("v$it") }
                        }.joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = BeszelFormatters.statusDisplayName(system.status),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Metrics bars (CPU, Memory, Disk)
            if (!system.hasNoData) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricProgressRow(
                        label = "CPU",
                        percent = system.cpuPercent,
                        barColor = MaterialTheme.colorScheme.primary,
                    )
                    MetricProgressRow(
                        label = "内存",
                        percent = system.memoryPercent,
                        barColor = MaterialTheme.colorScheme.secondary,
                    )
                    MetricProgressRow(
                        label = "磁盘",
                        percent = system.diskPercent,
                        barColor = MaterialTheme.colorScheme.tertiary,
                    )
                }

                // Bottom micro-telemetry pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelemetryPill(
                        icon = HugeIcons.ArrowDataTransferVertical,
                        text = BeszelFormatters.formatBytesPerSec(system.bandwidthBytesPerSec),
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryPill(
                        icon = HugeIcons.Clock01,
                        text = BeszelFormatters.formatUptime(system.uptimeSeconds),
                        modifier = Modifier.weight(1f),
                    )
                    system.temperature?.let { temp ->
                        TelemetryPill(
                            text = BeszelFormatters.formatTemp(temp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricProgressRow(
    label: String,
    percent: Double,
    barColor: Color,
) {
    val clamped = (percent / 100.0).toFloat().coerceIn(0f, 1f)
    val effectiveColor = when {
        percent >= 90.0 -> MaterialTheme.colorScheme.error
        percent >= 75.0 -> Color(0xFFD97706)
        else -> barColor
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(effectiveColor),
            )
        }
        Text(
            text = BeszelFormatters.formatPercent(percent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(50.dp),
        )
    }
}

@Composable
private fun TelemetryPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BeszelAlertsSheet(
    state: BeszelUiState,
    onDeleteHistory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text(
                text = "告警中心",
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
            )
            if (state.isLoadingAlerts) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else if (state.alertHistory.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = HugeIcons.CheckmarkCircle02,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                        )
                        Text(
                            text = "暂无告警记录，所有服务运行正常",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.alertHistory, key = { it.id }) { event ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (event.isActive) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (event.isActive) HugeIcons.Alert01 else HugeIcons.CheckmarkCircle02,
                                    contentDescription = null,
                                    tint = if (event.isActive) MaterialTheme.colorScheme.error else Color(0xFF16A34A),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${event.systemName.ifBlank { event.systemId }} · ${event.name.ifBlank { event.monitorName }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "值: ${event.value} · ${BeszelFormatters.formatRelativeTime(event.created)}" +
                                            if (event.isActive) " · 触发中" else " · 已恢复",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDeleteHistory(event.id) }) {
                                    Icon(
                                        imageVector = HugeIcons.Delete02,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(18.dp),
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

@Composable
private fun BeszelAccountSheet(
    session: BeszelSession,
    hubVersion: String?,
    autoRefreshEnabled: Boolean,
    onToggleAutoRefresh: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "连接信息",
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
            )

            CardGroup {
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.Link01) },
                    supportingContent = { Text(session.baseUrl) },
                ) {
                    Text("Hub 地址")
                }
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                    supportingContent = { Text(session.userEmail.ifBlank { session.identity }) },
                ) {
                    Text("当前账号")
                }
                if (!hubVersion.isNullOrBlank()) {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
                        supportingContent = { Text("v$hubVersion") },
                    ) {
                        Text("Hub 版本")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onToggleAutoRefresh,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text(if (autoRefreshEnabled) "暂停自动刷新" else "开启自动刷新")
                }
                Button(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(
                        imageVector = HugeIcons.Logout03,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("退出登录")
                }
            }
        }
    }
}
