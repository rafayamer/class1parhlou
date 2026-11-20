package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.ui.screens.ModuleScreen
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/**
 * Screenshots of the lesson screens with the real course, to review how they look together:
 * ./gradlew :app:testPrototypeDebugUnitTest --tests '*LessonShotsTest*' -PscreenshotDir=/tmp/shots -PcourseBundle=/path/bundle.json
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w393dp-h852dp-xxhdpi")
class LessonShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")
    private val bundle = System.getProperty("parhlou.courseBundle")

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun exists(t: String) = rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tap(t: String) { rule.onAllNodes(hasText(t, substring = true) and hasClickAction())[0].performScrollTo().performClick(); rule.waitForIdle() }

    @Test fun moduleAndLesson() {
        assumeTrue(dir != null && bundle != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking { store.clearAll() }
        val repo = ContentBundle.repository(JSONObject(File(bundle!!).readText()), 1, FakeRepository())
        val subject = repo.subject("pb-9-math")!!
        val module = subject.chapters.flatMap { it.modules }.first { it.lesson != null }
        val progress = ProgressStore(store)
        var screen by androidx.compose.runtime.mutableStateOf("hub")
        rule.setContent { ParhlouTheme {
            if (screen == "hub") ModuleScreen(module, repo.moduleQuiz(module.id), progress, store, DemoBrain(repo), "class 9", {}, {},
                onOpenLesson = { screen = "lesson" }, onPractice = {})
            else pk.parhlou.student.ui.screens.LessonPlayer(module.lesson!!, module.id, module.title, true, {}, {}, {}, {}, { screen = "hub" })
        } }
        rule.waitForIdle()
        save("l01-hub")
        rule.onNodeWithText("Start the lesson").performClick(); rule.waitForIdle()
        var n = 2
        repeat(14) {
            save("l" + "%02d".format(n++) + "-lesson")
            val next = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("lesson-next") or androidx.compose.ui.test.hasTestTag("lesson-finish")).fetchSemanticsNodes()
            if (next.isEmpty()) {
                // A "your turn" question: show the solution to move on.
                if (exists("Show me the solution")) tap("Show me the solution") else return@repeat
            } else rule.onAllNodes(androidx.compose.ui.test.hasTestTag("lesson-next") or androidx.compose.ui.test.hasTestTag("lesson-finish"))[0].performClick()
            rule.waitForIdle()
        }
        save("l30-lesson-end")
        screen = "hub"; rule.waitForIdle()
        listOf("step-solutions" to "l40-solutions", "step-write" to "l50-write", "step-improve" to "l60-improve").forEach { (tag, name) ->
            if (rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag(tag).performScrollTo().performClick(); rule.waitForIdle(); save(name)
                rule.onNodeWithTag("all-steps").performClick(); rule.waitForIdle()
            }
        }
        save("l70-hub-after")
    }
}
