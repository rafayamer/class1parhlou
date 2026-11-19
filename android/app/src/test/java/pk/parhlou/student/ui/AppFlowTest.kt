package pk.parhlou.student.ui

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
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

        // Step 1: about you and the privacy notice; Continue stays off until both are done.
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        aboutYou()

        // Step 2: Federal Board is shown but disabled; Punjab Class 9 English Maths is selectable.
        rule.onNodeWithText("Punjab Boards").performScrollTo().performClick()
        rule.onNodeWithText("Class 9").performScrollTo().performClick()
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNodeWithText("Mathematics").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().assertIsEnabled().performClick()

        // Step 3: how you'll study, and when.
        rule.onNodeWithText("Complete Academy").performScrollTo().performClick()
        rule.onNodeWithText("Finish setup").performScrollTo().performClick()

        // Home: one Continue action pointing at the first sample module.
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Do this now") }
        rule.onNodeWithText("Start: Rational and irrational numbers").assertExists()
        rule.onNodeWithText("Continue").performScrollTo().performClick()

        // Module: five-stage rail, honest empty video state, quiz with rationales.
        rule.onNodeWithText("2 Quiz").performScrollTo().performClick()
        rule.onNodeWithText("Which of these numbers is irrational?").assertExists()
        rule.onNodeWithText("√2").performScrollTo().performClick()
        rule.onNodeWithText("Correct.").assertExists()
    }

    @Test
    fun papersOpenWithoutALockAndOfferPracticeTests() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Get started") }
        rule.onNodeWithText("Get started").performClick()
        aboutYou()
        rule.onNodeWithText("Punjab Boards").performScrollTo().performClick()
        rule.onNodeWithText("Class 9").performScrollTo().performClick()
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNodeWithText("Mathematics").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Test Series").performScrollTo().performClick()
        rule.onNodeWithText("Finish setup").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("tab-tests")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tab-tests").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTextExists("✏️ Practice test") }
        // No lock in front of papers any more; practice tests start disabled until a chapter is picked.
        rule.onNodeWithTag("readiness").assertDoesNotExist()
        rule.onNodeWithTag("practice-quick").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("practice-ch-1").performScrollTo().performClick()
        rule.onNodeWithTag("practice-quick").performScrollTo().assertIsEnabled()
        // No developer wording on papers.
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("not official", substring = true)).fetchSemanticsNodes().size)
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
        rule.waitUntil(3_000) { rule.onAllNodesWithTextExists("Today's plan") }
        rule.onNodeWithText("You missed this once recently, last 2 days ago.", substring = true).assertExists()
        rule.onNodeWithText("See 1 weak topic").performScrollTo().performClick()
        rule.onNodeWithText("Missed once · Fix this").performClick()
        // The tutor opens on that topic.
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("square root", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Sign-up step 2: full name and email are needed (Continue stays off until they're valid); phone is optional. */
    private fun aboutYou() {
        rule.waitUntil(3_000) { rule.onAllNodesWithTextExists("About you") }
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithTag("profile-name").performTextInput("Ayesha Khan")
        rule.onNodeWithTag("profile-email").performTextInput("ayesha@")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithTag("profile-email").performTextInput("example.com")
        rule.onNodeWithTag("profile-phone").performTextInput("12345")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithTag("profile-phone").performTextClearance()
        // The privacy notice must be accepted too.
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("I have read this and want to continue").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
    }
}
