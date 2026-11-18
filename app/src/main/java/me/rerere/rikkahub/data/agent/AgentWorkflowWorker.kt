package me.rerere.rikkahub.data.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AgentWorkflowWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val settingsStore: AgentSettingsStore by inject()
    private val cloudClient: AgentCloudClient by inject()
    private val workflowRepository: WorkflowRepository by inject()

    override suspend fun doWork(): Result {
        if (workflowRepository.session.value == null) return Result.success()
        val now = System.currentTimeMillis()
        val currentTasks = settingsStore.current.agentSchedules
        if (currentTasks.none { it.enabled }) return Result.success()

        val updatedTasks = currentTasks.toMutableList()
        var changed = false
        for (index in updatedTasks.indices) {
            val task = updatedTasks[index]
            if (!task.enabled) continue
            val dueAt = task.nextRunAt ?: 0L
            if (dueAt > now) continue
            val intervalMs = task.intervalMinutes.coerceAtLeast(5) * 60_000L
            val outcome = runCatching {
                cloudClient.submitMessage(
                    prompt = task.prompt,
                    title = "[定时] ${task.name}",
                    modelOverride = task.model,
                    thinkingLevelOverride = task.thinkingLevel,
                    systemPromptOverride = task.systemPrompt,
                )
            }
            updatedTasks[index] = outcome.fold(
                onSuccess = { submitted ->
                    task.copy(
                        lastRunAt = now,
                        nextRunAt = now + intervalMs,
                        lastJobId = submitted.jobId,
                        lastStatus = submitted.status,
                        lastError = null,
                    )
                },
                onFailure = { error ->
                    task.copy(
                        lastRunAt = now,
                        nextRunAt = now + intervalMs,
                        lastStatus = "failed",
                        lastError = error.message ?: "触发失败",
                    )
                },
            )
            changed = true
        }
        if (changed) {
            settingsStore.update { it.copy(agentSchedules = updatedTasks) }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "agent_workflow_scheduled_tasks"

        fun sync(context: Context, tasks: List<AgentScheduledTask>) {
            val manager = WorkManager.getInstance(context)
            if (tasks.none { it.enabled }) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AgentWorkflowWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
