package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.agent.RemoteAgentClient
import me.rerere.rikkahub.data.agent.RemoteEvent
import me.rerere.rikkahub.data.agent.RemoteProject
import me.rerere.rikkahub.data.agent.RemoteSession
import me.rerere.rikkahub.data.agent.RemoteStream
import me.rerere.rikkahub.data.agent.RemoteTask
import me.rerere.rikkahub.data.agent.remoteAgentName
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private val activeTaskStates = setOf("scheduled", "queued", "running", "canceling")
private val retryableTaskStates = setOf("failed", "canceled", "interrupted")

@Composable
internal fun RemoteAgentPage() {
    val repository = koinInject<WorkflowRepository>()
    val http = koinInject<OkHttpClient>()
    val account by repository.session.collectAsStateWithLifecycle()
    val signedIn = account
    if (signedIn == null || signedIn.baseUrl.trimEnd('/') != DEFAULT_POCKETBASE_URL) {
        RemoteScaffold {
            Text(
                "请返回 Agent 页面，连接默认 AnyWorkflow 账号后再打开远程开发。",
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    val client = remember(repository, http, signedIn) { RemoteAgentClient(repository, http, signedIn) }
    key(signedIn.userId) { RemoteAgentWorkspace(client) }
}

@Composable
private fun RemoteScaffold(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("ACP 远程开发") }, navigationIcon = { BackButton() }) },
        content = content,
    )
}

@Composable
private fun RemoteAgentWorkspace(client: RemoteAgentClient) {
    val scope = rememberCoroutineScope()
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectId by rememberSaveable { mutableStateOf("") }
    var agent by rememberSaveable { mutableStateOf("") }
    var projects by remember { mutableStateOf<List<RemoteProject>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<RemoteSession>>(emptyList()) }
    var sessionPage by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf<RemoteSession?>(null) }
    var tasks by remember { mutableStateOf<List<RemoteTask>>(emptyList()) }
    var taskPage by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    fun report(problem: Throwable) {
        if (problem is CancellationException) throw problem
        error = problem.message ?: "远程开发请求失败"
    }
    fun act(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() } catch (problem: Exception) { report(problem) } finally { busy = false }
        }
    }

    LaunchedEffect(client, sessionId, refresh) {
        loading = true
        error = null
        try {
            val id = sessionId
            if (id == null) {
                projects = client.projects()
                val (rows, next) = client.sessions(1)
                sessions = rows
                sessionPage = next
            } else {
                selected = client.session(id)
                val (rows, next) = client.tasks(id, 1)
                tasks = rows
                taskPage = next
            }
        } catch (problem: Exception) {
            report(problem)
        } finally {
            loading = false
        }
    }
    BackHandler(enabled = taskId != null || sessionId != null) {
        if (!busy) {
            if (taskId != null) { taskId = null; refresh++ }
            else { sessionId = null; selected = null; tasks = emptyList() }
        }
    }

    val id = taskId
    if (id != null) {
        RemoteTaskPage(client, id, onReturn = { taskId = null; refresh++ }, onRetry = { taskId = it })
        return
    }
    RemoteScaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionId != null) TextButton(onClick = {
                        if (!busy) { sessionId = null; selected = null; tasks = emptyList() }
                    }) { Text("全部会话") }
                    TextButton(onClick = { refresh++ }, enabled = !loading && !busy) { Text("刷新") }
                }
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (sessionId == null) {
                item {
                    val project = projects.find { it.id == projectId } ?: projects.firstOrNull()
                    val engine = agent.takeIf { it in project?.agents.orEmpty() } ?: project?.agents?.firstOrNull()
                    val key = rememberSaveable(project?.id, engine) { Uuid.random().toString() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteChoice("项目", project?.name, projects.map { it.id to it.name }) {
                            projectId = it
                            agent = ""
                        }
                        RemoteChoice("Agent", engine?.let(::remoteAgentName),
                            project?.agents.orEmpty().map { it to remoteAgentName(it) }) { agent = it }
                        Button(
                            onClick = {
                                if (project != null && engine != null) {
                                    act {
                                        selected = client.create(project, engine, key)
                                        sessionId = selected?.id
                                    }
                                }
                            },
                            enabled = project != null && engine != null && !busy && !loading,
                        ) { Text("新建会话") }
                    }
                    if (!loading && projects.isEmpty() && error == null) Text("当前账号没有获授权的项目")
                }
                items(sessions, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.title) },
                        supportingContent = { Text(remoteAgentName(row.agent)) },
                        modifier = Modifier.clickable(enabled = !busy) {
                            selected = row
                            sessionId = row.id
                        },
                    )
                }
                if (sessionPage != null) item {
                    TextButton(enabled = !loading && !busy, onClick = {
                        act {
                            val (rows, next) = client.sessions(sessionPage ?: return@act)
                            sessions = (sessions + rows).distinctBy { it.id }
                            sessionPage = next
                        }
                    }) { Text("更多会话") }
                }
            } else {
                selected?.takeIf { it.id == sessionId }?.let { row ->
                    item(key = row.id) {
                        Text(row.title, style = MaterialTheme.typography.titleLarge)
                        RemotePrompt(row, busy || loading) { prompt, key, model, effort, runAt ->
                            act { taskId = client.submit(row.id, prompt, key, model, effort, runAt).id }
                        }
                    }
                }
                items(tasks, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.prompt, maxLines = 2) },
                        supportingContent = { Text(taskStatus(row.state)) },
                        modifier = Modifier.clickable(enabled = !busy) { taskId = row.id },
                    )
                }
                if (taskPage != null) item {
                    TextButton(enabled = !loading && !busy, onClick = {
                        act {
                            val (rows, next) = client.tasks(sessionId ?: return@act, taskPage ?: return@act)
                            tasks = (tasks + rows).distinctBy { it.id }
                            taskPage = next
                        }
                    }) { Text("更多任务") }
                }
            }
        }
    }
}

