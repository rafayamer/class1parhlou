package pk.parhlou.student.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.domain.Credits
import pk.parhlou.student.ui.screens.AssessmentStartScreen
import pk.parhlou.student.ui.theme.ParhlouTheme

/** A paper can be sat as often as the student likes; before it starts they see what checking it will cost, and confirm. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class AttemptsTest {
    @get:Rule val rule = createComposeRule()
    private val bp = PaperBlueprint("p", "Board paper A", 150, 15.0, listOf(BlueprintSection("MCQ", 15, 15, 1.0)), official = true)
    private val submittedLog = "S:1\nX:2"

    @Test fun noLimitOnTries() {
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, submittedLog, {}, {}, {}, {}, attemptsUsed = 3, bestPercent = 81) } }
        rule.onNodeWithText("🔁 Try 4 · best 81%").assertExists()
        rule.onNodeWithTag("sit-again").assertExists()
        rule.onNodeWithText("See your result").assertExists()
    }

    @Test fun aPaperSatThreeTimesCanStillBeStarted() {
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, "", {}, {}, {}, {}, attemptsUsed = 3, bestPercent = 81) } }
        rule.onNodeWithText("Start in the app").assertExists()
    }

    @Test fun startingShowsTheCreditsItWillUseAndWaitsForConfirm() {
        var started = 0
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, "", { started++ }, {}, {}, {},
            cost = { onPaper -> Credits.PaperCost(0, if (onPaper) 9 else 5, 1) }) } }
        rule.onNodeWithTag("paper-cost-hint").assertExists()
        rule.onNodeWithTag("start-in-app").performScrollTo().performClick()
        rule.onNodeWithTag("credit-confirm").assertExists()
        rule.onNodeWithTag("paper-cost-total").assertExists()
        rule.onNodeWithText("⚡ 6 credits").assertExists()
        assert(started == 0) { "nothing starts before the student confirms" }
        rule.onNodeWithTag("credit-confirm-ok").performClick()
        rule.waitForIdle()
        assert(started == 1)
        assert(rule.onAllNodes(hasTestTag("credit-confirm")).fetchSemanticsNodes().isEmpty())
    }
}
