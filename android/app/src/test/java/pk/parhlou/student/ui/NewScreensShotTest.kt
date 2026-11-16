package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.EvidenceCodec
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/**
 * Screenshots of the guided-learning screens with the real course:
 * ./gradlew :app:testDebugUnitTest --tests '*NewScreensShotTest*' -PscreenshotDir=/tmp/shots -PcourseBundle=/path/bundle.json
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w393dp-h852dp-xxhdpi")
class NewScreensShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")
    private val bundle = System.getProperty("parhlou.courseBundle")

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(dark: Boolean) {
        assumeTrue(dir != null && bundle != null)
        val mode = if (dark) "dark" else "light"
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf("pb-9-math"), Pathway.COMPLETE_ACADEMY, 5, 30,
                completed = true, name = "Ayesha"))
            store.setReadinessPassed(true)
            store.replaceProgress((1..3).joinToString("\n") { EvidenceCodec.line(now - it * day, "session", Evidence.SessionCompleted(60)) })
        }
        val repo = ContentBundle.repository(JSONObject(File(bundle!!).readText()), 1, FakeRepository())
        rule.setContent { ParhlouTheme(dark = dark) { ParhlouApp(repo, store) } }
        rule.waitUntil(8_000) { exists("Start today's session") }
        save("20-home-$mode")
        rule.onNodeWithText("Your path").performScrollTo()
        save("21-path-$mode")
        rule.onNodeWithTag("start-session").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Check") }
        save("22-session-$mode")
        // Answer whatever the first item is, wrongly if it's a choice, to show the red panel.
        if (exists("Choose the correct answer") || exists("True or false?") || exists("What is the next step?")) {
            rule.onNodeWithTag("option-0").performClick()
            rule.onNodeWithTag("session-check").performClick()
            save("23-feedback-$mode")
        }
    }

    @Test fun light() = shoot(false)
    @Test fun dark() = shoot(true)
}
