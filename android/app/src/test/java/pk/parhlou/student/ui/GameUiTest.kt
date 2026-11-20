package pk.parhlou.student.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.domain.Achievement
import pk.parhlou.student.domain.Game
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.ui.components.AchievementGrid
import pk.parhlou.student.ui.components.ChestCard
import pk.parhlou.student.ui.theme.ParhlouTheme

/** The chest, the achievements gallery and celebration pop-ups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun openingTheChestShowsTheBonus() {
        var opened = 0
        rule.setContent { ParhlouTheme { ChestCard(ready = true, openedToday = false, onOpen = { opened++; 30 }) } }
        rule.onNodeWithTag("open-chest").performClick()
        rule.onNodeWithTag("chest-won").assertExists()
        rule.onNodeWithText("+30 XP!").assertExists()
        assertEquals(1, opened)
        assert(rule.onAllNodes(hasTestTag("open-chest")).fetchSemanticsNodes().isEmpty()) { "the chest can't be opened twice" }
    }

    @Test fun aLockedChestCantBeOpened() {
        rule.setContent { ParhlouTheme { ChestCard(ready = false, openedToday = false, onOpen = { 30 }) } }
        assert(rule.onAllNodes(hasTestTag("open-chest")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Daily chest").assertExists()
    }

    @Test fun lockedAchievementsShowProgress() {
        rule.setContent { ParhlouTheme { AchievementGrid(listOf(
            Achievement("a", "🔥", "On a roll", "Study 3 days in a row", 3, 3),
            Achievement("b", "📘", "Bookworm", "Finish 10 lessons", 4, 10))) } }
        rule.onNodeWithText("On a roll").assertExists()
        rule.onNodeWithText("4/10").assertExists()
    }

    @Test fun aLevelUpIsCelebratedOnceAndRemembered() {
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking { store.clearAll(); store.saveDraft("celebrated", Game.Seen(2, 0, emptySet(), "").encode()) }
        val r = RewardSummary(400, 0, 120, List(7) { 0 }, 1, true, 0, 3, 200, emptyList())
        rule.setContent { ParhlouTheme { CelebrationHost(store, r, emptyList(), questsDone = false) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Level 3!").assertExists()
        rule.onNodeWithTag("celebration-ok").performClick()
        rule.waitUntil(5_000) { runBlocking { Game.Seen.decode(store.draft("celebrated").first())?.level } == 3 }
        rule.waitForIdle()
        assert(rule.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().isEmpty())
    }
}
