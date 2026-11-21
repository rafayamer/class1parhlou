package pk.parhlou.student.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A badge the student has earned. */
data class Badge(val id: String, val title: String, val description: String)

/** An achievement in the gallery: earned once [progress] reaches [target]. */
data class Achievement(val id: String, val icon: String, val title: String, val description: String, val progress: Int, val target: Int) {
    val earned: Boolean get() = progress >= target
    val shownProgress: Int get() = progress.coerceAtMost(target)
}

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
    /** The game's numbers, which the owner can tune from the owner panel (App design). */
    @Volatile var tuning: Tuning = Tuning()
    val LESSON_XP: Int get() = tuning.lessonXp
    val WRITTEN_XP: Int get() = tuning.writtenXp
    val PAPER_XP: Int get() = tuning.paperXp

    fun xpOf(e: Evidence): Int = when (e) {
        is Evidence.SessionCompleted -> e.xp
        is Evidence.ChestOpened -> e.xp.coerceIn(0, Game.CHEST_MAX_XP)
        Evidence.LessonCompleted -> LESSON_XP
        is Evidence.QuizCompleted -> 0 // sessions report their XP separately; module quizzes earn through sessions
        Evidence.WrittenSubmitted -> WRITTEN_XP
        is Evidence.ExamMarked, is Evidence.DemoCheckedExam, Evidence.ExamPendingReview -> PAPER_XP
        else -> 0
    }

    /** Opening a module or the daily chest isn't study: neither keeps a streak alive. */
    private fun studied(e: Evidence) = e !is Evidence.ModuleOpened && e !is Evidence.ChestOpened && e !is Evidence.Helpful

    /** The daily goal grows with the minutes the student chose (about 4 XP a minute). */
    fun dailyGoal(minutesPerDay: Int): Int = (minutesPerDay.coerceIn(10, 120) * tuning.xpPerMinute / 10) * 10

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
        val badges = achievements(history, zone).filter { it.earned }.map { Badge(it.id, it.title, it.description) }
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

    /**
     * Every achievement, earned or not, with progress towards it: the gallery shows locked ones too, so there's always a
     * next goal in sight. Worked out from the study history, like everything else here.
     */
    fun achievements(history: List<LoggedEvidence>, zone: ZoneId = ZoneId.systemDefault()): List<Achievement> {
        val studyDays = history.filter { studied(it.evidence) }.map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.toSortedSet()
        val best = maxStreak(studyDays)
        val total = history.sumOf { xpOf(it.evidence) }
        val sessions = history.count { it.evidence is Evidence.SessionCompleted }
        val lessons = history.count { it.evidence == Evidence.LessonCompleted }
        val strongQuizzes = history.count { (it.evidence as? Evidence.QuizCompleted)?.firstTryPercent?.let { p -> p >= 80 } == true }
        val papers = history.count { it.key.startsWith("paper:") && (it.evidence is Evidence.ExamMarked || it.evidence is Evidence.DemoCheckedExam) }
        val boardPapers = history.count { it.key.startsWith("paper:board-") }
        val chests = history.count { it.evidence is Evidence.ChestOpened }
        val lvl = level(total)
        return listOf(
            Achievement("first-session", "🌱", "First steps", "Finish your first guided session", sessions, 1),
            Achievement("streak-3", "🔥", "On a roll", "Study 3 days in a row", best, 3),
            Achievement("streak-7", "📅", "Week warrior", "Study 7 days in a row", best, 7),
            Achievement("lessons-10", "📘", "Bookworm", "Finish 10 lessons", lessons, 10),
            Achievement("quiz-whiz", "🎯", "Quiz whiz", "Score 80% or more on 10 quizzes", strongQuizzes, 10),
            Achievement("chests-7", "🎁", "Treasure hunter", "Open 7 daily chests", chests, 7),
            Achievement("board-paper", "📝", "Board ready", "Sit a full board paper", boardPapers, 1),
            Achievement("papers-5", "🏅", "Paper pro", "Finish 5 papers", papers, 5),
            Achievement("xp-1000", "⚡", "1,000 XP", "Earn 1,000 XP", total, 1000),
            Achievement("level-10", "🚀", "Level 10", "Reach level 10", lvl, 10),
            Achievement("streak-30", "🏆", "Unstoppable", "Study 30 days in a row", best, 30),
            Achievement("lessons-50", "🧠", "Scholar", "Finish 50 lessons", lessons, 50),
            Achievement("xp-5000", "💎", "5,000 XP", "Earn 5,000 XP", total, 5000),
            Achievement("streak-100", "👑", "Legend", "Study 100 days in a row", best, 100),
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

    /** The level at which a section counts as learned and "Do this now" moves on to the next one. */
    const val LEARNED_LEVEL = 3

    /**
     * Mastery of one section, 0–5, filling a ring one step at a time:
     * 1 lesson done · 2 practised once · 3 a practice at 70%+ (learned) · 4 a practice at 90%+, or 70%+ twice ·
     * 5 a board answer written, a paper at 80%+, or three practices at 70%+ (exam ready).
     * A section without a lesson counts its first step once it has been practised.
     */
    fun sectionLevel(events: List<Evidence>, hasLesson: Boolean = true): Int {
        val scores = events.filterIsInstance<Evidence.QuizCompleted>().map { it.firstTryPercent }
        val good = scores.count { it >= 70 }
        var n = 0
        if (events.any { it == Evidence.LessonCompleted } || (!hasLesson && scores.isNotEmpty())) n++
        if (scores.isNotEmpty()) n++
        if (good >= 1) n++
        if (scores.any { it >= 90 } || good >= 2) n++
        if (Evidence.WrittenSubmitted in events || events.any { it is Evidence.ExamMarked && it.percent >= 80 } || good >= 3) n++
        return n.coerceAtMost(5)
    }
}

/** Days left to the board exam, from the course's approximate exam date. Null when unknown or past. */
object ExamCountdown {
    fun daysLeft(examDate: String, today: java.time.LocalDate = java.time.LocalDate.now()): Int? = runCatching {
        val d = java.time.LocalDate.parse(examDate)
        java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt().takeIf { it >= 0 }
    }.getOrNull()
}
