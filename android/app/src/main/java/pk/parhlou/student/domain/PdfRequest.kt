package pk.parhlou.student.domain

import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.RubricCriterion
import pk.parhlou.student.data.SolutionKind
import pk.parhlou.student.data.WrittenQuestion

/**
 * A PDF the student asked Ustaad for: a paper ("make me a half-book paper") or notes ("unit 2 notes"), for one exercise,
 * one unit, the first half of the book or the whole book, with or without answers.
 */
data class PdfAction(val type: String, val scope: String, val unit: Int, val exercise: String, val withAnswers: Boolean) {
    val isNotes get() = type == "notes_pdf"

    /** "Exercise 3.2 paper", "Unit 2 notes with answers". */
    val label: String get() = when (scope) {
        "exercise" -> "Exercise $exercise"
        "unit" -> "Unit $unit"
        "half" -> "Half-book"
        else -> "Full-book"
    } + (if (isNotes) " notes" else " paper") + (if (withAnswers && !isNotes) " with answers" else "")

    companion object {
        private val SCOPES = setOf("exercise", "unit", "half", "full")
        fun parse(o: org.json.JSONObject?): PdfAction? {
            if (o == null) return null
            val type = o.optString("type").takeIf { it == "paper_pdf" || it == "notes_pdf" } ?: return null
            val scope = o.optString("scope").takeIf { it in SCOPES } ?: return null
            val unit = o.optInt("unit", 0).coerceIn(0, 30)
            val exercise = o.optString("exercise").trim().takeIf { Regex("""\d{1,2}(\.\d{1,2})?""").matches(it) }.orEmpty()
            if (scope == "unit" && unit == 0 || scope == "exercise" && exercise.isEmpty()) return null
            return PdfAction(type, scope, unit, exercise, o.optBoolean("withAnswers", o.optBoolean("with_answers")))
        }
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("type", type).put("scope", scope).put("unit", unit)
        .put("exercise", exercise).put("withAnswers", withAnswers)
}

/** Builds the papers and notes behind [PdfAction]s from the published course. */
object PdfContent {
    /** The unit an exercise belongs to: "3.2" → 3. */
    fun unitOf(exercise: String): Int? = exercise.substringBefore('.').toIntOrNull()

    /**
     * Every question of one textbook exercise as a paper: one numbered question per exercise question (its parts together),
     * with the book's checked working as the marking scheme. Null when the exercise isn't in the course.
     */
    fun exercisePaper(chapters: List<Chapter>, exercise: String): Pair<PaperBlueprint, PaperContent>? {
        val prefix = "Ex $exercise Q"
        val sols = chapters.flatMap { c -> c.modules.flatMap { it.solutions } }
            .filter { it.kind == SolutionKind.EXERCISE && it.source.startsWith(prefix) }
        if (sols.isEmpty()) return null
        val byQ = sols.groupBy { it.source.removePrefix(prefix).takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }.toSortedMap()
        val id = "exercise-${exercise.replace('.', '-')}"
        val written = byQ.entries.mapIndexed { i, (_, parts) ->
            val single = parts.size == 1
            val text = parts.joinToString("\n") { s ->
                val part = Regex("""\(([ivx]+|[a-z])\)$""").find(s.source)?.value
                if (single || part == null) s.question else "$part  ${s.question}"
            }
            val answer = parts.flatMap { s ->
                val part = Regex("""\(([ivx]+|[a-z])\)$""").find(s.source)?.value
                (if (single || part == null) emptyList() else listOf("$part")) + s.steps + listOfNotNull(s.answer.takeIf { it.isNotBlank() }?.let { "Answer: $it" })
            }
            WrittenQuestion("$id-q${i + 1}", text, 4.0, answer, listOf(RubricCriterion("r1", "Correct method and answer", 4.0, emptyList(), "", "")), "", section = 1, number = i + 1)
        }
        val blueprint = PaperBlueprint(id, "Exercise $exercise", durationMinutes = maxOf(20, written.size * 10), totalMarks = written.size * 4.0,
            sections = listOf(BlueprintSection("MCQ", 0, 0, 1.0), BlueprintSection("Exercise questions", written.size, written.size, 4.0)), official = false)
        return blueprint to PaperContent(id, emptyList(), written)
    }

    /** Revision notes for the chosen units: each unit's exam kit (definitions, formulas, important questions, mistakes). */
    fun notes(chapters: List<Chapter>, units: Set<Int>): List<Pair<String, List<String>>> =
        chapters.filter { it.number in units }.sortedBy { it.number }.flatMap { c ->
            val k = c.kit
            val u = "Unit ${c.number}"
            if (k == null) listOf("$u · ${c.title}" to c.modules.map { it.title })
            else listOf(
                "$u · ${c.title}" to listOf("Definitions, formulas and what the exam asks from this unit."),
                "$u · Definitions" to k.definitions.map { "${it.term}: ${it.text}" },
                "$u · Formulas" to k.formulas.map { "${it.formula}   (${it.use})" },
                "$u · Short questions" to k.shortTheory.map { "Q. ${it.question}\nA. ${it.answer}" },
                "$u · Important for the exam" to k.important.map { "${it.ref}: ${it.why}" },
                "$u · Full marks" to k.fullMarks,
                "$u · Common mistakes" to k.mistakes,
            )
        }

    /** Units a scope covers: the first half of the book in unit order, or all of it. */
    fun units(chapters: List<Chapter>, action: PdfAction): Set<Int> {
        val all = chapters.map { it.number }.sorted()
        return when (action.scope) {
            "exercise" -> setOfNotNull(unitOf(action.exercise))
            "unit" -> setOf(action.unit)
            "half" -> all.take((all.size + 1) / 2).toSet()
            else -> all.toSet()
        }
    }
}
