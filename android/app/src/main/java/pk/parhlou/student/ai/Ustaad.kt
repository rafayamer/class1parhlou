package pk.parhlou.student.ai

import org.json.JSONArray
import org.json.JSONObject

/** One message of an Ask Ustaad chat. [image] is a small JPEG data URL of a photographed question. */
data class UstaadMessage(
    val role: String, // "student" or "ustaad"
    val text: String,
    val image: String? = null,
    val answer: UstaadAnswer? = null,
    val at: Long = System.currentTimeMillis(),
)

data class UstaadRequest(
    val message: String,
    val image: String?,
    val history: List<UstaadMessage>,
    /** The student avatar summary (domain/StudentProfile), at most ~150 words. */
    val profile: String,
    val language: String,
    val style: String,
    val subjectIds: Set<String>,
    val moduleId: String? = null,
    val examActive: Boolean = false,
)

data class UstaadStep(val text: String, val why: String)
data class BookLink(val moduleId: String, val index: Int, val source: String, val section: String)
data class LessonVideo(val id: String, val title: String, val channel: String, val thumbnail: String)

/** Ask Ustaad's reply: a chat message, or a Chegg-style solution card. */
data class UstaadAnswer(
    val kind: String, // SOLUTION, CHAT, UNREADABLE, OFF_TOPIC, LOCKED, OFFLINE
    val reply: String,
    val questionAsRead: String = "",
    val topic: String = "",
    val steps: List<UstaadStep> = emptyList(),
    val finalAnswer: String = "",
    val concept: String = "",
    val examTip: String = "",
    val checkQuestion: String = "",
    val suggestions: List<String> = emptyList(),
    val videoQuery: String = "",
    val book: BookLink? = null,
    val videos: List<LessonVideo> = emptyList(),
    /** Offline answers only: the course note it came from, so follow-ups stay on the same topic. */
    val noteId: String = "",
    /** SOLVE, CONCEPT or CHAT: lesson videos come only with CONCEPT answers; the student can still ask for one. */
    val intent: String = "",
    /** A graph, number line, diagram, chart or table to draw, as the server checked it (JSON), or "". */
    val figure: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("reply", reply).put("questionAsRead", questionAsRead).put("topic", topic)
        .put("steps", JSONArray(steps.map { JSONObject().put("text", it.text).put("why", it.why) }))
        .put("finalAnswer", finalAnswer).put("concept", concept).put("examTip", examTip).put("checkQuestion", checkQuestion)
        .put("suggestions", JSONArray(suggestions)).put("videoQuery", videoQuery).put("noteId", noteId)
        .put("book", book?.let { JSONObject().put("moduleId", it.moduleId).put("index", it.index).put("source", it.source).put("section", it.section) } ?: JSONObject.NULL)
        .put("videos", JSONArray(videos.map { JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel).put("thumbnail", it.thumbnail) }))
        .put("intent", intent).put("figure", if (figure.isBlank()) JSONObject.NULL else JSONObject(figure))

    companion object {
        /** Reads a gateway reply (or a saved one). Null when it isn't a valid answer. */
        fun parse(r: JSONObject): UstaadAnswer? {
            val kind = r.optString("kind").takeIf { it in setOf("SOLUTION", "CHAT", "UNREADABLE", "OFF_TOPIC", "LOCKED", "OFFLINE") } ?: return null
            fun list(k: String) = r.optJSONArray(k)?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
            val steps = r.optJSONArray("steps")?.let { a -> List(a.length()) { a.getJSONObject(it) }.map { UstaadStep(it.optString("text"), it.optString("why")) } }.orEmpty()
            val book = r.optJSONObject("book")?.let { b ->
                b.optString("moduleId").takeIf { it.isNotBlank() }?.let { BookLink(it, b.optInt("index", -1), b.optString("source"), b.optString("section")) }
            }
            val videos = r.optJSONArray("videos")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
                .filter { it.optString("id").matches(Regex("[A-Za-z0-9_-]{6,20}")) }
                .map { LessonVideo(it.optString("id"), it.optString("title"), it.optString("channel"), it.optString("thumbnail")) }
            val reply = r.optString("reply")
            if (reply.isBlank() && steps.isEmpty()) return null
            return UstaadAnswer(kind, reply, r.optString("questionAsRead"), r.optString("topic"), steps, r.optString("finalAnswer"),
                r.optString("concept"), r.optString("examTip"), r.optString("checkQuestion"), list("suggestions"), r.optString("videoQuery"), book, videos,
                r.optString("noteId"), r.optString("intent"),
                r.optJSONObject("figure")?.takeIf { pk.parhlou.student.ui.components.FigureSpec.parse(it) != null }?.toString().orEmpty())
        }
    }
}

/** Ask Ustaad without the internet: text questions are answered from the course notes; photos need the AI. */
object UstaadOffline {
    suspend fun answer(ai: ParhlouAi, req: UstaadRequest): UstaadAnswer {
        if (req.examActive) return UstaadAnswer("LOCKED", "You have a paper in progress. I'll help you as soon as you hand it in. Good luck!")
        if (req.image != null) return UstaadAnswer("OFFLINE", "I need the internet to read a photo. Connect and send it again, or type the question and I'll help from your book.",
            suggestions = listOf("I'll type the question"))
        val previous = req.history.lastOrNull { it.role == "ustaad" }?.answer
        val style = FOLLOW_UPS[req.message]
        val t = ai.tutor(TutorTurn(if (style != null) "" else req.message, req.subjectIds, req.moduleId, style ?: TutorStyle.NORMAL,
            lastNoteId = previous?.noteId?.ifBlank { null },
            awaitingCheck = style == null && !previous?.checkQuestion.isNullOrBlank(), examActive = req.examActive))
        val text = buildString {
            append(t.answer)
            t.explanation?.let { append("\n\n").append(it) }
            if (t.steps.isNotEmpty()) t.steps.forEach { append("\n- ").append(it) }
            t.checkQuestion?.let { append("\n\n**Check yourself:** ").append(it) }
        }
        return UstaadAnswer("CHAT", text, checkQuestion = t.checkQuestion.orEmpty(), noteId = t.noteId.orEmpty(),
            suggestions = if (t.noteId != null) FOLLOW_UPS.keys.toList() else emptyList())
    }

    /** Offline follow-up chips and the tutor style each asks for. */
    private val FOLLOW_UPS = linkedMapOf(
        "Explain more simply" to TutorStyle.SIMPLER, "Another example" to TutorStyle.ANOTHER_EXAMPLE,
        "Show each step" to TutorStyle.STEPS, "Ask me a question" to TutorStyle.QUIZ_ME,
    )
}