@Composable
private fun RemotePrompt(
    session: RemoteSession, busy: Boolean,
    send: (String, String, String, String, Long) -> Unit,
) {
    var prompt by rememberSaveable(session.id) { mutableStateOf("") }
    var model by rememberSaveable(session.id) { mutableStateOf("") }
    var effort by rememberSaveable(session.id) { mutableStateOf("") }
    var minutes by rememberSaveable(session.id) { mutableStateOf("") }
    val run = rememberSaveable(prompt, model, effort, minutes) { mutableStateOf(0L) }
    val key = rememberSaveable(prompt, model, effort, minutes) { Uuid.random().toString() }
    val models = choices(session.options, "model")
    val efforts = choices(session.options, "thought_level")
    val delayMinutes = minutes.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (models.isNotEmpty()) RemoteChoice("模型", models.firstOrNull { it.first == model }?.second,
                listOf("" to "会话默认") + models) { model = it }
            if (efforts.isNotEmpty()) RemoteChoice("思考", efforts.firstOrNull { it.first == effort }?.second,
                listOf("" to "会话默认") + efforts) { effort = it }
        }
        OutlinedTextField(prompt, { prompt = it }, label = { Text("任务指令") }, modifier = Modifier.fillMaxWidth(),
            enabled = !busy, minLines = 2)
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(6) },
            label = { Text("延迟分钟数（可选）") }, enabled = !busy, singleLine = true)
        Button(
            enabled = !busy && prompt.isNotBlank() && (minutes.isEmpty() || (delayMinutes != null && delayMinutes in 1..525600)),
            onClick = {
                if (run.value == 0L && delayMinutes != null) run.value = System.currentTimeMillis() + delayMinutes * 60_000L
                send(prompt.trim(), key, model, effort, run.value)
            },
        ) { Text(if (minutes.isEmpty()) "发送任务" else "安排任务") }
    }
}

