package pk.parhlou.student.domain

import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import kotlin.math.ceil

/**
 * Premium's AI credits: 1,000 a month, worth US$3.50 of AI time. The server charges every AI call at what it really cost, so
 * the numbers here are estimates: before making or sitting a paper the student sees about how many credits making it,
 * checking it and the examiner's report will use, and confirms.
 */
object Credits {
    /**
     * Credits each kind of AI work typically costs, used until the server sends its own estimates (from the real average of
     * recent calls). The same typical sizes as the server's, at its default prices.
     */
    val TYPICAL = mapOf("chat" to 0.3, "photo" to 0.6, "mark" to 0.6, "mark_photo" to 0.8, "page" to 0.5, "report" to 0.5, "pick" to 0.4)

    /** About how many credits a paper uses: making it (when Ustaad chooses the questions), checking it, and the report. */
    data class PaperCost(val make: Int, val check: Int, val analyse: Int) {
        val total: Int get() = make + check + analyse
    }

    /** Written answers a student writes in a paper: in each section, the share of its questions they must answer. */
    fun answersToMark(blueprint: PaperBlueprint, paper: PaperContent): Int =
        paper.written.groupBy { it.section }.entries.sumOf { (section, questions) ->
            val s = blueprint.sections.getOrNull(section)
            if (s == null || s.count <= 0 || s.attempt >= s.count) questions.size
            else ceil(questions.size * s.attempt / s.count.toDouble()).toInt()
        }

    /** About two written answers fit on one page of an answer script. */
    fun pagesFor(answers: Int): Int = if (answers <= 0) 0 else (answers + 1) / 2

    /**
     * The estimate for a paper with [answers] written answers. On paper, every page is read and every answer is marked from
     * its photos; in the app, typed answers are marked. MCQs are checked by the app and cost nothing.
     */
    fun paper(answers: Int, onPaper: Boolean, made: Boolean, estimate: (String) -> Double): PaperCost {
        val make = if (made) estimate("pick") else 0.0
        val check = if (onPaper) answers * estimate("mark_photo") + pagesFor(answers) * estimate("page") else answers * estimate("mark")
        return PaperCost(up(make), up(check), up(estimate("report")))
    }

    private fun up(credits: Double): Int = ceil(credits - 1e-9).toInt()

    /** "1,000" with a thousands comma. */
    fun format(credits: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.ENGLISH).format(credits)
}
