package pk.parhlou.student.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Home
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
 * Real taps (touch input, not semantic actions) on the bottom bar and the Home screen. Each check looks for text only
 * that screen has, so a tap that does nothing fails.
 */
@RunWith(RobolectricTestRunner::class)
// A real phone-sized screen, so buttons are not hidden under the floating bar after scrolling.
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class NavigationTest {
    @get:Rule val rule = createComposeRule()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }

    @Before fun setUp() = runBlocking {
        store.clearAll()
        store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun onHome() = exists("Do this now")
    private val screens = mapOf(
        "home" to { onHome() },
        "subjects" to { !onHome() && exists("Book edition not yet verified") || (!onHome() && exists("My book")) },
        "tests" to { !onHome() && exists("Readiness check") },
        "profile" to { !onHome() && exists("Clear this phone") },
    )

    private fun tap(route: String) {
        rule.onNodeWithTag("tab-$route").performTouchInput { click() }
        rule.waitUntil(3_000) { screens.getValue(route)() }
        rule.onNodeWithTag("tab-$route").assertIsSelected()
    }

    /** Scrolls [text] into view clear of the floating tab bar, like a student would, then taps it. */
    private fun liftAboveBar(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val rootHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val barTop = rootHeight - with(rule.density) { 120.dp.toPx() }
        if (bounds.bottom > barTop) {
            val lift = bounds.bottom - barTop
            rule.onRoot().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - lift), 400) }
            rule.waitForIdle()
        }
    }

    private fun tapAboveBar(text: String) {
        val node = rule.onNodeWithText(text).performScrollTo()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val rootHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val barTop = rootHeight - with(rule.density) { 120.dp.toPx() }
        if (bounds.bottom > barTop) {
            val lift = bounds.bottom - barTop
            rule.onRoot().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - lift), 400) }
            rule.waitForIdle()
        }
        rule.onNodeWithText(text).performTouchInput { click() }
    }

    private fun launch() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { onHome() }
    }

    @Test fun everyTabOpensItsSectionFromEveryOther() {
        launch()
        for (route in listOf("subjects", "home", "tests", "home", "profile", "home", "subjects", "tests", "profile", "subjects", "home", "home")) tap(route)
    }

    @Test fun homeTabReturnsFromADeeperScreen() {
        launch()
        tap("subjects")
        rule.onAllNodesWithText("Mathematics · Class 9").onFirst().performTouchInput { click() }
        rule.waitUntil(3_000) { exists("Rational and irrational numbers") }
        rule.onNodeWithContentDescription("Back").performTouchInput { click() }
        rule.waitUntil(3_000) { exists("Edition") || exists("My book") }
        tap("home")
    }

    @Test fun homeScreenButtonsRespondToTaps() {
        val checks = listOf(
            "Continue" to "Sample — demo content",
            "Ask Ustaad" to "Demo tutor",
            "Journey" to "Journey",
            "Solutions" to "Step-by-step solutions appear here",
            "Full plan" to "Today's plan",
        )
        launch()
        for ((button, opens) in checks) {
            tapAboveBar(button)
            rule.waitUntil(3_000) { !onHome() && exists(opens) }
            rule.onNodeWithContentDescription("Back").performTouchInput { click() }
            rule.waitUntil(3_000) { onHome() }
        }
    }

    /** Regression: taps inside the glass bar must reach the tabs. */
    @Test fun barAloneReceivesTaps() {
        var picked = -1
        rule.setContent {
            ParhlouTheme {
                val backdrop = com.styropyr0.prismal.sources.rememberPrismalGlassLayer()
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                        pk.parhlou.student.ui.components.GlassTabBar(
                            listOf(pk.parhlou.student.ui.components.NavItem("home", "Home", androidx.compose.material.icons.Icons.Filled.Home),
                                   pk.parhlou.student.ui.components.NavItem("tests", "Papers", androidx.compose.material.icons.Icons.Filled.Home)),
                            0, { picked = it }, backdrop)
                    }
                }
            }
        }
        rule.onNodeWithTag("tab-tests").performTouchInput { click() }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(1, picked)
    }

    @Test fun weekCalendarDaysRespondToTaps() {
        runBlocking {
            store.replaceProgress(pk.parhlou.student.domain.EvidenceCodec.line(System.currentTimeMillis(), "m-1-1", pk.parhlou.student.domain.Evidence.QuizCompleted(80)))
        }
        launch()
        rule.onNodeWithText("Done so far: 1 quiz", substring = true).performScrollTo().assertExists()
        // Another day of this week: earlier days say what was done, later days that they're coming up.
        val fmt = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        val other = (cal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_MONTH, if (cal.get(java.util.Calendar.DAY_OF_WEEK) == cal.firstDayOfWeek) 1 else -1)
        }
        // The calendar sits low on Home: lift it clear of the floating tab bar before tapping, as a finger would.
        liftAboveBar(rule.onNodeWithContentDescription(fmt.format(other.time)).performScrollTo())
        rule.onNodeWithContentDescription(fmt.format(other.time)).performTouchInput { click() }
        rule.waitUntil(3_000) { exists("No practice recorded on this day.") || exists("Coming up.") }
        rule.onNodeWithContentDescription(fmt.format(cal.time)).performTouchInput { click() }
        liftAboveBar(rule.onNodeWithText("Open today's plan").performScrollTo())
        rule.onNodeWithText("Open today's plan").performTouchInput { click() }
        rule.waitUntil(3_000) { exists("Today's plan") && !onHome() }
    }

    private fun tapCamera() {
        rule.onNodeWithContentDescription("Scan answer sheets").performTouchInput { click() }
        rule.waitForIdle()
    }

    @Test fun homeCameraWithLockedPapersExplainsTheReadinessCheck() {
        launch()
        tapCamera()
        rule.waitUntil(3_000) { exists("Pass the readiness check first") }
        rule.onNodeWithText("Take the readiness check").performTouchInput { click() }
        rule.waitUntil(3_000) { !exists("Pass the readiness check first") && !onHome() }
    }

    @Test fun homeCameraStartsAPaperWithTheScannerOpen() {
        runBlocking { store.setReadinessPassed(true) }
        launch()
        tapCamera()
        rule.waitUntil(3_000) { exists("Start and scan") }
        rule.onNodeWithText("Start and scan").performTouchInput { click() }
        // The scanner is open inside the paper; the camera isn't allowed in tests, so the gallery is offered.
        rule.waitUntil(5_000) { exists("Scan answer pages") && exists("Add from gallery") }
        runBlocking { assertEquals(pk.parhlou.student.data.SampleContent.CHAPTER_TEST, store.activeExam.first()) }
        // Closing the scanner shows the paper, whose timer is running.
        rule.onNodeWithContentDescription("Close scanner").performTouchInput { click() }
        rule.waitUntil(3_000) { exists("Invigilator on") }
    }

    @Test fun homeCameraReopensThePaperInProgress() {
        runBlocking {
            store.setReadinessPassed(true)
            store.appendExamEvent(pk.parhlou.student.data.SampleContent.CHAPTER_TEST, pk.parhlou.student.domain.ExamEventType.STARTED, System.currentTimeMillis())
        }
        launch()
        tapCamera()
        rule.waitUntil(5_000) { exists("Scan answer pages") && !exists("Start and scan") }
    }
}
