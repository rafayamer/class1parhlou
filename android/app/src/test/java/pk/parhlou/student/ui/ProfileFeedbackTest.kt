package pk.parhlou.student.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.Allowance
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.ui.screens.ProfileScreen
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Profile: what's left of this month with Ustaad, in plain counts, and feedback that reaches the owner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class ProfileFeedbackTest {
    @get:Rule val rule = createComposeRule()
    private val state = OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 30, completed = true)

    @Test fun thisMonthShowsCountsNotCreditsAndFeedbackIsSent() {
        val sent = mutableListOf<String>()
        rule.setContent { ParhlouTheme { ProfileScreen(FakeRepository(), state, ProgressStore(), {}, {},
            allowance = { Allowance(840, "2026-12-01", mapOf("chat" to 840), usedUsd = 0.9, capUsd = 3.0) },
            onSendFeedback = { sent += it; true }) } }
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("allowance")).fetchSemanticsNodes().isNotEmpty() }
        // One simple energy bar: what's left and when it refills. No money, no credits.
        rule.onNodeWithText("70% left this month").assertExists()
        rule.onNodeWithText("refills on 1 December", substring = true).assertExists()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("credit", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("$", substring = true)).fetchSemanticsNodes().size)

        rule.onNodeWithText("Send feedback").performScrollTo().performClick()
        rule.onNodeWithTag("feedback-text").performTextInput("Please add a dark mode for papers")
        rule.onNodeWithTag("feedback-send").performScrollTo().performClick()
        rule.waitUntil(3_000) { sent.isNotEmpty() }
        assertEquals(listOf("Please add a dark mode for papers"), sent)
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Thank you!", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
