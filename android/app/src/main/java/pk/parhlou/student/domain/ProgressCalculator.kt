package pk.parhlou.student.domain

/** Learning states from PRD §9. Derived from evidence; never set directly by a tap. */
enum class LearningState(val label: String) {
    NOT_STARTED("Not started"),
    LEARNING("Learning"),
    PRACTISING("Practising"),
    READY_FOR_ASSESSMENT("Ready for test"),
    PENDING_REVIEW("Marking pending"),
    REPAIR_REQUIRED("Needs repair"),
    /** Marked by the on-phone demo checker: useful practice, but not verified evidence. */
    DEMO_CHECKED("Practised"),
    ASSESSED("Assessed"),
    MASTERED("Mastered"),
}

sealed interface Evidence {
    data object ModuleOpened : Evidence
    data object VideoStudyConfirmed : Evidence
    /** Worked through the module's tutor lesson to the end. */
    data object LessonCompleted : Evidence
    data object ReadingChecked : Evidence
    data class QuizCompleted(val firstTryPercent: Int) : Evidence
    data object WrittenSubmitted : Evidence
    data object ExamPendingReview : Evidence
    data class ExamMarked(val percent: Int) : Evidence
    /** A paper marked by the demo checker. It never counts as verified assessment. */
    data class DemoCheckedExam(val percent: Int) : Evidence
    /** A first-try MCQ or a marking point was missed on this topic (logged under key "topic:<noteId>"). */
    data object TopicMissed : Evidence
    /** The topic was answered correctly later (logged under key "topic:<noteId>"). */
    data object TopicCorrect : Evidence
    /** A guided practice session finished, with the XP it earned (rewards only; never counts as verified progress). */
    data class SessionCompleted(val xp: Int) : Evidence
    /** "Was this helpful?" on a solution: 1 = yes, 0 = no (feedback for the owner; no effect on progress). */
    data class Helpful(val yes: Int) : Evidence
    /** The daily chest, opened after finishing all of today's quests, with the bonus XP it gave (rewards only). */
    data class ChestOpened(val xp: Int) : Evidence
}

/**
 * Thresholds are placeholders until academic reviewers set them (PRD §7 "educator-configurable").
 */
data class ProgressRules(val quizReadyPercent: Int = 70, val examAssessedPercent: Int = 40, val examMasteredPercent: Int = 80)

object ProgressCalculator {
    fun stateOf(events: List<Evidence>, rules: ProgressRules = ProgressRules()): LearningState {
        if (events.isEmpty()) return LearningState.NOT_STARTED

        val lastExam = events.lastOrNull { it is Evidence.ExamMarked || it is Evidence.ExamPendingReview }
        when (lastExam) {
            is Evidence.ExamPendingReview -> return LearningState.PENDING_REVIEW
            is Evidence.ExamMarked -> return when {
                lastExam.percent >= rules.examMasteredPercent -> LearningState.MASTERED
                lastExam.percent >= rules.examAssessedPercent -> LearningState.ASSESSED
                else -> LearningState.REPAIR_REQUIRED
            }
            else -> Unit
        }

        if (events.any { it is Evidence.DemoCheckedExam }) return LearningState.DEMO_CHECKED

        val bestQuiz = events.filterIsInstance<Evidence.QuizCompleted>().maxOfOrNull { it.firstTryPercent }
        return when {
            bestQuiz != null && bestQuiz >= rules.quizReadyPercent && Evidence.WrittenSubmitted in events ->
                LearningState.READY_FOR_ASSESSMENT
            bestQuiz != null || Evidence.WrittenSubmitted in events -> LearningState.PRACTISING
            // Opening a module, watching a video or ticking "read" is study, not achievement.
            else -> LearningState.LEARNING
        }
    }

    /** The rocket only counts assessed evidence (PRD §9). */
    fun countsTowardMilestone(state: LearningState): Boolean =
        state == LearningState.ASSESSED || state == LearningState.MASTERED
}
