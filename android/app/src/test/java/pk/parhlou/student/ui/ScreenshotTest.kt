package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
// Android 12L renders the RenderEffect blur tier; Robolectric's native Skia cannot compile the API 33+ AGSL shaders.
@Config(sdk = [32], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun shoot(dark: Boolean) {
        assumeTrue(dir != null)
        val mode = if (dark) "dark" else "light"
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9),
                Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
            store.setReadinessPassed(true)
        }
        rule.setContent { ParhlouTheme(dark = dark) { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
        save("1-home-$mode")

        // AI tutor conversation
        rule.onNodeWithText("Ask a question").performClick()
        save("2-tutor-$mode")
        rule.onNodeWithTag("tutor-input").performTextInput("How do I simplify root 50?")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { exists("largest perfect-square factor") }
        rule.onNodeWithText("Show each step").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Split it") }
        save("3-tutor-chat-$mode")
        rule.onNodeWithContentDescription("Back").performClick()

        // Lesson module and quiz
        rule.onNodeWithText("Book").performClick()
        rule.onNodeWithText("Mathematics · Class 9").performClick(); save("4-units-$mode")
        rule.onNodeWithText("1. Rational and irrational numbers").performClick()
        rule.onNodeWithText("2 Quiz").performClick()
        rule.onNodeWithText("√9").performClick(); save("5-quiz-$mode")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        // Sit the chapter test, then Check Master and the invigilator report
        rule.onAllNodes(hasText("Papers")).onFirst().performClick()
        rule.onNodeWithText("Chapter test (sample practice pattern)").performScrollTo().performClick()
        rule.onNodeWithText("Start paper").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Invigilator on") }
        rule.onNodeWithTag("mcq-p1-3").performScrollTo().performClick()
        save("6-paper-$mode")
        rule.onNodeWithTag("mcq-p2-0").performScrollTo().performClick()
        rule.onNodeWithTag("answer-s1").performScrollTo().performTextInput("0.45 = 45/100 = 9/20, both integers, so it is rational")
        rule.onNodeWithTag("answer-s3").performScrollTo().performTextInput("about a third I think")
        rule.onNodeWithTag("answer-s7").performScrollTo().performTextInput("root 98 = 7 root 2, final answer 6 root 2")
        save("7-paper-written-$mode")
        rule.onNodeWithText("Hand in paper").performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("Confirmed marks") }
        save("8-checkmaster-$mode")
        rule.onNodeWithTag("result-s7").performScrollTo()
        save("9-checkmaster-question-$mode")
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun light() = shoot(false)
    @Test fun dark() = shoot(true)
}
