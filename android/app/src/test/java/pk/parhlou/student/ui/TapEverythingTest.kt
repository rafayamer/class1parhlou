package pk.parhlou.student.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.ui.theme.ParhlouTheme

/**
 * "No dead buttons": on each main tab, every tappable element is tapped in turn and must change something on screen
 * (open a screen, expand, select, show an answer…). Actions that leave the app or erase data are skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class TapEverythingTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }

    /** Taps that leave the app (camera, share, sign-in mail) or erase data, so they can't be judged by the screen. */
    private val skip = listOf("Scan answer sheets", "Clear this phone", "Delete my account", "Download my data", "Sign out",
        "Sign in", "Mute sounds", "Turn sounds on")

    @Before fun setUp() = runBlocking {
        store.clearAll()
        store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 30, completed = true))
        store.setReadinessPassed(true)
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun snapshot(): String = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString("|") { n -> describe(n) + (n.config.getOrNull(SemanticsProperties.Selected)?.toString() ?: "") +
            (n.config.getOrNull(SemanticsProperties.ToggleableState)?.toString() ?: "") }

    private fun describe(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "") +
            (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: "") +
            (n.config.getOrNull(SemanticsProperties.TestTag) ?: "")

    private fun clickables(route: String): List<String> = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        .filter { !it.config.contains(SemanticsProperties.Disabled) }
        .map { describeMerged(it) }
        .filter { it.isNotBlank() && skip.none { s -> it.contains(s) } && !it.endsWith("tab-$route") } // the tab you're on
        .distinct()

    private fun describeMerged(n: SemanticsNode): String {
        val own = describe(n)
        if (own.isNotBlank()) return own
        return n.children.joinToString(" ") { describe(it) }.trim()
    }

    private fun hasTabs() = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("tab-home")).fetchSemanticsNodes().isNotEmpty()

    private fun goTab(route: String) {
        // Back out of any screen the last tap opened (as a student would), until the tab bar is there again.
        var tries = 0
        while (!hasTabs() && tries++ < 6) {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
        }
        rule.onNodeWithTag("tab-$route").performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun crawl(route: String, dead: MutableList<String>) {
        goTab(route)
        val labels = clickables(route)
        for (label in labels) {
            goTab(route)
            val node = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .fetchSemanticsNodes().firstOrNull { describeMerged(it) == label } ?: continue
            val before = snapshot()
            val app = org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())
            while (app.nextStartedActivity != null) { /* clear */ }
            rule.onAllNodes(SemanticsMatcher("id ${node.id}") { it.id == node.id })[0].performSemanticsAction(SemanticsActions.OnClick)
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
            // Opening a web page or the share sheet counts as doing something.
            if (app.nextStartedActivity != null) continue
            if (snapshot() == before) {
                // Navigation can still be settling: give it a moment more before calling the tap dead.
                runCatching { rule.waitUntil(3_000) { snapshot() != before } }
            }
            if (snapshot() == before) dead += "$route: \"$label\""
        }
    }

    @Test fun everyTappableElementDoesSomething() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
        val dead = mutableListOf<String>()
        for (route in listOf("home", "subjects", "tests", "profile")) crawl(route, dead)
        assertTrue("Taps that changed nothing:\n" + dead.joinToString("\n"), dead.isEmpty())
    }
}
