package pk.parhlou.student.ui

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.ui.theme.ParhlouTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {
    @get:Rule val rule = createComposeRule()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }

    @Before fun resetLocalData() = runBlocking { store.clearAll() }

    @Test
    fun onboardingThenHomeThenQuiz() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }

        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Get started") }
        rule.onNodeWithText("Get started").performClick()

        // Step 1: cannot continue without acknowledging the notice.
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("I have read this and want to continue").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()

        // Step 2: Federal Board is shown but disabled; Punjab Class 9 English Maths is selectable.
        rule.onNodeWithText("Punjab Boards").performScrollTo().performClick()
        rule.onNodeWithText("Class 9").performScrollTo().performClick()
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNodeWithText("Mathematics").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().assertIsEnabled().performClick()

        // Step 3 and 4
        rule.onNodeWithText("Complete Academy").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Finish setup").performScrollTo().performClick()

        // Home: one Continue action pointing at the first sample module.
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Do this now") }
        rule.onNodeWithText("Start: Rational and irrational numbers").assertExists()
        rule.onNodeWithText("Continue").performScrollTo().performClick()

        // Module: five-stage rail, honest empty video state, quiz with rationales.
        rule.onNodeWithText("Sample — demo content").assertExists()
        rule.onNodeWithText("No teacher video has been linked", substring = true).assertExists()
        rule.onNodeWithText("2 Quiz").performScrollTo().performClick()
        rule.onNodeWithText("Which of these numbers is irrational?").assertExists()
        rule.onNodeWithText("√2").performScrollTo().performClick()
        rule.onNodeWithText("Correct.").assertExists()
    }

    @Test
    fun testSeriesPapersStayLockedUntilReadinessIsPassed() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Get started") }
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("I have read this and want to continue").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Punjab Boards").performScrollTo().performClick()
        rule.onNodeWithText("Class 9").performScrollTo().performClick()
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNodeWithText("Mathematics").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Test Series").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Finish setup").performScrollTo().performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Take your readiness check") }
        rule.onNodeWithText("Continue").performClick()
        // The chapter test is locked behind the readiness check; the full paper has no questions yet. Both are disabled.
        val locked = rule.onAllNodes(androidx.compose.ui.test.hasText("Locked until you pass the readiness check.", substring = true))
        assertEquals(1, locked.fetchSemanticsNodes().size)
        locked.assertAll(androidx.compose.ui.test.isNotEnabled())
        // Quarter, half and full mocks are listed but can't be built from Unit 1 alone.
        val notReady = rule.onAllNodes(androidx.compose.ui.test.hasText("Needs questions for Units", substring = true))
        assertEquals(3, notReady.fetchSemanticsNodes().size)
        notReady.assertAll(androidx.compose.ui.test.isNotEnabled())
        assertEquals(4, rule.onAllNodes(androidx.compose.ui.test.hasText("Practice pattern — not official", substring = true))
            .fetchSemanticsNodes().size)
    }

    @Test
    fun aMissedTopicIsRepairedFirstFromTheHomePlan() {
        val missed = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        runBlocking {
            store.saveOnboarding(pk.parhlou.student.data.OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9),
                pk.parhlou.student.data.Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
            store.replaceProgress(pk.parhlou.student.domain.EvidenceCodec.line(missed, "topic:n-surds", pk.parhlou.student.domain.Evidence.TopicMissed))
        }
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Do this now") }

        rule.onNodeWithText("Fix: Simplifying square roots").performScrollTo().assertExists()
        rule.onNodeWithText("Full plan").performScrollTo().performClick()
        rule.onNodeWithText("You missed this once recently, last 2 days ago.", substring = true).assertExists()
        rule.onNodeWithText("See 1 weak topic").performScrollTo().performClick()
        rule.onNodeWithText("Missed once · Fix this").performClick()
        // The tutor opens on that topic.
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("square root", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
