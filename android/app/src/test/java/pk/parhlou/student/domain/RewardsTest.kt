package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RewardsTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 10) = LocalDate.of(2026, 11, 1).plusDays(day.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun session(day: Int, xp: Int = 50) = LoggedEvidence(at(day), "session", Evidence.SessionCompleted(xp))

    @Test fun xpAndGoalForToday() {
        val h = listOf(session(0, 40), session(1, 30), LoggedEvidence(at(1, 12), "m-1-1", Evidence.LessonCompleted))
        val r = Rewards.summary(h, minutesPerDay = 30, now = at(1, 20), zone = zone)
        assertEquals(85, r.totalXp)
        assertEquals(45, r.xpToday)
        assertEquals(120, r.dailyGoalXp)
        assertEquals(listOf(0, 0, 0, 0, 0, 40, 45), r.week)
        assertEquals(2, r.streakDays)
        assertTrue(r.studiedToday)
    }

    @Test fun streakWaitsForTodayAndBreaksAfterAMissedDay() {
        val h = (0..2).map { session(it) }
        assertEquals(3, Rewards.summary(h, 30, at(3, 8), zone).streakDays)        // today not studied yet: still 3
        assertFalse(Rewards.summary(h, 30, at(3, 8), zone).studiedToday)
        assertEquals(0, Rewards.summary(h, 30, at(4, 8), zone).streakDays)        // a whole day missed, no freeze
    }

    @Test fun aFreezeEarnedAtSevenDaysCoversOneMissedDay() {
        val h = (0..6).map { session(it) } + session(8)
        val r = Rewards.summary(h, 30, at(8, 20), zone)
        assertEquals(8, r.streakDays)
        assertEquals(0, r.freezes)
        assertTrue(r.badges.any { it.id == "streak-7" })
    }

    @Test fun levelsAndSectionMastery() {
        assertEquals(1, Rewards.level(0))
        assertEquals(2, Rewards.level(100))
        assertEquals(3, Rewards.level(300))
        assertEquals(0, Rewards.sectionLevel(emptyList()))
        assertEquals(4, Rewards.sectionLevel(listOf(Evidence.LessonCompleted, Evidence.QuizCompleted(90), Evidence.QuizCompleted(85), Evidence.QuizCompleted(50))))
        assertEquals("learned after a lesson and one practice at 70%", Rewards.LEARNED_LEVEL, Rewards.sectionLevel(listOf(Evidence.LessonCompleted, Evidence.QuizCompleted(72))))
        assertEquals("a section without a lesson can still be learned", Rewards.LEARNED_LEVEL, Rewards.sectionLevel(listOf(Evidence.QuizCompleted(75)), hasLesson = false))
        assertEquals("a written board answer fills the ring", 5, Rewards.sectionLevel(listOf(Evidence.LessonCompleted, Evidence.QuizCompleted(95), Evidence.WrittenSubmitted)))
    }

    @Test fun sessionEvidenceRoundTrips() {
        val line = EvidenceCodec.line(1, "session", Evidence.SessionCompleted(42))
        assertEquals(Evidence.SessionCompleted(42), EvidenceCodec.parse(line).single().evidence)
    }
}
