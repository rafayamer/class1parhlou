package pk.parhlou.student.domain

data class LoggedEvidence(val at: Long, val key: String, val evidence: Evidence)

/** Text form of the append-only evidence log kept on the phone ("time|key|code" per line). */
object EvidenceCodec {
    fun encode(e: Evidence): String = when (e) {
        Evidence.ModuleOpened -> "O"
        Evidence.VideoStudyConfirmed -> "V"
        Evidence.ReadingChecked -> "R"
        is Evidence.QuizCompleted -> "Q:${e.firstTryPercent}"
        Evidence.WrittenSubmitted -> "W"
        Evidence.ExamPendingReview -> "P"
        is Evidence.ExamMarked -> "E:${e.percent}"
        is Evidence.DemoCheckedExam -> "D:${e.percent}"
        Evidence.TopicMissed -> "M"
        Evidence.TopicCorrect -> "C"
    }

    fun decode(code: String): Evidence? {
        val number = code.substringAfter(':', "").toIntOrNull()
        return when (code.substringBefore(':')) {
            "O" -> Evidence.ModuleOpened
            "V" -> Evidence.VideoStudyConfirmed
            "R" -> Evidence.ReadingChecked
            "Q" -> number?.let { Evidence.QuizCompleted(it) }
            "W" -> Evidence.WrittenSubmitted
            "P" -> Evidence.ExamPendingReview
            "E" -> number?.let { Evidence.ExamMarked(it) }
            "D" -> number?.let { Evidence.DemoCheckedExam(it) }
            "M" -> Evidence.TopicMissed
            "C" -> Evidence.TopicCorrect
            else -> null
        }
    }

    fun line(at: Long, key: String, e: Evidence): String = "$at|$key|${encode(e)}"

    /** Unknown or damaged lines are skipped, so an older or newer app version never crashes on the log. */
    fun parse(log: String): List<LoggedEvidence> = log.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val at = parts[0].toLongOrNull() ?: return@mapNotNull null
        val evidence = decode(parts[2]) ?: return@mapNotNull null
        LoggedEvidence(at, parts[1], evidence)
    }.toList()
}
