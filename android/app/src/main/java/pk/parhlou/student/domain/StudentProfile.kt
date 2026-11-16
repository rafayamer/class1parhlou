package pk.parhlou.student.domain

import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Subject
import pk.parhlou.student.data.UstaadPrefs

/**
 * The student avatar Ask Ustaad uses: who the student is and how they learn, built on the phone from their own
 * study history. Only this short summary (no email, no account id) is sent with an Ask Ustaad message.
 */
data class StudentProfile(
    val name: String,
    val classLine: String,
    val exam: String?,
    val target: String?,
    val level: Int,
    val streak: Int,
    val strong: List<String>,
    val weak: List<String>,
    val prefs: UstaadPrefs,
) {
    /** At most about 150 words, for the AI. */
    fun summary(): String = buildList {
        add("Name: ${name.ifBlank { "the student" }}. $classLine.")
        exam?.let { add("Exam: $it.") }
        target?.let { add("Target: $it.") }
        add("Level $level, ${streak}-day study streak.")
        if (strong.isNotEmpty()) add("Strong in: ${strong.take(5).joinToString("; ")}.")
        if (weak.isNotEmpty()) add("Struggles with (recent mistakes): ${weak.take(5).joinToString("; ")}. Explain these slowly and warn about the usual mistakes.")
        if (strong.isEmpty() && weak.isEmpty()) add("New to the app: no strong or weak topics known yet.")
        add("Prefers: ${prefs.language}; ${prefs.style}.")
    }.joinToString(" ").take(1200)

    companion object {
        fun build(
            onboarding: OnboardingState, subjects: List<Subject>, history: List<LoggedEvidence>, prefs: UstaadPrefs,
            noteTitle: (String) -> String?, now: Long = System.currentTimeMillis(),
        ): StudentProfile {
            val h = history.filter { it.at >= prefs.forgetBefore }
            val subject = subjects.firstOrNull()
            val r = Rewards.summary(h, onboarding.minutesPerDay, now)
            val modules = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules } }
            val strong = modules.filter { m -> Rewards.sectionLevel(h.filter { it.key == m.id }.map { it.evidence }) >= 3 }.map { it.title }
            val weak = Weakness.topics(h, noteTitle, now).map { it.title }
            val exam = subject?.let { s -> ExamCountdown.daysLeft(s.examDate)?.let { "${s.examName.ifBlank { "board exam" }} in about $it days" } }
            return StudentProfile(
                name = onboarding.name,
                classLine = "Class ${onboarding.grade ?: 9}, ${if (onboarding.boardCode == "PB" || onboarding.boardCode == null) "Punjab" else onboarding.boardCode} board, ${onboarding.medium ?: "English"} medium" +
                    (subject?.let { ", studying ${it.name}" } ?: ""),
                exam = exam,
                target = prefs.targetMarks.takeIf { it > 0 }?.let { "$it out of 75 in Mathematics" },
                level = r.level, streak = r.streakDays, strong = strong, weak = weak, prefs = prefs,
            )
        }
    }
}
