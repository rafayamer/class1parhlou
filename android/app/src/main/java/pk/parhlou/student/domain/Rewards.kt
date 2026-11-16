package pk.parhlou.student.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A badge the student has earned. */
data class Badge(val id: String, val title: String, val description: String)

/** Everything the reward bar shows: XP, today's goal, streak, level and badges. */
data class RewardSummary(
    val totalXp: Int,
    val xpToday: Int,
    val dailyGoalXp: Int,
    /** XP for each of the last 7 days, oldest first (today last). */
    val week: List<Int>,
    val streakDays: Int,
    /** True when today already counts towards the streak. */
    val studiedToday: Boolean,
    val freezes: Int,
    val level: Int,
    /** XP still needed for the next level. */
    val toNextLevel: Int,
    val badges: List<Badge>,
) {
    val goalMet: Boolean get() = xpToday >= dailyGoalXp
}

/**
 * Rewards are worked out from the study history the app already keeps (and syncs), so they can never disagree with
 * real activity. XP: guided sessions earn what they report; lessons, quizzes, written answers and papers earn fixed
 * amounts. A streak counts days with any study; a streak freeze is earned every 7 streak days (max 2) and covers one
 * missed day. Levels follow total XP.
 */
object Rewards {
    const val LESSON_XP = 15
    const val WRITTEN_XP = 10
    const val PAPER_XP = 50

    fun xpOf(e: Evidence): Int = when (e) {
        is Evidence.SessionCompleted -> e.xp
        Evidence.LessonCompleted -> LESSON_XP
        is Evidence.QuizCompleted -> 0 // sessions report their XP separately; module quizzes earn through sessions
        Evidence.WrittenSubmitted -> WRITTEN_XP
        is Evidence.ExamMarked, is Evidence.DemoCheckedExam, Evidence.ExamPendingReview -> PAPER_XP
        else -> 0
    }

    private fun studied(e: Evidence) = e !is Evidence.ModuleOpened

    /** The daily goal grows with the minutes the student chose (about 4 XP a minute). */
    fun dailyGoal(minutesPerDay: Int): Int = (minutesPerDay.coerceIn(10, 120) * 4 / 10) * 10

    /** Level n starts at 50·n·(n−1) XP: 0, 100, 300, 600, 1000 … */
    fun level(totalXp: Int): Int {
        var n = 1
        while (50 * (n + 1) * n <= totalXp) n++
        return n
    }

    fun summary(history: List<LoggedEvidence>, minutesPerDay: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): RewardSummary {
        fun day(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = day(now)
        val xpByDay = history.groupBy { day(it.at) }.mapValues { (_, l) -> l.sumOf { xpOf(it.evidence) } }
        val studyDays = history.filter { studied(it.evidence) }.map { day(it.at) }.toSortedSet()
        val total = history.sumOf { xpOf(it.evidence) }

        // Streak with freezes, walked forward from the first study day.
        var streak = 0
        var freezes = 0
        if (studyDays.isNotEmpty()) {
            var d = studyDays.first()
            while (!d.isAfter(today)) {
                when {
                    d in studyDays -> {
                        streak++
                        if (streak % 7 == 0) freezes = (freezes + 1).coerceAtMost(2)
                    }
                    d == today -> Unit // today isn't over yet
                    freezes > 0 -> freezes--
                    else -> streak = 0
                }
                d = d.plusDays(1)
            }
        }
        val lvl = level(total)
        val badges = buildList {
            if (history.any { it.evidence is Evidence.SessionCompleted }) add(Badge("first-session", "First steps", "Finished your first guided session"))
            if (maxStreak(studyDays) >= 3) add(Badge("streak-3", "On a roll", "Studied 3 days in a row"))
            if (maxStreak(studyDays) >= 7) add(Badge("streak-7", "Week warrior", "Studied 7 days in a row"))
            if (maxStreak(studyDays) >= 30) add(Badge("streak-30", "Unstoppable", "Studied 30 days in a row"))
            if (history.any { it.key.startsWith("paper:board-") }) add(Badge("board-paper", "Board ready", "Sat a full 75-mark board paper"))
            if (total >= 1000) add(Badge("xp-1000", "1,000 XP", "Earned 1,000 XP"))
        }
        return RewardSummary(
            totalXp = total,
            xpToday = xpByDay[today] ?: 0,
            dailyGoalXp = dailyGoal(minutesPerDay),
            week = (6 downTo 0).map { xpByDay[today.minusDays(it.toLong())] ?: 0 },
            streakDays = streak,
            studiedToday = today in studyDays,
            freezes = freezes,
            level = lvl,
            toNextLevel = 50 * (lvl + 1) * lvl - total,
            badges = badges,
        )
    }

    private fun maxStreak(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) {
            if (d.minusDays(1) in days) continue
            var n = 0
            var x = d
            while (x in days) { n++; x = x.plusDays(1) }
            best = maxOf(best, n)
        }
        return best
    }

    /** Mastery of one section, 0–5: the lesson, then each strong practice (80%+), like filling a level ring. */
    fun sectionLevel(events: List<Evidence>): Int {
        val lesson = if (events.any { it == Evidence.LessonCompleted }) 1 else 0
        val strong = events.count { it is Evidence.QuizCompleted && it.firstTryPercent >= 80 }
        val exam = if (events.any { it is Evidence.ExamMarked && it.percent >= 80 }) 1 else 0
        return (lesson + strong + exam).coerceAtMost(5)
    }
}
