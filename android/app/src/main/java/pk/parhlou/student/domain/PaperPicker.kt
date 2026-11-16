package pk.parhlou.student.domain

import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion
import kotlin.random.Random

/** One question the AI may choose for a slot, with what it needs to judge it. */
data class PickCandidate(val id: String, val topic: String, val seen: Boolean, val score: Double)

/** A place in the paper to fill: "MCQ · Unit 3" needs 1 question, "Q2 · Unit 1" needs 2, "Q5 (a+b)" needs 1. */
data class PickSlot(val slot: String, val count: Int, val candidates: List<PickCandidate>)

data class PaperPick(val chosen: Map<String, List<String>>, val why: Map<String, String>, val summary: String) {
    companion object {
        /** Reads the server's pick; it is used only if every slot gets exactly its count of its own candidates, none twice. */
        fun parse(r: JSONObject, slots: List<PickSlot>): PaperPick? = runCatching {
            val arr = r.getJSONArray("slots")
            val chosen = mutableMapOf<String, List<String>>()
            val why = mutableMapOf<String, String>()
            val used = mutableSetOf<String>()
            for (s in slots) {
                val got = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.getString("slot") == s.slot } ?: return null
                val picks = got.getJSONArray("picks").let { a -> List(a.length()) { a.getJSONObject(it) } }
                val allowed = s.candidates.map { it.id }.toSet()
                if (picks.size != s.count || picks.any { it.getString("id") !in allowed || !used.add(it.getString("id")) }) return null
                chosen[s.slot] = picks.map { it.getString("id") }
                picks.forEach { p -> p.optString("why").takeIf { it.isNotBlank() }?.let { why[p.getString("id")] = it.take(120) } }
            }
            PaperPick(chosen, why, r.optString("summary").take(400))
        }.getOrNull()
    }
}

/** A paper the AI (or, offline, the app) put together for this student: the 2027 board pattern, filled from the question bank. */
data class MyPaper(val blueprint: PaperBlueprint, val paper: PaperContent, val why: Map<String, String>, val summary: String, val byAi: Boolean)

/**
 * Builds a full board-pattern paper for one student. The template (an official board paper) fixes the slots: how many MCQs and
 * short questions come from each unit, and which units each long question tests. Every slot gets a shortlist from the whole
 * question bank, best first: the questions that came most often in board papers, then ones the student hasn't seen.
 * It follows the exam, not the student: a paper never leans towards a student's weak topics.
 * The AI then chooses from the shortlist; without the AI the top of each shortlist is used.
 */
object PaperPicker {
    const val SHORTLIST = 12

    class Bank(
        val mcqs: List<McqQuestion>,
        val written: List<WrittenQuestion>,
        /** Unit number of a note (from its module's chapter). */
        val unitOfNote: (String?) -> Int?,
        val topicOfNote: (String?) -> String,
    )

    fun slots(template: PaperBlueprint, templatePaper: PaperContent, bank: Bank, seen: Set<String>, seed: Long): List<PickSlot> {
        val rnd = Random(seed)
        val jitter = mutableMapOf<String, Double>()
        fun score(id: String, note: String?, pastPapers: Int) =
            minOf(pastPapers, 8) * 1.5 - (if (id in seen) 3.0 else 0.0) + jitter.getOrPut(id) { rnd.nextDouble() * 1.2 }
        fun cand(id: String, note: String?, pastPapers: Int) = PickCandidate(id, bank.topicOfNote(note), id in seen, score(id, note, pastPapers))
        val out = mutableListOf<PickSlot>()
        templatePaper.mcqs.groupBy { bank.unitOfNote(it.noteId ?: it.moduleId) }.forEach { (unit, qs) ->
            val pool = bank.mcqs.filter { unit != null && bank.unitOfNote(it.noteId ?: it.moduleId) == unit }.distinctBy { it.id }
                .map { cand(it.id, it.noteId ?: it.moduleId, it.pastPapers.size) }
            out += PickSlot("MCQ · Unit ${unit ?: "?"}", qs.size, pool.sortedByDescending { it.score }.take(SHORTLIST))
        }
        templatePaper.written.groupBy { it.section }.toSortedMap().forEach { (section, qs) ->
            val marks = qs.first().marks
            if (marks <= 3.0) qs.groupBy { bank.unitOfNote(it.noteId) }.forEach { (unit, inUnit) ->
                val pool = bank.written.filter { it.marks == marks && unit != null && bank.unitOfNote(it.noteId) == unit }.distinctBy { it.id }
                    .map { cand(it.id, it.noteId, it.pastPapers.size) }
                out += PickSlot("S$section · Unit ${unit ?: "?"}", inUnit.size, pool.sortedByDescending { it.score }.take(SHORTLIST))
            } else qs.sortedBy { it.number }.forEach { q ->
                val unit = bank.unitOfNote(q.noteId)
                // A long question tests a fixed pair of units ("Q5: (a) Unit 1, (b) Unit 2"): the same question of every board
                // and model paper fits; unit-test long questions of the first unit fit when there are none.
                val sameQ = Regex("""-Q\d+$""").find(q.id)?.value
                val board = bank.written.filter { sameQ != null && it.marks == marks && it.id.endsWith(sameQ) && Regex("""^(BP-[A-Z]|MP-2027)-Q\d+$""").matches(it.id) }
                val pool = board.ifEmpty { bank.written.filter { it.marks == marks && bank.unitOfNote(it.noteId) == unit } }.distinctBy { it.id }
                    .map { cand(it.id, it.noteId, it.pastPapers.size) }
                out += PickSlot("L$section-${q.number} · Unit ${unit ?: "?"}", 1, pool.sortedByDescending { it.score }.take(SHORTLIST))
            }
        }
        return out
    }

