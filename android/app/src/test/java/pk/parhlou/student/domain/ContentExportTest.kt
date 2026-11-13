package pk.parhlou.student.domain

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import java.io.File

/**
 * Writes the content packed in the app as a bundle file, ready to publish to the server.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*ContentExportTest' -PcontentExport=<file>
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentExportTest {
    @Test fun export() {
        val path = System.getProperty("parhlou.contentExport")
        assumeTrue(path != null)
        val bundle = ContentBundle.export(FakeRepository(), FakeRepository.MATHS_9, "Parhlou demo content, Unit 1 (not a textbook)")
        File(path!!).apply { parentFile?.mkdirs() }.writeText(bundle.toString())
    }
}
