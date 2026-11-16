package pk.parhlou.student.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.RubricCriterion
import pk.parhlou.student.data.WrittenQuestion
import java.io.File

/** Papers on paper: board numbering, printing, reading a script written in any order, and the AI-picked paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PrintedPaperTest {
    private val board = PaperBlueprint(
        id = "board-2027-a", title = "Board paper A", durationMinutes = 150, totalMarks = 75.0, official = true,
        sections = listOf(
            BlueprintSection("MCQ", 15, 15, 1.0, 20),
            BlueprintSection("Q2 Short", 9, 6, 2.0), BlueprintSection("Q3 Short", 9, 6, 2.0), BlueprintSection("Q4 Short", 9, 6, 2.0),
            BlueprintSection("Part II long", 3, 2, 8.0), BlueprintSection("Part III long", 2, 1, 8.0),
        ),
    )

    private fun w(id: String, section: Int, number: Int, marks: Double = 2.0, note: String? = null, past: List<String> = emptyList()) =
        WrittenQuestion(id, "Question $id", marks, listOf("model"), listOf(RubricCriterion("c1", "step", marks, emptyList(), "", "")), "f",
            noteId = note, section = section, number = number, pastPapers = past)
    private fun m(id: String, note: String, past: Int = 0) =
        McqQuestion(id, "Stem $id", listOf("a", "b", "c", "d"), 1, listOf("", "", "", ""), ReviewStatus.APPROVED, noteId = note,
            pastPapers = List(past) { "Lahore 20${10 + it}" })

    @Test fun questionsAreNumberedLikeTheBoardPaper() {
        val n = BoardNumbering(board)
        assertEquals("Q2 (iii)", n.label(w("a", 1, 3)))
        assertEquals("Q4 (ix)", n.label(w("b", 3, 9)))
        assertEquals("Q5", n.label(w("c", 4, 1, 8.0)))
        assertEquals("Q7", n.label(w("d", 4, 3, 8.0)))
        assertEquals("Q9", n.label(w("e", 5, 2, 8.0)))
        // A unit test (MCQs, short questions, long questions) follows the same rule.
        val unit = board.copy(sections = listOf(BlueprintSection("MCQ", 12, 12, 1.0), BlueprintSection("Short", 9, 6, 2.0), BlueprintSection("Long", 3, 2, 8.0)))
        assertEquals("Q2 (i)", BoardNumbering(unit).label(w("f", 1, 1)))
        assertEquals("Q4", BoardNumbering(unit).label(w("g", 2, 2, 8.0)))
    }

    private val paper = PaperContent("board-2027-a", listOf(m("m1", "n1"), m("m2", "n1"), m("m3", "n2")),
        listOf(w("s1", 1, 1), w("s3", 1, 3), w("L5", 4, 1, 8.0)))

    private fun seg(label: String, q: String?, text: String, continues: Boolean = false, crossed: Boolean = false) =
        ScriptSegment(label, q, continues, crossed, false, "CLEAR", text)

    @Test fun aScriptWrittenInAnyOrderIsPutBackTogether() {
        val pages = listOf(
            ScriptPage(2, "ANSWERS", "CLEAR", listOf(seg("", "L5", "x = 15.56 Ans", continues = true), seg("2-i)", "s1", "rational"),
                seg("", null, "some working with no number")), emptyMap(), ""),
            ScriptPage(1, "ANSWERS", "CLEAR", listOf(seg("Q2 (iii)", "s3", "2 × 10⁶"), seg("2 (i)", "s1", "irrational", crossed = true),
                seg("5 b)", "L5", "characteristic = 1")), emptyMap(), ""),
            ScriptPage(3, "BUBBLE_SHEET", "CLEAR", emptyList(), mapOf(1 to "B", 2 to "MULTIPLE", 3 to "BLANK"), ""),
        )
        val r = ScriptAssembler.assemble(pages, paper, BoardNumbering(board))
        assertEquals("Q2 (iii) 2 × 10⁶", r.answers["s3"])
        // The crossed-out first attempt is not marked; the answer written later is.
        assertEquals("2-i) rational", r.answers["s1"])
        // Q5 (b) began on page 1 and continued at the top of page 2.
        assertEquals("5 b) characteristic = 1\nx = 15.56 Ans", r.answers["L5"])
        assertEquals(listOf(1, 2), r.pagesOf["L5"])
        assertEquals(mapOf("m1" to 1), r.mcq)
        assertTrue(r.remarks.any { "MCQ 2" in it && "zero" in it })
        assertEquals(1, r.loose.size)
        assertTrue(r.remarks.any { "no clear question number" in it })
        // The student says where the loose piece belongs.
        val fixed = ScriptAssembler.assemble(pages, paper, BoardNumbering(board), mapOf("2-2" to "s3"))
        assertEquals("Q2 (iii) 2 × 10⁶\nsome working with no number", fixed.answers["s3"])
        assertTrue(fixed.loose.isEmpty())
        val ignored = ScriptAssembler.assemble(pages, paper, BoardNumbering(board), mapOf("2-2" to ""))
        assertTrue(ignored.loose.isEmpty())
        assertEquals("Q2 (iii) 2 × 10⁶", ignored.answers["s3"])
    }

    @Test fun unreadableAndForeignPagesAreReportedNotGuessed() {
        val r = ScriptAssembler.assemble(listOf(
            ScriptPage(1, "ANSWERS", "UNREADABLE", emptyList(), emptyMap(), ""),
            ScriptPage(2, "NOT_A_SCRIPT", "CLEAR", emptyList(), emptyMap(), ""),
        ), paper, BoardNumbering(board))
        assertTrue(r.answers.isEmpty())
        assertTrue(r.remarks.any { "Page 1 couldn't be read" in it })
        assertTrue(r.remarks.any { "Page 2 doesn't look like an answer page" in it })
    }

    @Test fun aReadPageFromTheServerIsParsed() {
        val page = ScriptPage.parse(JSONObject("""{"page":2,"kind":"ANSWERS","legibility":"CLEAR","note":"",
            "segments":[{"label":"5 a)","questionId":"L5","continues":false,"crossedOut":false,"roughWork":false,"legible":"CLEAR","text":"9x = 4"},
                        {"label":"","questionId":null,"continues":true,"crossedOut":false,"roughWork":false,"legible":"PARTLY","text":"x = 4/9"}],
            "mcq":[{"number":4,"choice":"C"}]}"""))!!
        assertEquals("L5", page.segments[0].questionId)
        assertNull(page.segments[1].questionId)
        assertEquals(mapOf(4 to "C"), page.mcq)
        assertNull(ScriptPage.parse(JSONObject("""{"nonsense":true}""")))
    }

    // ── Make my paper ──
    private val template = PaperContent("board-2027-a", listOf(m("t1", "u1"), m("t2", "u2")),
        listOf(w("ts1", 1, 1, note = "u1"), w("ts2", 1, 2, note = "u1"), w("BP-A-Q5", 4, 1, 8.0, "u1")))
    private val bank = PaperPicker.Bank(
        mcqs = listOf(m("t1", "u1"), m("t2", "u2"), m("a1", "u1", past = 6), m("a2", "u1"), m("b1", "u2", past = 2), m("b2", "u2")),
        written = listOf(w("ts1", 1, 1, note = "u1"), w("ts2", 1, 2, note = "u1"), w("x1", 0, 0, note = "u1", past = listOf("Lahore 2023")),
            w("x2", 0, 0, note = "u1"), w("x3", 0, 0, note = "u2"), w("BP-A-Q5", 4, 1, 8.0, "u1"), w("BP-B-Q5", 4, 1, 8.0, "u1"),
            w("BP-B-Q6", 4, 2, 8.0, "u2")),
        unitOfNote = { mapOf("u1" to 1, "u2" to 2)[it] }, topicOfNote = { it.orEmpty() },
    )

    @Test fun slotsFollowTheBoardPatternAndShortlistTheBestFirst() {
        val slots = PaperPicker.slots(board, template, bank, seen = setOf("x2"), seed = 1)
        assertEquals(listOf("MCQ · Unit 1", "MCQ · Unit 2", "S1 · Unit 1", "L4-1 · Unit 1"), slots.map { it.slot })
        assertEquals(listOf(1, 1, 2, 1), slots.map { it.count })
        // Most repeated board question first; nothing from another unit; Q5 comes only from the other papers' Q5.
        assertEquals("a1", slots[0].candidates.first().id)
        assertTrue(slots[2].candidates.none { it.id == "x3" })
        assertEquals("x1", slots[2].candidates.first().id)
        assertEquals("x2", slots[2].candidates.last().id)
        assertEquals(setOf("BP-A-Q5", "BP-B-Q5"), slots[3].candidates.map { it.id }.toSet())
    }

    @Test fun aPickedPaperKeepsTheTemplatesPlacesAndIsSaved() {
        val slots = PaperPicker.slots(board, template, bank, emptySet(), seed = 2)
        val pick = PaperPicker.localPick(slots)
        val all = pick.chosen.values.flatten()
        assertEquals(all.size, all.toSet().size)
        val made = PaperPicker.build("my-paper-1", "My paper 1", board, template, slots, pick, bank, byAi = false)!!
        assertEquals(listOf(1 to 1, 1 to 2, 4 to 1), made.paper.written.map { it.section to it.number })
        assertEquals("my-paper-1", made.blueprint.id)
        assertEquals(board.sections, made.blueprint.sections)
        val back = PaperPicker.fromJson(PaperPicker.toJson(made), board, bank)!!
        assertEquals(made.paper, back.paper)
        assertEquals("My paper 1", back.blueprint.title)
    }

    @Test fun anAiPickIsUsedOnlyIfItFitsEverySlot() {
        val slots = PaperPicker.slots(board, template, bank, emptySet(), seed = 3)
        fun reply(vararg slotPicks: Pair<String, List<String>>) = JSONObject().put("summary", "Chosen for you").put("slots",
            org.json.JSONArray(slotPicks.map { (s, ids) -> JSONObject().put("slot", s).put("picks", org.json.JSONArray(ids.map { JSONObject().put("id", it).put("why", "Came in 6 papers") })) }))
        val good = PaperPick.parse(reply("MCQ · Unit 1" to listOf("a1"), "MCQ · Unit 2" to listOf("b1"), "S1 · Unit 1" to listOf("x1", "x2"), "L4-1 · Unit 1" to listOf("BP-B-Q5")), slots)!!
        assertEquals("Came in 6 papers", good.why["a1"])
        assertNull(PaperPick.parse(reply("MCQ · Unit 1" to listOf("a1"), "MCQ · Unit 2" to listOf("a1"), "S1 · Unit 1" to listOf("x1", "x2"), "L4-1 · Unit 1" to listOf("BP-B-Q5")), slots))
        assertNull(PaperPick.parse(reply("MCQ · Unit 1" to listOf("zz"), "MCQ · Unit 2" to listOf("b1"), "S1 · Unit 1" to listOf("x1", "x2"), "L4-1 · Unit 1" to listOf("BP-B-Q5")), slots))
        assertNull(PaperPick.parse(reply("MCQ · Unit 1" to listOf("a1"), "MCQ · Unit 2" to listOf("b1"), "S1 · Unit 1" to listOf("x1"), "L4-1 · Unit 1" to listOf("BP-B-Q5")), slots))
    }

    // ── The printed paper ──
    @Test fun thePrintedPaperHasTheBoardLayout() {
        val path = System.getProperty("parhlou.courseBundle")
        val (bp, content) = if (path != null && File(path).exists()) {
            val repo = ContentBundle.repository(JSONObject(File(path).readText()), 1, FakeRepository())
            val b = repo.blueprints("pb-9-math").first { it.id == "board-2027-a" }
            b to repo.paper(b.id)!!
        } else {
            val repo = FakeRepository()
            val b = repo.blueprints(FakeRepository.MATHS_9).first { repo.paper(it.id) != null }
            b to repo.paper(b.id)!!
        }
        val out = File("build/paper-preview").apply { deleteRecursively(); mkdirs() }
        val pages = mutableListOf<Bitmap>()
        PaperPdf.draw(object : PaperPdf.PageSink {
            override fun start(number: Int, width: Int, height: Int): Canvas {
                val b = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                pages += b
                return Canvas(b).apply { scale(2f, 2f) }
            }
            override fun finish() { File(out, "page-${pages.size}.png").outputStream().use { pages.last().compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }, bp, content, withScheme = true)
        assertTrue("objective, bubble sheet, subjective and scheme pages", pages.size >= 4)
        assertTrue(PaperPdf.code(bp, content).matches(Regex("PRL-[0-9A-Z]{6}")))
        assertEquals("x² + 10⁻³ + a^(b/c)", PaperPdf.math("x^2 + 10^-3 + a^(b/c)"))
        assertEquals("Express in scientific notation: 2000000", PaperPdf.clean("Express in scientific notation: (i) 2000000"))
        assertEquals("Convert 36° into radians.", PaperPdf.clean("Convert 36° into radians (Ex 6.1 Q4(i))."))
        assertEquals("Sketch y = 3x − 5.", PaperPdf.clean("Sketch y = 3x − 5. Draw it on paper and photograph your work, or type your table of values / measurements."))
    }
}
