package pk.parhlou.student.domain

import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion

/**
 * Real Punjab board papers, put back together from the question bank: every question records where it was in each board paper
 * ("Lahore 2023 (Group I)" → "Q2(iv)"), so a paper is its questions in their printed order. Only questions still in the new book
 * and the 2027 syllabus are in the bank, so a rebuilt paper is the part of that board paper a student should practise now.
 */
object PastPapers {
    data class Real(val tag: String, val year: Int, val blueprint: PaperBlueprint, val paper: PaperContent)

    /** The smallest rebuilt paper worth offering (fewer questions than this is not a paper). */
    const val MIN_QUESTIONS = 8

    /** Sort key for a printed place: "Q2(iv)" → (2, 4); "Q1(12)" → (1, 12); "Q5(b)" → (5, 2). */
    fun order(label: String): Pair<Int, Int> {
        val q = Regex("""Q\s*(\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 99
        val part = Regex("""\(([^)]+)\)""").find(label)?.groupValues?.get(1)?.lowercase().orEmpty()
        val n = part.toIntOrNull() ?: roman(part) ?: part.firstOrNull()?.let { if (it in 'a'..'h') it - 'a' + 1 else null } ?: 0
        return q to n
    }

    private fun roman(s: String): Int? {
        if (s.isEmpty() || s.any { it !in "ivxl" }) return null
        val v = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50)
        var t = 0
        s.forEachIndexed { i, c -> val x = v.getValue(c); t += if (i + 1 < s.length && v.getValue(s[i + 1]) > x) -x else x }
        return t
    }

    fun yearOf(tag: String): Int = Regex("""20\d\d""").find(tag)?.value?.toIntOrNull() ?: 0

    /** Every board paper that has at least [MIN_QUESTIONS] questions in the bank, newest first. */
    fun rebuild(subjectName: String, idPrefix: String, mcqs: List<McqQuestion>, written: List<WrittenQuestion>): List<Real> {
        val tags = (mcqs.flatMap { it.pastRefs.keys } + written.flatMap { it.pastRefs.keys }).toSet()
        return tags.mapNotNull { tag ->
            val m = mcqs.filter { tag in it.pastRefs }.distinctBy { it.id }.sortedBy { order(it.pastRefs.getValue(tag)).let { (a, b) -> a * 1000 + b } }
            val w = written.filter { tag in it.pastRefs }.distinctBy { it.id }.sortedBy { order(it.pastRefs.getValue(tag)).let { (a, b) -> a * 1000 + b } }
            // Section 0 of every paper is its MCQs, so a rebuilt paper needs some.
            if (m.isEmpty() || w.isEmpty() || m.size + w.size < MIN_QUESTIONS) return@mapNotNull null
            // Written questions grouped by marks (2-mark shorts, then longer ones), each group one section.
            val groups = w.groupBy { it.marks }.toSortedMap()
            val sections = buildList {
                add(BlueprintSection("MCQ", m.size, m.size, 1.0, maxOf(5, m.size)))
                groups.forEach { (marks, qs) ->
                    add(BlueprintSection(if (marks <= 2.0) "Short questions" else "Long questions (${fmtMarks(marks)} marks each)", qs.size, qs.size, marks))
                }
            }
            val total = sections.sumOf { it.attempt * it.marksEach }
            val id = "${idPrefix}past-${tag.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
            val bp = PaperBlueprint(id = id, title = "$tag · $subjectName past paper", durationMinutes = (total * 2).toInt().coerceIn(20, 150),
                totalMarks = total, sections = sections, official = false, chapterId = null)
            val content = PaperContent(id, m, groups.entries.flatMapIndexed { gi, (_, qs) ->
                qs.mapIndexed { i, q -> q.copy(section = gi + 1, number = i + 1) }
            })
            Real(tag, yearOf(tag), bp, content)
        }.sortedWith(compareByDescending<Real> { it.year }.thenBy { it.tag })
    }

    private fun fmtMarks(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString()
}
