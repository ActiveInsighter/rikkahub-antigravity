package me.rerere.rikkahub.data.examdocs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

internal const val DOCS_GUEST = "guest"

internal val docsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun ExamDocsSite.supportsCloudProgress() = baseUrl == DEFAULT_EXAM_DOCS_BASE_URL

internal data class DocsPageKey(val site: String, val account: String, val path: String) {
    val canSync: Boolean get() = site == DEFAULT_EXAM_DOCS_BASE_URL && account != DOCS_GUEST
}

@Serializable
internal data class DocsQuestionKey(val documentPath: String, val questionId: String, val questionRevision: String) {
    val answerId: String get() = docsJson.encodeToString(listOf(documentPath, questionId, questionRevision))
    val markId: String get() = docsJson.encodeToString(listOf(documentPath, questionId))
    val hasRevision: Boolean get() = questionRevision != "unversioned"
}

@Serializable
internal data class DocsAnswerState(
    val selected: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val submitted: Boolean = false,
    val attempt: Int = 0,
) {
    fun checkWireLimits() {
        require(attempt >= 0)
        require(selected.size <= 64 && selected.all { it.length <= 128 && '\u0000' !in it })
        require(values.size <= 64 && values.all { it.length <= 8192 && '\u0000' !in it })
        require(docsJson.encodeToString(this).toByteArray().size <= 16_384)
    }
}

@Serializable
internal data class DocsAnswer(
    val documentPath: String,
    val questionId: String,
    val questionRevision: String,
    val state: DocsAnswerState,
    val updatedAt: String,
) {
    val key get() = DocsQuestionKey(documentPath, questionId, questionRevision)
    val timestamp get() = Instant.parse(updatedAt).toEpochMilli()
}

@Serializable
internal data class DocsAttempt(
    val documentPath: String,
    val questionId: String,
    val questionRevision: String,
    val attemptId: String,
    val state: DocsAnswerState,
    val createdAt: String,
)

@Serializable
internal data class DocsTag(
    val tagId: String,
    val name: String,
    val color: String,
    val deleted: Boolean,
    val updatedAt: String,
    val createdAt: String,
)

@Serializable
internal data class DocsMark(
    val documentPath: String,
    val questionId: String,
    val favorite: Boolean = false,
    val note: String = "",
    val excerpt: String = "",
    val tagIds: List<String> = emptyList(),
    val updatedAt: String = Instant.EPOCH.toString(),
) {
    val id get() = DocsQuestionKey(documentPath, questionId, "unversioned").markId
    val timestamp get() = Instant.parse(updatedAt).toEpochMilli()
}

@Serializable
internal data class DocsTagMerge(val from: String, val to: String)

@Serializable
internal data class DocsChanges(
    val answers: List<JsonObject> = emptyList(),
    val attempts: List<JsonObject> = emptyList(),
    val tags: List<JsonObject> = emptyList(),
    val marks: List<JsonObject> = emptyList(),
)

@Serializable
internal data class DocsSyncRequest(
    val p_changes: DocsChanges,
    val p_cursor: String,
    val p_limit: Int,
)

@Serializable
internal data class DocsSyncResponse(
    val answers: List<DocsAnswer>,
    val attempts: List<DocsAttempt>,
    val tags: List<DocsTag>,
    val marks: List<DocsMark>,
    val merged: List<DocsTagMerge>,
    val cursor: String,
    val hasMore: Boolean,
    val reset: Boolean = false,
)

internal data class DocsSavedAnswer(val state: DocsAnswerState, val draft: String, val timestamp: Long)
internal data class DocsPageProgress(
    val answers: Map<String, DocsSavedAnswer>,
    val marks: Map<String, DocsMark>,
    val tags: List<DocsTag>,
)
