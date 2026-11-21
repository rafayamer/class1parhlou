package pk.parhlou.student.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The game layer on top of real study: the daily chest (a reward for finishing all of today's quests) and the moments
 * worth celebrating (a new level, a streak milestone, a new achievement, all quests done). Nothing here can be earned
 * without studying: the chest needs today's quests done, and celebrations only follow the study history.
 */
/** The game's numbers, tunable from the owner panel; defaults are the original design. */
data class Tuning(
    val lessonXp: Int = 15,
    val writtenXp: Int = 10,
    val paperXp: Int = 50,
    val chestMinXp: Int = 20,
    val chestMaxXp: Int = 35,
    /** Daily goal = minutes a day × this, rounded down to tens. */
    val xpPerMinute: Int = 4,
)

object Game {
    val CHEST_MIN_XP: Int get() = Rewards.tuning.chestMinXp
    val CHEST_MAX_XP: Int get() = Rewards.tuning.chestMaxXp
    val STREAK_MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /** Today's chest: its XP is fixed for the day (a small surprise, 20–35), so it can't be re-rolled. */
    fun chestXp(date: LocalDate): Int = CHEST_MIN_XP + Math.floorMod(date.toEpochDay() * 7919 + 13, (CHEST_MAX_XP - CHEST_MIN_XP) / 5 + 1).toInt() * 5

    fun chestOpenedToday(history: List<LoggedEvidence>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        history.any { it.evidence is Evidence.ChestOpened && day(it.at, zone) == day(now, zone) }

    /** The chest can be opened once a day, after every quest is done. */
    fun chestReady(quests: List<Quests.Quest>, history: List<LoggedEvidence>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        quests.isNotEmpty() && quests.all { it.done } && !chestOpenedToday(history, now, zone)

    // ---- Celebrations ---------------------------------------------------------------------------------------------

    enum class Kind { LEVEL, ACHIEVEMENT, STREAK, QUESTS }
    data class Celebration(val kind: Kind, val emoji: String, val title: String, val message: String)

    /** What the student has already been congratulated on (kept on the phone). */
    data class Seen(val level: Int, val streak: Int, val achievements: Set<String>, val questsDay: String) {
        fun encode(): String = listOf(level, streak, questsDay, achievements.sorted().joinToString(",")).joinToString("|")
        companion object {
            fun decode(s: String): Seen? = runCatching {
                val p = s.split('|')
                Seen(p[0].toInt(), p[1].toInt(), p.getOrElse(3) { "" }.split(',').filter { it.isNotBlank() }.toSet(), p[2])
            }.getOrNull()
        }
    }

    /**
     * The next thing to celebrate and what counts as seen once it's shown. One at a time, biggest first; the first time the
     * app looks (nothing seen yet) it only remembers the current state, so a student isn't flooded with old news.
     */
    fun next(seen: Seen?, rewards: RewardSummary, achievements: List<Achievement>, questsDone: Boolean, today: String): Pair<Celebration?, Seen> {
        val earned = achievements.filter { it.earned }.map { it.id }.toSet()
        val now = Seen(rewards.level, rewards.streakDays, earned, if (questsDone) today else "")
        if (seen == null) return null to now.copy(questsDay = if (questsDone) today else "")
        if (rewards.level > seen.level) {
            return Celebration(Kind.LEVEL, "🚀", "Level ${rewards.level}!", "You've reached level ${rewards.level}. Keep going: " +
                "${rewards.toNextLevel} XP to level ${rewards.level + 1}.") to seen.copy(level = rewards.level)
        }
        achievements.firstOrNull { it.earned && it.id !in seen.achievements }?.let { a ->
            return Celebration(Kind.ACHIEVEMENT, a.icon, "New badge: ${a.title}", a.description.replaceFirstChar { it.uppercase() } + ". Well done!") to
                seen.copy(achievements = seen.achievements + a.id)
        }
        val milestone = STREAK_MILESTONES.lastOrNull { rewards.streakDays >= it && seen.streak < it }
        if (milestone != null) {
            return Celebration(Kind.STREAK, "🔥", "$milestone-day streak!", "You've studied $milestone days in a row. " +
                if (milestone >= 7) "That's how toppers are made." else "Come back tomorrow to keep it alive.") to seen.copy(streak = rewards.streakDays)
        }
        if (questsDone && seen.questsDay != today) {
            return Celebration(Kind.QUESTS, "🎁", "All quests done!", "Open today's chest for bonus XP.") to seen.copy(questsDay = today)
        }
        // Nothing new; a broken streak or level is simply remembered as it is now.
        return null to seen.copy(level = maxOf(seen.level, rewards.level), streak = rewards.streakDays,
            achievements = seen.achievements + earned)
    }
}
