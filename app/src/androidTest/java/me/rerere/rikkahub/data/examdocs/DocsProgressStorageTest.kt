package me.rerere.rikkahub.data.examdocs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DocsProgressStorageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, DocsDatabase::class.java).build()
    private val dao = database.dao()
    private val exam = builtInDocsSites[0]
    private val study = builtInDocsSites[1]
    private val cloud = DocsCloudClient(OkHttpClient())
    private val progress = DocsProgressRepository(context, database, DocsAccountRepository(context, cloud), cloud)
    private val question = DocsQuestionKey("/docs/same", "common-id", "aa123456")

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readsExamAndStudyFromSeparatePersistentCachesWithoutNetwork() = runBlocking {
        var available = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (!available) throw IOException("offline")
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("# ${request.url.host}".toResponseBody("text/markdown".toMediaType())).build()
        }.build()
        val online = ExamDocsRepository(context, client, docsJson, database)
        online.fetchDocument(exam, question.documentPath, true, false)
        online.fetchDocument(study, question.documentPath, true, false)

        available = false
        val reopened = ExamDocsRepository(context, client, docsJson, database)
        assertEquals(exam.baseUrl.substringAfter("https://"), reopened.fetchDocument(exam, question.documentPath, true, false).headings.single().title)
        assertEquals(study.baseUrl.substringAfter("https://"), reopened.fetchDocument(study, question.documentPath, true, true).headings.single().title)
    }

    @Test
    fun savesGuestDraftsIndependentlyPerSiteAndHidesThemFromSignedInReaders() = runBlocking {
        val examPage = DocsPageKey(exam.baseUrl, DOCS_GUEST, question.documentPath)
        val studyPage = examPage.copy(site = study.baseUrl)
        progress.saveAnswer(examPage, question, DocsAnswerState(selected = listOf("A")), "", DocsWrite.Draft, 0)
        progress.saveAnswer(studyPage, question, DocsAnswerState(selected = listOf("B")), "", DocsWrite.Draft, 0)
        withTimeout(5_000) {
            assertEquals(listOf("A"), progress.page(examPage).first { it.answers.isNotEmpty() }.answers.values.single().state.selected)
            assertEquals(listOf("B"), progress.page(studyPage).first { it.answers.isNotEmpty() }.answers.values.single().state.selected)
        }
        assertTrue(progress.page(examPage.copy(account = "another-account")).first().answers.isEmpty())
        assertTrue(dao.pending(exam.baseUrl, DOCS_GUEST, 10).isEmpty())
    }

    @Test
    fun cloudAcknowledgementPreservesNewerLocalCommitAndOtherPartitions() = runBlocking {
        val user = "account-a"
        val initial = DocsPendingRecord(exam.baseUrl, user, "answers", question.answerId, 1000, "{}")
        val newer = DocsAnswer(
            question.documentPath, question.questionId, question.questionRevision,
            DocsAnswerState(selected = listOf("B"), submitted = true, attempt = 2), "1970-01-01T00:00:02Z",
        )
        val record = DocsRecord(
            exam.baseUrl, user, "answers", question.answerId, question.documentPath, 2000, docsJson.encodeToString(newer),
        )
        dao.putRecord(record)
        dao.enqueue(initial.copy(timestamp = 2000, payload = record.payload))
        val remote = newer.copy(state = DocsAnswerState(submitted = true, attempt = 1), updatedAt = "1970-01-01T00:00:01Z")
        val response = DocsSyncResponse(listOf(remote), emptyList(), emptyList(), emptyList(), emptyList(), "80", false)

        progress.applyExchange(exam.baseUrl, user, listOf(initial), response)
        assertEquals(2000L, dao.pending(exam.baseUrl, user, 10).single().timestamp)
        assertEquals("80", dao.position(exam.baseUrl, user)?.cursor)
        assertEquals(record, dao.record(exam.baseUrl, user, "answers", question.answerId))
        assertTrue(progress.page(DocsPageKey(study.baseUrl, user, question.documentPath)).first().answers.isEmpty())

        progress.applyExchange(exam.baseUrl, "account-b", emptyList(), response)
        assertEquals(remote.state, progress.page(DocsPageKey(exam.baseUrl, "account-b", question.documentPath))
            .first().answers[question.answerId]?.state)
        assertEquals(record, dao.record(exam.baseUrl, user, "answers", question.answerId))
    }
}