    /** The app's own choice: the top of each shortlist, never the same question twice. */
    fun localPick(slots: List<PickSlot>): PaperPick {
        val used = mutableSetOf<String>()
        val chosen = slots.associate { s -> s.slot to s.candidates.filter { used.add(it.id) }.take(s.count).map { it.id } }
        return PaperPick(chosen, emptyMap(), "")
    }

    /** Puts the chosen questions into the template's places. Null if a slot could not be filled. */
    fun build(id: String, title: String, template: PaperBlueprint, templatePaper: PaperContent, slots: List<PickSlot>, pick: PaperPick,
              bank: Bank, byAi: Boolean): MyPaper? {
        if (slots.any { (pick.chosen[it.slot]?.size ?: 0) != it.count }) return null
        val mcqById = bank.mcqs.associateBy { it.id }
        val writtenById = bank.written.associateBy { it.id }
        val queue = pick.chosen.mapValues { it.value.toMutableList() }
        val mcqs = templatePaper.mcqs.map { t ->
            val slot = "MCQ · Unit ${bank.unitOfNote(t.noteId ?: t.moduleId) ?: "?"}"
            mcqById[queue[slot]?.removeFirstOrNull() ?: return null] ?: return null
        }
        val written = templatePaper.written.sortedWith(compareBy({ it.section }, { it.number })).map { t ->
            val unit = bank.unitOfNote(t.noteId) ?: "?"
            val slot = if (t.marks <= 3.0) "S${t.section} · Unit $unit" else "L${t.section}-${t.number} · Unit $unit"
            val q = writtenById[queue[slot]?.removeFirstOrNull() ?: return null] ?: return null
            q.copy(section = t.section, number = t.number)
        }
        val blueprint = template.copy(id = id, title = title, chapterId = null)
        return MyPaper(blueprint, PaperContent(id, mcqs, written), pick.why, pick.summary, byAi)
    }

    // ── Saving: a paper keeps its questions, so it can be printed, sat and marked later, and its 3 attempts count. ──
    fun toJson(p: MyPaper): String = JSONObject()
        .put("id", p.blueprint.id).put("title", p.blueprint.title).put("summary", p.summary).put("byAi", p.byAi)
        .put("mcqs", JSONArray(p.paper.mcqs.map { it.id }))
        .put("written", JSONArray(p.paper.written.map { JSONObject().put("id", it.id).put("section", it.section).put("number", it.number) }))
        .put("why", JSONObject(p.why as Map<*, *>)).toString()

    fun fromJson(text: String, template: PaperBlueprint, bank: Bank): MyPaper? = runCatching {
        val j = JSONObject(text)
        val id = j.getString("id")
        val mcqById = bank.mcqs.associateBy { it.id }
        val writtenById = bank.written.associateBy { it.id }
        val mcqs = j.getJSONArray("mcqs").let { a -> List(a.length()) { mcqById.getValue(a.getString(it)) } }
        val written = j.getJSONArray("written").let { a -> List(a.length()) { i ->
            val w = a.getJSONObject(i)
            writtenById.getValue(w.getString("id")).copy(section = w.getInt("section"), number = w.getInt("number"))
        } }
        val why = j.optJSONObject("why")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
        MyPaper(template.copy(id = id, title = j.getString("title"), chapterId = null), PaperContent(id, mcqs, written), why,
            j.optString("summary"), j.optBoolean("byAi"))
    }.getOrNull()
}
