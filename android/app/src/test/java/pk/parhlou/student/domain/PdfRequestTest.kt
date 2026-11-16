package pk.parhlou.student.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.SolutionKind

@RunWith(RobolectricTestRunner::class)
class PdfRequestTest {
    private fun sol(source: String, q: String) = BookSolution(SolutionKind.EXERCISE, source, q, listOf("step for $q"), "ans $q", "", emptyList(), true)
    private fun chapter(n: Int, vararg sols: BookSolution) = Chapter("c$n", n, "Unit $n title",
        listOf(Module("m$n", 1, "$n.1 Topic", null, null, emptyList(), emptyList(), ReviewStatus.PUBLISHED, solutions = sols.toList())), ReviewStatus.PUBLISHED)
    private val chapters = listOf(
        chapter(3, sol("Ex 3.2 Q1(i)", "a"), sol("Ex 3.2 Q1(ii)", "b"), sol("Ex 3.2 Q2", "c"), sol("Ex 3.1 Q1", "other")),
        chapter(1), chapter(2), chapter(4),
    )

    @Test fun onlyRequestsTheAppCanBuildAreAccepted() {
        assertEquals("Exercise 3.2 paper with answers",
            PdfAction.parse(JSONObject("""{"type":"paper_pdf","scope":"exercise","unit":0,"exercise":"3.2","withAnswers":true}"""))!!.label)
        assertEquals("Unit 2 notes", PdfAction.parse(JSONObject("""{"type":"notes_pdf","scope":"unit","unit":2,"exercise":""}"""))!!.label)
        assertNull(PdfAction.parse(JSONObject("""{"type":"paper_pdf","scope":"unit","unit":0}""")))
        assertNull(PdfAction.parse(JSONObject("""{"type":"paper_pdf","scope":"exercise","exercise":"three"}""")))
        assertNull(PdfAction.parse(JSONObject("""{"type":"none","scope":"none"}""")))
    }

    @Test fun anExercisePaperHasEveryQuestionOfThatExerciseWithItsPartsTogether() {
        val (b, p) = PdfContent.exercisePaper(chapters, "3.2")!!
        assertEquals("Exercise 3.2", b.title)
        assertEquals(listOf("(i)  a\n(ii)  b", "c"), p.written.map { it.text })
        assertEquals(listOf(1, 2), p.written.map { it.number })
        assertEquals(b.sections[1].count, p.written.size)
        assertNull(PdfContent.exercisePaper(chapters, "9.9"))
    }

    @Test fun halfBookMeansTheFirstHalfOfTheUnits() {
        val half = PdfAction("paper_pdf", "half", 0, "", false)
        assertEquals(setOf(1, 2), PdfContent.units(chapters, half))
        assertEquals(setOf(3), PdfContent.units(chapters, PdfAction("notes_pdf", "exercise", 0, "3.2", false)))
    }
}
