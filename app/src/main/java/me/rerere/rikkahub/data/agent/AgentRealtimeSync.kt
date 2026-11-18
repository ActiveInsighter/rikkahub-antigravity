package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import kotlin.uuid.Uuid

internal class AgentRealtimeSync(
    private val realtime: PocketBaseAgentRealtime,
    private val repository: AgentRepository,
    private val workflowRepository: WorkflowRepository,
) {
    suspend fun keepSynced(selectedId: Flow<Uuid?>) = coroutineScope {
        val selected = selectedId.stateIn(this, SharingStarted.Eagerly, null)
        launch {
            try {
                selected.collect { repository.showConversation(it) }
            } finally {
                repository.showConversation(null)
            }
        }
        combine(selected, repository.observeActiveConversationIds(), repository.submittingIds, workflowRepository.session) {
                visible, active, submitting, session ->
            session.takeIf { visible != null || active.isNotEmpty() || submitting.isNotEmpty() }
        }.distinctUntilChanged().collectLatest { session ->
            if (session != null) syncSession(session, selected)
        }
    }

    private suspend fun syncSession(session: WorkflowSession, selectedId: Flow<Uuid?>) = coroutineScope {
        val workers = mutableMapOf<String, Worker>()
        val requests = Semaphore(4)

        launch {
            combine(
                selectedId,
                repository.observeRemoteConversations(),
                repository.submittingIds,
            ) { selected, conversations, submitting ->
                conversations.mapNotNull { conversation ->
                    val jobId = conversation.remoteJobId ?: return@mapNotNull null
                    val id = Uuid.parse(conversation.id)
                    if (id !in submitting &&
                        (id == selected || PocketBaseAgentCloudClient.isActiveStatus(conversation.remoteStatus.orEmpty()))
                    ) {
                        jobId to id
                    } else {
                        null
                    }
                }.toMap()
            }.distinctUntilChanged().collect { targets ->
                val obsolete = workers.keys - targets.keys
                for (jobId in obsolete) workers.remove(jobId)?.task?.cancel()
                for ((jobId, id) in targets) {
                    if (jobId in workers) continue
                    val worker = Worker()
                    worker.task = launch {
                        var previous: RemoteThreadConversation? = null
                        worker.invalidate(refreshJob = true)
                        for (change in worker.changes) {
                            // A transaction can publish several notifications for the same update.
                            delay(150)
                            worker.changes.tryReceive()
                            val refreshJob = worker.refreshJob
                            worker.refreshJob = false
                            val updated = requests.withPermit {
                                retrySync { repository.syncRemoteJob(session, id, jobId, previous, refreshJob) }
                            }
                            if (updated != null) {
                                previous = updated
                            } else {
                                worker.refreshJob = worker.refreshJob || refreshJob
                            }
                        }
                    }
                    workers[jobId] = worker
                }
            }
        }

        val metadataChanges = Channel<Unit>(Channel.CONFLATED)
        val dirtyRoots = mutableSetOf<String>()
        var reconcileAll = false
        val initialRefresh = launch {
            // Keep the initial list usable if a proxy is slow to establish the event stream.
            delay(1_000)
            reconcileAll = true
            metadataChanges.trySend(Unit)
        }
        launch {
            for (change in metadataChanges) {
                delay(100)
                metadataChanges.tryReceive()
                if (reconcileAll) {
                    reconcileAll = false
                    dirtyRoots.clear()
                    retrySync {
                        repository.syncFoldersWithRemote()
                        repository.syncThreads(session)
                    }
                } else {
                    val roots = dirtyRoots.toList()
                    dirtyRoots.clear()
                    for (root in roots) retrySync { repository.syncThreads(session, root) }
                }
            }
        }

        realtime.observe(session).collect { event ->
            when (event) {
                AgentRealtimeEvent.Subscribed -> {
                    initialRefresh.cancel()
                    reconcileAll = true
                    metadataChanges.trySend(Unit)
                    workers.values.forEach { it.invalidate(refreshJob = true) }
                }
                is AgentRealtimeEvent.JobChanged -> {
                    dirtyRoots += event.threadRoot
                    metadataChanges.trySend(Unit)
                    workers[event.jobId]?.invalidate(refreshJob = true)
                }
                is AgentRealtimeEvent.MessageChanged -> workers[event.jobId]?.invalidate(refreshJob = false)
            }
        }
    }

    private class Worker {
        val changes = Channel<Unit>(Channel.CONFLATED)
        var refreshJob = false
        lateinit var task: Job

        fun invalidate(refreshJob: Boolean) {
            this.refreshJob = this.refreshJob || refreshJob
            changes.trySend(Unit)
        }
    }

    private suspend fun <T> retrySync(sync: suspend () -> T): T? {
        repeat(3) { attempt ->
            try {
                return sync()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val status = (error as? WorkflowApiException)?.statusCode
                if (status == 404) return null
                if (attempt == 2 || (status != null && status in 400..499)) {
                    Log.w("AgentRealtime", "Agent cache sync failed (${status ?: error.javaClass.simpleName})")
                    return null
                }
                delay(500L * (attempt + 1))
            }
        }
        return null
    }
}
