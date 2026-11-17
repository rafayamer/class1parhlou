package pk.parhlou.student.domain

import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent

enum class MockScope(val label: String, val share: Double) {
    QUARTER("Quarter-book mock", 0.25),
    HALF("Half-book mock", 0.5),
    FULL("Full-book mock", 1.0),
}

sealed interface MockPaper {
    val scope: MockScope
    val chapters: List<Chapter>
    data class Ready(override val scope: MockScope, override val chapters: List<Chapter>, val blueprint: PaperBlueprint, val paper: PaperContent) : MockPaper
    /**
     * Not enough published questions: never assembled from partial content. [missing] lists units with no questions;
     * when it is empty, the units have questions but not enough of the right marks for the pattern.
     */
    data class NeedsQuestions(override val scope: MockScope, override val chapters: List<Chapter>, val missing: List<Chapter>) : MockPaper
}

/**
 * Builds quarter-, half- and full-book mock papers from the chapters' published questions, following a pattern
 * blueprint (PRD §13). "Quarter" means the first quarter of the units in book order, not of the pages.
 * Questions are taken in turn from each chapter so every unit in scope is represented.
 */
object PaperAssembler {
    fun assemble(
        scope: MockScope,
        chapters: List<Chapter>,
        pool: Map<String, PaperContent>,            // chapter id → its published questions
        pattern: PaperBlueprint,                     // sections: [MCQ, short, long…]
        /** Keeps each subject's mock ids apart ("" for Mathematics, whose ids predate other subjects). */
        idPrefix: String = "",
    ): MockPaper {
        val inScope = chapters.sortedBy { it.number }.let { all ->
            all.take(maxOf(1, Math.ceil(all.size * scope.share).toInt()))
        }
        val missing = inScope.filter { c -> pool[c.id].let { it == null || (it.mcqs.isEmpty() && it.written.isEmpty()) } }
        if (missing.isNotEmpty()) return MockPaper.NeedsQuestions(scope, inScope, missing)

        val sources = inScope.map { pool.getValue(it.id) }
        val mcqs = roundRobin(sources.map { it.mcqs }, pattern.sections.getOrNull(0)?.count ?: 0)
        // Only questions written for the same marks fit a section; a question is never re-weighted against its rubric.
        val writtenSections = pattern.sections.drop(1).mapIndexed { i, section ->
            roundRobin(sources.map { p -> p.written.filter { it.section == i + 1 && it.marks == section.marksEach } }, section.count)
        }
        val shortages = listOf(mcqs.size to (pattern.sections.getOrNull(0)?.count ?: 0)) +
            writtenSections.mapIndexed { i, qs -> qs.size to pattern.sections[i + 1].count }
        if (shortages.any { (have, need) -> have < need }) return MockPaper.NeedsQuestions(scope, inScope, emptyList())

        val id = "${idPrefix}mock-${scope.name.lowercase()}"
        val written = writtenSections.flatMapIndexed { i, qs ->
            qs.mapIndexed { n, q -> q.copy(section = i + 1, number = n + 1) }
        }
        val blueprint = pattern.copy(
            id = id,
            title = "${scope.label} (Units ${inScope.first().number}–${inScope.last().number})",
            chapterId = null,
        )
        return MockPaper.Ready(scope, inScope, blueprint, PaperContent(id, mcqs, written))
    }

    /** Takes items from each list in turn until [count] are chosen or all lists are used up. */
    private fun <T> roundRobin(lists: List<List<T>>, count: Int): List<T> {
        val out = mutableListOf<T>()
        var round = 0
        while (out.size < count && lists.any { round < it.size }) {
            lists.forEach { if (round < it.size && out.size < count) out += it[round] }
            round++
        }
        return out
    }

    /** The board pattern of a subject's paper (Mathematics 75 marks; Physics and Chemistry 60 marks: 12 MCQs, 15 of 24 shorts,
     * 2 of 3 long questions of 9 marks). */
    fun patternFor(subjectId: String): PaperBlueprint = when {
        subjectId.endsWith("-phy") || subjectId.endsWith("-chem") || subjectId.endsWith("-bio") -> physicsPattern()
        subjectId.endsWith("-cs") -> computerPattern()
        else -> boardPattern()
    }

    /** Computer Science & Entrepreneurship 9 (PECTAA): 10 MCQs; three short-answer questions, 4 of 6 parts each (2 marks);
     * 2 of 3 long questions of 8 marks. 50 marks, 2 hours. */
    fun computerPattern(duration: Int = 120) = PaperBlueprint(
        id = "pattern", title = "pattern", durationMinutes = duration, totalMarks = 50.0,
        sections = listOf(
            BlueprintSection("MCQ", count = 10, attempt = 10, marksEach = 1.0, minutes = 15),
            BlueprintSection("Short questions", count = 18, attempt = 12, marksEach = 2.0),
            BlueprintSection("Long questions", count = 3, attempt = 2, marksEach = 8.0),
        ),
        official = false,
    )

    /** Physics 9 (PECTAA): 12 MCQs; three short-answer questions, 5 of 8 parts each (2 marks); 2 of 3 long questions,
     * each (a) 4 marks + (b) 5 marks = 9. 60 marks, 2 hours. */
    fun physicsPattern(duration: Int = 120) = PaperBlueprint(
        id = "pattern", title = "pattern", durationMinutes = duration, totalMarks = 60.0,
        sections = listOf(
            BlueprintSection("MCQ", count = 12, attempt = 12, marksEach = 1.0, minutes = 15),
            BlueprintSection("Short questions", count = 24, attempt = 15, marksEach = 2.0),
            BlueprintSection("Long questions", count = 3, attempt = 2, marksEach = 9.0),
        ),
        official = false,
    )

    /** Totals of the official 2027 paper (15 MCQs; 27 short, 18 attempted; 5 long, 3 attempted). The full board papers with the
     * official per-unit layout are published with the course; these quarter and half mocks are practice. */
    fun boardPattern(duration: Int = 150) = PaperBlueprint(
        id = "pattern", title = "pattern", durationMinutes = duration, totalMarks = 75.0,
        sections = listOf(
            BlueprintSection("MCQ", count = 15, attempt = 15, marksEach = 1.0, minutes = 20),
            BlueprintSection("Short questions", count = 27, attempt = 18, marksEach = 2.0),
            BlueprintSection("Long questions", count = 5, attempt = 3, marksEach = 8.0),
        ),
        official = false,
    )
}
