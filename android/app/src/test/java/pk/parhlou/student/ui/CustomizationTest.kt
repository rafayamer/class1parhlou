package pk.parhlou.student.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.AppSettings
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalAppSettings
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.ui.theme.ParhlouTheme

/** What the owner sets in the owner panel shows up in the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomizationTest {
    @get:Rule val rule = createComposeRule()

    private fun open(settings: AppSettings, build: Int = 0, waitFor: String = "tab-home", refresh: () -> Unit = {}) {
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true, name = "Ali"))
        }
        rule.setContent { ParhlouTheme { CompositionLocalProvider(LocalAppSettings provides settings) { ParhlouApp(FakeRepository(), store, currentBuild = build, refreshSettings = refresh) } } }
        rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag(waitFor)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theAnnouncementShowsAndCanBeClosed() {
        open(AppSettings.parse("""{"banner":{"id":"b1","emoji":"📢","title":"Board exams in 30 days","message":"Do one Guess Paper this week."},"homeMessage":"Welcome back, champ!"}"""))
        rule.onNodeWithText("Board exams in 30 days").assertExists()
        rule.onNodeWithText("Welcome back, champ!").assertExists()
        rule.onNodeWithTag("announcement-close").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("announcement")).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun switchedOffFeaturesDisappear() {
        open(AppSettings.parse("""{"features":{"league":{"on":false},"askUstaad":{"on":false}}}"""))
        assert(rule.onAllNodes(hasTestTag("tab-league")).fetchSemanticsNodes().isEmpty())
        assert(rule.onAllNodes(hasTestTag("home-ask")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun maintenanceCoversTheApp() {
        var checks = 0
        open(AppSettings.parse("""{"maintenance":true,"maintenanceMessage":"Back at 6 pm."}"""), waitFor = "maintenance", refresh = { checks++ })
        rule.onNodeWithTag("maintenance").assertExists()
        rule.onNodeWithText("Back at 6 pm.").assertExists()
        rule.onNodeWithText("Check again").performClick()
        assert(checks == 1) { "Check again fetches the owner's settings again" }
    }

    @Test fun oldBuildsMustUpdate() {
        open(AppSettings.parse("""{"minBuild":80}"""), build = 70, waitFor = "must-update")
        rule.onNodeWithTag("must-update").assertExists()
    }
}
