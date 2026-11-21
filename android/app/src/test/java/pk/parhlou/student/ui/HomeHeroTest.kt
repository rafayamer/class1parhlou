package pk.parhlou.student.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.screens.HomeHero
import pk.parhlou.student.ui.theme.ParhlouTheme

/** The top of Home greets the student by name, and tapping Ustaad gives a new study tip each time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeHeroTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tappingUstaadGivesStudyTips() {
        rule.setContent { ParhlouTheme { HomeHero("Ali", "Ready to study today?", UstadMood.HAPPY) } }
        rule.onNodeWithText("Ali").assertExists()
        rule.onNodeWithText("Ready to study today?").assertExists()
        rule.onNodeWithTag("hero-ustaad").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ten minutes every day beats three hours on Sunday.").assertExists()
        rule.onNodeWithTag("hero-ustaad").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Got one wrong? That's exactly where your next marks are hiding.").assertExists()
    }

    @Test fun askUstaadIsPartOfTheTopCard() {
        var asked = 0
        rule.setContent { ParhlouTheme { HomeHero("Ali", "Ready to study today?", UstadMood.HAPPY, onAsk = { asked++ }) } }
        rule.onNodeWithTag("home-ask").performClick()
        rule.onNodeWithText("Scan question").performClick()
        assert(asked == 2) { "the ask bar and the quick starts open Ask Ustaad" }
    }
}
