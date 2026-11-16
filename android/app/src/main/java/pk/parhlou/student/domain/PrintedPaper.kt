package pk.parhlou.student.domain

import org.json.JSONObject
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion

/**
 * Board numbering, as printed on a Punjab board paper: Q1 is the MCQs, each short-question section is one question with
 * roman parts ("Q2 (iii)"), and every long question has its own number with parts (a) and (b) ("Q5").
 */
class BoardNumbering(private val blueprint: PaperBlueprint) {
    private val written = blueprint.sections.drop(1)
    private val isShort = written.map { it.marksEach <= 3.0 }
    private val shortQ = mutableMapOf<Int, Int>()
    private val longStart = mutableMapOf<Int, Int>()

    init {
        var q = 2
        written.indices.filter { isShort[it] }.forEach { shortQ[it + 1] = q++ }
        written.indices.filter { !isShort[it] }.forEach { longStart[it + 1] = q; q += written[it].count }
    }

    fun isLong(q: WrittenQuestion): Boolean = isShort.getOrNull(q.section - 1) == false

    /** The question number alone: 2 for Q2 (iii), 5 for Q5. */
    fun number(q: WrittenQuestion): Int =
        shortQ[q.section] ?: ((longStart[q.section] ?: (2 + q.section)) + q.number - 1)

    fun label(q: WrittenQuestion): String =
        if (isLong(q)) "Q${number(q)}" else "Q${number(q)} (${roman(q.number)})"

    companion object {
        private val ROMAN = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv",
            "xvi", "xvii", "xviii", "xix", "xx")
        fun roman(n: Int): String = ROMAN.getOrElse(n - 1) { n.toString() }
    }
}

/** One answer (or piece of an answer) the AI read on a page, as the student labelled it. */
data class ScriptSegment(
    val label: String,
    val questionId: String?,
    val continues: Boolean,
    val crossedOut: Boolean,
    val roughWork: Boolean,
    val legible: String,
    val text: String,
)

/** One page of the student's answer script, as the AI read it. */
data class ScriptPage(
    val page: Int,
    val kind: String,
    val legibility: String,
    val segments: List<ScriptSegment>,
    /** MCQ number → "A".."D", "BLANK" or "MULTIPLE" (read from the bubble sheet). */
    val mcq: Map<Int, String>,
    val note: String,
) {
    companion object {
        fun parse(r: JSONObject): ScriptPage? = runCatching {
            val segs = r.getJSONArray("segments")
            val mcq = r.optJSONArray("mcq")
            ScriptPage(
                page = r.getInt("page"), kind = r.getString("kind"), legibility = r.optString("legibility", "PARTLY"),
                segments = List(segs.length()) { i ->
                    val s = segs.getJSONObject(i)
                    ScriptSegment(s.optString("label"), s.optString("questionId").takeIf { !s.isNull("questionId") && it.isNotBlank() },
                        s.optBoolean("continues"), s.optBoolean("crossedOut"), s.optBoolean("roughWork"), s.optString("legible", "PARTLY"),
                        s.optString("text"))
                },
                mcq = if (mcq == null) emptyMap() else (0 until mcq.length()).associate { i ->
                    mcq.getJSONObject(i).let { it.getInt("number") to it.getString("choice") }
                },
                note = r.optString("note"),
            )
        }.getOrNull()
    }
}

/** A piece of writing that could not be matched to a question; the student assigns it on the review screen. */
data class LoosePiece(val page: Int, val index: Int, val segment: ScriptSegment)

/** The whole script put back together: one answer per question, whatever order the student wrote in. */
data class ScriptReading(
    /** Question id → the student's answer, parts in the order written ("5 a) …", "5 (b) …"). */
    val answers: Map<String, String>,
    /** Question id → the pages its answer is on (1-based, in scan order). */
    val pagesOf: Map<String, List<Int>>,
    /** MCQ id → chosen option index; only clearly filled single circles. */
    val mcq: Map<String, Int>,
    /** Remarks for the student: two circles filled, unlabelled answers, unreadable pages. */
    val remarks: List<String>,
    val loose: List<LoosePiece>,
)

object ScriptAssembler {
    /**
     * Joins the pages' pieces into answers. A piece that continues from the previous page joins the answer the previous page
     * ended with. Struck-through work and rough work are not marked, as in the board. [assigned] holds the student's own
     * choices for pieces the AI could not place ("page-index" → question id; an empty id discards the piece).
     */
    fun assemble(pages: List<ScriptPage>, paper: PaperContent, numbering: BoardNumbering, assigned: Map<String, String> = emptyMap()): ScriptReading {
        val parts = linkedMapOf<String, MutableList<String>>()
        val pagesOf = linkedMapOf<String, MutableSet<Int>>()
        val loose = mutableListOf<LoosePiece>()
        val remarks = mutableListOf<String>()
        val ids = paper.written.map { it.id }.toSet()
        var last: String? = null
        for (p in pages.sortedBy { it.page }) {
            if (p.kind == "NOT_A_SCRIPT") remarks += "Page ${p.page} doesn't look like an answer page, so it wasn't marked."
            if (p.legibility == "UNREADABLE") remarks += "Page ${p.page} couldn't be read. Scan it again in good light, flat and in focus."
            p.segments.forEachIndexed { i, s ->
                val key = "${p.page}-$i"
                if (s.crossedOut || s.roughWork) return@forEachIndexed
                val choice = assigned[key]
                val q = when {
                    choice != null -> choice.takeIf { it in ids }
                    s.continues && s.label.isBlank() -> last ?: s.questionId
                    else -> s.questionId
                }
                if (q == null) { if (choice == null && s.text.isNotBlank()) loose += LoosePiece(p.page, i, s); return@forEachIndexed }
                parts.getOrPut(q) { mutableListOf() } += listOf(s.label, s.text).filter { it.isNotBlank() }.joinToString(" ")
                pagesOf.getOrPut(q) { linkedSetOf() } += p.page
                last = q
            }
            if (p.note.isNotBlank()) remarks += "Page ${p.page}: ${p.note}"
        }
        if (loose.isNotEmpty()) remarks += "${loose.size} answer${if (loose.size == 1) " has" else "s have"} no clear question number. " +
            "In the board exam, write the same question and part number as on the paper, e.g. \"Q2 (iii)\"."
        val sheet = pages.flatMap { it.mcq.entries }.associate { it.key to it.value }
        val mcq = mutableMapOf<String, Int>()
        paper.mcqs.forEachIndexed { i, q ->
            when (val c = sheet[i + 1]) {
                "A", "B", "C", "D" -> mcq[q.id] = c[0] - 'A'
                "MULTIPLE" -> remarks += "MCQ ${i + 1}: more than one circle filled, or a choice was cut. The board gives zero for that."
                else -> Unit
            }
        }
        return ScriptReading(
            answers = parts.mapValues { (_, v) -> v.joinToString("\n") },
            pagesOf = pagesOf.mapValues { (_, v) -> v.toList() },
            mcq = mcq,
            remarks = remarks.distinct(),
            loose = loose,
        )
    }
}
