package pk.parhlou.student.domain

enum class ExamEventType(val code: Char) { STARTED('S'), LEFT('L'), RETURNED('R'), SUBMITTED('X') }

data class ExamEvent(val type: ExamEventType, val at: Long)

/** Append-only log of one sitting, stored as text ("S:1700000000000" per line). */
object ExamLog {
    fun parse(text: String): List<ExamEvent> = text.lineSequence().mapNotNull { line ->
        val type = ExamEventType.entries.firstOrNull { it.code == line.firstOrNull() } ?: return@mapNotNull null
        val at = line.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        ExamEvent(type, at)
    }.toList()

    /** Adds an event if it makes sense after the last one; otherwise returns the log unchanged. */
    fun append(text: String, type: ExamEventType, at: Long): String {
        val events = parse(text)
        val last = events.lastOrNull()?.type
        val allowed = when (type) {
            ExamEventType.STARTED -> events.isEmpty()
            ExamEventType.LEFT -> last == ExamEventType.STARTED || last == ExamEventType.RETURNED
            ExamEventType.RETURNED -> last == ExamEventType.LEFT
            ExamEventType.SUBMITTED -> last != null && last != ExamEventType.SUBMITTED
        }
        if (!allowed) return text
        val line = "${type.code}:$at"
        return if (text.isBlank()) line else "$text\n$line"
    }

    fun startedAt(text: String): Long? = parse(text).firstOrNull { it.type == ExamEventType.STARTED }?.at

    fun isSubmitted(text: String): Boolean = parse(text).any { it.type == ExamEventType.SUBMITTED }
}

data class IntegrityReport(
    val allowedMillis: Long,
    val usedMillis: Long,
    val timesAway: Int,
    val totalAwayMillis: Long,
    val longestAwayMillis: Long,
    val finishedOnTime: Boolean,
    val overrunMillis: Long,
)

/**
 * The invigilator only observes: it records when the student leaves the paper and reports afterwards.
 * It never blocks, warns or uses the camera. The report stays on the phone.
 */
object Invigilator {
    /** Shorter gaps are screen rotations or system dialogs, not leaving the paper. */
    const val MIN_ABSENCE_MILLIS = 2_000L
    private const val GRACE_MILLIS = 5_000L

    fun report(events: List<ExamEvent>, durationMinutes: Int): IntegrityReport? {
        val start = events.firstOrNull { it.type == ExamEventType.STARTED }?.at ?: return null
        val end = events.firstOrNull { it.type == ExamEventType.SUBMITTED }?.at ?: return null
        val allowed = durationMinutes * 60_000L
        val absences = mutableListOf<Long>()
        var leftAt: Long? = null
        for (e in events) {
            when (e.type) {
                ExamEventType.LEFT -> leftAt = e.at
                ExamEventType.RETURNED, ExamEventType.SUBMITTED -> {
                    leftAt?.let { absences += (e.at - it).coerceAtLeast(0) }
                    leftAt = null
                }
                ExamEventType.STARTED -> Unit
            }
        }
        val real = absences.filter { it >= MIN_ABSENCE_MILLIS }
        val used = (end - start).coerceAtLeast(0)
        return IntegrityReport(
            allowedMillis = allowed,
            usedMillis = used,
            timesAway = real.size,
            totalAwayMillis = real.sum(),
            longestAwayMillis = real.maxOrNull() ?: 0,
            finishedOnTime = used <= allowed + GRACE_MILLIS,
            overrunMillis = (used - allowed).coerceAtLeast(0),
        )
    }

    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
