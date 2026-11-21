package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.hasTestTag
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
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/** Screenshots of the paywall for review: ./gradlew ... --tests '*PremiumShotsTest*' -PscreenshotDir=/tmp/shots [-PshotNight=1] */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class PremiumShotsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")
    private val night = System.getProperty("parhlou.night") == "1"
    private fun exists(t: String) = rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tag(t: String) = rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()
    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs(); File(dir, "$name${if (night) "-dark" else ""}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun paywall() {
        assumeTrue(dir != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ayesha"))
            store.setReadinessPassed(true)
        }
        val root = File(System.getProperty("user.dir")!!).let { if (File(it, "../../content").exists()) File(it, "../../content") else File(it, "../content") }
        val repo = ContentBundle.repository(JSONObject(File(root, "class9-maths/bundle.json").readText()), 1, FakeRepository())
        rule.setContent { ParhlouTheme(dark = night) { ParhlouApp(repo, store, paywall = true) } }
        rule.waitUntil(20_000) { tag("tab-home") }
        rule.onNodeWithTag("home-premium").performScrollTo(); save("p01-home-invite")
        rule.onNodeWithTag("tab-tests").performClick(); rule.waitUntil(8_000) { tag("make-test") }; save("p02-papers")
        rule.onNodeWithTag("guess-paper").performClick(); rule.waitUntil(5_000) { tag("premium-lock") }; save("p03-guess-locked")
        rule.onNodeWithText("Not now").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("tab-subjects").performClick()
        rule.waitUntil(5_000) { exists("Mathematics · Class 9") }
        rule.onNodeWithText("Mathematics · Class 9").performScrollTo().performClick()
        rule.waitUntil(5_000) { tag("learning-path") }
        if (exists("All units")) rule.onNodeWithText("All units").performClick()
        val fourth = repo.subject(FakeRepository.MATHS_9)!!.chapters[3]
        rule.onNodeWithTag("unit-locked-${fourth.id}").performScrollTo(); save("p04-path-locked")
        rule.onNodeWithTag("path-${fourth.modules.first().id}").performScrollTo().performClick()
        rule.waitUntil(5_000) { tag("premium-lock") }; save("p05-chapter-locked")
        rule.onNodeWithTag("premium-upgrade").performClick(); rule.waitUntil(5_000) { tag("plan-compare") }; save("p06-premium-screen")
        rule.onNodeWithTag("plan-compare").performScrollTo(); save("p07-premium-compare")
    }

    @Test fun askUstaadLocked() {
        assumeTrue(dir != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ayesha"))
        }
        rule.setContent { ParhlouTheme(dark = night) { ParhlouApp(FakeRepository(), store, paywall = true) } }
        rule.waitUntil(20_000) { tag("tab-home") }
        rule.onNodeWithTag("home-ask").performClick(); rule.waitUntil(5_000) { tag("premium-lock") }; save("p08-ask-locked")
    }

    /** What a Premium student sees: credits on Home, the cost before making a paper, the cost before sitting one. */
    @Test fun premiumCredits() {
        assumeTrue(dir != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ayesha"))
            store.setReadinessPassed(true)
        }
        val credits = pk.parhlou.student.ai.Allowance(820, "2026-12-21", monthly = 1000, premiumUntil = "2027-02-21")
        rule.setContent { ParhlouTheme(dark = night) {
            androidx.compose.runtime.CompositionLocalProvider(pk.parhlou.student.ui.components.LocalPremium provides
                pk.parhlou.student.ui.components.PremiumAccess(pk.parhlou.student.domain.Premium.Access(premium = true, paywall = true), credits = credits)) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    pk.parhlou.student.ui.screens.MakePaperScreen(make = { null }, onOpen = {}, onBack = {}, cost = pk.parhlou.student.domain.Credits.PaperCost(1, 9, 1))
                }
            }
        } }
        rule.waitUntil(5_000) { tag("make-paper-cost") }; save("p09-make-paper-cost")
    }

    @Test fun sitPaperCost() {
        assumeTrue(dir != null)
        val credits = pk.parhlou.student.ai.Allowance(820, "2026-12-21", monthly = 1000)
        val bp = pk.parhlou.student.data.PaperBlueprint("p", "Mathematics · Lahore 2024", 180, 39.0,
            listOf(pk.parhlou.student.data.BlueprintSection("MCQ", 15, 15, 1.0), pk.parhlou.student.data.BlueprintSection("Short", 18, 12, 2.0)), official = true)
        rule.setContent { ParhlouTheme(dark = night) {
            androidx.compose.runtime.CompositionLocalProvider(pk.parhlou.student.ui.components.LocalPremium provides
                pk.parhlou.student.ui.components.PremiumAccess(pk.parhlou.student.domain.Premium.Access(premium = true, paywall = true), credits = credits)) {
                pk.parhlou.student.ui.screens.AssessmentStartScreen(bp, "", {}, {}, {}, {}, onStartOnPaper = {},
                    cost = { onPaper -> pk.parhlou.student.domain.Credits.paper(12, onPaper, made = false) { k -> pk.parhlou.student.domain.Credits.TYPICAL.getValue(k) } })
            }
        } }
        rule.waitUntil(5_000) { tag("start-on-paper") }
        rule.onNodeWithTag("start-on-paper").performScrollTo().performClick()
        rule.waitUntil(5_000) { tag("credit-confirm") }; save("p10-sit-paper-cost")
    }
}
