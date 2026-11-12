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
