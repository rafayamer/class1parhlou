package pk.parhlou.student.domain

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import java.io.File

/**
 * The printable question paper: laid out like a Punjab board paper (roll number boxes, Paper I objective with its own time,
 * a bubble sheet, the subjective part with "attempt any …" notes and question/part numbers), with Parhlou's branding.
 * The student writes the answers on paper, as in the real exam, and scans them back into the app to be marked.
 */
object PaperPdf {
    private const val W = 595f   // A4 in points
    private const val H = 842f
    private const val M = 42f
    private val LEAF = Color.rgb(59, 118, 20)
    private val LIME = Color.rgb(198, 236, 140)
    private val INK = Color.rgb(28, 33, 29)
    private val MUTED = Color.rgb(90, 101, 88)

    private val NUMBERS = listOf("", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN")

    /** A short code printed on every page, so a scanned script can be matched to its paper. */
    fun code(blueprint: PaperBlueprint, paper: PaperContent): String {
        val h = (blueprint.id + paper.mcqs.joinToString { it.id } + paper.written.joinToString { it.id }).hashCode()
        return "PRL-" + Integer.toString(h and 0x7fffffff, 36).uppercase().padStart(6, '0').takeLast(6)
    }

    /** Where pages are drawn: a PDF in the app; plain bitmaps in tests, so the layout can be looked at. */
    interface PageSink { fun start(number: Int, width: Int, height: Int): Canvas; fun finish() }

    fun write(file: File, blueprint: PaperBlueprint, paper: PaperContent, withScheme: Boolean = false, subject: String = "Mathematics"): File {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        draw(object : PageSink {
            override fun start(number: Int, width: Int, height: Int): Canvas =
                doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas
            override fun finish() { page?.let { doc.finishPage(it) }; page = null }
        }, blueprint, paper, withScheme, subject)
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Revision notes as a PDF: a title, then headed sections of short paragraphs (definitions, formulas, solved questions). */
    fun notes(file: File, title: String, sections: List<Pair<String, List<String>>>, subject: String = "Mathematics"): File {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        drawNotes(object : PageSink {
            override fun start(number: Int, width: Int, height: Int): Canvas =
                doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas
            override fun finish() { page?.let { doc.finishPage(it) }; page = null }
        }, title, sections, subject)
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun drawNotes(sink: PageSink, title: String, sections: List<Pair<String, List<String>>>, subject: String = "Mathematics") {
        val w = Writer(sink, title, "NOTES", subject)
        w.newPage()
        w.text("${subject.uppercase()} (CLASS 9) · ${title.uppercase()}", bold = true, size = 13f, center = true)
        w.space(6f)
        sections.filter { it.second.isNotEmpty() }.forEach { (heading, items) ->
            w.heading(heading)
            items.forEach { w.block(listOf(math(clean(it)) to Style.Q)) }
        }
        w.space(14f)
        w.text("— End of notes —", size = 10f, color = MUTED, center = true)
        w.finish()
    }

    fun draw(sink: PageSink, blueprint: PaperBlueprint, paper: PaperContent, withScheme: Boolean = false, subject: String = "Mathematics") {
        val code = code(blueprint, paper)
        val w = Writer(sink, blueprint.title, code, subject)
        val numbering = BoardNumbering(blueprint)
        val objective = blueprint.sections.firstOrNull()
        val mcqMarks = (objective?.attempt ?: paper.mcqs.size) * (objective?.marksEach ?: 1.0)
        val objMinutes = objective?.minutes ?: if (paper.mcqs.isEmpty()) 0 else maxOf(10, blueprint.durationMinutes * paper.mcqs.size / 75)

        // ── Paper I: objective ──
        if (paper.mcqs.isNotEmpty()) {
            w.newPage()
            w.title("PAPER I (OBJECTIVE TYPE)", "Time: $objMinutes minutes", "Marks: ${fmt(mcqMarks)}")
            w.note("Note: Four possible answers A, B, C and D are given for each question. Fill the circle of the answer you think is " +
                "correct on the bubble sheet with a blue or black pen. Cutting or filling two or more circles gives zero marks for " +
                "that question. Do not write anything else on the bubble sheet.")
            w.text("1.  Choose the correct answer.", bold = true, size = 11.5f)
            paper.mcqs.forEachIndexed { i, q ->
                val opts = q.options.mapIndexed { k, o -> "(${'A' + k}) ${math(o)}" }
                w.block(listOf("${i + 1}.  ${math(clean(q.stem))}" to Style.Q) + opts.chunked(2).map { "      " + it.joinToString("        ") to Style.Opt })
            }
            bubbleSheet(w, paper.mcqs.size)
        }

        // ── Paper I: subjective ──
        w.newPage()
        val subjMarks = blueprint.totalMarks - (if (paper.mcqs.isEmpty()) 0.0 else mcqMarks)
        val subjMinutes = blueprint.durationMinutes - (if (paper.mcqs.isEmpty()) 0 else objMinutes)
        w.title("PAPER I (SUBJECTIVE TYPE)", "Time: ${hours(subjMinutes)}", "Marks: ${fmt(subjMarks)}")
        w.note("Note: Write the same question number and part number on your answer sheet as given in this paper, e.g. \"Q2 (iii)\" or " +
            "\"Q5 (b)\". Draw a single line through anything you want cancelled. Answers may be written in any order.")
        val sections = blueprint.sections.drop(1)
        val written = paper.written.groupBy { it.section }.toSortedMap()
        var longNoteDone = mutableSetOf<Int>()
        var sectionHeading = 0
        written.forEach { (s, qs) ->
            val sec = sections.getOrNull(s - 1) ?: return@forEach
            val ordered = qs.sortedBy { it.number }
            if (!numbering.isLong(ordered.first())) {
                if (sectionHeading == 0) { w.heading("SECTION I"); sectionHeading = 1 }
                val n = numbering.number(ordered.first())
                w.block(listOf("$n.  Write short answers to any ${word(sec.attempt)} (${sec.attempt}) parts." to Style.Q,
                    "(${sec.attempt} × ${fmt(sec.marksEach)} = ${fmt(sec.attempt * sec.marksEach)})" to Style.Right))
                ordered.forEach { q -> w.block(listOf("(${BoardNumbering.roman(q.number)})  ${math(clean(q.text))}" to Style.Part)) }
            } else {
                if (sectionHeading < 2) { w.heading("SECTION II"); sectionHeading = 2 }
                if (longNoteDone.add(s)) {
                    val nums = ordered.map { numbering.number(it) }
                    w.text("Note: Attempt any ${word(sec.attempt)} (${sec.attempt}) question${if (sec.attempt == 1) "" else "s"} from Q${nums.first()}–Q${nums.last()}." +
                        "  (${sec.attempt} × ${fmt(sec.marksEach)} = ${fmt(sec.attempt * sec.marksEach)})", bold = true, size = 11f)
                }
                ordered.forEach { q ->
                    // "(a) …\n(b) …", where a part may run over several lines; each part shows its marks at its end.
                    val parts = mutableListOf<String>()
                    q.text.split("\n").filter { it.isNotBlank() }.forEach { line ->
                        if (parts.isEmpty() || Regex("""^\([a-d]\)\s""").containsMatchIn(line)) parts += line else parts[parts.lastIndex] += "\n" + line
                    }
                    val each = fmt(q.marks / parts.size)
                    w.block(parts.mapIndexed { i, p ->
                        ((if (i == 0) "${numbering.number(q)}.  " else "") + math(clean(p)) + "   ($each)") to (if (i == 0) Style.Q else Style.Part)
                    })
                }
            }
        }
        w.space(14f)
        w.text("— End of paper —", size = 10f, color = MUTED, center = true)

        if (withScheme) {
            w.newPage()
            w.title("MARKING SCHEME", "For parents and teachers", "Step marks as a board examiner gives them")
            paper.written.sortedWith(compareBy({ it.section }, { it.number })).forEach { q ->
                val lines = mutableListOf("${numbering.label(q)}  (${fmt(q.marks)} marks)" to Style.Q)
                q.modelAnswer.forEach { lines += "      " + math(it) to Style.Opt }
                q.rubric.forEach { lines += "      • ${it.description}  [${fmt(it.marks)}]" to Style.Scheme }
                w.block(lines)
            }
            if (paper.mcqs.isNotEmpty()) w.block(listOf("MCQ key: " + paper.mcqs.mapIndexed { i, q -> "${i + 1}-${'A' + q.sampleCorrectIndex}" }.joinToString("  ") to Style.Q))
        }
        w.finish()
    }

    private fun bubbleSheet(w: Writer, count: Int) {
        w.newPage()
        w.title("BUBBLE SHEET · PAPER I (OBJECTIVE)", "Fill one circle per question", "Pen only")
        val c = w.canvas()
        var y = w.y + 6f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = INK }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = INK }
        val cols = if (count > 15) 2 else 1
        val perCol = (count + cols - 1) / cols
        for (i in 0 until count) {
            val col = i / perCol
            val x0 = M + 30f + col * 250f
            val yy = y + (i % perCol) * 30f
            c.drawText("${i + 1}.", x0, yy + 4f, label)
            for (k in 0 until 4) {
                val cx = x0 + 40f + k * 36f
                c.drawCircle(cx, yy, 9f, ring)
                label.textAlign = Paint.Align.CENTER
                c.drawText("${'A' + k}", cx, yy + 4f, label)
                label.textAlign = Paint.Align.LEFT
            }
        }
        w.y = y + perCol * 30f + 10f
        w.text("Example of a correctly filled circle: ●   Wrong: ✓  ✗  ⊘  half-filled, or two circles.", size = 10f, color = MUTED)
    }

    private enum class Style { Q, Part, Opt, Right, Scheme }

    private class Writer(private val sink: PageSink, private val paperTitle: String, private val code: String, private val subject: String = "Mathematics") {
        private var page: Canvas? = null
        private var number = 0
        var y = M

        fun canvas(): Canvas = page ?: run { newPage(); page!! }

        fun newPage() {
            finish()
            number++
            page = sink.start(number, W.toInt(), H.toInt())
            val c = page!!
            c.drawColor(Color.WHITE)
            // Brand band.
            val band = Paint().apply { color = LEAF }
            c.drawRect(0f, 0f, W, 46f, band)
            c.drawRoundRect(RectF(M, 12f, M + 22f, 34f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME })
            c.drawText("P", M + 6.5f, 28.5f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = LEAF })
            c.drawText("Parhlou", M + 30f, 29f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE })
            val right = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.WHITE; textAlign = Paint.Align.RIGHT }
            val notes = code == "NOTES"
            c.drawText(if (notes) "Revision notes · Class 9 $subject" else "Board-style practice paper · Class 9 $subject", W - M, 22f, right)
            c.drawText(paperTitle.take(70), W - M, 35f, right)
            if (notes) {
                val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = MUTED }
                c.drawLine(M, H - 34f, W - M, H - 34f, Paint().apply { color = LIME; strokeWidth = 1.5f })
                c.drawText("Parhlou · revision notes", M, H - 22f, foot)
                c.drawText("Page $number", W - M, H - 22f, TextPaint(foot).apply { textAlign = Paint.Align.RIGHT })
                y = 64f
                return
            }
            // Roll number boxes, as on the board paper.
            val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = INK }
            c.drawText("Roll No.", M, 70f, small)
            val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.9f; color = INK }
            for (i in 0 until 6) c.drawRect(M + 44f + i * 18f, 58f, M + 62f + i * 18f, 76f, box)
            c.drawText("(to be filled in by the candidate)", M + 158f, 70f, TextPaint(small).apply { color = MUTED; textSize = 8.5f })
            c.drawText("Paper code: $code", W - M, 70f, TextPaint(small).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD })
            // Footer.
            val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = MUTED }
            c.drawLine(M, H - 34f, W - M, H - 34f, Paint().apply { color = LIME; strokeWidth = 1.5f })
            c.drawText("Parhlou · $code · Write your answers on paper, then scan them in the app: open this paper → \"I wrote it on paper\".", M, H - 22f, foot)
            c.drawText("Page $number", W - M, H - 22f, TextPaint(foot).apply { textAlign = Paint.Align.RIGHT })
            y = 92f
        }

        fun finish() { if (page != null) sink.finish(); page = null }

        private fun layout(text: String, size: Float, bold: Boolean, color: Int, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, hanging: Int = 0): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).setAlignment(align).setLineSpacing(1.5f, 1f)
                // Wrapped lines start under the text, not under the question number.
                .apply { if (hanging > 0) setIndents(intArrayOf(0, hanging), null) }.build()
        }

        private fun draw(l: StaticLayout, x: Float) {
            val c = canvas()
            c.save(); c.translate(x, y); l.draw(c); c.restore()
            y += l.height
        }

        fun space(d: Float) { y += d }

        fun text(t: String, bold: Boolean = false, size: Float = 11f, color: Int = INK, center: Boolean = false) {
            val l = layout(t, size, bold, color, W - 2 * M, if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            if (page == null || y + l.height > H - 44f) newPage()
            draw(l, M); y += 5f
        }

        fun heading(t: String) {
            if (y + 60f > H - 44f) newPage()
            y += 6f
            text(t, bold = true, size = 12.5f, center = true)
        }

        fun note(t: String) {
            val l = layout(t, 9.5f, false, INK, W - 2 * M - 16f)
            canvas().drawRect(M, y, W - M, y + l.height + 12f, Paint().apply { color = Color.rgb(237, 242, 234) })
            y += 6f; draw(l, M + 8f); y += 14f
        }

        fun title(main: String, left: String, right: String) {
            val c = canvas()
            text("${subject.uppercase()} (CLASS 9) · $main", bold = true, size = 13f, center = true)
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = INK; typeface = Typeface.DEFAULT_BOLD }
            c.drawText(left, M, y + 8f, p)
            c.drawText(right, W - M, y + 8f, TextPaint(p).apply { textAlign = Paint.Align.RIGHT })
            y += 16f
            c.drawLine(M, y, W - M, y, Paint().apply { color = INK; strokeWidth = 0.8f })
            y += 10f
        }

        /** Lines that belong together (a question and its options) stay on one page. */
        fun block(lines: List<Pair<String, Style>>) {
            val layouts = lines.map { (t, s) ->
                when (s) {
                    Style.Q -> layout(t, 11f, false, INK, W - 2 * M, hanging = 22)
                    Style.Part -> layout(t, 11f, false, INK, W - 2 * M - 22f, hanging = 22)
                    Style.Opt -> layout(t, 10.5f, false, INK, W - 2 * M)
                    Style.Right -> layout(t, 10f, true, INK, W - 2 * M, Layout.Alignment.ALIGN_OPPOSITE)
                    Style.Scheme -> layout(t, 9.5f, false, MUTED, W - 2 * M)
                } to s
            }
            val height = layouts.sumOf { it.first.height.toDouble() }.toFloat() + 8f
            if (page == null || y + height > H - 44f) newPage()
            layouts.forEach { (l, s) -> draw(l, if (s == Style.Part) M + 22f else M) }
            y += 8f
        }
    }

    private fun word(n: Int) = NUMBERS.getOrElse(n) { n.toString() }
    private fun hours(minutes: Int) = if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')} hours" else "$minutes minutes"
    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)

    /** The app's own wording that doesn't belong on paper: book references, sub-part labels and "photograph your work". */
    fun clean(s: String): String = s
        .replace(Regex("""\s*Draw it on paper and photograph your work[^.]*\."""), "")
        .replace(Regex("""\s*\((?:Ex|Example)\s+[\d.]+[^()]*(?:\([^()]*\))?[^()]*\)"""), "")
        .replace(Regex("""(?<=[:.] )\((?:i|ii|iii|iv|v|vi|vii|viii|ix|x)\)\s+"""), "")
        .trim()

    private val SUP = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', '+' to '⁺', 'n' to 'ⁿ', 'x' to 'ˣ')

    /** The course's plain-text powers ("x^2", "10^-3", "a^(n+1)") as printed superscripts where the characters exist. */
    fun math(s: String): String = Regex("""\^\(([^()]*)\)|\^(-?[0-9nx+]+)""").replace(s) { m ->
        val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
        if (body.all { it in SUP }) body.map { SUP.getValue(it) }.joinToString("") else m.value
    }
}
