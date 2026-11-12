package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.PaperBlueprint

class QuizScorerTest {
    @Test fun firstAttemptIsPreservedAfterRetry() {
        var h = QuizScorer.record(emptyList(), "q1", chosen = 0, correctIndex = 2)
        h = QuizScorer.record(h, "q1", chosen = 2, correctIndex = 2)
        assertEquals(listOf(1, 2), h.map { it.attemptNo })
        assertFalse(h.first().correct)
        val s = QuizScorer.summarise(listOf("q1", "q2"), h)
        assertEquals(0, s.firstTryCorrect)
        assertEquals(1, s.eventuallyCorrect)
        assertEquals(0, s.firstTryPercent)
    }

    @Test fun percentOfEmptyQuizIsZero() {
        assertEquals(0, QuizScorer.summarise(emptyList(), emptyList()).firstTryPercent)
    }
}

class BlueprintValidatorTest {
    private fun bp(total: Double, vararg s: BlueprintSection) = PaperBlueprint("x", "x", 90, total, s.toList(), official = false)

    @Test fun reportedBoardPatternReconcilesTo75() {
        val b = bp(75.0,
            BlueprintSection("MCQ", 15, 15, 1.0),
            BlueprintSection("Short", 27, 18, 2.0),
            BlueprintSection("Long", 5, 3, 8.0))
        assertTrue(BlueprintValidator.problems(b).isEmpty())
    }

    @Test fun mismatchedTotalIsRejected() {
        val p = BlueprintValidator.problems(bp(75.0, BlueprintSection("MCQ", 15, 15, 1.0)))
        assertEquals(listOf("sections add up to 15 marks but total is 75"), p)
    }

    @Test fun attemptCannotExceedCount() {
        val p = BlueprintValidator.problems(bp(12.0, BlueprintSection("Short", 3, 4, 3.0)))
        assertTrue(p.any { "attempt exceeds count" in it })
    }

    @Test fun sampleBlueprintsAllValidate() {
        FakeRepository().blueprints(FakeRepository.MATHS_9).forEach {
            assertTrue(it.title, BlueprintValidator.problems(it).isEmpty())
        }
    }
}

class ExamClockTest {
    private val start = 1_000_000L
    private val clock = ExamClock(start, durationMinutes = 90)

    @Test fun ninetyMinutesFromStart() {
        assertEquals("1:30:00", clock.label(start))
        assertEquals("1:29:59", clock.label(start + 1_000))
    }

    @Test fun timeContinuesWhileAppIsClosed() {
        // Restarting the app 30 minutes later reads the same stored start time.
        assertEquals("1:00:00", clock.label(start + 30 * 60_000))
    }

    @Test fun expiresAndNeverGoesNegative() {
        assertFalse(clock.isExpired(start + 90 * 60_000 - 1))
        assertTrue(clock.isExpired(start + 90 * 60_000))
        assertEquals(0, clock.remainingMillis(start + 200 * 60_000))
        assertEquals("00:00", clock.label(start + 200 * 60_000))
    }
}

class ProgressCalculatorTest {
    @Test fun nothingDoneIsNotStarted() = assertEquals(LearningState.NOT_STARTED, ProgressCalculator.stateOf(emptyList()))

    @Test fun watchingAndReadingAreOnlyLearning() {
        val s = ProgressCalculator.stateOf(listOf(Evidence.ModuleOpened, Evidence.VideoStudyConfirmed, Evidence.ReadingChecked))
        assertEquals(LearningState.LEARNING, s)
        assertFalse(ProgressCalculator.countsTowardMilestone(s))
    }

    @Test fun goodQuizPlusWritingIsReadyNotAssessed() {
        val s = ProgressCalculator.stateOf(listOf(Evidence.QuizCompleted(90), Evidence.WrittenSubmitted))
        assertEquals(LearningState.READY_FOR_ASSESSMENT, s)
        assertFalse(ProgressCalculator.countsTowardMilestone(s))
    }

    @Test fun weakQuizIsPractising() =
        assertEquals(LearningState.PRACTISING, ProgressCalculator.stateOf(listOf(Evidence.QuizCompleted(40), Evidence.WrittenSubmitted)))

    @Test fun examOutcomesDriveState() {
        assertEquals(LearningState.PENDING_REVIEW, ProgressCalculator.stateOf(listOf(Evidence.ExamPendingReview)))
        assertEquals(LearningState.REPAIR_REQUIRED, ProgressCalculator.stateOf(listOf(Evidence.ExamMarked(20))))
        assertEquals(LearningState.ASSESSED, ProgressCalculator.stateOf(listOf(Evidence.ExamMarked(60))))
        assertEquals(LearningState.MASTERED, ProgressCalculator.stateOf(listOf(Evidence.ExamMarked(85))))
        assertTrue(ProgressCalculator.countsTowardMilestone(LearningState.ASSESSED))
    }

    @Test fun latestExamWins() {
        val s = ProgressCalculator.stateOf(listOf(Evidence.ExamMarked(20), Evidence.ExamMarked(85)))
        assertEquals(LearningState.MASTERED, s)
    }
}

class StudyPlanTest {
    @Test fun unknownExamDateSaysSo() {
        val f = StudyPlan.feasibility(60, 75, daysPerWeek = 5, minutesPerDay = 60, weeksUntilExam = null)
        assertEquals(15, f.weeksNeeded)
        assertTrue("not confirmed" in f.message)
    }

    @Test fun lateStarterGetsDiagnosticRoute() {
        val f = StudyPlan.feasibility(60, 75, daysPerWeek = 2, minutesPerDay = 30, weeksUntilExam = 8)
        assertTrue("diagnostic" in f.message)
    }

    @Test fun zeroStudyTimeAsksForMore() {
        val f = StudyPlan.feasibility(60, 75, daysPerWeek = 0, minutesPerDay = 60, weeksUntilExam = 20)
        assertTrue("at least one" in f.message)
    }
}

class SampleDataTest {
    @Test fun sampleQuestionsAreWellFormedAndLabelled() {
        FakeRepository.sampleMcqs.forEach { q ->
            assertEquals(q.id, 4, q.options.size)
            assertEquals(q.id, 4, q.rationales.size)
            assertTrue(q.id, q.sampleCorrectIndex in q.options.indices)
            assertEquals(pk.parhlou.student.data.ReviewStatus.SAMPLE_UNREVIEWED, q.status)
        }
    }

    @Test fun noVerifiedPageNumbersAreClaimed() {
        val subject = FakeRepository().subject(FakeRepository.MATHS_9)!!
        subject.chapters.flatMap { it.modules }.forEach { m ->
            assertFalse(m.id, m.bookReference?.verified ?: false)
        }
        assertFalse(subject.editionVerified)
    }

    @Test fun unreleasedBoardsAndClassesOfferNothingSelectable() {
        val repo = FakeRepository()
        assertTrue(repo.subjectOptions("FBISE", 9).isEmpty())
        assertTrue(repo.subjectOptions("PB", 10).none { it.available })
    }
}
