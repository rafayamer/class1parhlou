package pk.parhlou.student.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.BlueprintSection
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.ui.screens.AssessmentStartScreen
import pk.parhlou.student.ui.theme.ParhlouTheme

/** A paper can be sat three times; then it locks and the best score stands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class AttemptsTest {
    @get:Rule val rule = createComposeRule()
    private val bp = PaperBlueprint("p", "Board paper A", 150, 15.0, listOf(BlueprintSection("MCQ", 15, 15, 1.0)), official = true)
    private val submittedLog = "S:1\nX:2"

    @Test fun secondTryOffersASitAgainWithTriesLeft() {
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, submittedLog, {}, {}, {}, {}, attemptsUsed = 1, bestPercent = 60) } }
        rule.onNodeWithText("🔁 Try 2 of 3 · best 60%").assertExists()
        rule.onNodeWithText("Sit this paper again (2 tries left)").assertExists()
    }

    @Test fun afterThreeAttemptsThePaperLocks() {
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, submittedLog, {}, {}, {}, {}, attemptsUsed = 3, bestPercent = 81) } }
        rule.onNodeWithTag("attempts-used").assertExists()
        rule.onNodeWithText("Sit this paper again", substring = true).assertDoesNotExist()
        rule.onNodeWithText("See your result").assertExists()
    }

    @Test fun lockedEvenBeforeStartingWhenAllTriesAreUsed() {
        rule.setContent { ParhlouTheme { AssessmentStartScreen(bp, "", {}, {}, {}, {}, attemptsUsed = 3, bestPercent = 81) } }
        rule.onNodeWithText("Start in the app").assertDoesNotExist()
    }
}
