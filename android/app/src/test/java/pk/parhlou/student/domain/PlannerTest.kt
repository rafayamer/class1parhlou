package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.RubricCriterion
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.ui.screens.unitRanges

private const val DAY = 24 * 60 * 60 * 1000L
private const val NOW = 1_800_000_000_000L

class WeaknessTest {
    private val titles = mapOf("n-surds" to "Surds", "n-rational" to "Rational numbers")

    @Test fun missesSinceTheLastCorrectAnswerMakeATopicWeak() {
        val history = listOf(
            LoggedEvidence(NOW - 9 * DAY, "topic:n-surds", Evidence.TopicMissed),
            LoggedEvidence(NOW - 8 * DAY, "topic:n-surds", Evidence.TopicCorrect),
            LoggedEvidence(NOW - 2 * DAY, "topic:n-surds", Evidence.TopicMissed),
            LoggedEvidence(NOW - 1 * DAY, "topic:n-surds", Evidence.TopicMissed),
            LoggedEvidence(NOW - 5 * DAY, "topic:n-rational", Evidence.TopicMissed),
            LoggedEvidence(NOW - 3 * DAY, "topic:n-rational", Evidence.TopicCorrect),
        )
        val weak = Weakness.topics(history, titles::get, NOW)
        assertEquals(listOf("n-surds"), weak.map { it.noteId })
        assertEquals(2, weak.single().recentMisses)
        assertEquals("Surds", weak.single().title)
    }

    @Test fun recentMissesRankAboveOldOnes() {
        val history = listOf(
            LoggedEvidence(NOW - 40 * DAY, "topic:n-rational", Evidence.TopicMissed),
            LoggedEvidence(NOW - 1 * DAY, "topic:n-surds", Evidence.TopicMissed),
        )
        assertEquals(listOf("n-surds", "n-rational"), Weakness.topics(history, titles::get, NOW).map { it.noteId })
    }

    @Test fun unknownTopicsAreIgnored() {
        val history = listOf(LoggedEvidence(NOW, "topic:n-other-board", Evidence.TopicMissed))
        assertTrue(Weakness.topics(history, titles::get, NOW).isEmpty())
    }

    @Test fun topicEvidenceSurvivesTheCodec() {
        assertEquals(Evidence.TopicMissed, EvidenceCodec.decode(EvidenceCodec.encode(Evidence.TopicMissed)))
        assertEquals(Evidence.TopicCorrect, EvidenceCodec.decode(EvidenceCodec.encode(Evidence.TopicCorrect)))
    }
}

class StudyPlannerTest {
    private val modules = listOf(
        PlanModule("m-1-1", "Real numbers", "u-1", "Unit 1 Real Numbers"),
        PlanModule("m-1-2", "Surds", "u-1", "Unit 1 Real Numbers"),
    )
    private val titles = mapOf("n-surds" to "Surds")
    private val papers = listOf("unit-1-test" to "Unit 1 test")

    private fun plan(history: List<LoggedEvidence>, testSeries: Boolean = false, minutes: Int = 120) =
        StudyPlanner.plan(modules, history, titles::get, papers, testSeries, minutes, NOW)

    @Test fun aNewStudentStartsTheFirstModule() {
        val tasks = plan(emptyList())
        assertEquals(PlanKind.LEARN, tasks.first().kind)
        assertEquals("m-1-1", tasks.first().target)
        assertTrue(tasks.first().title.startsWith("Start"))
    }

    @Test fun weakTopicRepairComesFirstAndSaysWhy() {
        val tasks = plan(listOf(LoggedEvidence(NOW - 2 * DAY, "topic:n-surds", Evidence.TopicMissed)))
        assertEquals(PlanKind.REPAIR, tasks.first().kind)
        assertEquals("n-surds", tasks.first().target)
        assertTrue(tasks.first().reason, tasks.first().reason.contains("last 2 days ago"))
    }

    @Test fun reviewsFollowTheSpacedSchedule() {
        val practisedOnce = listOf(LoggedEvidence(NOW - 2 * DAY, "m-1-1", Evidence.QuizCompleted(80)))
        assertTrue(plan(practisedOnce).any { it.kind == PlanKind.REVIEW && it.target == "m-1-1" })
        // After a second practice the next review waits 3 days.
        val practisedTwice = listOf(
            LoggedEvidence(NOW - 5 * DAY, "m-1-1", Evidence.QuizCompleted(80)),
            LoggedEvidence(NOW - 2 * DAY, "m-1-1", Evidence.QuizCompleted(90)),
        )
        assertTrue(plan(practisedTwice).none { it.kind == PlanKind.REVIEW })
    }

