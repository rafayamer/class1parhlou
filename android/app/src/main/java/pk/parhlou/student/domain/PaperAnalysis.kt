package pk.parhlou.student.domain

import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.data.PaperBlueprint
import kotlin.math.roundToInt

/** What a question tests: its topic (a lesson section) and unit. */
data class TopicInfo(val key: String, val title: String, val unit: Int?, val unitTitle: String, val noteId: String? = null)

/** Marks got out of marks available in one area of the paper. */
data class AreaScore(val key: String, val title: String, val got: Double, val max: Double, val noteId: String? = null) {
    val percent: Int get() = if (max <= 0) 0 else ((got * 100) / max).roundToInt()
    val lost: Double get() = max - got
}

enum class LossKind(val label: String) {
    STEPS("Working steps missing or wrong"),
    FINAL_ANSWER("Final answer wrong or missing"),
    WORDING("Definitions and statements not in the book's words"),
    MCQ_WRONG("MCQs answered wrongly"),
    MCQ_BLANK("MCQs left blank"),
    NOT_ATTEMPTED("Questions you were required to attempt but didn't"),
}

/** One lost marking point, as an examiner would note it. */
data class Mistake(val label: String, val topic: String, val point: String, val lost: Double, val fix: String)

/** The examiner's numbers for one marked paper. Worked out on the phone, so they never depend on the AI. */
data class PaperAnalysis(
    val percent: Int,
    val grade: String,
    /** The same performance on the 75-mark board paper. */
    val outOf75: Int,
    val units: List<AreaScore>,
    val topics: List<AreaScore>,
    val types: List<AreaScore>,
    val lost: Map<LossKind, Double>,
    /** Marks the checker could not confirm; never counted as lost. */
    val unchecked: Double,
    val strong: List<AreaScore>,
    val weak: List<AreaScore>,
    val needsWork: List<AreaScore>,
    val mistakes: List<Mistake>,
    val timeNote: String?,
) {
    /** The facts the AI examiner writes its report from (numbers are final; it may only explain them). */
    fun factsJson(paperTitle: String): String = JSONObject()
        .put("paper", paperTitle).put("percent", percent).put("grade", grade).put("outOf75", outOf75)
        .put("units", JSONArray(units.map { it.json() })).put("topics", JSONArray(topics.map { it.json() }))
        .put("questionTypes", JSONArray(types.map { it.json() }))
        .put("marksLostBy", JSONObject(lost.filterValues { it > 0 }.mapKeys { it.key.label }.mapValues { fmt(it.value) } as Map<*, *>))
        .put("uncheckedMarks", fmt(unchecked))
        .put("strong", JSONArray(strong.map { it.title })).put("weak", JSONArray(weak.map { it.title })).put("needsWork", JSONArray(needsWork.map { it.title }))
        .put("mistakes", JSONArray(mistakes.map { JSONObject().put("question", it.label).put("topic", it.topic).put("point", it.point).put("lost", fmt(it.lost)).put("fix", it.fix) }))
        .put("time", timeNote ?: "").toString()

    private fun AreaScore.json() = JSONObject().put("name", title).put("got", fmt(got)).put("max", fmt(max)).put("percent", percent)

    companion object {
        fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
    }
}

object PaperAnalyzer {
    private val FINAL = Regex("""final|answer|result|conclu|value of|solution set""", RegexOption.IGNORE_CASE)
    private val WORDING = Regex("""defin|state the|statement|book's words|wording|key terms""", RegexOption.IGNORE_CASE)

