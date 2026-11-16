package pk.parhlou.student.domain

/**
 * Remaining time is always derived from a fixed start timestamp (the server's, once connected),
 * never from a counter that pauses when the app is killed (PRD §12).
 */
data class ExamClock(val startedAtMillis: Long, val durationMinutes: Int) {
    val endsAtMillis: Long get() = startedAtMillis + durationMinutes * 60_000L

    fun remainingMillis(nowMillis: Long): Long = (endsAtMillis - nowMillis).coerceAtLeast(0)

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= endsAtMillis

    fun label(nowMillis: Long): String {
        val totalSeconds = remainingMillis(nowMillis) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/**
 * Board papers run in two parts, as in the exam hall: the objective part (MCQs) has its own time limit, after which
 * the MCQ sheet is collected (locked) and the subjective part opens for the rest of the paper's time. A student may
 * hand in the objective part early. Papers without an objective time limit have one part only.
 */
data class PaperPhases(val startedAtMillis: Long, val objectiveMinutes: Int?, val objectiveHandedInAt: Long? = null) {
    val twoParts: Boolean get() = objectiveMinutes != null && objectiveMinutes > 0
    val objectiveEndsAt: Long get() = startedAtMillis + (objectiveMinutes ?: 0) * 60_000L

    fun objectiveOpen(now: Long): Boolean = twoParts && objectiveHandedInAt == null && now < objectiveEndsAt
    /** The subjective part is open once the objective part is over (or immediately on a one-part paper). */
    fun subjectiveOpen(now: Long): Boolean = !objectiveOpen(now)
    fun objectiveClock(): ExamClock = ExamClock(startedAtMillis, objectiveMinutes ?: 0)
}
