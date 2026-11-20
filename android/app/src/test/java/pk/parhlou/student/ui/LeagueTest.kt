package pk.parhlou.student.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.SocialClient
import pk.parhlou.student.domain.Social
import pk.parhlou.student.ui.theme.ParhlouTheme

/** The League tab: quests and streak, this week's board, friends by code with 👏 nudges, and class groups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeagueTest {
    @get:Rule val rule = createComposeRule()

    private class FakeSocial : SocialClient(null) {
        val friendList = mutableListOf(Social.Friend("BILAL1", "Bilal", "🦁", 80, 2))
        var nudged: String? = null
        var postedXp = -1
        override suspend fun me() = Social.Me("AYE123", "Ayesha", "🦉", 1)
        override suspend fun postWeek(xp: Int, streak: Int): Social.Board {
            postedXp = xp
            return Social.Board(1, 3, listOf(Social.Row(1, "Bilal", "🦁", 80, false), Social.Row(2, "Ayesha", "🦉", xp, true)))
        }
        override suspend fun friends() = friendList.toList()
        override suspend fun nudges() = listOf(Social.Nudge("Sara", "🐼"))
        override suspend fun groups() = emptyList<Social.Group>()
        override suspend fun addFriend(code: String): Pair<List<Social.Friend>?, String?> =
            if (code == "SARA22") { friendList += Social.Friend("SARA22", "Sara", "🐼", 10, 1); friendList.toList() to null } else null to "CODE_NOT_FOUND"
        override suspend fun nudge(code: String): Boolean { nudged = code; return true }
    }

    private fun open(social: SocialClient?): LocalStore {
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
        }
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store, socialClient = social) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("tab-league")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tab-league").performClick()
        rule.waitForIdle()
        return store
    }

    @Test fun guestsSeeQuestsAndAreAskedToSignIn() {
        open(null)
        rule.onNodeWithTag("player-card").assertIsDisplayed()
        rule.onNodeWithTag("quests").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("league-sign-in").performScrollTo().assertIsDisplayed()
    }

    @Test fun boardFriendsAndNudges() {
        val social = FakeSocial()
        open(social)
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("league-board")).fetchSemanticsNodes().isNotEmpty() }
        assert(social.postedXp == 0)
        rule.onNodeWithText("Silver League", substring = true).assertExists()
        rule.onNodeWithText("Sara cheered you on", substring = true).assertExists()
        rule.onNodeWithTag("friend-code").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Your code: AYE123").assertExists()

        rule.onNodeWithTag("friend-code-input").performScrollTo().performTextInput("zzzzzz")
        rule.onNodeWithTag("add-friend").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("No one has that code", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag("nudge-BILAL1").performScrollTo().performClick()
        rule.waitUntil(5_000) { social.nudged == "BILAL1" }
    }

    @Test fun addingAFriendByCode() {
        val social = FakeSocial()
        open(social)
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("friend-code-input")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("friend-code-input").performScrollTo().performTextInput("sara22")
        rule.onNodeWithTag("add-friend").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("nudge-SARA22")).fetchSemanticsNodes().isNotEmpty() }
    }
}
