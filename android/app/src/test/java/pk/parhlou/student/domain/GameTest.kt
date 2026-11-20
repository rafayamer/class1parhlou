package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GameTest {
    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val now = LocalDate.of(2026, 11, 20).atTime(15, 0).toInstant(zone).toEpochMilli()
    private val done = listOf(Quests.Quest("xp", "", 120, 120), Quests.Quest("lesson", "", 1, 1), Quests.Quest("practice", "", 2, 2))

    @Test fun chestXpIsFixedForTheDayAndInRange() {
        val d = LocalDate.of(2026, 11, 20)
        assertEquals(Game.chestXp(d), Game.chestXp(d))
        (0L..60L).map { Game.chestXp(d.plusDays(it)) }.forEach { assertTrue(it in Game.CHEST_MIN_XP..Game.CHEST_MAX_XP) }
        assertTrue("the amount varies from day to day", (0L..30L).map { Game.chestXp(d.plusDays(it)) }.toSet().size > 1)
    }

    @Test fun theChestNeedsEveryQuestAndOpensOnceADay() {
        assertFalse(Game.chestReady(done.dropLast(1) + Quests.Quest("practice", "", 1, 2), emptyList(), now, zone))
        assertTrue(Game.chestReady(done, emptyList(), now, zone))
        val opened = listOf(LoggedEvidence(now - 1000, "chest", Evidence.ChestOpened(25)))
        assertFalse(Game.chestReady(done, opened, now, zone))
        assertTrue("a new day, a new chest", Game.chestReady(done, opened, now + day, zone))
    }

    @Test fun chestXpCountsButDoesNotKeepAStreakAlive() {
        val h = listOf(LoggedEvidence(now, "chest", Evidence.ChestOpened(30)))
        val s = Rewards.summary(h, 30, now, zone)
        assertEquals(30, s.totalXp)
        assertEquals(0, s.streakDays)
        assertEquals("forged amounts are capped", Game.CHEST_MAX_XP, Rewards.xpOf(Evidence.ChestOpened(9999)))
        assertEquals(Evidence.ChestOpened(25), EvidenceCodec.decode(EvidenceCodec.encode(Evidence.ChestOpened(25))))
    }

    @Test fun achievementsShowProgressTowardsLockedOnes() {
        val h = (0 until 3).map { LoggedEvidence(now - it * day, "m", Evidence.LessonCompleted) }
        val a = Rewards.achievements(h, zone).associateBy { it.id }
        assertTrue(a.getValue("streak-3").earned)
        assertFalse(a.getValue("lessons-10").earned)
        assertEquals(3, a.getValue("lessons-10").shownProgress)
        assertEquals("earned badges match the summary", Rewards.summary(h, 30, now, zone).badges.map { it.id }, a.values.filter { it.earned }.map { it.id })
    }

    private fun summary(level: Int, streak: Int) = RewardSummary(level * 100, 0, 120, List(7) { 0 }, streak, true, 0, level, 50, emptyList())

    @Test fun celebrationsComeOneAtATimeAndOnlyOnce() {
        val badges = listOf(Achievement("streak-7", "📅", "Week warrior", "study 7 days in a row", 7, 7))
        // First look: nothing old is celebrated.
        val (first, seen0) = Game.next(null, summary(3, 7), badges, questsDone = false, today = "2026-11-20")
        assertNull(first)
        assertNull(Game.next(seen0, summary(3, 7), badges, false, "2026-11-20").first)
        // Level up, then a streak milestone, then quests, each once.
        val (c1, s1) = Game.next(seen0, summary(4, 14), badges, true, "2026-11-20")
        assertEquals(Game.Kind.LEVEL, c1!!.kind)
        val (c2, s2) = Game.next(s1, summary(4, 14), badges, true, "2026-11-20")
        assertEquals(Game.Kind.STREAK, c2!!.kind)
        assertEquals("14-day streak!", c2.title)
        val (c3, s3) = Game.next(s2, summary(4, 14), badges, true, "2026-11-20")
        assertEquals(Game.Kind.QUESTS, c3!!.kind)
        assertNull(Game.next(s3, summary(4, 14), badges, true, "2026-11-20").first)
        // A new badge.
        val more = badges + Achievement("lessons-10", "📘", "Bookworm", "finish 10 lessons", 10, 10)
        val (c4, _) = Game.next(s3, summary(4, 14), more, true, "2026-11-20")
        assertEquals("New badge: Bookworm", c4!!.title)
        // Saved and read back.
        assertEquals(s3, Game.Seen.decode(s3.encode()))
        assertNotNull(Game.Seen.decode(Game.Seen(1, 0, emptySet(), "").encode()))
    }

    @Test fun aBrokenStreakIsRememberedSoTheNextMilestoneCountsAgain() {
        val seen = Game.Seen(3, 7, emptySet(), "")
        val (c, s) = Game.next(seen, summary(3, 1), emptyList(), false, "2026-11-20")
        assertNull(c)
        assertEquals(1, s.streak)
        val (c2, _) = Game.next(s, summary(3, 3), emptyList(), false, "2026-11-23")
        assertEquals("3-day streak!", c2!!.title)
    }
}
