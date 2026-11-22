package me.rerere.rikkahub.ui.pages.examdocs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.examdocs.DOCS_GUEST
import me.rerere.rikkahub.data.examdocs.DocsAccountRepository
import me.rerere.rikkahub.data.examdocs.DocsAnswer
import me.rerere.rikkahub.data.examdocs.DocsAttempt
import me.rerere.rikkahub.data.examdocs.DocsProgressRepository
import me.rerere.rikkahub.data.examdocs.DocsRecord
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.ExamDocsTree
import me.rerere.rikkahub.data.examdocs.docsJson
import me.rerere.rikkahub.data.examdocs.supportsCloudProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ExamDocsModuleStat(
    val name: String,
    val answered: Int,
    val totalQuestions: Int,
)

internal data class ExamDocsPageStat(
    val path: String,
    val title: String,
    val moduleName: String?,
    val answeredCount: Int,
    val lastTimestamp: Long,
)

internal data class ExamDocsStatsUiState(
    val isLoading: Boolean = true,
    val siteName: String = "",
    val attemptsPerDay: Map<LocalDate, Int> = emptyMap(),
    val answeredQuestions: Int = 0,
    val totalQuestions: Int = 0,
    val totalAttempts: Int = 0,
    val activeDays: Int = 0,
    val touchedPages: Int = 0,
    val moduleStats: List<ExamDocsModuleStat> = emptyList(),
    val recentPages: List<ExamDocsPageStat> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExamDocsStatsVM(
    private val examDocsRepository: ExamDocsRepository,
    private val progressRepository: DocsProgressRepository,
    accountRepository: DocsAccountRepository,
) : ViewModel() {

    private val treeFlow = MutableStateFlow<Pair<String, ExamDocsTree?>>( "" to null)

    private val siteAndAccount = combine(
        examDocsRepository.siteSettingsFlow.map { it.active },
        accountRepository.state,
    ) { site, account ->
        val accountId = if (site.supportsCloudProgress()) account.user?.id ?: DOCS_GUEST else DOCS_GUEST
        site to accountId
    }

    init {
        viewModelScope.launch {
            examDocsRepository.siteSettingsFlow
                .map { it.active }
                .collect { site ->
                    val tree = runCatching { examDocsRepository.loadTree(site) }.getOrNull()
                    treeFlow.value = site.baseUrl to tree
                }
        }
    }

    val uiState: StateFlow<ExamDocsStatsUiState> = siteAndAccount
        .flatMapLatest { (site, accountId) ->
            progressRepository.observeAllRecords(site.baseUrl, accountId)
                .combine(treeFlow) { records, (treeSite, tree) ->
                    buildStats(
                        siteName = site.name,
                        records = records,
                        tree = tree.takeIf { treeSite == site.baseUrl },
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExamDocsStatsUiState(),
        )

    private fun buildStats(
        siteName: String,
        records: List<DocsRecord>,
        tree: ExamDocsTree?,
    ): ExamDocsStatsUiState {
        val zone = ZoneId.systemDefault()
        val answers = mutableListOf<Pair<DocsRecord, DocsAnswer>>()
        val attempts = mutableListOf<Pair<DocsRecord, DocsAttempt>>()

        for (row in records) {
            when (row.kind) {
                "answers" -> runCatching { docsJson.decodeFromString<DocsAnswer>(row.payload) }
                    .getOrNull()?.let { answers.add(row to it) }
                "attempts" -> runCatching { docsJson.decodeFromString<DocsAttempt>(row.payload) }
                    .getOrNull()?.let { attempts.add(row to it) }
            }
        }

        val submittedAnswers = answers.filter { (_, ans) -> ans.state.submitted }
        val attemptsPerDay = mutableMapOf<LocalDate, Int>()
        if (attempts.isNotEmpty()) {
            for ((row, attempt) in attempts) {
                val ts = runCatching { Instant.parse(attempt.createdAt).toEpochMilli() }.getOrElse { row.timestamp }
                val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                attemptsPerDay[date] = (attemptsPerDay[date] ?: 0) + 1
            }
        } else {
            for ((row, ans) in submittedAnswers) {
                val ts = runCatching { ans.timestamp }.getOrElse { row.timestamp }
                val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                attemptsPerDay[date] = (attemptsPerDay[date] ?: 0) + 1
            }
        }

        val entryByPath = tree?.entries?.associateBy { ExamDocsRepository.normalizeDocPath(it.page.url) }.orEmpty()
        val answeredByPath = submittedAnswers.groupBy { (_, ans) ->
            ExamDocsRepository.normalizeDocPath(ans.documentPath)
        }

        val moduleStats = tree?.modules.orEmpty().mapNotNull { module ->
            val paths = collectPagePaths(module)
            val answeredInModule = paths.sumOf { answeredByPath[it]?.size ?: 0 }
            if (module.questions <= 0 && answeredInModule == 0) {
                null
            } else {
                ExamDocsModuleStat(
                    name = module.name,
                    answered = answeredInModule,
                    totalQuestions = module.questions,
                )
            }
        }

        val recentPages = answeredByPath.entries.map { (path, rows) ->
            val entry = entryByPath[path]
            val lastTs = rows.maxOf { (row, _) -> row.timestamp }
            val fallbackTitle = path.trim('/').substringAfterLast('/').ifBlank { "首页" }
            ExamDocsPageStat(
                path = path,
                title = entry?.page?.name ?: fallbackTitle,
                moduleName = entry?.moduleName,
                answeredCount = rows.size,
                lastTimestamp = lastTs,
            )
        }.sortedByDescending { it.lastTimestamp }.take(15)

        val totalQuestions = tree?.modules?.sumOf { it.questions } ?: 0
        val totalAttemptCount = maxOf(
            attempts.size,
            answers.sumOf { (_, ans) -> ans.state.attempt },
        )

        return ExamDocsStatsUiState(
            isLoading = false,
            siteName = siteName,
            attemptsPerDay = attemptsPerDay,
            answeredQuestions = submittedAnswers.size,
            totalQuestions = totalQuestions,
            totalAttempts = totalAttemptCount,
            activeDays = attemptsPerDay.count { it.value > 0 },
            touchedPages = answeredByPath.size,
            moduleStats = moduleStats,
            recentPages = recentPages,
        )
    }

    private fun collectPagePaths(node: ExamDocNode): List<String> = when (node) {
        is ExamDocNode.Page -> listOf(ExamDocsRepository.normalizeDocPath(node.url))
        is ExamDocNode.Folder -> node.children.flatMap(::collectPagePaths)
    }
}
