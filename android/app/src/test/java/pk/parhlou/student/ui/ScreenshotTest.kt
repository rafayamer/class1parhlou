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
        rule.onNodeWithText("Your journey").performScrollTo()
        save("1b-home-journey-$mode")

        // Ask Ustaad: four starts, then an answer.
        rule.onNodeWithTag("home-ask").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Snap a question") }
        save("2-tutor-$mode")
        rule.onNodeWithTag("tutor-input").performTextInput("How do I simplify root 50?")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("ustaad-reply")).fetchSemanticsNodes().isNotEmpty() }
        save("3-tutor-chat-$mode")
        rule.onNodeWithContentDescription("Back").performClick()

        // Books: every subject, each with its own path.
        rule.onNodeWithTag("tab-subjects").performClick()
        rule.waitUntil(3_000) { exists("Your books") }
        save("4-books-$mode")
        rule.onNodeWithText("Mathematics · Class 9").performClick()
        rule.waitUntil(3_000) { exists("Rational and irrational numbers") }
        save("4b-book-path-$mode")
        rule.onNodeWithContentDescription("Back").performClick()

        // Papers: board papers, make a paper, then a chapter test.
        rule.onNodeWithTag("tab-tests").performClick()
        rule.waitUntil(3_000) { exists("Board papers") }
        save("5-papers-$mode")
        rule.onNodeWithTag("scope-chapter").performScrollTo().performClick()
        rule.onNodeWithTag("scope-picker").performScrollTo()
        save("5b-papers-chapter-$mode")
        rule.onNodeWithTag("paper-${pk.parhlou.student.data.SampleContent.CHAPTER_TEST}").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Before you start") }
        save("6-paper-start-$mode")
        rule.onNodeWithText("Start in the app").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Invigilator on") }
        save("7-paper-$mode")
    }

    /** The screens added for plans, weak topics, mock papers, the scanner and the profile. */
    private fun shootMore(dark: Boolean) {
        assumeTrue(dir != null)
        val mode = if (dark) "dark" else "light"
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(ctx)
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9),
                Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ayesha"))
            store.setReadinessPassed(true)
            store.replaceProgress(listOf(
                pk.parhlou.student.domain.EvidenceCodec.line(now - 3 * day, "m-1-1", pk.parhlou.student.domain.Evidence.QuizCompleted(70)),
                pk.parhlou.student.domain.EvidenceCodec.line(now - 2 * day, "topic:n-surds", pk.parhlou.student.domain.Evidence.TopicMissed),
                pk.parhlou.student.domain.EvidenceCodec.line(now - 1 * day, "topic:n-surds", pk.parhlou.student.domain.Evidence.TopicMissed),
                pk.parhlou.student.domain.EvidenceCodec.line(now - 1 * day, "topic:n-rational", pk.parhlou.student.domain.Evidence.TopicMissed),
            ).joinToString("\n"))
        }
        // Two scanned pages waiting in the chapter test (the camera can't run here).
        val scan = pk.parhlou.scanner.PageStore(File(ctx.filesDir, "exam/${pk.parhlou.student.data.SampleContent.CHAPTER_TEST}/_scan")).apply { clear() }
        repeat(2) { n ->
            val f = File(scan.photoDir(), "p$n.jpg")
            val page = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
                val c = android.graphics.Canvas(this); c.drawColor(android.graphics.Color.WHITE)
                val pen = android.graphics.Paint().apply { color = android.graphics.Color.rgb(30, 50, 140); textSize = 22f }
                listOf("Q B${n + 1}.", "0.45 = 45/100", "= 9/20", "so it is rational").forEachIndexed { i, t -> c.drawText(t, 24f, 60f + i * 40f, pen) }
            }
            f.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            scan.add(pk.parhlou.scanner.Photo(f, 300, 400, null))?.let { if (n == 0) scan.assign(it.id, "s1") }
        }
        rule.setContent { ParhlouTheme(dark = dark) { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
        rule.onNodeWithText("Full plan").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Today's plan") }
        save("11-plan-$mode")
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithTag("tab-profile").performClick()
        rule.waitUntil(3_000) { exists("Send feedback") }
        save("16-profile-$mode")
        rule.onNodeWithText("Send feedback").performScrollTo().performClick()
        rule.onNodeWithTag("feedback").performScrollTo()
        save("17-feedback-$mode")

        // The study calendar (last: a sheet stays open in tests).
        rule.onNodeWithTag("tab-home").performClick()
        rule.waitUntil(3_000) { exists("Do this now") }
        rule.onNodeWithTag("open-calendar").performScrollTo().performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("study-calendar")).fetchSemanticsNodes().isNotEmpty() }
        save("10-calendar-$mode")
    }

    /** A paper sat on paper: the running start screen, the scan-and-hand-in desk, the locked Ustaad, then in-app with the keyboard. */
    @Test fun paperFlowLight() {
        assumeTrue(dir != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
            store.setReadinessPassed(true)
        }
        rule.setContent { ParhlouTheme(dark = false) { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
        rule.onNodeWithTag("tab-tests").performClick()
        rule.waitUntil(3_000) { exists("Make a paper") }
        rule.onNodeWithTag("scope-chapter").performScrollTo().performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("scope-picker")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("paper-${pk.parhlou.student.data.SampleContent.CHAPTER_TEST}").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Before you start") }
        rule.onNodeWithTag("start-on-paper").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("printed-clock")).fetchSemanticsNodes().isNotEmpty() }
        save("20-printed-desk")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(3_000) { exists("Before you start") }
        save("21-start-running")
        rule.onNodeWithTag("quit-paper").performScrollTo().performClick()
        save("22-quit-dialog")
        rule.onNodeWithTag("quit-confirm").performClick()
        rule.waitUntil(5_000) { exists("Assalam-o-Alaikum") }
        save("23-ustaad-after-quit")

        // Now sit it in the app: keyboard, hand in, result.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("tab-tests").performClick()
        rule.waitUntil(3_000) { exists("Make a paper") }
        rule.onNodeWithTag("scope-chapter").performScrollTo().performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("scope-picker")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("paper-${pk.parhlou.student.data.SampleContent.CHAPTER_TEST}").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Start in the app") }
        rule.onNodeWithText("Start in the app").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Time left") }
        rule.onAllNodes(androidx.compose.ui.test.hasText("∑ Maths symbols"))[0].performScrollTo().performClick()
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag("key-a/b"))[0].performScrollTo().performClick()
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag("key-√"))[0].performScrollTo().performClick()
        rule.onAllNodes(androidx.compose.ui.test.hasText("Hide ∑ symbols"))[0].performScrollTo()
        save("24-maths-keyboard")
        rule.onNodeWithText("Hand in paper").performScrollTo().performClick()
        rule.waitUntil(20_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !exists("Hand in paper") }
        rule.waitForIdle()
        save("25-result")
    }

    @Test fun moreLight() = shootMore(false)
    @Test fun moreDark() = shootMore(true)

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
