package pk.parhlou.student.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
import pk.parhlou.student.data.word
import pk.parhlou.student.domain.Premium
import pk.parhlou.student.ui.components.LocalPremium
import pk.parhlou.student.ui.components.PremiumAccess
import pk.parhlou.student.ui.components.PremiumGate
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/** Premium content is blurred behind a lock for free students, with the price and an Unlock button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PremiumGateTest {
    @get:Rule val rule = createComposeRule()

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun lockedContentShowsThePriceAndUnlock() {
        var upgraded = 0
        val free = PremiumAccess(Premium.Access(premium = false, paywall = true), upgrade = { upgraded++ })
        rule.setContent { ParhlouTheme { CompositionLocalProvider(LocalPremium provides free) {
            PremiumGate(true, "Unit 4 is Premium", "Units 1–3 of this book are free.") { androidx.compose.material3.Text("Secret lesson") }
        } } }
        rule.onNodeWithTag("premium-lock").assertExists()
        rule.onNodeWithText("Unit 4 is Premium").assertExists()
        rule.onNodeWithText("Rs 3,999 a month").assertExists()
        assert(rule.onAllNodes(androidx.compose.ui.test.hasText("Secret lesson")).fetchSemanticsNodes().isEmpty()) { "the content behind the lock can't be read" }
        rule.onNodeWithTag("premium-upgrade").performClick()
        assert(upgraded == 1)
    }

    @Test fun unlockedContentShowsAsNormal() {
        rule.setContent { ParhlouTheme { PremiumGate(false, "x", "y") { androidx.compose.material3.Text("Open lesson") } } }
        rule.onNodeWithText("Open lesson").assertExists()
        assert(!exists("premium-lock"))
    }

    private class PremiumPlan : pk.parhlou.student.data.PlanClient(null) {
        override suspend fun info() = pk.parhlou.student.domain.Plans.Info(true, "2027-02-21", 3999, emptyList(), true, 0.0, 3.5, null)
    }

    private fun openApp(premium: Boolean = false): pk.parhlou.student.data.ParhlouRepository {
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ali"))
            store.setReadinessPassed(true)
        }
        val root = File(System.getProperty("user.dir")!!).let { if (File(it, "../../content").exists()) File(it, "../../content") else File(it, "../content") }
        val repo = ContentBundle.repository(JSONObject(File(root, "class9-maths/bundle.json").readText()), 1, FakeRepository())
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store, paywall = true, planClient = if (premium) PremiumPlan() else null) } }
        rule.waitUntil(20_000) { exists("tab-home") }
        return repo
    }

    private fun openBook() {
        rule.onNodeWithTag("tab-subjects").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Mathematics · Class 9")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mathematics · Class 9").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("learning-path") }
        // The book shows every unit (or a button to show them all).
        if (rule.onAllNodes(androidx.compose.ui.test.hasText("All units")).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("All units").performClick()
        rule.waitForIdle()
    }

    @Test fun theFirstQuarterOfTheBookIsFreeAndUnitFourIsLocked() {
        val repo = openApp()
        val chapters = repo.subject(FakeRepository.MATHS_9)!!.chapters
        assert(chapters.size == 13)
        val fourth = chapters[3]
        rule.onNodeWithTag("home-premium").performScrollTo().assertExists()
        openBook()
        // Units 1–3 (a quarter of 13) are open; unit 4 carries a Premium tag, and opening one of its sections shows the lock.
        assert((0..2).none { exists("unit-locked-${chapters[it].id}") })
        rule.onNodeWithTag("unit-locked-${fourth.id}").performScrollTo().assertExists()
        rule.onNodeWithTag("path-${fourth.modules.first().id}").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("premium-lock") }
        rule.onNodeWithText("${fourth.word} 4 is Premium").assertExists()
        rule.onNodeWithText("${fourth.word}s 1–3 of this book are free", substring = true).assertExists()
    }

    @Test fun askUstaadIsPremiumWithNoFreeTries() {
        openApp()
        rule.onNodeWithTag("home-ask").performClick()
        rule.waitUntil(5_000) { exists("premium-lock") }
        rule.onNodeWithText("Ask Ustaad is Premium").assertExists()
        assert(!exists("tutor-tries"))
    }

    @Test fun premiumOpensEveryUnitAndAskUstaad() {
        val repo = openApp(premium = true)
        val chapters = repo.subject(FakeRepository.MATHS_9)!!.chapters
        rule.waitUntil(5_000) { !exists("home-premium") }
        openBook()
        assert(chapters.none { exists("unit-locked-${it.id}") })
        rule.onNodeWithTag("path-${chapters[3].modules.first().id}").performScrollTo().performClick()
        rule.waitForIdle()
        assert(!exists("premium-lock"))
    }

    @Test fun makingAPaperShowsItsCreditsFirst() {
        var made = 0
        rule.setContent { ParhlouTheme { pk.parhlou.student.ui.screens.MakePaperScreen(make = { made++; null }, onOpen = {}, onBack = {},
            cost = pk.parhlou.student.domain.Credits.PaperCost(1, 9, 1)) } }
        rule.onNodeWithTag("make-paper-cost").assertExists()
        rule.onNodeWithText("⚡ 11 credits").assertExists()
        rule.waitForIdle()
        assert(made == 0) { "nothing is made before the student confirms" }
        rule.onNodeWithTag("make-paper-go").performScrollTo().performClick()
        rule.waitUntil(5_000) { made == 1 }
    }

    @Test fun askUstaadShowsCreditsAndStopsWhenTheyRunOut() {
        val ai = pk.parhlou.student.ai.DemoBrain(FakeRepository())
        var credits by androidx.compose.runtime.mutableStateOf(pk.parhlou.student.ai.Allowance(820, "2026-12-21", monthly = 1000))
        rule.setContent { ParhlouTheme { CompositionLocalProvider(LocalPremium provides PremiumAccess(Premium.Access(premium = true, paywall = true), credits = credits)) {
            pk.parhlou.student.ui.screens.TutorScreen(ai, setOf(FakeRepository.MATHS_9), null, examActive = false, onBack = {})
        } } }
        rule.onNodeWithTag("tutor-credits").assertExists()
        rule.onNodeWithText("820 credits left", substring = true).assertExists()
        credits = credits.copy(balance = 0)
        rule.waitForIdle()
        rule.onNodeWithTag("tutor-no-credits").assertExists()
        rule.onNodeWithText("They renew on 21 December", substring = true).assertExists()
        assert(!exists("tutor-input"))
    }
}
