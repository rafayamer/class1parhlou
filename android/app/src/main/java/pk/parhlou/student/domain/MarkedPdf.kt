package pk.parhlou.student.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.data.PaperBlueprint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the marked paper as a PDF on the phone: a summary page, a page per answered question with its marks and
 * reasons, and each scanned page headed with the question it answers. Nothing is uploaded.
 */
object MarkedPdf {
    private const val W = 595   // A4 in points
    private const val H = 842
    private const val MARGIN = 40f

    fun write(
        file: File,
        blueprint: PaperBlueprint,
        result: PaperResult,
        report: IntegrityReport?,
        photos: Map<String, List<File>>,
        label: String,
        now: Long = System.currentTimeMillis(),
        analysis: PaperAnalysis? = null,
        examiner: ExaminerReport? = null,
    ): File {
        val doc = PdfDocument()
        val pages = PageWriter(doc)

        pages.heading("Parhlou — marked paper")
        pages.text(blueprint.title, bold = true)
        pages.text(SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(now)))
        pages.text(label, size = 10f, color = Color.rgb(138, 90, 0))
        pages.gap()
        pages.text("Confirmed marks: ${fmt(result.confirmed)} / ${fmt(result.total)}  (${result.percent}%)", bold = true, size = 16f)
        if (result.pending > 0) pages.text("${fmt(result.pending)} more marks were not checked automatically. They are not counted as wrong.")
        pages.text("Section A · MCQs: ${fmt(result.mcqMarks)} / ${result.mcq.size}")
        result.written.groupBy { it.question.section }.forEach { (section, outcomes) ->
            val counted = outcomes.filter { it.counted }
            pages.text("Section ${'A' + section}: ${fmt(counted.sumOf { it.mark.awarded })} / ${fmt(counted.sumOf { it.question.marks })} (${counted.size} counted)")
        }
        if (report != null) {
            pages.gap()
            pages.text("Invigilator report", bold = true)
            pages.text("Time used: ${Invigilator.duration(report.usedMillis)} of ${Invigilator.duration(report.allowedMillis)}")
            pages.text("Left the paper: ${if (report.timesAway == 0) "never" else "${report.timesAway} times, ${Invigilator.duration(report.totalAwayMillis)} in total"}")
            pages.text("Finished: ${if (report.finishedOnTime) "on time" else "${Invigilator.duration(report.overrunMillis)} late"}")
        }
        if (analysis != null) {
            pages.newPage()
            pages.heading("Examiner's analysis")
            examiner?.let { pages.text(it.headline, bold = true); pages.text(it.summary, size = 11f) }
            pages.gap(); pages.text("By unit", bold = true)
            analysis.units.forEach { pages.text("${it.title}: ${fmt(it.got)}/${fmt(it.max)} (${it.percent}%)", size = 11f) }
            pages.gap(); pages.text("By question type", bold = true)
            analysis.types.forEach { pages.text("${it.title}: ${fmt(it.got)}/${fmt(it.max)} (${it.percent}%)", size = 11f) }
            val lost = analysis.lost.filterValues { it > 0 }.entries.sortedByDescending { it.value }
            if (lost.isNotEmpty()) { pages.gap(); pages.text("Where the marks went", bold = true); lost.forEach { (k, v) -> pages.text("${k.label}: −${fmt(v)}", size = 11f) } }
            examiner?.let { e ->
                if (e.strengths.isNotEmpty()) { pages.gap(); pages.text("Strong", bold = true); e.strengths.forEach { pages.text("• $it", size = 11f) } }
                if (e.weaknesses.isNotEmpty()) {
                    pages.gap(); pages.text("Needs hard work", bold = true)
                    e.weaknesses.forEach { w -> pages.text("• ${w.topic}: ${w.why}", size = 11f); pages.text("   What to do: ${w.action}", size = 10f, color = Color.DKGRAY) }
                }
                if (e.plan.isNotEmpty()) { pages.gap(); pages.text("Plan for the next 7 days", bold = true); e.plan.forEach { pages.text("• $it", size = 11f) } }
                if (e.technique.isNotEmpty()) { pages.gap(); pages.text("Exam technique", bold = true); e.technique.forEach { pages.text("• $it", size = 11f) } }
                if (e.closing.isNotBlank()) { pages.gap(); pages.text(e.closing, size = 11f) }
            }
        }
        val wrongMcqs = result.mcq.filter { !it.correct }
        if (wrongMcqs.isNotEmpty()) {
            pages.gap()
            pages.text("MCQs to review", bold = true)
            wrongMcqs.forEach { o ->
                val q = o.question
                pages.text("${q.stem}  Your answer: ${o.chosen?.let { q.options[it] } ?: "none"}. Correct: ${q.options[q.sampleCorrectIndex]}.", size = 11f)
            }
        }

        result.written.filter { it.answer.isNotBlank() || it.photos > 0 }.forEach { o ->
            val q = o.question
            pages.newPage()
            pages.heading("Question ${'A' + q.section}${q.number}  ·  ${fmt(o.mark.awarded)} / ${fmt(q.marks)}")
            if (!o.counted) pages.text("Not counted: more questions were answered than the section allows.", color = Color.GRAY)
            pages.text(q.text, bold = true)
            pages.text("Status: ${o.mark.status.label}", size = 11f)
            pages.gap()
            o.mark.criteria.forEach { c ->
                val sign = when (c.status) { CriterionStatus.MET -> "✓"; CriterionStatus.NOT_FOUND -> "✗"; CriterionStatus.UNSURE -> "?" }
                pages.text("$sign  ${c.description}  (${fmt(c.awarded)}/${fmt(c.max)})")
                if (c.status == CriterionStatus.NOT_FOUND) pages.text("     Why: ${c.why}   How: ${c.how}", size = 10f, color = Color.DKGRAY)
            }
            if (o.answer.isNotBlank()) {
                pages.gap(); pages.text("Your typed answer", bold = true); pages.text(o.answer, size = 11f)
            }
            pages.gap(); pages.text("Model answer", bold = true)
            q.modelAnswer.forEach { pages.text(it, size = 11f) }
            photos[q.id].orEmpty().forEachIndexed { i, photo ->
                pages.image(photo, "Question ${'A' + q.section}${q.number} · page ${i + 1} · ${fmt(o.mark.awarded)}/${fmt(q.marks)}")
            }
        }
        pages.finish()
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)

    private class PageWriter(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = MARGIN

        private fun canvas(): Canvas {
            if (page == null) newPage()
            return page!!.canvas
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++number).create())
            y = MARGIN
        }

        fun finish() { page?.let { doc.finishPage(it) }; page = null }

        fun heading(text: String) = text(text, bold = true, size = 18f)
        fun gap() { y += 10f }

        fun text(text: String, bold: Boolean = false, size: Float = 12f, color: Int = Color.BLACK) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val width = (W - 2 * MARGIN).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            if (y + layout.height > H - MARGIN) newPage()
            val c = canvas()
            c.save(); c.translate(MARGIN, y); layout.draw(c); c.restore()
            y += layout.height + 4f
        }

        /** A scanned page on its own PDF page, scaled to fit under a header line. */
        fun image(file: File, header: String) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2
            val bmp: Bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            newPage()
            text(header, bold = true, color = Color.rgb(59, 118, 20))
            val top = y + 4f
            val maxW = W - 2 * MARGIN
            val maxH = H - MARGIN - top
            val scale = minOf(maxW / bmp.width, maxH / bmp.height)
            val dst = RectF(MARGIN, top, MARGIN + bmp.width * scale, top + bmp.height * scale)
            canvas().drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
            y = H.toFloat()
        }
    }
}
