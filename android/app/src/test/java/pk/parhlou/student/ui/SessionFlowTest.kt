package pk.parhlou.student.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.domain.SessionItem
import pk.parhlou.student.domain.SessionResult
import pk.parhlou.student.ui.session.SessionScreen
import pk.parhlou.student.ui.theme.ParhlouTheme

/** A guided session from start to the celebration screen, the way a student plays it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class SessionFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun playSessionWithAMistakeThenFinish() {
        val items = listOf(
            SessionItem.Mcq("m1", "n-1", "What is 2 + 2?", listOf("3", "4"), 1, listOf("No, 3 is too small.", "Yes, 2 + 2 = 4.")),
            SessionItem.TypeAnswer("t1", "n-1", "Solve x + 1 = 4", "x = 3", listOf(listOf("x=3")), "Subtract 1", listOf("x = 4 − 1", "x = 3")),
            SessionItem.OrderSteps("o1", "n-1", "Simplify", listOf("first", "second"), listOf(1, 0)),
        )
        var result: SessionResult? = null
        val answered = mutableListOf<Pair<String, Boolean>>()
        rule.setContent { ParhlouTheme {
            SessionScreen("Unit 1 · Test", items, soundOn = false, onToggleSound = {}, onAnswered = { i, ok -> answered += i.id to ok },
                onAskTutor = {}, onFinished = { result = it }, onClose = {})
        } }
        rule.onNodeWithTag("session-check").assertIsNotEnabled()
        // 1. Wrong MCQ answer: red panel with the right answer and the reason.
        rule.onNodeWithTag("option-0").performClick()
        rule.onNodeWithTag("session-check").assertIsEnabled().performClick()
        rule.onNodeWithTag("feedback-wrong").assertExists()
        rule.onNodeWithText("Correct answer: 4").assertExists()
        rule.onNodeWithTag("session-continue").performClick()
        // 2. Typed answer, right.
        rule.onNodeWithTag("session-input").performTextInput("x = 3")
        rule.onNodeWithTag("session-check").performClick()
        rule.onNodeWithTag("feedback-right").assertExists()
        rule.onNodeWithTag("session-continue").performClick()
        // 3. Put the steps in order.
        rule.onNodeWithTag("step-bank-0").performClick()
        rule.onNodeWithTag("step-bank-1").performClick()
        rule.onNodeWithTag("session-check").performClick()
        rule.onNodeWithTag("feedback-right").assertExists()
        rule.onNodeWithTag("session-continue").performClick()
        // 4. The missed MCQ comes back; answer it right.
        rule.onNodeWithText("What is 2 + 2?").assertExists()
        rule.onNodeWithTag("option-1").performClick()
        rule.onNodeWithTag("session-check").performClick()
        rule.onNodeWithTag("session-continue").performClick()
        // Celebration screen.
        rule.onNodeWithTag("session-done").assertExists()
        rule.onNodeWithTag("session-finish").performClick()
        val r = result!!
        assertEquals(2, r.firstTryCorrect)
        assertEquals(listOf("m1"), r.missed.map { it.id })
        assertTrue(r.xp >= 25)
        assertEquals(listOf("m1" to false, "t1" to true, "o1" to true), answered)
    }
}
