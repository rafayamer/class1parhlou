package pk.parhlou.student.ui

import android.net.ConnectivityManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Without internet the whole app is covered by "Connect to the internet". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTest {
    @get:Rule val rule = createComposeRule()

    @Test fun offlineCoversTheApp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).setDefaultNetworkActive(false)
        shadowOf(cm).setActiveNetworkInfo(null)
        val store = LocalStore(ctx)
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
        }
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store, requireInternet = true) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("offline-cover")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun onlineShowsNoCover() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        val store = LocalStore(ctx)
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
        }
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store, requireInternet = true) } }
        rule.waitForIdle()
        assert(rule.onAllNodes(hasTestTag("offline-cover")).fetchSemanticsNodes().isEmpty())
    }
}
