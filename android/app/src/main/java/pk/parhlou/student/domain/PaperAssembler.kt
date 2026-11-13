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
        pattern: PaperBlueprint,                     // sections: [MCQ, short, long]
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

        val id = "mock-${scope.name.lowercase()}"
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

    /** Pattern used for mock papers: the reported board layout (unconfirmed until the official scheme is supplied). */
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
