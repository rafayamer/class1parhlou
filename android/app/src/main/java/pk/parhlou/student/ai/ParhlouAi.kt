package pk.parhlou.student.ai

import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.WrittenQuestion

/**
 * The AI tasks Parhlou needs, shaped after docs/AI-INSTRUCTIONS.md (GPT-6 Luna).
 * [DemoBrain] answers on the phone from sample content. The production implementation will call the
 * server gateway, which holds the OpenAI key and calls `gpt-6-luna`; screens do not change.
 */
interface ParhlouAi {
    /** Shown to the student so they always know what is answering. */
    val label: String

    /** True when this AI can read page photos. The demo brain cannot. */
    val readsPhotos: Boolean get() = false

    suspend fun tutor(turn: TutorTurn): TutorReply

    suspend fun mcqFeedback(question: McqQuestion, chosen: Int, subjectIds: Set<String>): TutorReply

    /** Marks one typed answer against its rubric. [photos] is how many page photos are attached. */
    /** [images] holds the photos as data URLs when [readsPhotos] is true. */
    suspend fun markWritten(question: WrittenQuestion, answer: String, photos: Int, images: List<String> = emptyList(), paperId: String? = null): WrittenMark

    /**
     * Asks the server to check a paper's MCQ choices against its own answer key and record them, so they count as
     * server-verified marks. Returns false where there is no server (the demo brain), which is not an error.
     */
    suspend fun recordMcqs(paperId: String, answers: Map<String, Int>): Boolean = false

    /**
     * Marks an answer the student wrote on paper: [answer] is what the AI read from their pages and [images] are the pages
     * themselves (the original, which decides). Answers on paper are double-marked by the server.
     */
    suspend fun markHandwritten(question: WrittenQuestion, answer: String, images: List<String>, paperId: String? = null): WrittenMark =
        markWritten(question, answer, images.size, images, paperId)

    /** Reads one page of a handwritten answer script. [questions] are the paper's (question id, printed label). Null = couldn't read. */
    suspend fun readScriptPage(image: String, page: Int, questions: List<Pair<String, String>>, mcqCount: Int): pk.parhlou.student.domain.ScriptPage? = null

    /** Lets the AI choose a practice paper's questions from each slot's candidates. Null = not available (the app picks itself). */
    suspend fun pickPaper(slots: List<pk.parhlou.student.domain.PickSlot>, profile: String): pk.parhlou.student.domain.PaperPick? = null

    /** Lesson videos on a topic, asked for by the student. Null = not available here (the app opens a YouTube search). */
    suspend fun lessonVideos(query: String): List<LessonVideo>? = null

    /** The examiner's written analysis of a marked paper, from the app's own [facts]. Null = not available (the app writes it). */
    suspend fun paperReport(facts: String, topics: List<String>, profile: String, language: String): pk.parhlou.student.domain.ExaminerReport? = null

    /** Ask Ustaad: a chat turn or a photographed question. Without the AI, the notes-based tutor answers text questions. */
    suspend fun ustaad(req: UstaadRequest): UstaadAnswer = UstaadOffline.answer(this, req)
}

enum class TutorStyle { NORMAL, SIMPLER, ANOTHER_EXAMPLE, STEPS, QUIZ_ME }

/** One student message plus the trusted context the app supplies (never taken from the message text). */
data class TutorTurn(
    val question: String,
    val subjectIds: Set<String>,
    val moduleId: String? = null,
    val style: TutorStyle = TutorStyle.NORMAL,
    val lastNoteId: String? = null,
    /** True when the tutor's previous message ended with a check question. */
    val awaitingCheck: Boolean = false,
    /** True while a timed paper is running: the tutor must not help (instructions §10). */
    val examActive: Boolean = false,
)

enum class ReplyKind { ANSWER, NOT_FOUND, LOCKED, CHECK_RESULT }

data class TutorReply(
    val kind: ReplyKind,
    val answer: String,
    val title: String? = null,
    val explanation: String? = null,
    val steps: List<String> = emptyList(),
    val source: String? = null,
    val checkQuestion: String? = null,
    val noteId: String? = null,
)

enum class CriterionStatus { MET, NOT_FOUND, UNSURE }

data class CriterionMark(
    val criterionId: String,
    val description: String,
    val max: Double,
    val awarded: Double,
    val status: CriterionStatus,
    val why: String,
    val how: String,
    /** What the examiner saw in the answer for this point (a quote, or what is missing). */
    val evidence: String = "",
)

enum class MarkStatus(val label: String) {
    CHECKED("Checked"),
    PARTLY_CHECKED("Partly checked"),
    COULD_NOT_CHECK("Couldn't check automatically"),
    WAITING_FOR_AI("Waiting for the AI examiner"),
    NOT_ATTEMPTED("Not attempted"),
}

data class WrittenMark(
    val questionId: String,
    val status: MarkStatus,
    val criteria: List<CriterionMark>,
    val followUp: String,
    /** 2 = double-marked and both examiners agreed; 3 = a head examiner settled their disagreements. */
    val examiners: Int = 1,
    /** The examiner's correction for the most important mistake. */
    val correction: String = "",
) {
    val max: Double get() = criteria.sumOf { it.max }
    val awarded: Double get() = criteria.sumOf { it.awarded }
    /** Marks that could not be confirmed either way. Never counted as lost. */
    val unsure: Double get() = criteria.filter { it.status == CriterionStatus.UNSURE }.sumOf { it.max }
}

/** Checks an AI result before the app trusts it: model output is untrusted input. */
object MarkingValidator {
    fun problems(question: WrittenQuestion, mark: WrittenMark): List<String> {
        val problems = mutableListOf<String>()
        if (mark.questionId != question.id) problems += "mark is for another question"
        val rubricIds = question.rubric.map { it.id }
        if (mark.criteria.map { it.criterionId } != rubricIds) problems += "criteria do not match the rubric"
        mark.criteria.forEach { c ->
            val max = question.rubric.firstOrNull { it.id == c.criterionId }?.marks
            if (max != null && c.max != max) problems += "${c.criterionId}: wrong maximum"
            if (c.awarded < 0 || c.awarded > c.max) problems += "${c.criterionId}: awarded marks out of range"
            if (c.status != CriterionStatus.MET && c.awarded != 0.0) problems += "${c.criterionId}: marks without evidence"
        }
        if (mark.awarded > question.marks) problems += "total exceeds the question's marks"
        return problems
    }
}
