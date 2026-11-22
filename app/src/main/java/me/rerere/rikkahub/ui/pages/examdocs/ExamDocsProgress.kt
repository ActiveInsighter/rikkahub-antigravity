package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.data.examdocs.DOCS_GUEST
import me.rerere.rikkahub.data.examdocs.DocsAccountRepository
import me.rerere.rikkahub.data.examdocs.DocsAccountState
import me.rerere.rikkahub.data.examdocs.DocsPageKey
import me.rerere.rikkahub.data.examdocs.DocsPageProgress
import me.rerere.rikkahub.data.examdocs.DocsProgressRepository
import me.rerere.rikkahub.data.examdocs.DocsQuestionKey
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import me.rerere.rikkahub.data.examdocs.supportsCloudProgress
import org.koin.compose.koinInject
import java.util.IdentityHashMap

internal data class DocsReadingProgress(
    val page: DocsPageKey?,
    val snapshot: DocsPageProgress?,
    val identities: Map<ExamDocsSegment.CustomBlock, DocsQuestionKey>,
    val repository: DocsProgressRepository,
    val storageError: String?,
)

internal val LocalDocsReadingProgress = staticCompositionLocalOf<DocsReadingProgress?> { null }

@Composable
internal fun ExamDocsProgressProvider(
    site: ExamDocsSite,
    pageUrl: String,
    document: ExamDocsDocument,
    content: @Composable () -> Unit,
) {
    val repository = koinInject<DocsProgressRepository>()
    val account = if (site.supportsCloudProgress()) {
        val state by koinInject<DocsAccountRepository>().state.collectAsStateWithLifecycle()
        state
    } else {
        DocsAccountState(restoring = false)
    }
    val page = when {
        account.restoring -> null
        else -> DocsPageKey(site.baseUrl, account.user?.id ?: DOCS_GUEST, ExamDocsRepository.normalizeDocPath(pageUrl))
    }
    val identities = remember(document, page?.path) {
        if (page == null) emptyMap() else indexQuestions(page.path, document.blocks)
    }
    LaunchedEffect(page, account.needsLogin) {
        if (page?.canSync == true && !account.needsLogin) repository.requestSync(page.account)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (page?.canSync == true && !account.needsLogin) repository.requestSync(page.account)
    }
    key(page) {
        val progress = remember(page) { if (page == null) flowOf(null) else repository.page(page) }
        val snapshot by progress.collectAsStateWithLifecycle(initialValue = null)
        val storageError by repository.storageError.collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalDocsReadingProgress provides DocsReadingProgress(page, snapshot, identities, repository, storageError),
            content = content,
        )
    }
}

private fun indexQuestions(path: String, segments: List<ExamDocsSegment>): Map<ExamDocsSegment.CustomBlock, DocsQuestionKey> {
    val identities = IdentityHashMap<ExamDocsSegment.CustomBlock, DocsQuestionKey>()
    var ordinal = 0
    fun visit(children: List<ExamDocsSegment>) {
        for (child in children) {
            if (child !is ExamDocsSegment.CustomBlock) continue
            if (child.type == "question") {
                ordinal++
                val authoredId = child.attrs["id"]?.takeIf { it.isNotBlank() && it.length <= 200 }
                val revision = child.attrs["revision"]?.takeIf { it.matches(Regex("[a-f0-9]{8}")) }
                identities[child] = DocsQuestionKey(path, authoredId ?: "local:$ordinal", revision?.takeIf { authoredId != null } ?: "unversioned")
            }
            visit(child.blocks)
        }
    }
    visit(segments)
    return identities
}