    fun analyse(
        blueprint: PaperBlueprint,
        result: PaperResult,
        topicOf: (String?) -> TopicInfo?,
        label: (pk.parhlou.student.data.WrittenQuestion) -> String,
        report: IntegrityReport? = null,
    ): PaperAnalysis {
        val mcqEach = blueprint.sections.firstOrNull()?.marksEach ?: 1.0
        // Every mark in the paper, with where it came from.
        data class Piece(val topic: TopicInfo?, val type: String, val got: Double, val max: Double)
        val pieces = mutableListOf<Piece>()
        val lost = LossKind.entries.associateWith { 0.0 }.toMutableMap()
        val mistakes = mutableListOf<Mistake>()
        result.mcq.forEach { o ->
            val t = topicOf(o.question.noteId ?: o.question.moduleId)
            pieces += Piece(t, "MCQs", if (o.correct) mcqEach else 0.0, mcqEach)
            when {
                o.chosen == null -> lost[LossKind.MCQ_BLANK] = lost.getValue(LossKind.MCQ_BLANK) + mcqEach
                !o.correct -> lost[LossKind.MCQ_WRONG] = lost.getValue(LossKind.MCQ_WRONG) + mcqEach
            }
        }
        var unchecked = 0.0
        result.written.filter { it.counted }.forEach { o ->
            val q = o.question
            val type = if (q.marks <= 3.0) "Short questions" else "Long questions"
            val t = topicOf(q.noteId)
            val confirmedMax = q.marks - o.mark.unsure
            unchecked += o.mark.unsure
            pieces += Piece(t, type, o.mark.awarded, confirmedMax)
            o.mark.criteria.filter { it.status == CriterionStatus.NOT_FOUND }.forEachIndexed { i, c ->
                val m = c.max - c.awarded
                val isLast = c.criterionId == o.mark.criteria.last().criterionId
                val kind = when {
                    WORDING.containsMatchIn(c.description) -> LossKind.WORDING
                    isLast && FINAL.containsMatchIn(c.description) -> LossKind.FINAL_ANSWER
                    else -> LossKind.STEPS
                }
                lost[kind] = lost.getValue(kind) + m
                if (i < 2) mistakes += Mistake(label(q), t?.title ?: "", c.description, m,
                    o.mark.correction.takeIf { it.isNotBlank() && i == 0 } ?: c.how)
            }
        }
        // Questions the paper required but the student did not attempt: their marks are lost too.
        blueprint.sections.drop(1).forEachIndexed { i, s ->
            val section = i + 1
            val inSection = result.written.filter { it.question.section == section }
            val answered = inSection.count { it.counted }
            val missing = (minOf(s.attempt, inSection.size) - answered).coerceAtLeast(0)
            if (missing > 0) {
                val m = missing * s.marksEach
                lost[LossKind.NOT_ATTEMPTED] = lost.getValue(LossKind.NOT_ATTEMPTED) + m
                // Unattempted marks are charged to the topics of the unanswered questions, first ones first.
                inSection.filter { !it.counted }.take(missing).forEach { o ->
                    pieces += Piece(topicOf(o.question.noteId), if (o.question.marks <= 3.0) "Short questions" else "Long questions", 0.0, o.question.marks)
                }
            }
        }

        fun group(key: (Piece) -> Triple<String, String, String?>?): List<AreaScore> =
            pieces.mapNotNull { p -> key(p)?.let { it to p } }.groupBy { it.first.first }.map { (k, list) ->
                AreaScore(k, list.first().first.second, list.sumOf { it.second.got }, list.sumOf { it.second.max }, list.first().first.third)
            }.filter { it.max > 0 }
        val units = group { p -> p.topic?.unit?.let { Triple("u$it", "Unit $it · ${p.topic.unitTitle}", null) } }.sortedBy { it.key.drop(1).toIntOrNull() ?: 99 }
        val topics = group { p -> p.topic?.let { Triple(it.key, it.title, it.noteId) } }.sortedByDescending { it.lost }
        val types = group { p -> Triple(p.type, p.type, null) }.sortedBy { listOf("MCQs", "Short questions", "Long questions").indexOf(it.key) }
        // A topic needs at least 2 marks of evidence before it is called strong or weak.
        val judged = topics.filter { it.max >= 2.0 }
        val strong = judged.filter { it.percent >= 75 }.sortedByDescending { it.percent }
        val weak = judged.filter { it.percent < 50 }.sortedByDescending { it.lost }
        val needsWork = judged.filter { it.percent in 50..74 }.sortedByDescending { it.lost }
        val timeNote = report?.let {
            when {
                !it.finishedOnTime -> "Finished ${Invigilator.duration(it.overrunMillis)} after time was up."
                it.usedMillis < it.allowedMillis / 2 -> "Used only ${Invigilator.duration(it.usedMillis)} of ${Invigilator.duration(it.allowedMillis)}."
                else -> "Finished on time (${Invigilator.duration(it.usedMillis)} of ${Invigilator.duration(it.allowedMillis)})."
            }
        }
        return PaperAnalysis(
            percent = result.percent, grade = BoardMarking.grade(result.percent), outOf75 = (result.percent * 75 / 100.0).roundToInt(),
            units = units, topics = topics, types = types, lost = lost, unchecked = unchecked,
            strong = strong, weak = weak, needsWork = needsWork,
            mistakes = mistakes.sortedByDescending { it.lost }.take(6), timeNote = timeNote,
        )
    }
}

