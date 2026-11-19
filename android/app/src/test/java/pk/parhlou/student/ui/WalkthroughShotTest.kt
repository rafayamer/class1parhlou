package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.PlanClient
import pk.parhlou.student.data.SocialClient
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.EvidenceCodec
import pk.parhlou.student.domain.Plans
import pk.parhlou.student.domain.Social
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/**
 * Walkthrough screenshots of the new screens (Home quests, Papers, League, Plus):
 * ./gradlew :app:testDebugUnitTest --tests '*WalkthroughShotTest*' -PscreenshotDir=/tmp/shots -PcourseBundle=/path/bundle.json
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w393dp-h852dp-xxhdpi")
class WalkthroughShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("parhlou.screenshotDir")
    private val bundle = System.getProperty("parhlou.courseBundle")

    private fun has(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val social = object : SocialClient(null) {
        val names = listOf("Bilal" to "🦁", "Sara" to "🐼", "Hamza" to "🦅", "Zainab" to "🦊", "Usman" to "🐯", "Fatima" to "📚", "Ali" to "🚀", "Hira" to "⭐")
        override suspend fun me() = Social.Me("AYE123", "Ayesha", "🦉", 2)
        override suspend fun postWeek(xp: Int, streak: Int) = Social.Board(2, 3,
            (names.mapIndexed { i, (n, a) -> Social.Row(0, n, a, 420 - i * 45, false) } + Social.Row(0, "Ayesha", "🦉", 260, true))
                .sortedByDescending { it.xp }.mapIndexed { i, r -> r.copy(rank = i + 1) })
        override suspend fun friends() = listOf(Social.Friend("BILAL1", "Bilal", "🦁", 420, 6), Social.Friend("SARA22", "Sara", "🐼", 375, 3))
        override suspend fun nudges() = listOf(Social.Nudge("Sara", "🐼"))
        override suspend fun groups() = listOf(Social.Group("BLUE9A", "9th Blue", listOf(
            Social.Row(1, "Bilal", "🦁", 420, false), Social.Row(2, "Ayesha", "🦉", 260, true), Social.Row(3, "Usman", "🐯", 240, false))))
    }
    private val plans = object : PlanClient(null) {
        override suspend fun info() = Plans.Info(false, null, 499,
            listOf(Plans.Wallet("jazzcash", "0300 1234567", "Parhlou"), Plans.Wallet("easypaisa", "0345 7654321", "Parhlou")), true, 0.5, 3.0, null)
    }

    @Test fun walkthrough() {
        assumeTrue(dir != null && bundle != null)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf("pb-9-math"), Pathway.COMPLETE_ACADEMY, 5, 30,
                completed = true, name = "Ayesha"))
            store.replaceProgress((0..3).joinToString("\n") { EvidenceCodec.line(now - it * 86_400_000L, "session", Evidence.SessionCompleted(60)) })
        }
        val repo = ContentBundle.repository(JSONObject(File(bundle!!).readText()), 1, FakeRepository())
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store, socialClient = social, planClient = plans) } }
        rule.waitUntil(8_000) { has("tab-home") }
        save("w01-home")
        rule.onNodeWithTag("home-quests").performScrollTo(); save("w02-home-quests")
        rule.onNodeWithTag("tab-tests").performClick(); rule.waitForIdle(); save("w03-papers")
        rule.onAllNodes(hasText("Practice test", substring = true))[0].performScrollTo(); save("w04-papers-practice")
        rule.onNodeWithTag("tab-league").performClick()
        rule.waitUntil(8_000) { has("league-board") }
        save("w05-league")
        rule.onNodeWithTag("league-board").performScrollTo(); save("w06-league-board")
        rule.onNodeWithTag("friends").performScrollTo(); save("w07-friends")
        rule.onNodeWithTag("groups").performScrollTo(); save("w08-groups")
        rule.onNodeWithTag("tab-profile").performClick(); rule.waitForIdle()
        rule.onAllNodes(hasText("Parhlou Plus"))[0].performScrollTo(); save("w09-profile-plus")
        rule.onAllNodes(hasText("Parhlou Plus"))[0].performClick()
        rule.waitUntil(8_000) { has("plan-pay") }
        save("w10-plus")
        rule.onNodeWithTag("plan-pay").performScrollTo(); save("w11-plus-pay")
    }
}
