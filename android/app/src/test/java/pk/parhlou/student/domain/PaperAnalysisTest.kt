package pk.parhlou.student.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.CriterionMark
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.RubricCriterion
import pk.parhlou.student.data.WrittenQuestion

/** The examiner's analysis: every mark accounted for, strong and weak topics, and a report that can't invent a weak area. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaperAnalysisTest {
    private val bp = PaperBlueprint("p", "Unit test", 60, 18.0, listOf(
        BlueprintSection("MCQ", 4, 4, 1.0), BlueprintSection("Short", 4, 3, 2.0), BlueprintSection("Long", 2, 1, 8.0)), official = false)
    private val topics = mapOf(
        "logs" to TopicInfo("m-2-1", "Unit 2 · Logarithms", 2, "Logarithms", "n-logs"),
        "sets" to TopicInfo("m-6-1", "Unit 6 · Sets", 6, "Sets", "n-sets"),
    )
    private fun mcq(id: String, note: String) = McqQuestion(id, id, listOf("a", "b", "c", "d"), 0, listOf("", "", "", ""), ReviewStatus.APPROVED, noteId = note)
    private fun w(id: String, section: Int, number: Int, note: String, vararg points: Pair<String, Double>) =
        WrittenQuestion(id, id, points.sumOf { it.second }, listOf("m"), points.mapIndexed { i, (d, m) -> RubricCriterion("c$i", d, m, emptyList(), "why", "how $i") }, "f",
            noteId = note, section = section, number = number)
    private fun mark(q: WrittenQuestion, vararg statuses: CriterionStatus, correction: String = "") = WrittenMark(q.id, MarkStatus.CHECKED,
        q.rubric.mapIndexed { i, c -> CriterionMark(c.id, c.description, c.marks, if (statuses[i] == CriterionStatus.MET) c.marks else 0.0, statuses[i], c.why, c.how) },
        q.followUp, correction = correction)

    private val s1 = w("s1", 1, 1, "sets", "Write the set" to 1.0, "Give the final set" to 1.0)
    private val s2 = w("s2", 1, 2, "logs", "Take log of both sides" to 1.0, "Final answer" to 1.0)
    private val s3 = w("s3", 1, 3, "logs", "State the law of logarithms" to 1.0, "Apply it" to 1.0)
    private val s4 = w("s4", 1, 4, "sets", "Draw the Venn diagram" to 2.0)
    private val l1 = w("l1", 2, 1, "logs", "Characteristic" to 2.0, "Mantissa" to 2.0, "Antilog" to 2.0, "Final value" to 2.0)
    private val l2 = w("l2", 2, 2, "sets", "Union" to 8.0)

    private val result = PaperResult(
        mcq = listOf(McqOutcome(mcq("q1", "sets"), 0, true), McqOutcome(mcq("q2", "sets"), 0, true), McqOutcome(mcq("q3", "logs"), 2, false), McqOutcome(mcq("q4", "logs"), null, false)),
        written = listOf(
            WrittenOutcome(s1, "x", 0, true, mark(s1, CriterionStatus.MET, CriterionStatus.MET)),
            WrittenOutcome(s2, "x", 0, true, mark(s2, CriterionStatus.MET, CriterionStatus.NOT_FOUND, correction = "Divide by log 3, not 3.")),
            // Q2 (iii) and (iv) were not attempted: one of them was required.
            WrittenOutcome(s3, "", 0, false, mark(s3, CriterionStatus.NOT_FOUND, CriterionStatus.NOT_FOUND)),
            WrittenOutcome(s4, "", 0, false, mark(s4, CriterionStatus.NOT_FOUND)),
            WrittenOutcome(l1, "x", 0, true, mark(l1, CriterionStatus.MET, CriterionStatus.NOT_FOUND, CriterionStatus.MET, CriterionStatus.NOT_FOUND)),
            WrittenOutcome(l2, "", 0, false, mark(l2, CriterionStatus.NOT_FOUND)),
        ),
        mcqMarks = 2.0, total = 18.0,
    )

    private fun analyse() = PaperAnalyzer.analyse(bp, result, { topics[it] }, { it.id })

    @Test fun everyMarkIsAccountedForByUnitTopicAndType() {
        val a = analyse()
        // Got: 2 MCQ + 2 (s1) + 1 (s2) + 4 (l1) = 9 of 18 (4 MCQs, 3 short of 2, 1 long of 8).
        assertEquals(9.0, a.units.sumOf { it.got }, 0.0)
        assertEquals(18.0, a.units.sumOf { it.max }, 0.0)
        assertEquals(listOf("Unit 2 · Logarithms", "Unit 6 · Sets"), a.units.map { it.title })
        assertEquals(listOf("MCQs", "Short questions", "Long questions"), a.types.map { it.title })
        assertEquals(1.0, a.lost[LossKind.MCQ_WRONG]!!, 0.0)
        assertEquals(1.0, a.lost[LossKind.MCQ_BLANK]!!, 0.0)
        assertEquals(2.0, a.lost[LossKind.NOT_ATTEMPTED]!!, 0.0)       // one required short question left out
        assertEquals(1.0 + 2.0, a.lost[LossKind.FINAL_ANSWER]!!, 0.0) // s2's final answer, l1's final value
        assertEquals(2.0, a.lost[LossKind.STEPS]!!, 0.0)               // l1's mantissa
        assertEquals(50, a.percent)
        assertEquals(38, a.outOf75)
    }

    @Test fun strongAndWeakTopicsComeFromTheMarks() {
        val a = analyse()
        assertEquals(listOf("Unit 6 · Sets"), a.strong.map { it.title })   // 4/4 MCQ+short, nothing lost
        assertEquals(listOf("Unit 2 · Logarithms"), a.weak.map { it.title }) // 5 of 14 = 36%
        assertEquals("n-logs", a.weak.single().noteId)
        assertEquals("Divide by log 3, not 3.", a.mistakes.first { it.label == "s2" }.fix)
    }

    @Test fun theReportCannotInventAWeakTopicAndWorksOffline() {
        val a = analyse()
        val allowed = a.topics.map { it.title }.toSet()
        val r = ExaminerReport.parse(JSONObject("""{"headline":"50%. Logarithms cost you most.","summary":"s","strengths":["Sets"],"plan":["Day 1–2: logs"],
            "technique":[],"closing":"c","weaknesses":[{"topic":"Unit 2 · Logarithms","why":"w","action":"a"},{"topic":"Matrices","why":"w","action":"a"}]}"""), allowed, byAi = true)!!
        assertEquals(listOf("Unit 2 · Logarithms"), r.weaknesses.map { it.topic })
        val local = ExaminerReport.local(a, "Ali")
        assertTrue(local.headline.contains("50%"))
        assertEquals("Unit 2 · Logarithms", local.weaknesses.first().topic)
        assertTrue(local.technique.any { "Attempt the full number" in it })
        assertTrue(local.plan.last().startsWith("Day 7"))
        assertNotNull(ExaminerReport.parse(JSONObject(local.toJson()), allowed, byAi = false))
        // The facts sent to the AI carry the numbers, not answer keys.
        val facts = JSONObject(a.factsJson("Unit test"))
        assertEquals(50, facts.getInt("percent"))
        assertTrue(!facts.toString().contains("\"m\""))
    }
}
