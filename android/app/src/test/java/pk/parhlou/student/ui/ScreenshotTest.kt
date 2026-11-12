package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/** Renders design screenshots on demand: ./gradlew :app:testDebugUnitTest -PscreenshotDir=/tmp/shots */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")

    private fun shoot(dark: Boolean) {
        assumeTrue(dir != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9),
                Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
        }
        rule.setContent { ParhlouTheme(dark = dark) { ParhlouApp(FakeRepository(), store) } }
        val mode = if (dark) "dark" else "light"
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Do this now")).fetchSemanticsNodes().isNotEmpty() }
        save("1-home-$mode")
        rule.onNodeWithText("Ask a question").performClick(); save("2-tutor-$mode")
        rule.activityRule()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, ComponentActivity>.activityRule() {
        onNode(hasContentDescription("Back")).performClick()
        onNode(hasContentDescription("Profile")).performClick(); save("3-profile-${if (isDark) "dark" else "light"}")
        onNode(hasContentDescription("Book")).performClick()
        onNodeWithText("Mathematics · Class 9").performClick(); save("4-units-${if (isDark) "dark" else "light"}")
        onNodeWithText("1. Rational and irrational numbers").performClick()
        onNodeWithText("2 Quiz").performClick()
        onNodeWithText("√2").performClick(); save("5-quiz-${if (isDark) "dark" else "light"}")
    }

    private var isDark = false

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun light() { isDark = false; shoot(false) }
    @Test fun dark() { isDark = true; shoot(true) }
}
