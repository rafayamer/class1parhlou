package pk.parhlou.student.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.PaperPick
import pk.parhlou.student.domain.PickSlot
import pk.parhlou.student.domain.ScriptPage
import java.net.HttpURLConnection
import java.net.URL

/** Sends a JSON body to the gateway. Returns the HTTP status and response text, or null if it could not be reached. */
typealias GatewayTransport = suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String>?

/**
 * Real AI: calls the Parhlou gateway (supabase/functions/ai), which holds the OpenAI key and calls GPT-6 Luna.
 * The phone sends only ids and the student's own text or photos; questions and marking keys stay on the server.
 * Whenever the gateway is unreachable or returns something invalid, [fallback] answers and the reply says so.
 */
class LunaGateway(
    private val endpoint: String,
    private val anonKey: String,
    private val installId: suspend () -> String,
    private val fallback: ParhlouAi,
    /** The signed-in student's session token, or null to fall back to the install id. */
    private val accessToken: suspend () -> String? = { null },
    private val transport: GatewayTransport = ::httpPost,
) : ParhlouAi {

    override val label = "Parhlou AI (GPT-6 Luna)"
    override val readsPhotos = true

    override suspend fun tutor(turn: TutorTurn): TutorReply {
        val body = JSONObject()
            .put("task", "tutor").put("question", turn.question).put("style", turn.style.name)
            .put("subjectIds", JSONArray(turn.subjectIds.toList())).put("lastNoteId", turn.lastNoteId ?: JSONObject.NULL)
            .put("awaitingCheck", turn.awaitingCheck).put("examActive", turn.examActive)
        return call(body)?.let(::parseTutor) ?: offline(fallback.tutor(turn))
    }

    override suspend fun mcqFeedback(question: McqQuestion, chosen: Int, subjectIds: Set<String>): TutorReply {
        val body = JSONObject().put("task", "mcq_feedback").put("questionId", question.id).put("chosen", chosen)
        return call(body)?.let(::parseTutor) ?: offline(fallback.mcqFeedback(question, chosen, subjectIds))
    }

    override suspend fun markWritten(question: WrittenQuestion, answer: String, photos: Int, images: List<String>, paperId: String?): WrittenMark {
        if (answer.isBlank() && images.isEmpty()) return fallback.markWritten(question, answer, photos)
        val body = JSONObject().put("task", "mark_written").put("questionId", question.id)
            .put("answer", answer).put("images", JSONArray(images))
        if (paperId != null) body.put("paperId", paperId)
        val mark = call(body)?.let { parseMark(question, it) }
        // Model output is untrusted: it is used only if it matches this question's rubric exactly.
        return if (mark != null && MarkingValidator.problems(question, mark).isEmpty()) mark
        else fallback.markWritten(question, answer, photos)
    }

    override suspend fun markHandwritten(question: WrittenQuestion, answer: String, images: List<String>, paperId: String?): WrittenMark {
        if (images.isEmpty()) return markWritten(question, answer, 0, images, paperId)
        val body = JSONObject().put("task", "mark_written").put("questionId", question.id)
            .put("answer", answer).put("images", JSONArray(images)).put("handwritten", true)
        if (paperId != null) body.put("paperId", paperId)
        val mark = call(body)?.let { parseMark(question, it) }
        return if (mark != null && MarkingValidator.problems(question, mark).isEmpty()) mark
        else fallback.markWritten(question, answer, images.size)
    }

    override suspend fun readScriptPage(image: String, page: Int, questions: List<Pair<String, String>>, mcqCount: Int): ScriptPage? {
        if (questions.isEmpty()) return null
        val body = JSONObject().put("task", "read_script").put("image", image).put("page", page).put("mcqCount", mcqCount)
            // The first question id tells the server which course this paper belongs to.
            .put("questionId", questions.first().first)
            .put("questions", JSONArray(questions.map { (id, label) -> JSONObject().put("id", id).put("label", label) }))
        return call(body)?.let(ScriptPage::parse)
    }

    override suspend fun pickPaper(slots: List<PickSlot>, profile: String): PaperPick? {
        val first = slots.firstOrNull()?.candidates?.firstOrNull() ?: return null
        val body = JSONObject().put("task", "pick_paper").put("profile", profile).put("questionId", first.id)
            .put("slots", JSONArray(slots.map { s ->
                JSONObject().put("slot", s.slot).put("count", s.count).put("candidates", JSONArray(s.candidates.map {
                    JSONObject().put("id", it.id).put("topic", it.topic).put("seen", it.seen)
                }))
            }))
        return call(body)?.let { PaperPick.parse(it, slots) }
    }

    override suspend fun ustaad(req: UstaadRequest): UstaadAnswer {
        val body = JSONObject().put("task", "ustaad").put("message", req.message)
            .put("image", req.image ?: JSONObject.NULL)
            .put("history", JSONArray(req.history.takeLast(12).map { JSONObject().put("role", it.role).put("text", historyText(it)) }))
            .put("profile", req.profile).put("language", req.language).put("style", req.style)
            .put("subjectIds", JSONArray(req.subjectIds.toList())).put("moduleId", req.moduleId ?: JSONObject.NULL)
            .put("examActive", req.examActive)
        return call(body)?.let(UstaadAnswer::parse) ?: UstaadOffline.answer(fallback, req).let {
            if (it.kind == "OFFLINE") it else it.copy(reply = it.reply + "\n\n_(Answered offline from your course notes.)_")
        }
    }

    /** What an earlier message contributes to the conversation: its text, or the solved question and answer. */
    private fun historyText(m: UstaadMessage): String = m.answer?.let { a ->
        listOf(a.questionAsRead.takeIf { it.isNotBlank() }?.let { "Question: $it" }, a.reply,
            a.finalAnswer.takeIf { it.isNotBlank() }?.let { "Final answer: $it" }).filterNotNull().joinToString("\n")
    } ?: (m.text.ifBlank { if (m.image != null) "(sent a photo of a question)" else "" })

    override suspend fun recordMcqs(paperId: String, answers: Map<String, Int>): Boolean {
        if (answers.isEmpty()) return true
        val body = JSONObject().put("task", "mark_mcqs").put("paperId", paperId).put("answers", JSONObject(answers as Map<*, *>))
        return call(body) != null
    }

    private suspend fun call(body: JSONObject): JSONObject? = runCatching {
        val token = accessToken()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${token ?: anonKey}",
            "apikey" to anonKey,
            "x-parhlou-install" to installId(),
        )
        val (status, text) = transport(endpoint, headers, body.toString()) ?: return null
        if (status != 200) return null
        JSONObject(text).optJSONObject("result")
    }.getOrNull()

    private fun parseTutor(r: JSONObject): TutorReply? {
        val kind = runCatching { ReplyKind.valueOf(r.getString("kind")) }.getOrNull() ?: return null
        val answer = r.optString("answer").ifBlank { return null }
        val noteId = r.optString("noteId").takeIf { !r.isNull("noteId") && it.isNotBlank() }
        return TutorReply(
            kind = kind, answer = answer,
            explanation = r.optString("explanation").takeIf { !r.isNull("explanation") && it.isNotBlank() },
            steps = r.optJSONArray("steps")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            source = if (noteId != null) SOURCE else null,
            checkQuestion = r.optString("checkQuestion").takeIf { !r.isNull("checkQuestion") && it.isNotBlank() },
            noteId = noteId,
        )
    }

    private fun parseMark(question: WrittenQuestion, r: JSONObject): WrittenMark? {
        val status = runCatching { MarkStatus.valueOf(r.getString("status")) }.getOrNull() ?: return null
        val array = r.optJSONArray("criteria") ?: return null
        val criteria = List(array.length()) { i ->
            val c = array.getJSONObject(i)
            val rubric = question.rubric.firstOrNull { it.id == c.optString("criterionId") } ?: return null
            val criterionStatus = runCatching { CriterionStatus.valueOf(c.getString("status")) }.getOrNull() ?: return null
            // Marks, wording and advice come from the app's own rubric, never from the reply.
            CriterionMark(rubric.id, rubric.description, rubric.marks,
                if (criterionStatus == CriterionStatus.MET) rubric.marks else 0.0, criterionStatus, rubric.why, rubric.how,
                evidence = c.optString("evidence").take(400))
        }
        return WrittenMark(question.id, status, criteria, question.followUp,
            examiners = r.optInt("examiners", 1).coerceIn(1, 3), correction = r.optString("correction").take(400))
    }

    private fun offline(reply: TutorReply) =
        reply.copy(source = "Demo tutor: the AI is offline right now" + (reply.source?.let { " · $it" } ?: ""))

    companion object {
        const val SOURCE = "Parhlou approved notes, answered by GPT-6 Luna"

        suspend fun httpPost(url: String, headers: Map<String, String>, body: String): Pair<Int, String>? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(url).openConnection() as HttpURLConnection
                    try {
                        c.requestMethod = "POST"
                        c.connectTimeout = 15_000
                        c.readTimeout = 90_000
                        c.doOutput = true
                        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                        c.outputStream.use { it.write(body.toByteArray()) }
                        val status = c.responseCode
                        val stream = if (status in 200..299) c.inputStream else c.errorStream
                        status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
                    } finally { c.disconnect() }
                }.getOrNull()
            }
    }
}
