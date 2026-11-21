package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.data.BundleRepository
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/** Audit: the app with all five real courses, every main screen saved. Run with -PscreenshotDir=/path. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class AuditShotsTest {
    private val night = System.getProperty("parhlou.night") == "1"
    private val nightSuffix = if (night) "-dark" else ""
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")
    private fun exists(t: String) = rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs(); File(dir, "$name$nightSuffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun allSubjects() {
        assumeTrue(dir != null)
        val root = File(System.getProperty("user.dir")!!).let { if (File(it, "../../content").exists()) File(it, "../../content") else File(it, "../content") }
        val base = FakeRepository()
        val repos = listOf("maths", "physics", "chemistry", "biology", "computer").mapNotNull { s ->
            File(root, "class9-$s/bundle.json").takeIf { it.exists() }?.let { ContentBundle.repository(JSONObject(it.readText()), 1, base) }
        }
        val repo = BundleRepository.stack(repos, base)
        val ids = setOf(FakeRepository.MATHS_9, FakeRepository.PHYSICS_9, FakeRepository.CHEMISTRY_9, FakeRepository.BIOLOGY_9, FakeRepository.COMPUTER_9)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", ids, Pathway.COMPLETE_ACADEMY, 5, 60, completed = true,
                name = "Ayesha Khan", email = "ayesha@example.com", favoriteSubject = FakeRepository.PHYSICS_9))
            store.setReadinessPassed(true)
        }
        rule.setContent { ParhlouTheme(dark = night) { ParhlouApp(repo, store) } }
        rule.waitUntil(20_000) { exists("Do this now") }
        save("a01-home")
        rule.onNodeWithText("Your journey").performScrollTo(); save("a02-home-journey")
        rule.onNodeWithTag("tab-subjects").performClick(); rule.waitUntil(3_000) { exists("Your books") }; save("a03-books")
        for ((name, title) in listOf("maths" to "Mathematics · Class 9", "physics" to "Physics · Class 9", "chemistry" to "Chemistry · Class 9", "biology" to "Biology · Class 9", "computer" to "Computer Science · Class 9")) {
            rule.onNodeWithText(title).performScrollTo().performClick()
            rule.waitUntil(5_000) { exists("Your path") || exists("Path") }
            save("a04-$name-path")
            if (exists("Solutions")) { rule.onNodeWithText("Solutions").performClick(); rule.waitForIdle(); save("a05-$name-solutions") }
            if (exists("Practicals")) { rule.onNodeWithText("Practicals").performClick(); rule.waitForIdle(); save("a06-$name-practicals") }
            repeat(3) { if (!exists("Your books")) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick(); rule.waitForIdle() } }
            if (!exists("Your books")) rule.onNodeWithTag("tab-subjects").performClick()
            rule.waitUntil(3_000) { exists("Your books") }
        }
        rule.onNodeWithTag("tab-tests").performClick(); rule.waitUntil(3_000) { exists("Practice test") }; save("a07-papers")
        rule.onNodeWithTag("guess-paper").performScrollTo(); save("a07b-guess")
        rule.onNodeWithTag("past-papers").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Lahore").performScrollTo().performClick(); rule.waitForIdle(); save("a07c-past-lahore")
        rule.onAllNodes(hasText("Lahore 20", substring = true))[0].performScrollTo().performClick(); rule.waitUntil(5_000) { exists("Before you start") }; save("a07e-past-start")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick(); rule.waitUntil(5_000) { exists("Choose a board") || exists("Pick a board") }
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick(); rule.waitUntil(5_000) { exists("Practice test") }
        rule.onNodeWithTag("make-test").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("practice-ch-2").performScrollTo().performClick()
        rule.onNodeWithTag("practice-ch-3").performScrollTo().performClick(); save("a07f-practice-pick")
        rule.onNodeWithTag("practice-half").performScrollTo().performClick()
        rule.onNodeWithTag("practice-start").performScrollTo().performClick(); rule.waitUntil(5_000) { exists("Before you start") }; save("a07g-practice-start")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick(); rule.waitUntil(5_000) { exists("Which chapters") }
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick(); rule.waitUntil(5_000) { exists("Practice test") }
        rule.onNodeWithTag("my-results").performScrollTo(); save("a07h-my-papers")
        for (id in listOf(FakeRepository.BIOLOGY_9, FakeRepository.COMPUTER_9)) {
            rule.onNodeWithTag("papers-subject-$id").performScrollTo().performClick(); rule.waitForIdle(); save("a07-papers-$id")
        }
        rule.onNodeWithTag("tab-profile").performClick(); rule.waitUntil(3_000) { exists("Edit profile") }; save("a08-profile")
        rule.onNodeWithText("Edit profile").performClick(); rule.waitUntil(3_000) { exists("About you") }; save("a09-edit-profile")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("tab-home").performClick(); rule.waitUntil(20_000) { exists("Do this now") }
        rule.onNodeWithText("Start today's session").performScrollTo().performClick()
        rule.waitForIdle(); save("a10-session")
    }
}
