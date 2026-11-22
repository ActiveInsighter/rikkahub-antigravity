package me.rerere.rikkahub.data.examdocs

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal enum class DocsWrite { Draft, Submit, Reset }

internal class DocsProgressRepository(
    private val context: Context,
    private val database: DocsDatabase,
    private val accounts: DocsAccountRepository,
    private val cloud: DocsCloudClient,
) {
    private val dao = database.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val syncLock = Mutex()
    private val lastWrite = AtomicLong()
    private val _syncing = MutableStateFlow<String?>(null)
    private val _syncError = MutableStateFlow<Pair<String, String>?>(null)
    private val _storageError = MutableStateFlow<String?>(null)

    val syncing = _syncing.asStateFlow()
    val syncError = _syncError.asStateFlow()
    val storageError = _storageError.asStateFlow()

    init {
        scope.launch {
            accounts.state.collect { account ->
                if (account.user != null && !account.needsLogin) requestSync(account.user.id, force = true)
            }
        }
    }

    fun page(key: DocsPageKey) = dao.observePage(key.site, key.account, key.path).map { rows ->
        DocsPageProgress(
            answers = rows.filter { it.kind == "answers" }.associate {
                val answer = docsJson.decodeFromString<DocsAnswer>(it.payload)
                it.recordId to DocsSavedAnswer(answer.state, it.draft, it.timestamp)
            },
            marks = rows.filter { it.kind == "marks" }.associate {
                it.recordId to docsJson.decodeFromString<DocsMark>(it.payload)
            },
            tags = rows.filter { it.kind == "tags" }.map {
                docsJson.decodeFromString<DocsTag>(it.payload)
            }.filterNot(DocsTag::deleted).sortedBy(DocsTag::name),
        )
    }.distinctUntilChanged().flowOn(Dispatchers.IO).catch {
        _storageError.value = "无法读取文档本地数据库"
        emit(DocsPageProgress(emptyMap(), emptyMap(), emptyList()))
    }

    fun pending(userId: String) = dao.observePendingCount(DEFAULT_EXAM_DOCS_BASE_URL, userId)

    fun position(userId: String) = dao.observePosition(DEFAULT_EXAM_DOCS_BASE_URL, userId)

    fun saveAnswer(
        page: DocsPageKey,
        key: DocsQuestionKey,
        state: DocsAnswerState,
        draft: String,
        write: DocsWrite,
        previousTimestamp: Long,
    ): DocsSavedAnswer {
        val timestamp = nextTimestamp(previousTimestamp)
        val answer = DocsAnswer(key.documentPath, key.questionId, key.questionRevision, state, instant(timestamp))
        val queue = page.canSync && key.hasRevision && write != DocsWrite.Draft
        writeLocally(page, queue) {
            state.checkWireLimits()
            val saved = DocsRecord(
                page.site, page.account, "answers", key.answerId, key.documentPath,
                timestamp, docsJson.encodeToString(answer), draft,
            )
            dao.putRecord(saved)
            if (queue) enqueue(saved)
            if (write == DocsWrite.Submit) {
                val attempt = DocsAttempt(
                    key.documentPath, key.questionId, key.questionRevision, UUID.randomUUID().toString(),
                    state, instant(timestamp),
                )
                val record = saved.copy(
                    kind = "attempts", recordId = attempt.attemptId, payload = docsJson.encodeToString(attempt), draft = "",
                )
                dao.putRecord(record)
                if (queue) enqueue(record)
            }
        }
        return DocsSavedAnswer(state, draft, timestamp)
    }

    fun saveMark(page: DocsPageKey, mark: DocsMark, syncable: Boolean) {
        val timestamp = nextTimestamp(mark.timestamp)
        val updated = mark.copy(updatedAt = instant(timestamp))
        val queue = page.canSync && syncable
        writeLocally(page, queue) {
            val record = DocsRecord(
                page.site, page.account, "marks", mark.id, mark.documentPath,
                timestamp, docsJson.encodeToString(updated),
            )
            dao.putRecord(record)
            if (queue) enqueue(record)
        }
    }

    /** The account's existing rows win, including resets and cleared notes. */
    suspend fun importGuest(userId: String): Int {
        val site = DEFAULT_EXAM_DOCS_BASE_URL
        require(accounts.state.value.user?.id == userId)
        check(sync(userId) && accounts.state.value.let { it.user?.id == userId && !it.needsLogin }) {
            "请先联网完成账号同步，再导入访客记录"
        }
        val count = writes.withLock {
            database.withTransaction {
                var copied = 0
                for (row in dao.records(site, DOCS_GUEST)) {
                    if (dao.record(site, userId, row.kind, row.recordId) != null) continue
                    val upload = when (row.kind) {
                        "answers" -> {
                            val answer = docsJson.decodeFromString<DocsAnswer>(row.payload)
                            if (!answer.key.hasRevision) continue
                            answer.state.submitted || answer.state.attempt > 0 &&
                                answer.state.selected.isEmpty() && answer.state.values.all(String::isBlank)
                        }
                        "attempts" -> {
                            val attempt = docsJson.decodeFromString<DocsAttempt>(row.payload)
                            if (attempt.questionRevision == "unversioned") continue
                            true
                        }
                        "marks" -> {
                            val mark = docsJson.decodeFromString<DocsMark>(row.payload)
                            if (mark.questionId.startsWith("local:") || (!mark.favorite && mark.note.isBlank())) continue
                            true
                        }
                        else -> continue
                    }
                    val copy = row.copy(account = userId)
                    dao.putRecord(copy)
                    if (upload) enqueue(copy)
                    copied++
                }
                copied
            }
        }
        if (count > 0) requestSync(userId, force = true)
        return count
    }

    private suspend fun enqueue(record: DocsRecord) {
        dao.enqueue(DocsPendingRecord(record.site, record.account, record.kind, record.recordId, record.timestamp, record.payload))
    }

    private fun writeLocally(page: DocsPageKey, upload: Boolean, write: suspend () -> Unit) {
        // Taking the mutex before returning orders consecutive UI callbacks even during navigation.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                writes.withLock { database.withTransaction { write() } }
                _storageError.value = null
                if (upload) schedule(page.account, delay = 400, replace = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _storageError.value = "未能保存文档记录，请检查设备存储后重试"
            }
        }
    }

    fun requestSync(userId: String, force: Boolean = false) {
        if (!cloud.configured) return
        scope.launch {
            val last = dao.position(DEFAULT_EXAM_DOCS_BASE_URL, userId)?.syncedAt ?: 0
            if (force || System.currentTimeMillis() - last >= 60_000) {
                schedule(userId, delay = 0, replace = force && _syncing.value != userId)
            }
        }
    }

    private fun schedule(userId: String, delay: Long, replace: Boolean) {
        val work = OneTimeWorkRequestBuilder<DocsSyncWorker>()
            .setInputData(workDataOf(DocsSyncWorker.ACCOUNT to userId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "exam-docs-sync:$userId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            work,
        )
    }

    /** Returns true once the durable queue and the current page of the cloud feed are drained. */
    suspend fun sync(userId: String): Boolean = syncLock.withLock {
        if (!cloud.configured) return@withLock true
        _syncing.value = userId
        _syncError.value = null
        val site = DEFAULT_EXAM_DOCS_BASE_URL
        try {
            repeat(20) {
                val token = accounts.accessToken(userId) ?: return@withLock true
                val pending = dao.pending(site, userId, BATCH)
                val byKind = pending.groupBy(DocsPendingRecord::kind) { docsJson.parseToJsonElement(it.payload).jsonObject }
                val request = DocsSyncRequest(
                    DocsChanges(
                        answers = byKind["answers"].orEmpty(),
                        attempts = byKind["attempts"].orEmpty(),
                        marks = byKind["marks"].orEmpty(),
                    ),
                    p_cursor = dao.position(site, userId)?.cursor ?: "0",
                    p_limit = 150,
                )
                val response = try {
                    cloud.exchange(request, token)
                } catch (error: DocsCloudError) {
                    if (error.status != 401) throw error
                    val renewed = accounts.accessToken(userId, forced = true) ?: return@withLock true
                    cloud.exchange(request, renewed)
                }
                applyExchange(site, userId, pending, response)
                if (!response.hasMore && pending.size < BATCH && dao.pending(site, userId, 1).isEmpty()) {
                    return@withLock true
                }
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _syncError.value = userId to if (error is DocsCloudError) {
                error.message.orEmpty()
            } else {
                "同步暂未完成，已保留本机记录，将自动重试"
            }
            false
        } finally {
            _syncing.value = null
        }
    }

    internal suspend fun applyExchange(
        site: String,
        userId: String,
        sent: List<DocsPendingRecord>,
        response: DocsSyncResponse,
    ) {
        require(response.cursor.toLongOrNull()?.let { it >= 0 } == true)
        val versions = sent.associate { (it.kind to it.recordId) to it.timestamp }
        database.withTransaction {
            for (answer in response.answers) {
                answer.state.checkWireLimits()
                val local = dao.record(site, userId, "answers", answer.key.answerId)
                if (shouldApply(local, answer.timestamp, versions["answers" to answer.key.answerId])) {
                    dao.putRecord(DocsRecord(
                        site, userId, "answers", answer.key.answerId, answer.documentPath,
                        answer.timestamp, docsJson.encodeToString(answer), local?.draft.orEmpty(),
                    ))
                }
            }
            for (attempt in response.attempts) {
                if (dao.record(site, userId, "attempts", attempt.attemptId) == null) {
                    attempt.state.checkWireLimits()
                    dao.putRecord(DocsRecord(
                        site, userId, "attempts", attempt.attemptId, attempt.documentPath,
                        Instant.parse(attempt.createdAt).toEpochMilli(), docsJson.encodeToString(attempt),
                    ))
                }
            }
            for (tag in response.tags) {
                val timestamp = Instant.parse(tag.updatedAt).toEpochMilli()
                if (shouldApply(dao.record(site, userId, "tags", tag.tagId), timestamp, null)) {
                    dao.putRecord(DocsRecord(site, userId, "tags", tag.tagId, "", timestamp, docsJson.encodeToString(tag)))
                }
            }
            for (merge in response.merged) {
                dao.deleteRecord(site, userId, "tags", merge.from)
            }
            for (mark in response.marks) {
                if (shouldApply(dao.record(site, userId, "marks", mark.id), mark.timestamp, versions["marks" to mark.id])) {
                    dao.putRecord(DocsRecord(
                        site, userId, "marks", mark.id, mark.documentPath, mark.timestamp, docsJson.encodeToString(mark),
                    ))
                }
            }
            for (record in sent) {
                dao.acknowledge(site, userId, record.kind, record.recordId, record.timestamp)
            }
            dao.putPosition(DocsSyncPosition(site, userId, response.cursor, System.currentTimeMillis()))
        }
    }

    private fun shouldApply(local: DocsRecord?, remote: Long, sent: Long?): Boolean =
        local == null || remote >= local.timestamp || local.timestamp == sent

    private fun nextTimestamp(previous: Long) = lastWrite.updateAndGet {
        maxOf(System.currentTimeMillis(), it + 1, previous + 1)
    }

    private fun instant(timestamp: Long) = Instant.ofEpochMilli(timestamp).toString()

    private companion object {
        const val BATCH = 100
    }
}

internal class DocsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository by inject<DocsProgressRepository>()

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT) ?: return Result.failure()
        return if (repository.sync(account)) Result.success() else Result.retry()
    }

    companion object {
        const val ACCOUNT = "account"
    }
}
