package me.rerere.rikkahub.data.workflow

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowSession(
    val token: String,
    val userId: String,
    val email: String,
    val name: String = "",
    val baseUrl: String,
)

enum class DispatchStatus(
    val wireValue: String,
    val label: String,
    val isActive: Boolean = false,
    val isTerminal: Boolean = false,
) {
    DRAFT("draft", "草稿"),
    QUEUED("queued", "排队中", isActive = true),
    RUNNING("running", "执行中", isActive = true),
    SUCCEEDED("succeeded", "已完成", isTerminal = true),
    FAILED("failed", "失败", isTerminal = true),
    CANCELED("canceled", "已取消", isTerminal = true);

    companion object {
        fun fromWire(value: String?): DispatchStatus =
            entries.firstOrNull { it.wireValue == value } ?: DRAFT
    }
}

enum class DispatchRequestedAction(val wireValue: String) {
    NONE("none"),
    PAUSE("pause"),
    RESUME("resume"),
    CANCEL("cancel");

    companion object {
        fun fromWire(value: String?): DispatchRequestedAction =
            entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

enum class DispatchExecutionMode(val wireValue: String, val label: String) {
    SERIAL("serial", "串行"),
    PARALLEL("parallel", "并行");

    companion object {
        fun fromWire(value: String?): DispatchExecutionMode =
            entries.firstOrNull { it.wireValue == value } ?: SERIAL
    }
}

enum class DispatchRunOrigin(val wireValue: String, val label: String) {
    INITIAL("initial", "初始版本"),
    RERUN("rerun", "完整重跑"),
    RESUME("resume", "检查点恢复"),
    EDITED_RERUN("edited_rerun", "编辑副本");

    companion object {
        fun fromWire(value: String?): DispatchRunOrigin =
            entries.firstOrNull { it.wireValue == value } ?: INITIAL
    }
}

enum class DispatchExecutorKind(val wireValue: String, val label: String) {
    BROWSER("browser", "浏览器"),
    CODEX("codex", "Codex");

    companion object {
        fun fromWire(value: String?): DispatchExecutorKind =
            entries.firstOrNull { it.wireValue == value } ?: BROWSER
    }
}

enum class DispatchEventStatus(
    val wireValue: String,
    val isActive: Boolean = false,
) {
    WAITING("waiting"),
    READY("ready", isActive = true),
    LEASED("leased", isActive = true),
    RUNNING("running", isActive = true),
    PAUSED("paused", isActive = true),
    TERMINAL("terminal");

    companion object {
        fun fromWire(value: String?): DispatchEventStatus =
            entries.firstOrNull { it.wireValue == value } ?: WAITING
    }
}

enum class WorkflowHistoryStatus(val wireValue: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELED("canceled"),
    INTERRUPTED("interrupted"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): WorkflowHistoryStatus =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class RunListFilter(val key: String, val label: String) {
    ALL("all", "全部"),
    DRAFT("draft", "草稿"),
    ACTIVE("active", "进行中"),
    DONE("done", "已完成"),
    FAILED("failed", "失败"),
    CANCELED("canceled", "取消"),
}

enum class StatusTone {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
}

data class StatusMeta(
    val label: String,
    val tone: StatusTone,
)

data class WorkflowRun(
    val id: String,
    val owner: String,
    val title: String,
    val planText: String,
    val familyId: String,
    val versionNumber: Int,
    val versionMajor: Int,
    val versionMinor: Int,
    val parentRun: String,
    val origin: DispatchRunOrigin,
    val executionMode: DispatchExecutionMode,
    val maxConcurrency: Int,
    val status: DispatchStatus,
    val requestedAction: DispatchRequestedAction,
    val commandVersion: Int,
    val scheduledAt: String,
    val totalTasks: Int,
    val completedTasks: Int,
    val lastError: String,
    val startedAt: String,
    val endedAt: String,
    val created: String,
    val updated: String,
) {
    val displayTitle: String
        get() = title.trim().ifEmpty { "未命名 Run" }

    val versionLabel: String
        get() = "v$versionMajor.$versionMinor"

    val canEditDraft: Boolean
        get() = status == DispatchStatus.DRAFT

    val canEditAsNewVersion: Boolean
        get() = status.isTerminal && planText.isNotBlank()

    val canPause: Boolean
        get() = status.isActive && requestedAction == DispatchRequestedAction.NONE

    val canResume: Boolean
        get() = status.isActive && requestedAction == DispatchRequestedAction.PAUSE

    val canCancel: Boolean
        get() = status.isActive && requestedAction != DispatchRequestedAction.CANCEL

    val canDelete: Boolean
        get() = status == DispatchStatus.DRAFT || status.isTerminal

    fun statusMeta(): StatusMeta {
        if (status.isActive) {
            when (requestedAction) {
                DispatchRequestedAction.PAUSE -> return StatusMeta("已请求暂停", StatusTone.WARNING)
                DispatchRequestedAction.RESUME -> return StatusMeta("正在恢复", StatusTone.INFO)
                DispatchRequestedAction.CANCEL -> return StatusMeta("正在取消", StatusTone.WARNING)
                DispatchRequestedAction.NONE -> Unit
            }
        }
        return when (status) {
            DispatchStatus.DRAFT -> StatusMeta("草稿", StatusTone.NEUTRAL)
            DispatchStatus.QUEUED -> StatusMeta("排队中", StatusTone.INFO)
            DispatchStatus.RUNNING -> StatusMeta("进行中", StatusTone.WARNING)
            DispatchStatus.SUCCEEDED -> StatusMeta("已完成", StatusTone.SUCCESS)
            DispatchStatus.FAILED -> StatusMeta("失败", StatusTone.DANGER)
            DispatchStatus.CANCELED -> StatusMeta("已取消", StatusTone.NEUTRAL)
        }
    }
}

data class WorkflowTask(
    val id: String,
    val owner: String,
    val run: String,
    val runIndex: Int,
    val executorKind: DispatchExecutorKind,
    val title: String,
    val executionMode: DispatchExecutionMode,
    val maxConcurrency: Int,
    val orchestrationState: String,
    val compileError: String,
    val status: DispatchStatus,
    val requestedAction: DispatchRequestedAction,
    val totalEvents: Int,
    val completedEvents: Int,
    val lastError: String,
    val startedAt: String,
    val endedAt: String,
    val created: String,
    val updated: String,
) {
    val displayTitle: String
        get() = title.trim().ifEmpty { "Task ${runIndex + 1}" }

    fun statusMeta(): StatusMeta {
        if (orchestrationState == "failed" || compileError.isNotBlank()) {
            return StatusMeta("编排失败", StatusTone.DANGER)
        }
        if (status.isActive) {
            when (requestedAction) {
                DispatchRequestedAction.PAUSE -> return StatusMeta("已请求暂停", StatusTone.WARNING)
                DispatchRequestedAction.RESUME -> return StatusMeta("正在恢复", StatusTone.INFO)
                DispatchRequestedAction.CANCEL -> return StatusMeta("正在取消", StatusTone.WARNING)
                DispatchRequestedAction.NONE -> Unit
            }
        }
        return when (status) {
            DispatchStatus.DRAFT -> StatusMeta("草稿", StatusTone.NEUTRAL)
            DispatchStatus.QUEUED -> StatusMeta("排队中", StatusTone.INFO)
            DispatchStatus.RUNNING -> StatusMeta("执行中", StatusTone.WARNING)
            DispatchStatus.SUCCEEDED -> StatusMeta("已完成", StatusTone.SUCCESS)
            DispatchStatus.FAILED -> StatusMeta("失败", StatusTone.DANGER)
            DispatchStatus.CANCELED -> StatusMeta("已取消", StatusTone.NEUTRAL)
        }
    }
}

data class WorkflowEvent(
    val id: String,
    val owner: String,
    val task: String,
    val eventIndex: Int,
    val queueTextOverride: String,
    val status: DispatchEventStatus,
    val attempt: Int,
    val workerId: String,
    val localRunId: String,
    val localAttempt: Int,
    val lastError: String,
    val terminalResult: String,
    val progressLabel: String,
    val created: String,
    val updated: String,
) {
    val displayTitle: String
        get() {
            val line = queueTextOverride.lineSequence()
                .firstOrNull { it.trim().startsWith("@event=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
            return line?.takeIf { it.isNotEmpty() } ?: "Event ${eventIndex + 1}"
        }

    fun statusMeta(): StatusMeta {
        return when (status) {
            DispatchEventStatus.WAITING -> StatusMeta("等待前置", StatusTone.NEUTRAL)
            DispatchEventStatus.READY -> StatusMeta("待领取", StatusTone.INFO)
            DispatchEventStatus.LEASED -> StatusMeta("已分配", StatusTone.INFO)
            DispatchEventStatus.RUNNING -> StatusMeta("执行中", StatusTone.WARNING)
            DispatchEventStatus.PAUSED -> StatusMeta("已暂停", StatusTone.WARNING)
            DispatchEventStatus.TERMINAL -> when (terminalResult) {
                "succeeded" -> StatusMeta("已完成", StatusTone.SUCCESS)
                "failed" -> StatusMeta("失败", StatusTone.DANGER)
                "canceled" -> StatusMeta("已取消", StatusTone.NEUTRAL)
                else -> StatusMeta("已结束", StatusTone.NEUTRAL)
            }
        }
    }
}

data class WorkflowHistoryEvent(
    val id: String,
    val owner: String,
    val task: String,
    val eventIndex: Int,
    val attempt: Int,
    val status: WorkflowHistoryStatus,
    val title: String,
    val actCount: Int,
    val messageCount: Int,
)

data class WorkflowHistoryAct(
    val id: String,
    val owner: String,
    val event: String,
    val actIndex: Int,
    val attempt: Int,
    val status: WorkflowHistoryStatus,
    val title: String,
    val startedAt: String,
    val endedAt: String,
    val durationMs: Long,
    val messageCount: Int,
    val created: String,
    val updated: String,
) {
    val displayTitle: String
        get() = title.trim().ifEmpty { "Act ${actIndex + 1}" }

    fun statusMeta(eventActive: Boolean): StatusMeta {
        return when (status) {
            WorkflowHistoryStatus.SUCCEEDED -> StatusMeta("已完成", StatusTone.SUCCESS)
            WorkflowHistoryStatus.FAILED -> StatusMeta("失败", StatusTone.DANGER)
            WorkflowHistoryStatus.CANCELED -> StatusMeta("已取消", StatusTone.NEUTRAL)
            WorkflowHistoryStatus.INTERRUPTED -> StatusMeta("已中断", StatusTone.WARNING)
            WorkflowHistoryStatus.UNKNOWN -> if (eventActive) {
                StatusMeta("进行中", StatusTone.WARNING)
            } else {
                StatusMeta("未知", StatusTone.NEUTRAL)
            }
        }
    }
}

data class WorkflowHistoryMessageSummary(
    val id: String,
    val owner: String,
    val act: String,
    val nodeIndex: Int,
    val attempt: Int,
    val status: WorkflowHistoryStatus,
    val sentAt: String,
    val receivedAt: String,
    val created: String,
    val updated: String,
) {
    fun statusMeta(eventActive: Boolean): StatusMeta {
        return when (status) {
            WorkflowHistoryStatus.SUCCEEDED -> StatusMeta("已回复", StatusTone.SUCCESS)
            WorkflowHistoryStatus.FAILED -> StatusMeta("失败", StatusTone.DANGER)
            WorkflowHistoryStatus.CANCELED -> StatusMeta("已取消", StatusTone.NEUTRAL)
            WorkflowHistoryStatus.INTERRUPTED -> StatusMeta("已中断", StatusTone.WARNING)
            WorkflowHistoryStatus.UNKNOWN -> if (receivedAt.isNotBlank()) {
                StatusMeta("已回复", StatusTone.SUCCESS)
            } else if (eventActive) {
                StatusMeta("等待回复", StatusTone.WARNING)
            } else {
                StatusMeta("未完成", StatusTone.NEUTRAL)
            }
        }
    }
}

data class WorkflowHistoryMessage(
    val id: String,
    val owner: String,
    val act: String,
    val nodeIndex: Int,
    val attempt: Int,
    val status: WorkflowHistoryStatus,
    val userMarkdown: String,
    val assistantMarkdown: String,
    val conversationUrl: String,
    val sentAt: String,
    val receivedAt: String,
    val created: String,
    val updated: String,
) {
    fun statusMeta(eventActive: Boolean): StatusMeta {
        return when (status) {
            WorkflowHistoryStatus.SUCCEEDED -> StatusMeta("已回复", StatusTone.SUCCESS)
            WorkflowHistoryStatus.FAILED -> StatusMeta("失败", StatusTone.DANGER)
            WorkflowHistoryStatus.CANCELED -> StatusMeta("已取消", StatusTone.NEUTRAL)
            WorkflowHistoryStatus.INTERRUPTED -> StatusMeta("已中断", StatusTone.WARNING)
            WorkflowHistoryStatus.UNKNOWN -> if (receivedAt.isNotBlank()) {
                StatusMeta("已回复", StatusTone.SUCCESS)
            } else if (eventActive) {
                StatusMeta("等待回复", StatusTone.WARNING)
            } else {
                StatusMeta("未完成", StatusTone.NEUTRAL)
            }
        }
    }
}

data class WorkflowListPage<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>,
)

typealias WorkflowRunPage = WorkflowListPage<WorkflowRun>

sealed interface RunEditorTarget {
    data object Create : RunEditorTarget
    data class FromTemplate(val title: String, val planText: String) : RunEditorTarget
    data class EditDraft(val run: WorkflowRun) : RunEditorTarget
    data class EditedRerun(val source: WorkflowRun) : RunEditorTarget
}

fun createStarterPlan(title: String = ""): String = buildString {
    appendLine("@run=${title.trim()}")
    appendLine("@mode=serial")
    appendLine()
    appendLine("@task Task {")
    appendLine("  @mode=serial")
    appendLine("  @let start_url=")
    appendLine()
    appendLine("  @event Event {")
    appendLine("    <%start_url%>")
    appendLine("    {")
    appendLine("      在这里写第一条消息")
    appendLine("    }")
    appendLine("  }")
    appendLine("}")
}

// ---------------------------------------------------------------------------
// 重复计划 (Schedules & Daily Recurrence)
// ---------------------------------------------------------------------------

data class DailyRecurrence(
    val frequency: String = "daily",
    val time: String = "09:00",
    val utcOffsetMinutes: Int = 480,
) {
    fun formatUtcOffset(): String {
        val abs = kotlin.math.abs(utcOffsetMinutes)
        val sign = if (utcOffsetMinutes < 0) "-" else "+"
        val hours = (abs / 60).toString().padStart(2, '0')
        val mins = (abs % 60).toString().padStart(2, '0')
        return "UTC$sign$hours:$mins"
    }

    fun isValid(): Boolean {
        val timeOk = Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""").matches(time)
        return frequency == "daily" &&
            timeOk &&
            utcOffsetMinutes in -720..840 &&
            utcOffsetMinutes % 15 == 0
    }
}

fun defaultDailyRecurrence(): DailyRecurrence {
    val offsetSeconds = java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds
    val offsetMinutes = ((offsetSeconds / 60) / 15) * 15
    return DailyRecurrence(
        frequency = "daily",
        time = "09:00",
        utcOffsetMinutes = offsetMinutes.coerceIn(-720, 840),
    )
}

data class WorkflowRunSchedule(
    val id: String,
    val owner: String,
    val sourceRun: String,
    val title: String,
    val rule: DailyRecurrence,
    val enabled: Boolean,
    val revision: Int,
    val nextRunAt: String,
    val lastRun: String,
    val skippedCount: Int,
    val lastSkippedAt: String,
    val lastError: String,
) {
    val displayTitle: String
        get() = title.trim().ifEmpty { "未命名工作流" }
}

// ---------------------------------------------------------------------------
// 收藏与模板 (Favorites & Templates)
// ---------------------------------------------------------------------------

enum class LibraryFolderScope(val wireValue: String, val label: String) {
    FAVORITE("favorite", "收藏"),
    TEMPLATE("template", "模板");

    companion object {
        fun fromWire(value: String?): LibraryFolderScope? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class LibraryFolder(
    val id: String,
    val owner: String,
    val parent: String,
    val name: String,
    val scope: LibraryFolderScope?,
    val sortOrder: Int,
    val created: String,
    val updated: String,
)

data class FlattenedLibraryFolder(
    val folder: LibraryFolder,
    val depth: Int,
)

data class RunFavorite(
    val id: String,
    val owner: String,
    val folder: String,
    val run: String,
    val note: String,
    val sortOrder: Int,
    val created: String,
    val updated: String,
    val runRecord: WorkflowRun? = null,
    val folderRecord: LibraryFolder? = null,
)

data class WorkflowTemplate(
    val id: String,
    val owner: String,
    val folder: String,
    val title: String,
    val description: String,
    val planText: String,
    val sourceRun: String,
    val tags: List<String>,
    val sortOrder: Int,
    val created: String,
    val updated: String,
    val folderRecord: LibraryFolder? = null,
)

data class WorkflowLibrarySnapshot(
    val folders: List<LibraryFolder> = emptyList(),
    val favoriteFolders: List<LibraryFolder> = emptyList(),
    val templateFolders: List<LibraryFolder> = emptyList(),
    val favorites: List<RunFavorite> = emptyList(),
    val templates: List<WorkflowTemplate> = emptyList(),
)

fun scopedFolders(
    folders: List<LibraryFolder>,
    scope: LibraryFolderScope,
    referencedFolderIds: List<String>,
): List<LibraryFolder> {
    val byId = folders.associateBy { it.id }
    val included = folders.filter { it.scope == scope }.mapTo(mutableSetOf()) { it.id }
    for (id in referencedFolderIds) {
        if (id.isBlank()) continue
        var current = id
        val seen = mutableSetOf<String>()
        while (current.isNotBlank() && seen.add(current)) {
            val folder = byId[current] ?: break
            if (folder.scope == null || folder.scope == scope) {
                included.add(folder.id)
            }
            current = folder.parent
        }
    }
    return folders.filter { it.id in included }
}

fun flattenLibraryFolders(folders: List<LibraryFolder>): List<FlattenedLibraryFolder> {
    val byId = folders.associateBy { it.id }
    val children = mutableMapOf<String, MutableList<LibraryFolder>>()
    val roots = mutableListOf<LibraryFolder>()
    val comparator = compareBy<LibraryFolder> { it.sortOrder }.thenBy { it.name.lowercase() }

    for (folder in folders) {
        if (folder.parent.isBlank() || folder.parent !in byId || folder.parent == folder.id) {
            roots.add(folder)
        } else {
            children.getOrPut(folder.parent) { mutableListOf() }.add(folder)
        }
    }
    roots.sortWith(comparator)
    children.values.forEach { it.sortWith(comparator) }

    val result = mutableListOf<FlattenedLibraryFolder>()
    val visited = mutableSetOf<String>()
    fun visit(folder: LibraryFolder, depth: Int) {
        if (!visited.add(folder.id)) return
        result.add(FlattenedLibraryFolder(folder = folder, depth = depth))
        children[folder.id]?.forEach { visit(it, depth + 1) }
    }
    roots.forEach { visit(it, 0) }
    folders.sortedWith(comparator).forEach { visit(it, 0) }
    return result
}

// ---------------------------------------------------------------------------
// 资料库 (File Library / Drive)
// ---------------------------------------------------------------------------

data class LibraryFile(
    val id: String,
    val fileName: String,
    val size: Long,
    val sourceUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val folder: String,
    val expiresAt: String = "",
) {
    val isMarkdown: Boolean
        get() = fileName.endsWith(".md", ignoreCase = true)

    val isPdf: Boolean
        get() = fileName.endsWith(".pdf", ignoreCase = true)
}

data class LibraryFileFolder(
    val id: String,
    val name: String,
    val parent: String,
    val createdAt: String,
    val updatedAt: String,
)

data class LibraryDriveData(
    val files: List<LibraryFile> = emptyList(),
    val folders: List<LibraryFileFolder> = emptyList(),
    val unavailableCount: Int = 0,
)

enum class LibraryFileTypeFilter(val label: String) {
    ALL("全部"),
    MARKDOWN("Markdown"),
    PDF("PDF"),
}

enum class LibraryFileSort(val label: String) {
    UPDATED_DESC("最近更新"),
    NAME_ASC("名称 A-Z"),
    SIZE_DESC("大小从大到小"),
}

data class LibraryImage(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: String,
    val sourceUrl: String,
)

data class LibraryImagePage(
    val items: List<LibraryImage>,
    val cursor: String,
)

