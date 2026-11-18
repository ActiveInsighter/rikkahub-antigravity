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
import kotlinx.coroutines.withTimeoutOrNull
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
                    val active = PocketBaseAgentCloudClient.isActiveStatus(conversation.remoteStatus.orEmpty())
                    if (id !in submitting && (id == selected || active)) {
                        jobId to Target(id, active)
                    } else {
                        null
                    }
                }.toMap()
            }.distinctUntilChanged().collect { targets ->
                val obsolete = workers.keys - targets.keys
                for (jobId in obsolete) workers.remove(jobId)?.task?.cancel()
                for ((jobId, target) in targets) {
                    val current = workers[jobId]
                    if (current != null) {
                        current.updates.setActive(target.active)
                        continue
                    }
                    val updates = AgentJobInvalidations(target.active)
                    val task = launch {
                        var previous: RemoteThreadConversation? = null
                        while (true) {
                            val refreshJob = updates.next()
                            val updated = requests.withPermit {
                                retrySync { repository.syncRemoteJob(session, target.id, jobId, previous, refreshJob) }
                            }
                            if (updated != null) {
                                previous = updated
                                updates.setActive(updated.isActive)
                            } else {
                                updates.requireJobRefresh(refreshJob)
                            }
                        }
                    }
                    workers[jobId] = Worker(task, updates)
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
                    workers.values.forEach { it.updates.invalidate(refreshJob = true) }
                }
                is AgentRealtimeEvent.JobChanged -> {
                    dirtyRoots += event.threadRoot
                    metadataChanges.trySend(Unit)
                    workers[event.jobId]?.updates?.invalidate(refreshJob = true)
                }
                is AgentRealtimeEvent.MessageChanged -> workers[event.jobId]?.updates?.invalidate(refreshJob = false)
            }
        }
    }

    private data class Target(val id: Uuid, val active: Boolean)

    private data class Worker(val task: Job, val updates: AgentJobInvalidations)

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


// Running executors normally publish a persisted heartbeat every 30 seconds.
// If one goes quiet, reconcile just this job to cover a stalled stream or a missed terminal event.
internal class AgentJobInvalidations(initiallyActive: Boolean) {
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private var active = initiallyActive
    private var refreshJob = true

    init {
        changes.trySend(Unit)
    }

    fun setActive(value: Boolean) {
        if (!active && value) invalidate(refreshJob = true)
        active = value
    }

    fun requireJobRefresh(value: Boolean) {
        refreshJob = refreshJob || value
    }

    fun invalidate(refreshJob: Boolean) {
        requireJobRefresh(refreshJob)
        changes.trySend(Unit)
    }

    suspend fun next(): Boolean {
        val timedOut = if (active) {
            withTimeoutOrNull(45_000) { changes.receive() } == null
        } else {
            changes.receive()
            false
        }
        // A transaction can publish several notifications for the same update.
        if (!timedOut) delay(150)
        changes.tryReceive()
        return (timedOut || refreshJob).also { refreshJob = false }
    }
}
