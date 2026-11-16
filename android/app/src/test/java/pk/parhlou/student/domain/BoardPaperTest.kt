package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardPaperTest {
    private val min = 60_000L

    @Test fun objectivePartLocksAfterItsTimeOrWhenHandedIn() {
        val p = PaperPhases(startedAtMillis = 0, objectiveMinutes = 20)
        assertTrue(p.twoParts)
        assertTrue(p.objectiveOpen(19 * min))
        assertFalse(p.subjectiveOpen(19 * min))
        assertFalse(p.objectiveOpen(20 * min))
        assertTrue(p.subjectiveOpen(20 * min))
        val early = p.copy(objectiveHandedInAt = 5 * min)
        assertFalse(early.objectiveOpen(6 * min))
        assertTrue(early.subjectiveOpen(6 * min))
    }

    @Test fun onePartPapersAreAlwaysOpen() {
        val p = PaperPhases(0, objectiveMinutes = null)
        assertFalse(p.twoParts)
        assertTrue(p.subjectiveOpen(0))
    }

    @Test fun punjabGradeBands() {
        assertEquals("A+", BoardMarking.grade(80))
        assertEquals("A", BoardMarking.grade(79))
        assertEquals("C", BoardMarking.grade(50))
        assertEquals("E", BoardMarking.grade(33))
        assertEquals("F", BoardMarking.grade(32))
    }

    @Test fun threeAttemptsPerPaperWithBestScore() {
        val h = listOf(
            LoggedEvidence(1, "paper:board-2027-a", Evidence.DemoCheckedExam(40)),
            LoggedEvidence(2, "paper:board-2027-a", Evidence.ExamMarked(72)),
            LoggedEvidence(3, "paper:other", Evidence.ExamMarked(90)),
            LoggedEvidence(4, "m-1-1", Evidence.LessonCompleted),
        )
        assertEquals(2, PaperAttempts.used(h, "board-2027-a"))
        assertEquals(1, PaperAttempts.left(h, "board-2027-a"))
        assertEquals(72, PaperAttempts.best(h, "board-2027-a"))
        val full = h + LoggedEvidence(5, "paper:board-2027-a", Evidence.ExamPendingReview)
        assertEquals(0, PaperAttempts.left(full, "board-2027-a"))
        assertEquals(72, PaperAttempts.best(full, "board-2027-a"))
    }
}