/** The examiner's written report: what went well, what didn't, and exactly what to work on. */
data class ExaminerReport(
    val headline: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<Weakness>,
    val plan: List<String>,
    val technique: List<String>,
    val closing: String,
    val byAi: Boolean,
) {
    data class Weakness(val topic: String, val why: String, val action: String)

    fun toJson(): String = JSONObject().put("headline", headline).put("summary", summary).put("strengths", JSONArray(strengths))
        .put("weaknesses", JSONArray(weaknesses.map { JSONObject().put("topic", it.topic).put("why", it.why).put("action", it.action) }))
        .put("plan", JSONArray(plan)).put("technique", JSONArray(technique)).put("closing", closing).put("byAi", byAi).toString()

    companion object {
        private fun strings(a: JSONArray?, max: Int, len: Int) = a?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it).trim().takeIf { s -> s.isNotBlank() }?.take(len) } }.orEmpty().take(max)

        /** Reads a report; weaknesses must name topics from the analysis, so the AI cannot invent a weak area. */
        fun parse(r: JSONObject, allowedTopics: Set<String>, byAi: Boolean): ExaminerReport? = runCatching {
            val w = r.optJSONArray("weaknesses") ?: JSONArray()
            val weaknesses = (0 until w.length()).map { w.getJSONObject(it) }
                .filter { it.optString("topic") in allowedTopics }
                .map { Weakness(it.getString("topic"), it.optString("why").take(400), it.optString("action").take(400)) }.take(6)
            val headline = r.getString("headline").trim().take(160)
            val summary = r.getString("summary").trim().take(1200)
            if (headline.isBlank() || summary.isBlank()) return null
            ExaminerReport(headline, summary, strings(r.optJSONArray("strengths"), 5, 300), weaknesses,
                strings(r.optJSONArray("plan"), 7, 300), strings(r.optJSONArray("technique"), 5, 300), r.optString("closing").trim().take(400), byAi)
        }.getOrNull()

        /** The examiner's report written by the app itself, from the numbers alone (used offline). */
        fun local(a: PaperAnalysis, name: String = ""): ExaminerReport {
            val you = name.ifBlank { "You" }
            val headline = when {
                a.percent >= 80 -> "$you scored ${a.percent}% (grade ${a.grade}). This is A+ work; now make it consistent."
                a.percent >= 60 -> "${a.percent}% (grade ${a.grade}). A solid paper, but marks are leaking in a few clear places."
                a.percent >= 33 -> "${a.percent}% (grade ${a.grade}). You passed, but this paper needs serious work before the board exam."
                else -> "${a.percent}% (grade ${a.grade}). Below the pass mark. Let's fix this step by step, starting today."
            }
            val biggest = a.lost.filterValues { it > 0 }.maxByOrNull { it.value }
            val summary = buildString {
                append("On the 75-mark board paper this would be about ${a.outOf75}/75. ")
                a.types.filter { it.max > 0 }.joinToString("; ") { "${it.title}: ${it.percent}%" }.takeIf { it.isNotBlank() }?.let { append("$it. ") }
                biggest?.let { append("Most marks went on: ${it.key.label.lowercase()} (${PaperAnalysis.fmt(it.value)} marks). ") }
                if (a.unchecked > 0) append("${PaperAnalysis.fmt(a.unchecked)} marks could not be checked and are not counted against you. ")
                a.timeNote?.let { append(it) }
            }.trim()
            val strengths = a.strong.take(4).map { "${it.title}: ${it.percent}% (${PaperAnalysis.fmt(it.got)}/${PaperAnalysis.fmt(it.max)}). Keep it fresh with one revision a week." }
            val weaknesses = (a.weak + a.needsWork).take(5).map { t ->
                val m = a.mistakes.firstOrNull { it.topic == t.title }
                ExaminerReport.Weakness(t.title,
                    "${t.percent}%: lost ${PaperAnalysis.fmt(t.lost)} of ${PaperAnalysis.fmt(t.max)} marks" + (m?.let { ". Missed: ${it.point.lowercase()}" } ?: "."),
                    m?.fix?.takeIf { it.isNotBlank() } ?: "Re-learn the lesson, then do 5 past-paper questions on it with full working.")
            }
            val plan = buildList {
                (a.weak + a.needsWork).take(3).forEachIndexed { i, t -> add("Day ${i * 2 + 1}–${i * 2 + 2}: ${t.title}. Lesson again, then 5 board questions with every step written.") }
                if ((a.lost[LossKind.FINAL_ANSWER] ?: 0.0) > 0) add("Every day: check each final answer by substituting it back, and write it clearly with \"Ans\".")
                add("Day 7: sit another paper and compare your marks with this one.")
            }
            val technique = buildList {
                if ((a.lost[LossKind.NOT_ATTEMPTED] ?: 0.0) > 0) add("Attempt the full number of parts asked. A blank part is a certain zero; even the first two steps earn marks.")
                if ((a.lost[LossKind.MCQ_BLANK] ?: 0.0) > 0) add("Never leave an MCQ blank. There is no negative marking.")
                if ((a.lost[LossKind.STEPS] ?: 0.0) > 0) add("Board examiners give marks for each step. Write the formula, the substitution and each step on its own line.")
                if ((a.lost[LossKind.WORDING] ?: 0.0) > 0) add("Learn definitions and statements word for word from the book. Examiners look for the exact key terms.")
                add("Write the question and part number exactly as on the paper, e.g. \"Q2 (iii)\".")
            }
            val closing = if (a.percent >= 60) "You have the base. Sharpen these few areas and an A+ is within reach." else
                "This is where the climb starts. Work through the plan honestly and your next paper will show it."
            return ExaminerReport(headline, summary, strengths, weaknesses, plan, technique, closing, byAi = false)
        }
    }
}
