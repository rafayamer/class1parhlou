package pk.parhlou.student.domain

/** One answer to one MCQ. attemptNo starts at 1; retries are kept, never overwritten. */
data class QuizAnswer(val questionId: String, val chosen: Int, val correct: Boolean, val attemptNo: Int)

data class QuizSummary(
    val questions: Int,
    val firstTryCorrect: Int,
    val eventuallyCorrect: Int,
) {
    /** First-try accuracy is the evidence that counts; corrections after feedback are logged separately (PRD §7B). */
    val firstTryPercent: Int get() = if (questions == 0) 0 else (firstTryCorrect * 100) / questions
}

object QuizScorer {
    fun record(history: List<QuizAnswer>, questionId: String, chosen: Int, correctIndex: Int): List<QuizAnswer> {
        val attemptNo = history.count { it.questionId == questionId } + 1
        return history + QuizAnswer(questionId, chosen, chosen == correctIndex, attemptNo)
    }

    fun summarise(questionIds: List<String>, history: List<QuizAnswer>): QuizSummary {
        val first = questionIds.count { id -> history.firstOrNull { it.questionId == id }?.correct == true }
        val eventually = questionIds.count { id -> history.any { it.questionId == id && it.correct } }
        return QuizSummary(questionIds.size, first, eventually)
    }
}
