package pk.parhlou.student.domain

import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.MarkingValidator
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion

/** Everything the student handed in for one paper. */
data class PaperSubmission(
    val mcq: Map<String, Int> = emptyMap(),
    val written: Map<String, String> = emptyMap(),
    val photos: Map<String, Int> = emptyMap(),
    /** Page photos as data URLs, filled only when the AI can read them. */
    val photoData: Map<String, List<String>> = emptyMap(),
)

data class McqOutcome(val question: McqQuestion, val chosen: Int?, val correct: Boolean)

data class WrittenOutcome(
    val question: WrittenQuestion,
    val answer: String,
    val photos: Int,
    /** False when the student answered more questions than the section allows; extra answers are not counted. */
    val counted: Boolean,
    val mark: WrittenMark,
)

data class PaperResult(
    val mcq: List<McqOutcome>,
    val written: List<WrittenOutcome>,
    val mcqMarks: Double,
    val total: Double,
) {
    /** Marks the checker is sure about. */
    val confirmed: Double get() = mcqMarks + written.filter { it.counted }.sumOf { it.mark.awarded }
    /** Marks that still need the AI examiner. Never treated as lost. */
    val pending: Double get() = written.filter { it.counted }.sumOf { it.mark.unsure }
    val percent: Int get() = if (total <= 0) 0 else ((confirmed * 100) / total).toInt()
}

/**
 * Check Master: marks a whole paper. MCQs are checked exactly; written answers go to the AI one question
 * at a time, and each AI result is validated before it is counted (docs/AI-INSTRUCTIONS.md §9).
 */
object CheckMaster {
    suspend fun check(blueprint: PaperBlueprint, paper: PaperContent, submission: PaperSubmission, ai: ParhlouAi): PaperResult {
        val mcqEach = blueprint.sections.firstOrNull()?.marksEach ?: 1.0
        val mcq = paper.mcqs.map { q ->
            val chosen = submission.mcq[q.id]
            McqOutcome(q, chosen, chosen == q.sampleCorrectIndex)
        }
        val counted = countedQuestionIds(blueprint, paper, submission)
        val written = paper.written.sortedWith(compareBy({ it.section }, { it.number })).map { q ->
            val answer = submission.written[q.id].orEmpty()
            val photos = submission.photos[q.id] ?: 0
            var mark = ai.markWritten(q, answer, photos, submission.photoData[q.id].orEmpty(), blueprint.id)
            if (MarkingValidator.problems(q, mark).isNotEmpty()) {
                // An invalid AI result is rejected, not shown: the question simply stays unchecked.
                mark = WrittenMark(q.id, MarkStatus.COULD_NOT_CHECK, emptyList(), q.followUp)
            }
            WrittenOutcome(q, answer, photos, q.id in counted, mark)
        }
        return PaperResult(mcq, written, mcq.count { it.correct } * mcqEach, blueprint.totalMarks)
    }

    /**
     * The paper's percentage from marks the server recorded itself, or null if the server does not hold a mark for
     * every answered MCQ and every counted written answer (then nothing is treated as verified).
     */
    fun verifiedPercent(result: PaperResult, server: Map<String, Pair<Double, Double>>, mcqMarksEach: Double): Int? {
        if (server.isEmpty() || result.total <= 0) return null
        var marks = 0.0
        for (o in result.mcq) {
            if (o.chosen == null) continue
            val (awarded, max) = server[o.question.id] ?: return null
            marks += if (max > 0) awarded / max * mcqMarksEach else 0.0
        }
        for (o in result.written.filter { it.counted }) {
            val (awarded, _) = server[o.question.id] ?: return null
            marks += awarded.coerceAtMost(o.question.marks)
        }
        return ((marks * 100) / result.total).toInt().coerceIn(0, 100)
    }

    /** Applies each section's "answer N of M" rule: the first N attempted questions count. */
    fun countedQuestionIds(blueprint: PaperBlueprint, paper: PaperContent, submission: PaperSubmission): Set<String> =
        paper.written.groupBy { it.section }.flatMap { (section, questions) ->
            val limit = blueprint.sections.getOrNull(section)?.attempt ?: questions.size
            questions.sortedBy { it.number }
                .filter { !submission.written[it.id].isNullOrBlank() || (submission.photos[it.id] ?: 0) > 0 }
                .take(limit)
                .map { it.id }
        }.toSet()
}

/** Board-style grade and examiner's remarks for a marked paper. */
object BoardMarking {
    /** Punjab boards' grade bands (pass mark 33%). */
    fun grade(percent: Int): String = when {
        percent >= 80 -> "A+"
        percent >= 70 -> "A"
        percent >= 60 -> "B"
        percent >= 50 -> "C"
        percent >= 40 -> "D"
        percent >= 33 -> "E"
        else -> "F"
    }

    /** The marking points that cost the most marks, as an examiner would write them in the margin. */
    fun remarks(result: PaperResult, label: (pk.parhlou.student.data.WrittenQuestion) -> String, max: Int = 4): List<String> {
        val lost = result.written.filter { it.counted }.flatMap { o ->
            o.mark.criteria.filter { it.status == pk.parhlou.student.ai.CriterionStatus.NOT_FOUND && o.answer.isNotBlank() }
                .map { c -> Triple(o, c, c.max - c.awarded) }
        }.sortedByDescending { it.third }
        val notes = lost.take(max).map { (o, c, m) ->
            "Q ${label(o.question)}: −${if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()} for \"${c.description}\". ${c.how}"
        }.toMutableList()
        val wrongMcq = result.mcq.count { it.chosen != null && !it.correct }
        val blankMcq = result.mcq.count { it.chosen == null }
        if (wrongMcq > 0) notes += "MCQs: $wrongMcq wrong. Revise the reasons shown under each one."
        if (blankMcq > 0) notes += "MCQs: $blankMcq left blank. There is no negative marking, so always choose an answer."
        if (notes.isEmpty() && result.percent >= 80) notes += "Excellent paper. Keep writing every step and the final statement."
        return notes
    }
}
