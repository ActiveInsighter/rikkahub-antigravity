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
    suspend fun keepSynced(selectedId: Flow<Uuid?>) {
        workflowRepository.session.collectLatest { session ->
            if (session != null) syncSession(session, selectedId)
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
                    val changes = Channel<Unit>(Channel.CONFLATED)
                    val task = launch {
                        var previous: RemoteThreadConversation? = null
                        changes.trySend(Unit)
                        for (change in changes) {
                            // A transaction can publish several notifications for the same update.
                            delay(150)
                            previous = requests.withPermit {
                                retrySync { repository.syncRemoteJob(session, id, jobId, previous) } ?: previous
                            }
                        }
                    }
                    workers[jobId] = Worker(task, changes)
                }
            }
        }

        val metadataChanges = Channel<Unit>(Channel.CONFLATED)
        val dirtyRoots = mutableSetOf<String>()
        var reconcileAll = true
        launch {
            metadataChanges.trySend(Unit)
            for (change in metadataChanges) {
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
                    reconcileAll = true
                    metadataChanges.trySend(Unit)
                    workers.values.forEach { it.changes.trySend(Unit) }
                }
                is AgentRealtimeEvent.JobChanged -> {
                    dirtyRoots += event.threadRoot
                    metadataChanges.trySend(Unit)
                    workers[event.jobId]?.changes?.trySend(Unit)
                }
                is AgentRealtimeEvent.MessageChanged -> workers[event.jobId]?.changes?.trySend(Unit)
            }
        }
    }

    private data class Worker(val task: Job, val changes: Channel<Unit>)

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
