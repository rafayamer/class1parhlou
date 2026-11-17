package pk.parhlou.student.ui

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.domain.PaperPdf
import java.io.File

/**
 * "Print the paper (PDF)" used to crash the app: papers are saved in cache/papers, which the share provider didn't allow.
 * (One test on purpose: FileProvider remembers its folders for the whole process, while Robolectric gives each test a new
 * data folder, so share checks split across tests would compare against the wrong folder.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrintPaperTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun printingAPaperOpensTheShareSheetAndNeverCrashes() {
        // FileProvider remembers folders from earlier tests (each had its own data folder): start fresh.
        FileProvider::class.java.declaredFields.firstOrNull { it.name == "sCache" }?.let { f -> f.isAccessible = true; (f.get(null) as? MutableMap<*, *>)?.clear() }
        // Every folder the app makes files in can be shared.
        for (dir in listOf("papers", "exports", "ustaad", "updates")) {
            val f = File(app.cacheDir, "$dir/check.pdf").apply { parentFile?.mkdirs(); writeText("x") }
            assertNotNull(dir, FileProvider.getUriForFile(app, "${app.packageName}.files", f))
        }
        // The paper's pages draw (Robolectric has no real PDF writer, so pages go to bitmaps, as in PrintedPaperTest).
        val repo = FakeRepository()
        val b = repo.blueprints(FakeRepository.MATHS_9).first()
        var pages = 0
        PaperPdf.draw(object : PaperPdf.PageSink {
            override fun start(number: Int, width: Int, height: Int) =
                android.graphics.Canvas(android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)).also { pages++ }
            override fun finish() {}
        }, b, repo.paper(b.id)!!, false, "Mathematics")
        assertTrue(pages > 0)
        // A paper saved where the app saves printed papers opens the share sheet.
        val file = File(app.cacheDir, "papers/${b.id}.pdf").apply { parentFile?.mkdirs(); writeText("%PDF-1.4") }
        assertTrue(shareFile(app, file, "application/pdf", "Print or share your paper"))
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(app).nextStartedActivity.action)
        // A file the provider can't share gives a message, not a crash.
        val outside = File(app.filesDir, "elsewhere/x.pdf").apply { parentFile?.mkdirs(); writeText("x") }
        assertFalse(shareFile(app, outside, "application/pdf", "x"))
    }
}
