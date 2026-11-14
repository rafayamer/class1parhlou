package pk.parhlou.student.domain

import kotlin.math.exp

const val TOPIC_PREFIX = "topic:"
private const val DAY = 24 * 60 * 60 * 1000L

data class WeakTopic(
    val noteId: String,
    val title: String,
    /** Misses since the topic was last answered correctly. */
    val recentMisses: Int,
    val lastMissedAt: Long,
    /** Higher = more urgent: more misses, more recent. */
    val score: Double,
)

/** Weak topics come only from real mistakes, never from questions asked to the tutor (AI instructions §12). */
object Weakness {
    fun topics(history: List<LoggedEvidence>, titleOf: (String) -> String?, now: Long): List<WeakTopic> =
        history.filter { it.key.startsWith(TOPIC_PREFIX) }
            .groupBy { it.key.removePrefix(TOPIC_PREFIX) }
            .mapNotNull { (noteId, events) ->
                val lastCorrect = events.filter { it.evidence == Evidence.TopicCorrect }.maxOfOrNull { it.at } ?: Long.MIN_VALUE
                val misses = events.filter { it.evidence == Evidence.TopicMissed && it.at > lastCorrect }
                if (misses.isEmpty()) return@mapNotNull null
                val title = titleOf(noteId) ?: return@mapNotNull null
                // Each miss counts less as it ages (half-life of about two weeks).
                val score = misses.sumOf { exp(-(now - it.at).coerceAtLeast(0) / (20.0 * DAY)) }
                WeakTopic(noteId, title, misses.size, misses.maxOf { it.at }, score)
            }
            .sortedByDescending { it.score }
}

enum class PlanKind { REPAIR, REVIEW, LEARN, PAPER, READINESS }

data class PlanTask(
    val kind: PlanKind,
    val title: String,
    val reason: String,
    val minutes: Int,
    /** Module id, note id or paper id, depending on [kind]. */
    val target: String,
)

data class PlanModule(val id: String, val title: String, val chapterId: String, val chapterTitle: String)

/**
 * Today's plan from real evidence (PRD §10): weak-topic repair first, then reviews that are due, then new learning
 * or the next paper, filled up to the student's daily minutes. Every task says why it was chosen.
 */
object StudyPlanner {
    /** Days after the last practice when the next review is due, by how many times the module has been practised. */
    val REVIEW_DAYS = listOf(1, 3, 7, 14, 30)

    fun plan(
        modules: List<PlanModule>,
        history: List<LoggedEvidence>,
        noteTitle: (String) -> String?,
        papers: List<Pair<String, String>>,          // (paper id, title) of papers with questions, in order
        testSeries: Boolean,
        minutesPerDay: Int,
        now: Long,
    ): List<PlanTask> {
        val byKey = history.groupBy { it.key }
        fun events(key: String) = byKey[key].orEmpty()
        fun state(moduleId: String) = ProgressCalculator.stateOf(events(moduleId).map { it.evidence })
        val tasks = mutableListOf<PlanTask>()

        Weakness.topics(history, noteTitle, now).take(2).forEach { t ->
            tasks += PlanTask(PlanKind.REPAIR, "Fix: ${t.title}",
                "You missed this ${times(t.recentMisses)} recently, last ${ago(now - t.lastMissedAt)}.", 10, t.noteId)
        }

        modules.forEach { m ->
            val practice = events(m.id).filter { it.evidence is Evidence.QuizCompleted || it.evidence == Evidence.WrittenSubmitted }
            if (practice.isEmpty()) return@forEach
            val last = practice.maxOf { it.at }
            val interval = REVIEW_DAYS[(practice.size - 1).coerceIn(0, REVIEW_DAYS.lastIndex)]
            val since = days(now - last)
            if (since >= interval) {
                tasks += PlanTask(PlanKind.REVIEW, "Review: ${m.title}",
                    "Last practised ${ago(now - last)}. Reviewing now helps you remember it for the exam.", 15, m.id)
            }
        }

        val submitted = history.filter { it.key.startsWith("paper:") }.map { it.key.removePrefix("paper:") }.toSet()
        val nextPaper = papers.firstOrNull { it.first !in submitted }
        if (testSeries) {
            if (nextPaper != null) {
                tasks += PlanTask(PlanKind.READINESS, "Readiness check", "A short quiz before your next paper: ${nextPaper.second}.", 10, nextPaper.first)
                tasks += PlanTask(PlanKind.PAPER, "Paper: ${nextPaper.second}", "Your Test Series path: the next paper you haven't sat.", 90, nextPaper.first)
            }
        } else {
            val next = modules.firstOrNull {
                state(it.id) in setOf(LearningState.NOT_STARTED, LearningState.LEARNING, LearningState.PRACTISING)
            }
            if (next != null) {
                val started = state(next.id) != LearningState.NOT_STARTED
                tasks += PlanTask(PlanKind.LEARN, "${if (started) "Continue" else "Start"}: ${next.title}",
                    "Next in ${next.chapterTitle}.", 30, next.id)
            }
            // A chapter test once every module of that chapter is ready.
            modules.groupBy { it.chapterId }.forEach { (_, ms) ->
                val ready = ms.all { state(it.id) == LearningState.READY_FOR_ASSESSMENT }
                if (ready && nextPaper != null) {
                    tasks += PlanTask(PlanKind.PAPER, "Test: ${ms.first().chapterTitle}",
                        "You've practised every module of this unit. Time for the 90-minute test.", 90, nextPaper.first)
                }
            }
        }

        // Keep the day realistic: stop once the student's minutes are used, but always keep the first task.
        var used = 0
        return tasks.distinctBy { it.kind to it.target }.filterIndexed { i, t ->
            val keep = i == 0 || used + t.minutes <= minutesPerDay.coerceAtLeast(15)
            if (keep) used += t.minutes
            keep
        }
    }

    private fun days(ms: Long) = (ms / DAY).toInt()
    private fun times(n: Int) = when (n) { 1 -> "once"; 2 -> "twice"; else -> "$n times" }
    private fun ago(ms: Long) = when (val d = days(ms)) { 0 -> "today"; 1 -> "yesterday"; else -> "$d days ago" }
}

/** What a student did on one day, for the Home week calendar. */
data class DaySummary(val quizzes: Int, val written: Int, val papers: Int, val topicsFixed: Int) {
    val empty: Boolean get() = quizzes + written + papers + topicsFixed == 0

    /** "2 quizzes, 1 written answer and 1 paper", or "" when nothing was done. */
    fun describe(): String {
        fun n(count: Int, one: String, many: String) = if (count == 0) null else "$count ${if (count == 1) one else many}"
        val parts = listOfNotNull(
            n(quizzes, "quiz", "quizzes"), n(written, "written answer", "written answers"),
            n(papers, "paper", "papers"), n(topicsFixed, "topic fixed", "topics fixed"),
        )
        return when (parts.size) { 0 -> ""; 1 -> parts[0]; else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last() }
    }

    companion object {
        /** Evidence logged in [from, until). Paper marks are counted once per paper. */
        fun of(history: List<LoggedEvidence>, from: Long, until: Long): DaySummary {
            val day = history.filter { it.at in from until until }
            return DaySummary(
                quizzes = day.count { it.evidence is Evidence.QuizCompleted },
                written = day.count { it.evidence == Evidence.WrittenSubmitted },
                papers = day.filter { it.key.startsWith("paper:") }.map { it.key }.distinct().size,
                topicsFixed = day.count { it.key.startsWith(TOPIC_PREFIX) && it.evidence == Evidence.TopicCorrect },
            )
        }
    }
}
