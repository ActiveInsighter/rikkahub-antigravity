package me.rerere.rikkahub.data.examdocs

import me.rerere.rikkahub.ui.pages.examdocs.DocsDockStatus
import me.rerere.rikkahub.ui.pages.examdocs.components.dockQuestionStatus
import me.rerere.rikkahub.ui.pages.examdocs.dockItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DocsQuestionDockTest {
    private fun option(label: String, correct: Boolean) = ExamDocsSegment.CustomBlock(
        "option", null, emptyList(), mapOf("label" to label, "correct" to correct.toString()),
    )
    private fun question(type: String, vararg options: ExamDocsSegment.CustomBlock, extra: Map<String, String> = emptyMap()) =
        ExamDocsSegment.CustomBlock("question", null, options.toList(), mapOf("type" to type) + extra)

    private fun answer(vararg selection: String, submitted: Boolean = true, values: List<String> = emptyList()) =
        DocsSavedAnswer(DocsAnswerState(selected = selection.toList(), values = values, submitted = submitted), "", 1L)

    @Test
    fun tracksUnansweredDraftCorrectPartialAndWrong() {
        val q = question("multiple", option("A", true), option("B", true), option("C", false))
        assertEquals(DocsDockStatus.Idle, dockQuestionStatus(q, null))
        assertEquals(DocsDockStatus.Draft, dockQuestionStatus(q, answer("A", submitted = false)))
        assertEquals(DocsDockStatus.Correct, dockQuestionStatus(q, answer("B", "A")))
        assertEquals(DocsDockStatus.Partial, dockQuestionStatus(q, answer("A")))
        assertEquals(DocsDockStatus.Incorrect, dockQuestionStatus(q, answer("A", "C")))
    }

    @Test
    fun supportsBooleanEssayAndManualBlankGrades() {
        val bool = question("boolean", extra = mapOf("answer" to "false"))
        assertEquals(DocsDockStatus.Correct, dockQuestionStatus(bool, answer("B")))
        val essay = question("essay")
        assertEquals(DocsDockStatus.Partial, dockQuestionStatus(essay, answer("B")))
        val blank = question("blank", extra = mapOf("blanks" to """[{"answer":"2"},{"answer":"3"}]"""))
        assertEquals(DocsDockStatus.Partial, dockQuestionStatus(blank, answer("blank:1:incorrect", values = listOf("2","3"))))
        assertEquals(DocsDockStatus.Correct, dockQuestionStatus(blank, answer(values = listOf("2","3"))))
    }

    @Test
    fun preservesNestedQuestionOrderAndRootIndex() {
        val first = question("single", extra = mapOf("number" to "7"))
        val second = question("essay")
        val third = question("essay")
        val container = ExamDocsSegment.CustomBlock("tabs", null, listOf(first, second))
        val document = ExamDocsDocument(listOf(container, third), emptyList(), "https://example.test", 0)
        val items = dockItems(document)
        assertEquals(listOf(0, 0, 1), items.map { it.blockIndex })
        assertEquals(listOf("7", "2", "3"), items.map { it.label })
        assertSame(second, items[1].block)
    }
}
