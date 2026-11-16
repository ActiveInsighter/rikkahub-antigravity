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

data class WorkflowRunPage(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<WorkflowRun>,
)

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
