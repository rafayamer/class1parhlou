package pk.parhlou.student.ui

import androidx.compose.runtime.CompositionLocalProvider
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
            PremiumGate(true, "Unit 3 is Premium", "Chapters 1 and 2 are free.") { androidx.compose.material3.Text("Secret lesson") }
        } } }
        rule.onNodeWithTag("premium-lock").assertExists()
        rule.onNodeWithText("Unit 3 is Premium").assertExists()
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

    private fun openApp(aiTries: Int = 0): pk.parhlou.student.data.ParhlouRepository {
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ali"))
            store.setReadinessPassed(true)
            if (aiTries > 0) store.saveDraft("ai_tries", "$aiTries")
        }
        val root = File(System.getProperty("user.dir")!!).let { if (File(it, "../../content").exists()) File(it, "../../content") else File(it, "../content") }
        val repo = ContentBundle.repository(JSONObject(File(root, "class9-maths/bundle.json").readText()), 1, FakeRepository())
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store, paywall = true) } }
        rule.waitUntil(20_000) { exists("tab-home") }
        return repo
    }

    @Test fun chapterThreeIsLockedForFreeStudents() {
        val repo = openApp()
        val chapters = repo.subject(FakeRepository.MATHS_9)!!.chapters
        val third = chapters[2]
        rule.onNodeWithTag("home-premium").performScrollTo().assertExists()
        rule.onNodeWithTag("tab-subjects").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Mathematics · Class 9")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mathematics · Class 9").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("learning-path") }
        // The book shows every unit (or a button to show them all).
        if (rule.onAllNodes(androidx.compose.ui.test.hasText("All units")).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("All units").performClick()
        rule.waitForIdle()
        // Units 1 and 2 are open; unit 3 carries a Premium tag, and opening one of its sections shows the lock.
        assert(!exists("unit-locked-${chapters[0].id}") && !exists("unit-locked-${chapters[1].id}"))
        rule.onNodeWithTag("unit-locked-${third.id}").performScrollTo().assertExists()
        rule.onNodeWithTag("path-${third.modules.first().id}").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("premium-lock") }
        rule.onNodeWithText("${third.word} 3 is Premium").assertExists()
    }

    @Test fun askUstaadLocksAfterThreeFreeTries() {
        openApp(aiTries = 3)
        rule.onNodeWithTag("home-ask").performClick()
        rule.waitUntil(5_000) { exists("premium-lock") }
        rule.onNodeWithText("Ask Ustaad is Premium").assertExists()
    }

    @Test fun askUstaadCountsTheFreeTriesLeft() {
        openApp(aiTries = 1)
        rule.onNodeWithTag("home-ask").performClick()
        rule.waitUntil(5_000) { exists("tutor-tries") }
        rule.onNodeWithText("2 free questions left").assertExists()
    }
}
