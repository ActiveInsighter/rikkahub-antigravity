package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import kotlin.uuid.Uuid

internal class AgentSync(
    private val repository: AgentRepository,
    private val workflow: WorkflowRepository,
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
        combine(selected, repository.observeActiveConversationIds(), repository.submittingIds, workflow.session) {
                visible, active, submitting, account ->
            account?.takeIf {
                it.baseUrl.trimEnd('/') == DEFAULT_POCKETBASE_URL &&
                    (visible != null || active.isNotEmpty() || submitting.isNotEmpty())
            }
        }.distinctUntilChanged().collectLatest { account ->
            if (account != null) syncSession(account, selected)
        }
    }

    private suspend fun syncSession(account: WorkflowSession, selectedId: Flow<Uuid?>) = coroutineScope {
        val workers = mutableMapOf<Pair<Uuid, String>, Job>()
        val lastSynced = mutableMapOf<Uuid, RemoteThreadConversation>()
        val requests = Semaphore(4)
        launch {
            while (true) {
                attempt { repository.syncThreads(account) }
                delay(20_000)
            }
        }
        combine(selectedId, repository.observeRemoteConversations(), repository.submittingIds) {
                selected, conversations, submitting ->
            conversations.mapNotNull { conversation ->
                val jobId = conversation.remoteJobId ?: return@mapNotNull null
                val id = Uuid.parse(conversation.id)
                val status = conversation.remoteStatus.orEmpty()
                if (status != "unavailable" && id !in submitting && (id == selected || isAgentActiveStatus(status))) {
                    id to jobId
                } else null
            }.toSet()
        }.distinctUntilChanged().collect { targets ->
            for (target in workers.keys - targets) workers.remove(target)?.cancel()
            lastSynced.keys.retainAll(targets.mapTo(hashSetOf()) { it.first })
            for (target in targets - workers.keys) {
                val (id, jobId) = target
                workers[target] = launch {
                    // Poll events from the last saved cursor. The task snapshot supplies approval state.
                    while (true) {
                        val updated = requests.withPermit {
                            attempt { repository.syncRemoteJob(account, id, jobId, lastSynced[id]) }
                        }
                        if (updated != null) lastSynced[id] = updated
                        delay(if (updated?.isActive == true) 1_000 else 6_000)
                    }
                }
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val status = (error as? WorkflowApiException)?.statusCode ?: error.javaClass.simpleName
        Log.w("AgentSync", "ACP sync failed ($status)")
        null
    }
}
