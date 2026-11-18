package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.RubricCriterion
import pk.parhlou.student.data.WrittenQuestion

class PastPapersTest {
    private fun mcq(id: String, refs: Map<String, String>) =
        McqQuestion(id, "stem $id", listOf("a", "b", "c", "d"), 0, listOf("", "", "", ""), ReviewStatus.APPROVED, pastPapers = refs.keys.toList(), pastRefs = refs)
    private fun w(id: String, marks: Double, refs: Map<String, String>) =
        WrittenQuestion(id, "text $id", marks, listOf("answer"), listOf(RubricCriterion("a", "point", marks, emptyList(), "", "")), "", pastPapers = refs.keys.toList(), pastRefs = refs)

    @Test fun printedPlacesSortLikeThePaper() {
        val labels = listOf("Q2(x)", "Q1(ii)", "Q2(iv)", "Q5(b)", "Q1(12)", "Q5(a)")
        assertEquals(listOf("Q1(ii)", "Q1(12)", "Q2(iv)", "Q2(x)", "Q5(a)", "Q5(b)"), labels.sortedBy { PastPapers.order(it).let { (a, b) -> a * 1000 + b } })
    }

    @Test fun aBoardPaperIsRebuiltInItsPrintedOrder() {
        val tag = "Lahore 2023 (Group I)"
        val mcqs = (1..6).map { mcq("m$it", mapOf(tag to "Q1(${listOf("vi", "i", "iii", "ii", "v", "iv")[it - 1]})")) }
        val written = listOf(w("s1", 2.0, mapOf(tag to "Q2(iii)")), w("s2", 2.0, mapOf(tag to "Q2(i)")), w("l1", 4.0, mapOf(tag to "Q5(a)")))
        val real = PastPapers.rebuild("Mathematics", "", mcqs, written).single()
        assertEquals(tag, real.tag)
        assertEquals(2023, real.year)
        assertEquals(listOf("m2", "m4", "m3", "m6", "m5", "m1"), real.paper.mcqs.map { it.id })
        assertEquals(listOf("s2", "s1", "l1"), real.paper.written.map { it.id })
        assertTrue(BlueprintValidator.problems(real.blueprint).isEmpty())
        assertEquals(6.0 + 4.0 + 4.0, real.blueprint.totalMarks, 0.0)
    }

    @Test fun tooFewQuestionsIsNotAPaper() {
        val tag = "Multan 2019"
        assertTrue(PastPapers.rebuild("Physics", "phy-", listOf(mcq("m", mapOf(tag to "Q1(i)"))), listOf(w("s", 2.0, mapOf(tag to "Q2(i)")))).isEmpty())
    }
}
