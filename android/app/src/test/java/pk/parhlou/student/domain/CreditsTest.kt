package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion

/** About how many credits a paper uses: making it, checking it and the examiner's report. */
class CreditsTest {
    private fun q(id: String, section: Int, number: Int) = WrittenQuestion(id = id, text = "Q", marks = 2.0, modelAnswer = emptyList(),
        rubric = emptyList(), followUp = "", section = section, number = number)

    @Test fun onlyTheAnswersAStudentMustWriteAreCounted() {
        // Board pattern: 12 MCQs, short questions answer 6 of 9, long questions answer 2 of 3.
        val bp = PaperBlueprint("p", "Paper", 150, 60.0, listOf(BlueprintSection("MCQ", 12, 12, 1.0),
            BlueprintSection("Short", 9, 6, 2.0), BlueprintSection("Long", 3, 2, 8.0)), official = true)
        val paper = PaperContent("p", emptyList(), (1..9).map { q("s$it", 1, it) } + (1..3).map { q("l$it", 2, it) })
        assertEquals(8, Credits.answersToMark(bp, paper))
        assertEquals(4, Credits.pagesFor(8))
        assertEquals(0, Credits.pagesFor(0))
    }

    @Test fun aPaperOnPaperCostsMoreThanTypedBecauseEveryPageIsRead() {
        val typical: (String) -> Double = { Credits.TYPICAL.getValue(it) }
        // In the app: 8 answers × 0.6 = 4.8 → 5; report 0.5 → 1.
        assertEquals(Credits.PaperCost(0, 5, 1), Credits.paper(8, onPaper = false, made = false, estimate = typical))
        // On paper: 8 × 0.8 + 4 pages × 0.5 = 8.4 → 9; made for the student: + 0.4 → 1.
        val made = Credits.paper(8, onPaper = true, made = true, estimate = typical)
        assertEquals(Credits.PaperCost(1, 9, 1), made)
        assertEquals(11, made.total)
        // The server's real averages replace the typical ones.
        assertEquals(40, Credits.paper(8, onPaper = false, made = false, estimate = { if (it == "mark") 5.0 else 0.0 }).check)
    }
}
