package me.rerere.rikkahub.ui.pages.pocketbase

import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.pocketbase.PbCollection
import me.rerere.rikkahub.data.pocketbase.PbField
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalExtendColors
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PocketBaseCollectionPage(
    collectionName: String,
    vm: PocketBaseCollectionVM = koinViewModel(
        key = collectionName,
        parameters = { parametersOf(collectionName) },
    ),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val extendColors = LocalExtendColors.current

    var selectedRecord by remember { mutableStateOf<JsonObject?>(null) }
    var editingRecord by remember { mutableStateOf<JsonObject?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage, state.actionMessage) {
        val msg = state.actionMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessages()
        }
    }

    val isViewCollection = state.collection?.type == "view"

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
                                text = state.collectionName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.collection?.let { col ->
                                PbBadgePill(
                                    text = col.type.uppercase(),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        if (state.realtimeActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(extendColors.green6),
                                )
                                Text(
                                    text = state.lastRealtimeAction?.let { "SSE 实时 · $it" } ?: "SSE 实时监听中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extendColors.green7,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.realtimeActive,
                        onClick = vm::toggleRealtime,
                        label = { Text("实时") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilledTonalIconButton(onClick = vm::loadInitial) {
                        Icon(HugeIcons.Refresh01, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isViewCollection && state.selectedTab == CollectionDetailTab.RECORDS && state.collection != null) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(HugeIcons.Add01, contentDescription = "新增记录")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ConnectedChoiceButtons(
                options = CollectionDetailTab.entries,
                selected = state.selectedTab,
                onSelect = vm::selectTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { option, _ ->
                Text(option.label, maxLines = 1)
            }

            when (state.selectedTab) {
                CollectionDetailTab.RECORDS -> RecordsTab(
                    state = state,
                    onFilterChange = vm::setFilterQuery,
                    onApplyFilter = vm::applyFilter,
                    onSortChange = vm::setSort,
                    onPageChange = vm::loadRecords,
                    onRecordClick = { selectedRecord = it },
                )
                CollectionDetailTab.SCHEMA -> SchemaTab(collection = state.collection)
                CollectionDetailTab.RULES -> RulesAndIndexesTab(collection = state.collection)
            }
        }
    }

    selectedRecord?.let { record ->
        val recordId = record["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        RecordDetailSheet(
            record = record,
            canEdit = !isViewCollection,
            isSaving = state.isSaving,
            onEdit = {
                selectedRecord = null
                editingRecord = record
            },
            onDelete = {
                vm.deleteRecord(recordId) {
                    selectedRecord = null
                }
            },
            onDismiss = { selectedRecord = null },
        )
    }

    val col = state.collection
    if (showCreateSheet && col != null) {
        RecordEditorSheet(
            title = "新增 ${col.name} 记录",
            collection = col,
            initialRecord = null,
            isSaving = state.isSaving,
            onSubmit = { payload ->
                vm.createRecord(payload) {
                    showCreateSheet = false
                }
            },
            onDismiss = { showCreateSheet = false },
        )
    }

    if (editingRecord != null && col != null) {
        val target = editingRecord!!
        val recordId = target["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        RecordEditorSheet(
            title = "编辑记录 $recordId",
            collection = col,
            initialRecord = target,
            isSaving = state.isSaving,
            onSubmit = { payload ->
                vm.updateRecord(recordId, payload) {
                    editingRecord = null
                }
            },
            onDismiss = { editingRecord = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordsTab(
    state: PocketBaseCollectionUiState,
    onFilterChange: (String) -> Unit,
    onApplyFilter: () -> Unit,
    onSortChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onRecordClick: (JsonObject) -> Unit,
) {
    val sortOptions = remember {
        listOf("-created" to "最新创建", "+created" to "最早创建", "-updated" to "最近更新", "+id" to "ID 升序")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.filterQuery,
                    onValueChange = onFilterChange,
                    placeholder = { Text("PB filter，如 status=\"running\"") },
                    leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                    trailingIcon = {
                        if (state.filterQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onFilterChange("")
                                    onApplyFilter()
                                },
                            ) {
                                Icon(HugeIcons.Cancel01, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onApplyFilter() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onApplyFilter,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("查询")
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sortOptions.forEach { (sortKey, label) ->
                    FilterChip(
                        selected = state.sort == sortKey,
                        onClick = { onSortChange(sortKey) },
                        label = { Text(label) },
                    )
                }
            }
        }

        val page = state.recordsPage
        if (page == null && state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (page == null || page.items.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "暂无记录",
                    subtitle = "当前集合没有匹配的记录，可尝试调整筛选条件",
                )
            }
        } else {
            item {
                PaginationBar(
                    page = page.page,
                    totalPages = page.totalPages,
                    totalItems = page.totalItems,
                    onPrev = { onPageChange(page.page - 1) },
                    onNext = { onPageChange(page.page + 1) },
                )
            }
            items(
                items = page.items,
                key = { it["id"]?.jsonPrimitive?.contentOrNull ?: it.hashCode().toString() },
            ) { record ->
                RecordCard(
                    record = record,
                    fields = state.collection?.fields.orEmpty(),
                    onClick = { onRecordClick(record) },
                )
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: JsonObject,
    fields: List<PbField>,
    onClick: () -> Unit,
) {
    val id = record["id"]?.jsonPrimitive?.contentOrNull ?: "-"
    val created = record["created"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val previewEntries = remember(record, fields) {
        val preferredKeys = fields
            .filter { it.name != "id" && it.name != "created" && it.name != "updated" && !it.hidden }
            .sortedByDescending { it.presentable }
            .map { it.name }
        val keys = if (preferredKeys.isNotEmpty()) preferredKeys else record.keys.filter { it !in setOf("id", "created", "updated", "collectionId", "collectionName") }
        keys.take(4).mapNotNull { key ->
            val el = record[key] ?: return@mapNotNull null
            if (el is JsonNull) return@mapNotNull null
            val formatted = when (el) {
                is JsonPrimitive -> el.contentOrNull ?: el.toString()
                else -> el.toString()
            }
            if (formatted.isBlank()) return@mapNotNull null
            key to formatted
        }
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PbBadgePill(
                    text = id,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (created.isNotBlank()) {
                    Text(
                        text = formatPbTimestamp(created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            previewEntries.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(88.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemaTab(collection: PbCollection?) {
    if (collection == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(collection.fields, key = { it.id.ifEmpty { it.name } }) { field ->
            FieldSchemaCard(field)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldSchemaCard(field: PbField) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetbrainsMono,
                )
                PbBadgePill(
                    text = field.type,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (field.required) {
                    PbBadgePill("REQUIRED", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
                if (field.presentable) {
                    PbBadgePill("PRESENTABLE", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (field.system) {
                    PbBadgePill("SYSTEM", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (field.hidden) {
                    PbBadgePill("HIDDEN", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val constraints = buildList {
                if (field.values.isNotEmpty()) add("选项: ${field.values.joinToString(", ")}")
                field.maxSelect?.let { add("maxSelect: $it") }
                field.collectionId?.let { add("关联集合ID: $it") }
                field.min?.let { add("min: $it") }
                field.max?.let { add("max: $it") }
                field.pattern?.takeIf { it.isNotBlank() }?.let { add("pattern: $it") }
            }
            if (constraints.isNotEmpty()) {
                Text(
                    text = constraints.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetbrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RulesAndIndexesTab(collection: PbCollection?) {
    if (collection == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val rules = listOf(
        "listRule" to collection.listRule,
        "viewRule" to collection.viewRule,
        "createRule" to collection.createRule,
        "updateRule" to collection.updateRule,
        "deleteRule" to collection.deleteRule,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "API 访问规则 (API Rules)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(rules, key = { it.first }) { (name, expr) ->
            val (badge, container, content) = when {
                expr == null -> Triple("仅超级管理员", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                expr.isEmpty() -> Triple("公开 (任何人)", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                else -> Triple("条件规则", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetbrainsMono,
                            fontWeight = FontWeight.SemiBold,
                        )
                        PbBadgePill(badge, container, content)
                    }
                    if (!expr.isNullOrEmpty()) {
                        Text(
                            text = expr,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetbrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "索引 (${collection.indexes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (collection.indexes.isEmpty()) {
            item {
                EmptyPlaceholderCard(
                    title = "暂无自定义索引",
                    subtitle = "该数据表当前没有额外的 SQLite 索引",
                )
            }
        } else {
            items(collection.indexes) { idx ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = idx,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetbrainsMono,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordDetailSheet(
    record: JsonObject,
    canEdit: Boolean,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    text = "记录详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(JsonInstantPretty.encodeToString(record)))
                        },
                    ) {
                        Icon(HugeIcons.Copy01, contentDescription = "复制 JSON", modifier = Modifier.size(18.dp))
                    }
                    if (canEdit) {
                        FilledTonalIconButton(onClick = onEdit) {
                            Icon(HugeIcons.Edit03, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                        }
                        FilledTonalIconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = HugeIcons.Delete02,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        record.entries.forEach { (key, value) ->
                            val str = when (value) {
                                is JsonNull -> "null"
                                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                                else -> value.toString()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetbrainsMono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(100.dp),
                                )
                                Text(
                                    text = str,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetbrainsMono,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "JSON 树状视图",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    JsonTree(json = record, initialExpandLevel = 2)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除记录？") },
            text = { Text("删除后无法恢复该条数据库记录。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordEditorSheet(
    title: String,
    collection: PbCollection,
    initialRecord: JsonObject?,
    isSaving: Boolean,
    onSubmit: (JsonObject) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editableFields = remember(collection, initialRecord) {
        collection.fields.filter { field ->
            field.name != "id" &&
                field.type != "autodate" &&
                field.type != "file" &&
                !(field.system && field.name in setOf("collectionId", "collectionName", "tokenKey"))
        }
    }

    val textValues = remember(initialRecord, editableFields) {
        mutableStateMapOf<String, String>().apply {
            editableFields.forEach { field ->
                val raw = initialRecord?.get(field.name)
                val initial = when {
                    raw == null || raw is JsonNull -> ""
                    raw is JsonPrimitive -> raw.contentOrNull ?: raw.toString()
                    else -> JsonInstantPretty.encodeToString(raw)
                }
                put(field.name, initial)
            }
        }
    }
    val boolValues = remember(initialRecord, editableFields) {
        mutableStateMapOf<String, Boolean>().apply {
            editableFields.filter { it.type == "bool" }.forEach { field ->
                val raw = initialRecord?.get(field.name) as? JsonPrimitive
                put(field.name, raw?.booleanOrNull == true)
            }
        }
    }
    var formError by remember { mutableStateOf<String?>(null) }

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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            editableFields.forEach { field ->
                when (field.type) {
                    "bool" -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(field.name, style = MaterialTheme.typography.bodyMedium, fontFamily = JetbrainsMono)
                                    Text("bool", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = boolValues[field.name] == true,
                                    onCheckedChange = { boolValues[field.name] = it },
                                )
                            }
                        }
                    }
                    "select" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${field.name}${if (field.required) " *" else ""} (select)",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetbrainsMono,
                            )
                            if (field.values.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val current = textValues[field.name].orEmpty()
                                    field.values.forEach { option ->
                                        FilterChip(
                                            selected = current == option,
                                            onClick = { textValues[field.name] = option },
                                            label = { Text(option) },
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = textValues[field.name].orEmpty(),
                                    onValueChange = { textValues[field.name] = it },
                                    label = { Text(field.name) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    "number" -> {
                        OutlinedTextField(
                            value = textValues[field.name].orEmpty(),
                            onValueChange = { textValues[field.name] = it },
                            label = { Text("${field.name}${if (field.required) " *" else ""} (number)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "json" -> {
                        OutlinedTextField(
                            value = textValues[field.name].orEmpty(),
                            onValueChange = { textValues[field.name] = it },
                            label = { Text("${field.name}${if (field.required) " *" else ""} (json)") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = textValues[field.name].orEmpty(),
                            onValueChange = { textValues[field.name] = it },
                            label = { Text("${field.name}${if (field.required) " *" else ""} (${field.type})") },
                            minLines = if (field.type == "editor") 3 else 1,
                            maxLines = if (field.type == "editor") 6 else 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            formError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        formError = null
                        val built = runCatching {
                            buildJsonObject {
                                editableFields.forEach { field ->
                                    when (field.type) {
                                        "bool" -> put(field.name, JsonPrimitive(boolValues[field.name] == true))
                                        "number" -> {
                                            val raw = textValues[field.name].orEmpty().trim()
                                            if (raw.isNotEmpty()) {
                                                val num = raw.toDoubleOrNull()
                                                    ?: error("字段 ${field.name} 必须是有效数字")
                                                if (raw.contains('.')) {
                                                    put(field.name, JsonPrimitive(num))
                                                } else {
                                                    put(field.name, JsonPrimitive(raw.toLongOrNull() ?: num))
                                                }
                                            }
                                        }
                                        "json" -> {
                                            val raw = textValues[field.name].orEmpty().trim()
                                            if (raw.isNotEmpty()) {
                                                val el: JsonElement = JsonInstant.parseToJsonElement(raw)
                                                put(field.name, el)
                                            }
                                        }
                                        "relation" -> {
                                            val raw = textValues[field.name].orEmpty().trim()
                                            if (raw.startsWith("[")) {
                                                val arr = JsonInstant.parseToJsonElement(raw) as? JsonArray
                                                if (arr != null) put(field.name, arr)
                                            } else if ((field.maxSelect ?: 1) > 1 && raw.isNotEmpty()) {
                                                put(
                                                    field.name,
                                                    buildJsonArray {
                                                        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                                                            add(JsonPrimitive(it))
                                                        }
                                                    },
                                                )
                                            } else if (raw.isNotEmpty() || initialRecord != null) {
                                                put(field.name, JsonPrimitive(raw))
                                            }
                                        }
                                        else -> {
                                            val raw = textValues[field.name].orEmpty()
                                            if (raw.isNotEmpty() || initialRecord != null) {
                                                put(field.name, JsonPrimitive(raw))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        built.onSuccess(onSubmit).onFailure { err ->
                            formError = err.message ?: "表单校验失败"
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (isSaving) "保存中..." else "保存")
                }
            }
        }
    }
}
