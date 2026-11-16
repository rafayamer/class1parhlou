package pk.parhlou.student.ai

import pk.parhlou.student.data.ConceptNote
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.data.WrittenQuestion

/**
 * Offline stand-in for GPT-6 Luna. It is rule-based: it can only answer from the sample concept notes and
 * can only recognise the marking points written into each rubric. It never invents an answer, a citation
 * or a deduction; what it cannot recognise is reported as not checked, never as wrong.
 */
class DemoBrain(private val repo: ParhlouRepository) : ParhlouAi {

    override val label = "Ustaad"

    override suspend fun tutor(turn: TutorTurn): TutorReply {
        if (turn.examActive) {
            return TutorReply(ReplyKind.LOCKED, "You have a paper in progress. I can't help until you submit it. Good luck!")
        }
        val notes = turn.subjectIds.flatMap { repo.notes(it) }
        val last = notes.firstOrNull { it.id == turn.lastNoteId }

        if (turn.question.isBlank()) {
            return if (last == null) notFound(notes) else styled(last, turn.style)
        }
        val best = retrieve(notes, turn.question, turn.moduleId)
        if (turn.awaitingCheck && last != null) {
            if (TextMatch.matchesAny(turn.question, last.checkAccept)) {
                return TutorReply(
                    ReplyKind.CHECK_RESULT, "Correct! ${last.checkAnswer}",
                    title = last.title, noteId = last.id, source = SOURCE,
                )
            }
            // Only treat the message as an attempt at the check question if it doesn't read like a new question.
            if (best == null && !looksLikeQuestion(turn.question)) {
                return TutorReply(
                    ReplyKind.CHECK_RESULT, "Not quite. ${last.checkAnswer}",
                    title = last.title, explanation = last.simple, noteId = last.id, source = SOURCE,
                )
            }
        }
        return if (best == null) notFound(notes) else styled(best, turn.style)
    }

    override suspend fun mcqFeedback(question: McqQuestion, chosen: Int, subjectIds: Set<String>): TutorReply {
        val note = subjectIds.flatMap { repo.notes(it) }.firstOrNull { it.id == question.noteId }
        val correct = question.sampleCorrectIndex
        val whyWrong = question.rationales.getOrNull(chosen) ?: "That option does not fit the question."
        val whyRight = question.rationales.getOrNull(correct).orEmpty().removePrefix("Correct. ")
        return TutorReply(
            kind = ReplyKind.ANSWER,
            title = "Why \"${question.options.getOrNull(chosen)}\" is not the answer",
            answer = whyWrong,
            explanation = "The right answer is \"${question.options[correct]}\": $whyRight" +
                (note?.let { "\n\nThe idea behind it: ${it.simple}" } ?: ""),
            source = if (note != null) SOURCE else null,
            checkQuestion = note?.checkQuestion,
            noteId = note?.id,
        )
    }

    override suspend fun markWritten(question: WrittenQuestion, answer: String, photos: Int, images: List<String>, paperId: String?): WrittenMark {
        val typed = answer.isNotBlank()
        val met = question.rubric.associate { it.id to (typed && TextMatch.matchesAny(answer, it.accept)) }
        val anyMet = met.values.any { it }
        val criteria = question.rubric.map { c ->
            val status = when {
                met.getValue(c.id) -> CriterionStatus.MET
                !typed && photos == 0 -> CriterionStatus.NOT_FOUND
                // Work may be on a photo, or written in a way the rules don't recognise: don't call it wrong.
                photos > 0 || !anyMet -> CriterionStatus.UNSURE
                else -> CriterionStatus.NOT_FOUND
            }
            CriterionMark(c.id, c.description, c.marks, if (status == CriterionStatus.MET) c.marks else 0.0, status, c.why, c.how)
        }
        val status = when {
            !typed && photos == 0 -> MarkStatus.NOT_ATTEMPTED
            !typed -> MarkStatus.WAITING_FOR_AI
            !anyMet -> MarkStatus.COULD_NOT_CHECK
            criteria.any { it.status == CriterionStatus.UNSURE } -> MarkStatus.PARTLY_CHECKED
            else -> MarkStatus.CHECKED
        }
        return WrittenMark(question.id, status, criteria, question.followUp)
    }

    private fun styled(note: ConceptNote, style: TutorStyle): TutorReply = when (style) {
        TutorStyle.NORMAL -> TutorReply(
            ReplyKind.ANSWER, note.answer, note.title,
            explanation = note.explanation + "\n\nExample: " + note.examples.first(),
            source = SOURCE, checkQuestion = note.checkQuestion, noteId = note.id,
        )
        TutorStyle.SIMPLER -> TutorReply(
            ReplyKind.ANSWER, note.simple, note.title,
            explanation = "Example: " + note.examples.first(),
            source = SOURCE, checkQuestion = note.checkQuestion, noteId = note.id,
        )
        TutorStyle.ANOTHER_EXAMPLE -> TutorReply(
            ReplyKind.ANSWER, note.examples.getOrElse(1) { note.examples.first() }, note.title,
            source = SOURCE, checkQuestion = note.checkQuestion, noteId = note.id,
        )
        TutorStyle.STEPS -> TutorReply(
            ReplyKind.ANSWER, "Here it is step by step.", note.title,
            steps = note.steps, source = SOURCE, checkQuestion = note.checkQuestion, noteId = note.id,
        )
        TutorStyle.QUIZ_ME -> TutorReply(
            ReplyKind.ANSWER, "Try this one. Type your answer below.", note.title,
            source = SOURCE, checkQuestion = note.checkQuestion, noteId = note.id,
        )
    }

    private fun notFound(notes: List<ConceptNote>): TutorReply {
        val topics = notes.take(6).joinToString(", ") { it.title.lowercase() }
        val help = if (notes.isEmpty()) "There are no notes for your subjects yet."
        else "Right now I can help with: $topics, and more from Unit 1."
        return TutorReply(
            ReplyKind.NOT_FOUND,
            "I couldn't find that in my notes, so I won't guess. $help",
        )
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.endsWith("?") || QUESTION_STARTS.any { t.startsWith("$it ") }
    }

    /** Best note by matched keywords; longer phrases count for more. Null when nothing matches. */
    private fun retrieve(notes: List<ConceptNote>, question: String, moduleId: String?): ConceptNote? =
        notes.map { note ->
            val score = note.keywords.filter { TextMatch.matches(question, it) }
                .sumOf { k -> k.trim().split(" ").size.let { it * it } }
            note to (if (score > 0 && note.moduleId == moduleId) score + 1 else score)
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first

    companion object {
        private val QUESTION_STARTS = listOf(
            "what", "why", "how", "when", "where", "which", "who", "explain", "define", "describe", "prove",
            "show", "tell", "can", "could", "does", "do", "is", "are", "give", "help", "solve", "find",
        )
        const val SOURCE = "From your course notes"
    }
}
