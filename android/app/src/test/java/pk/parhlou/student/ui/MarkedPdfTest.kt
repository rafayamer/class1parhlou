package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.domain.CheckMaster
import pk.parhlou.student.domain.MarkedPdf
import pk.parhlou.student.domain.PaperSubmission
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32])
class MarkedPdfTest {
    /** Robolectric has no native PDF engine; this test runs fully only on a device or emulator (connected tests). */
    private fun pdfEngineWorks() = runCatching {
        android.graphics.pdf.PdfDocument().apply {
            finishPage(startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(10, 10, 1).create())); close()
        }
    }.isSuccess

    @Test fun markedPaperIsAPdfWithScannedPages() = runBlocking {
        org.junit.Assume.assumeTrue("no PDF engine in this test runtime", pdfEngineWorks())
        val repo = FakeRepository()
        val blueprint = repo.blueprints(FakeRepository.MATHS_9).first { repo.paper(it.id) != null }
        val paper = repo.paper(blueprint.id)!!
        val q = paper.written.first()
        val submission = PaperSubmission(
            mcq = mapOf(paper.mcqs.first().id to 0),
            written = mapOf(q.id to q.modelAnswer.joinToString("\n")),
            photos = mapOf(q.id to 1),
        )
        val result = CheckMaster.check(blueprint, paper, submission, DemoBrain(repo))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photo = File(context.cacheDir, "page.jpg").apply {
            outputStream().use { Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
        val pdf = MarkedPdf.write(File(exportsDir(context), "marked.pdf"), blueprint, result, null, mapOf(q.id to listOf(photo)), "Demo checker")
        val bytes = pdf.readBytes()
        assertEquals("%PDF", String(bytes, 0, 4))
        // Summary page, the answered question's page and its scanned page.
        assertTrue(Regex("/Type\\s*/Page[^s]").findAll(String(bytes, Charsets.ISO_8859_1)).count() >= 3)
    }
}
