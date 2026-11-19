package pk.parhlou.student.domain

import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import kotlin.random.Random

/**
 * Practice tests a student makes in two taps: which chapters, and how long. Questions come from the whole bank (past-paper
 * questions included), the ones repeated most in board papers first, with some variety from the test's seed. A test is saved
 * as its recipe (subject, chapters, length, seed) and rebuilt the same way when it is opened again.
 */
object PracticeTests {
    enum class Length(val label: String, val minutes: Int, val mcqs: Int, val shorts: Int, val shortsAttempt: Int, val longs: Int, val longsAttempt: Int) {
        QUICK("Quick · 15 min", 15, 10, 3, 3, 0, 0),
        HALF("Half paper · 30 min", 30, 15, 6, 5, 1, 1),
        LONG("Long · 60 min", 60, 20, 10, 8, 2, 2),
    }

    data class Recipe(val id: String, val subjectId: String, val subjectName: String, val chapters: List<Int>, val length: Length,
                      val seed: Long, val createdAt: Long) {
        val title: String get() = "${subjectName} practice · ${if (chapters.size == 1) "Ch ${chapters[0]}" else "Ch " + ranges(chapters)} · ${length.label.substringBefore(" ·")}"
    }

    /** Most recent tests kept on the phone. */
    const val KEEP = 10

    fun build(r: Recipe, bank: PaperPicker.Bank): Pair<PaperBlueprint, PaperContent>? {
        val rnd = Random(r.seed)
        val inScope = { note: String? -> bank.unitOfNote(note) in r.chapters }
        // Best first: repeated in board papers, then a little shuffle so two tests on the same chapters differ.
        fun <T> rank(items: List<T>, repeats: (T) -> Int) = items.map { it to repeats(it) * 2.0 + rnd.nextDouble() * 3 }.sortedByDescending { it.second }.map { it.first }
        val mcqs = rank(bank.mcqs.filter { inScope(it.noteId ?: it.moduleId) && it.options.size >= 2 }) { it.pastPapers.size }
            .distinctBy { it.stem.lowercase().trim() }.take(r.length.mcqs)
        val shorts = rank(bank.written.filter { it.marks == 2.0 && inScope(it.noteId) }) { it.pastPapers.size }
            .distinctBy { it.text.lowercase().trim() }.take(r.length.shorts)
        val longPool = rank(bank.written.filter { it.marks > 2.0 && inScope(it.noteId) }) { it.pastPapers.size }
        // Long questions in one section share their marks: take the most common long-question marks in scope.
        val longMarks = longPool.groupingBy { it.marks }.eachCount().maxByOrNull { it.value }?.key
        val longs = longPool.filter { it.marks == longMarks }.distinctBy { it.text.lowercase().trim() }.take(r.length.longs)
        if (mcqs.size < 5 || (r.length.shorts > 0 && shorts.size < 2)) return null
        val sections = buildList {
            add(BlueprintSection("MCQ", mcqs.size, mcqs.size, 1.0, null))
            if (shorts.isNotEmpty()) add(BlueprintSection("Short questions", shorts.size, minOf(r.length.shortsAttempt, shorts.size), 2.0))
            if (longs.isNotEmpty() && longMarks != null) add(BlueprintSection("Long questions", longs.size, minOf(r.length.longsAttempt, longs.size), longMarks))
        }
        val bp = PaperBlueprint(r.id, r.title, r.length.minutes, sections.sumOf { it.attempt * it.marksEach }, sections, official = false, chapterId = null)
        val written = shorts.mapIndexed { i, q -> q.copy(section = 1, number = i + 1) } +
            longs.mapIndexed { i, q -> q.copy(section = if (shorts.isEmpty()) 1 else 2, number = i + 1) }
        return bp to PaperContent(r.id, mcqs, written)
    }

    fun toJson(r: Recipe): JSONObject = JSONObject().put("id", r.id).put("subjectId", r.subjectId).put("subjectName", r.subjectName)
        .put("chapters", JSONArray(r.chapters)).put("length", r.length.name).put("seed", r.seed).put("createdAt", r.createdAt)

    fun fromJson(o: JSONObject): Recipe? = runCatching {
        Recipe(o.getString("id"), o.getString("subjectId"), o.getString("subjectName"),
            o.getJSONArray("chapters").let { a -> List(a.length()) { a.getInt(it) } }, Length.valueOf(o.getString("length")),
            o.getLong("seed"), o.getLong("createdAt"))
    }.getOrNull()

    fun parseAll(text: String): List<Recipe> = runCatching { JSONArray(text).let { a -> (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) } } }
        .getOrDefault(emptyList())

    /** Adds a recipe and keeps only the newest [KEEP]. */
    fun add(text: String, r: Recipe): String = JSONArray((listOf(r) + parseAll(text)).take(KEEP).map(::toJson)).toString()

    private fun ranges(n: List<Int>): String {
        val s = n.sorted(); val out = mutableListOf<String>(); var i = 0
        while (i < s.size) { var j = i; while (j + 1 < s.size && s[j + 1] == s[j] + 1) j++; out += if (j > i) "${s[i]}–${s[j]}" else "${s[i]}"; i = j + 1 }
        return out.joinToString(", ")
    }
}