    @Test fun testSeriesPlansTheNextUnsatPaper() {
        val tasks = plan(emptyList(), testSeries = true)
        assertEquals(listOf(PlanKind.READINESS, PlanKind.PAPER), tasks.map { it.kind })
        val sat = plan(listOf(LoggedEvidence(NOW, "paper:unit-1-test", Evidence.DemoCheckedExam(70))), testSeries = true)
        assertTrue(sat.none { it.kind == PlanKind.PAPER })
    }

    @Test fun theDayIsCappedToTheStudentsMinutesButNeverEmpty() {
        val history = listOf(
            LoggedEvidence(NOW - 2 * DAY, "topic:n-surds", Evidence.TopicMissed),
            LoggedEvidence(NOW - 3 * DAY, "m-1-1", Evidence.QuizCompleted(80)),
        )
        val tasks = plan(history, minutes = 15)
        assertEquals(PlanKind.REPAIR, tasks.first().kind)
        assertTrue(tasks.sumOf { it.minutes } <= 15)
        assertTrue(plan(emptyList(), minutes = 5).isNotEmpty())
    }
}

class PaperAssemblerTest {
    private val chapters = (1..8).map { Chapter("u-$it", it, "Unit $it", emptyList(), ReviewStatus.DRAFT) }

    private fun mcq(id: String) = McqQuestion(id, "Q $id", listOf("a", "b"), 0, listOf("yes", "no"), ReviewStatus.SAMPLE_UNREVIEWED)
    private fun written(id: String, section: Int, marks: Double) = WrittenQuestion(
        id, "Write $id", marks, listOf("answer"),
        listOf(RubricCriterion("$id-c", "point", marks, listOf(listOf("answer")), "why", "how")), "next", section = section,
    )
    private fun unitPool(n: Int) = PaperContent("pool-u-$n",
        (1..8).map { mcq("u$n-m$it") },
        (1..14).map { written("u$n-s$it", 1, 2.0) } + (1..3).map { written("u$n-l$it", 2, 8.0) } + written("u$n-odd", 1, 3.0))

    @Test fun mockWithMissingUnitsIsNeverAssembled() {
        val m = PaperAssembler.assemble(MockScope.QUARTER, chapters, mapOf("u-1" to unitPool(1)), PaperAssembler.boardPattern())
        m as MockPaper.NeedsQuestions
        assertEquals(listOf(1, 2), m.chapters.map { it.number })
        assertEquals(listOf(2), m.missing.map { it.number })
    }

    @Test fun fullPoolBuildsAValidPaperFromEveryUnit() {
        val pool = chapters.associate { it.id to unitPool(it.number) }
        val m = PaperAssembler.assemble(MockScope.HALF, chapters, pool, PaperAssembler.boardPattern())
        m as MockPaper.Ready
        assertEquals("mock-half", m.blueprint.id)
        assertEquals(15, m.paper.mcqs.size)
        assertEquals(27, m.paper.written.count { it.section == 1 })
        assertEquals(5, m.paper.written.count { it.section == 2 })
        // Every unit in scope is represented and no question was re-weighted.
        (1..4).forEach { u -> assertTrue(m.paper.mcqs.any { it.id.startsWith("u$u-") }) }
        assertTrue(m.paper.written.none { it.id.endsWith("-odd") })
        assertTrue(BlueprintValidator.problems(m.blueprint).isEmpty())
        assertEquals((1..27).toList(), m.paper.written.filter { it.section == 1 }.map { it.number })
    }

    @Test fun tooFewQuestionsOfTheRightMarksIsReported() {
        val thin = chapters.associate { it.id to PaperContent("p", (1..8).map { n -> mcq("${it.id}-$n") }, listOf(written("${it.id}-w", 1, 2.0))) }
        val m = PaperAssembler.assemble(MockScope.QUARTER, chapters, thin, PaperAssembler.boardPattern())
        m as MockPaper.NeedsQuestions
        assertTrue(m.missing.isEmpty())
    }

    @Test fun unitRangesReadNaturally() {
        assertEquals("Units 2–4", unitRanges(listOf(4, 2, 3)))
        assertEquals("Units 2–4, 7", unitRanges(listOf(2, 3, 4, 7)))
        assertEquals("Unit 5", unitRanges(listOf(5)))
    }
}