@Composable
private fun RemoteTaskPage(client: RemoteAgentClient, id: String, onReturn: () -> Unit, onRetry: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var task by remember(id) { mutableStateOf<RemoteTask?>(null) }
    val events = remember(id) { mutableStateListOf<RemoteEvent>() }
    var cursor by remember(id) { mutableStateOf(0) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var watchError by remember(id) { mutableStateOf<String?>(null) }
    var reconnect by remember(id) { mutableStateOf(0) }
    var busy by remember(id) { mutableStateOf(false) }
    val retryKey = rememberSaveable(id) { Uuid.random().toString() }

    fun append(event: RemoteEvent) {
        if (event.sequence > cursor) {
            events.add(event)
            cursor = event.sequence
        }
    }
    LaunchedEffect(client, id, lifecycle, reconnect) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var backoff = 1000L
            while (true) {
                try {
                    task = client.task(id)
                    do {
                        val page = client.events(id, cursor)
                        page.items.forEach(::append)
                        if (page.done) {
                            task = client.task(id)
                            watchError = null
                            return@repeatOnLifecycle
                        }
                    } while (page.items.size == 50)
                    watchError = null
                    var lastApprovalRefresh = 0L
                    val done = client.stream(id, cursor).firstOrNull { message ->
                        when (message) {
                            is RemoteStream.Event -> {
                                append(message.value)
                                if (message.value.type == "approval") task = client.task(id)
                                false
                            }
                            is RemoteStream.State -> {
                                task = task?.copy(state = message.state)
                                if (task?.pendingApproval != null &&
                                    android.os.SystemClock.elapsedRealtime() - lastApprovalRefresh >= 4000L
                                ) {
                                    task = client.task(id)
                                    lastApprovalRefresh = android.os.SystemClock.elapsedRealtime()
                                }
                                message.done
                            }
                        }
                    }
                    if (done != null) {
                        task = client.task(id)
                        return@repeatOnLifecycle
                    }
                    backoff = 1000L
                } catch (problem: Exception) {
                    if (problem is CancellationException) throw problem
                    if (problem is WorkflowApiException && problem.statusCode in listOf(401, 403, 404)) {
                        watchError = problem.message
                        return@repeatOnLifecycle
                    }
                    watchError = "连接中断，正在重新连接…"
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(8000)
            }
        }
    }

    fun act(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() } catch (problem: Exception) {
                if (problem is CancellationException) throw problem
                error = problem.message ?: "远程任务请求失败"
            } finally { busy = false }
        }
    }

    val updates = events.filter { it.type == "update" }.mapNotNull { it.data }
    val answer = updates.filter { it.string("sessionUpdate") == "agent_message_chunk" }
        .joinToString("") { (it["content"] as? JsonObject)?.string("text").orEmpty() }
    val thinking = updates.filter { it.string("sessionUpdate") == "agent_thought_chunk" }
        .joinToString("") { (it["content"] as? JsonObject)?.string("text").orEmpty() }
    val tools = linkedMapOf<String, JsonObject>()
    updates.filter { it.string("sessionUpdate") in setOf("tool_call", "tool_call_update") }.forEach { update ->
        update.string("toolCallId")?.let { key -> tools[key] = JsonObject(tools[key].orEmpty() + update) }
    }

    RemoteScaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TextButton(onClick = onReturn, enabled = !busy) { Text("返回会话") }
                val current = task
                if (current == null || current.state in activeTaskStates) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (current != null) {
                    Text(taskStatus(current.state), style = MaterialTheme.typography.titleLarge)
                    SelectionContainer { Text(current.prompt) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.state in activeTaskStates && current.state != "canceling") {
                            TextButton(onClick = { act { task = client.cancel(id); reconnect++ } }, enabled = !busy) { Text("取消任务") }
                        }
                        if (current.state in retryableTaskStates) {
                            TextButton(onClick = { act { onRetry(client.retry(id, retryKey).id) } }, enabled = !busy) { Text("重试任务") }
                        }
                    }
                    current.pendingApproval?.let { approval ->
                        val details = events.lastOrNull {
                            it.type == "approval" && it.data?.string("id") == approval.id
                        }?.data?.get("toolCall") as? JsonObject
                        Text("工具审批", style = MaterialTheme.typography.titleMedium)
                        details?.string("title")?.let { Text(it) }
                        details?.get("rawInput")?.let { SelectionContainer { Text(it.toString().take(4000)) } }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            approval.options.forEach { option ->
                                FilledTonalButton(
                                    onClick = { act { task = client.approve(id, approval.id, option.optionId) } },
                                    enabled = !busy,
                                ) { Text(when (option.kind) {
                                    "allow_once" -> "仅允许本次"
                                    "reject_once" -> "拒绝本次"
                                    else -> option.name
                                }) }
                            }
                        }
                    }
                }
                (error ?: watchError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { reconnect++ }) { Text("重新连接") }
                }
            }
            if (answer.isNotEmpty()) item {
                SelectionContainer { Text(answer, style = MaterialTheme.typography.bodyLarge) }
            }
            if (thinking.isNotEmpty()) item {
                RemoteDetails("思考过程", thinking)
            }
            items(tools.entries.toList(), key = { "tool-${it.key}" }) { (_, tool) ->
                val title = tool.string("title") ?: "工具调用"
                val status = tool.string("status")?.let { " · $it" }.orEmpty()
                val details = listOfNotNull(tool["rawInput"], tool["rawOutput"], tool["content"])
                    .joinToString("\n").take(12000)
                RemoteDetails(title + status, details)
            }
            items(events.filter { it.type == "error" || it.type == "artifact" }, key = { "event-${it.sequence}" }) { event ->
                Text(event.data?.string(if (event.type == "artifact") "path" else "code").orEmpty())
            }
        }
    }
}

@Composable
private fun RemoteDetails(title: String, details: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }, enabled = details.isNotEmpty()) { Text(title) }
        if (open) SelectionContainer { Text(details) }
    }
}

@Composable
private fun RemoteChoice(label: String, selection: String?, values: List<Pair<String, String>>, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        FilledTonalButton(onClick = { open = true }, enabled = values.isNotEmpty()) {
            Text(selection?.let { "$label · $it" } ?: label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; select(id) })
            }
        }
    }
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content

private fun choices(options: List<JsonObject>, category: String): List<Pair<String, String>> {
    val option = options.firstOrNull { it.string("category") == category || it.string("id") == category }
    val values = option?.get("options") as? JsonArray ?: return emptyList()
    return values.filterIsInstance<JsonObject>().flatMap { row ->
        (row["options"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: listOf(row)
    }.mapNotNull { value -> value.string("value")?.let { it to (value.string("name") ?: it) } }
}

private fun taskStatus(state: String): String = when (state) {
    "scheduled" -> "等待调度"
    "queued" -> "排队中"
    "running" -> "执行中"
    "canceling" -> "正在取消"
    "succeeded" -> "已完成"
    "failed" -> "失败"
    "canceled" -> "已取消"
    "interrupted" -> "已中断"
    else -> state
}
