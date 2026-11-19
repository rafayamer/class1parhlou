package pk.parhlou.student.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Leagues, friends and class groups as the app shows them. Other students appear only by first name and an emoji. */
object Social {
    val LEAGUES = listOf("Bronze", "Silver", "Gold", "Sapphire", "Ruby", "Emerald", "Diamond")
    val LEAGUE_ICONS = listOf("🥉", "🥈", "🥇", "💎", "❤️‍🔥", "💚", "👑")
    val AVATARS = listOf("🦉", "🦁", "🐯", "🦊", "🐼", "🐬", "🦅", "🐢", "🚀", "⭐", "📚", "🧠")

    data class Me(val friendCode: String, val firstName: String, val avatar: String, val league: Int)
    data class Row(val rank: Int, val name: String, val avatar: String, val xp: Int, val me: Boolean)
    data class Board(val league: Int, val daysLeft: Int, val rows: List<Row>) {
        /** Promotion and demotion zones, as in the weekly rules (top 7 up, bottom 5 down in groups of 10+). */
        fun zone(rank: Int): Int = when {
            rank <= 7 && league < LEAGUES.lastIndex -> 1
            rows.size >= 10 && rank > rows.size - 5 && league > 0 -> -1
            else -> 0
        }
    }
    data class Friend(val code: String, val name: String, val avatar: String, val xp: Int, val streak: Int)
    data class Nudge(val name: String, val avatar: String)
    data class Group(val code: String, val name: String, val rows: List<Row>)

    fun me(text: String): Me? = runCatching { JSONObject(text).let { Me(it.getString("friendCode"), it.getString("firstName"), it.getString("avatar"), it.optInt("league")) } }.getOrNull()

    fun board(text: String): Board? = runCatching {
        val o = JSONObject(text)
        Board(o.optInt("league"), o.optInt("daysLeft", 7), rows(o.optJSONArray("rows")))
    }.getOrNull()

    fun friends(text: String): List<Friend> = runCatching {
        JSONArray(text).let { a -> List(a.length()) { a.getJSONObject(it).let { f -> Friend(f.getString("code"), f.getString("name"), f.getString("avatar"), f.optInt("xp"), f.optInt("streak")) } } }
    }.getOrDefault(emptyList())

    fun nudges(text: String): List<Nudge> = runCatching {
        JSONArray(text).let { a -> List(a.length()) { a.getJSONObject(it).let { n -> Nudge(n.getString("name"), n.getString("avatar")) } } }
    }.getOrDefault(emptyList())

    fun groups(text: String): List<Group> = runCatching {
        JSONArray(text).let { a -> List(a.length()) { a.getJSONObject(it).let { g -> Group(g.getString("code"), g.getString("name"), rows(g.optJSONArray("rows"))) } } }
    }.getOrDefault(emptyList())

    private fun rows(a: JSONArray?): List<Row> = if (a == null) emptyList() else List(a.length()) { i ->
        a.getJSONObject(i).let { r -> Row(r.optInt("rank", i + 1), r.optString("name", "Learner"), r.optString("avatar", "🦉"), r.optInt("xp"), r.optBoolean("me")) }
    }

    /** XP earned since Monday (the league week). */
    fun weekXp(history: List<LoggedEvidence>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return history.filter { !Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate().isBefore(monday) }.sumOf { Rewards.xpOf(it.evidence) }
    }

    /** The error in a reply like {"error":"CODE_NOT_FOUND"}, else null. */
    fun errorOf(text: String?): String? = text?.trim()?.takeIf { it.startsWith("{") }
        ?.let { runCatching { JSONObject(it).optString("error").takeIf { e -> e.isNotBlank() } }.getOrNull() }

    /** A friendly line for a server refusal. */
    fun explain(code: String?): String = when (code) {
        "CODE_NOT_FOUND" -> "No one has that code. Check it and try again."
        "THAT_IS_YOU" -> "That's your own code."
        "TOO_MANY_FRIENDS" -> "You have 100 friends already."
        "TOO_MANY_GROUPS" -> "You've made 10 groups already."
        "GROUP_FULL" -> "That group is full."
        "TOO_MANY_TRIES" -> "Too many wrong codes today. Try again tomorrow."
        "NAME_NOT_ALLOWED" -> "Please choose a different name: letters and numbers only, nothing rude."
        "OFFLINE" -> "Connect to the internet and try again."
        else -> "That didn't work. Please try again."
    }
}

/** Three small goals each day, worked out from today's study: they reset at midnight. */
object Quests {
    data class Quest(val id: String, val title: String, val progress: Int, val target: Int) {
        val done: Boolean get() = progress >= target
    }

    fun today(history: List<LoggedEvidence>, dailyGoalXp: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Quest> {
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val events = history.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == today }
        val xp = events.sumOf { Rewards.xpOf(it.evidence) }
        val lessons = events.count { it.evidence == Evidence.LessonCompleted || it.evidence is Evidence.SessionCompleted }
        val practice = events.count { it.evidence is Evidence.QuizCompleted || it.evidence == Evidence.WrittenSubmitted ||
            it.evidence is Evidence.DemoCheckedExam || it.evidence is Evidence.ExamMarked }
        return listOf(
            Quest("xp", "Earn $dailyGoalXp XP", minOf(xp, dailyGoalXp), dailyGoalXp),
            Quest("lesson", "Finish a lesson or session", minOf(lessons, 1), 1),
            Quest("practice", "Do 2 quizzes, answers or papers", minOf(practice, 2), 2),
        )
    }
}
