package pk.parhlou.student.ui

import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.Account
import pk.parhlou.student.data.AuthClient
import pk.parhlou.student.data.AuthResult
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.HttpCall
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.data.Session
import pk.parhlou.student.data.SyncClient
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.ui.theme.ParhlouTheme

/** A tiny fake of the Supabase endpoints the app uses. */
class FakeSupabase {
    val calls = mutableListOf<Triple<String, String, String?>>()
    var events = JSONArray()
    var settings: JSONObject? = null
    var marks = JSONArray()
    var refreshStatus = 200
    var online = true
    val http: HttpCall = { method, url, headers, body ->
        calls += Triple(method, url.substringAfter("https://sb.test"), body)
        if (!online) null else route(method, url.substringAfter("https://sb.test"), headers, body)
    }
    private fun session(token: String) = JSONObject().put("access_token", token).put("refresh_token", "r-$token").put("expires_in", 3600)
        .put("user", JSONObject().put("id", "user-1").put("email", "ali@example.test")).toString()

    private fun route(method: String, path: String, headers: Map<String, String>, body: String?): Pair<Int, String> = when {
        path.startsWith("/auth/v1/otp") -> 200 to "{}"
        path == "/auth/v1/verify" -> if (JSONObject(body!!).getString("token") == "123456") 200 to session("tok-1") else 403 to "{}"
        path == "/auth/v1/user" -> if (headers["Authorization"] == "Bearer link-token") 200 to """{"id":"user-1","email":"ali@example.test"}""" else 401 to "{}"
        path.startsWith("/auth/v1/token") -> refreshStatus to (if (refreshStatus == 200) session("tok-2") else "{}")
        path == "/auth/v1/logout" -> 204 to ""
        path == "/rest/v1/rpc/register_learner" -> 200 to "\"tenant\""
        path.startsWith("/rest/v1/learner_settings") && method == "GET" ->
            200 to JSONArray().apply { settings?.let { put(JSONObject().put("settings", it)) } }.toString()
        path.startsWith("/rest/v1/learner_settings") -> { settings = JSONObject(body!!).getJSONObject("settings"); 201 to "" }
        path.startsWith("/rest/v1/learner_events") && method == "GET" -> 200 to events.toString()
        path.startsWith("/rest/v1/learner_events") -> { JSONArray(body!!).let { a -> for (i in 0 until a.length()) events.put(a.get(i)) }; 201 to "" }
        path.startsWith("/rest/v1/ai_marks") -> 200 to marks.toString()
        path == "/rest/v1/rpc/export_my_data" -> 200 to """{"user_id":"user-1","events":[]}"""
        path == "/rest/v1/rpc/delete_my_account" -> 204 to ""
        else -> 404 to "{}"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountTest {
    @get:Rule val rule = createComposeRule()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }
    private val fake = FakeSupabase()
    private var clock = 1_000_000L
    private val auth by lazy { AuthClient("https://sb.test", "pk", store, fake.http) { clock } }
    private val sync by lazy { SyncClient("https://sb.test", "pk", auth, store, fake.http) }

    @Before fun reset() = runBlocking { store.clearAll() }

    @Test fun codeSignInRefreshAndSignOut() = runBlocking {
        assertTrue(auth.sendLink("not-an-email") is AuthResult.Failed)
        assertEquals(AuthResult.Sent, auth.sendLink("Ali@Example.test"))
        assertTrue(fake.calls.last().second.contains("redirect_to=pk.parhlou.student"))
        assertTrue(auth.verifyCode("ali@example.test", "000000") is AuthResult.Failed)
        assertTrue(auth.verifyCode("ali@example.test", "123456") is AuthResult.SignedIn)
        assertEquals("tok-1", auth.accessToken())
        clock += 3_600_000                                   // expired: refreshed automatically
        assertEquals("tok-2", auth.accessToken())
        fake.refreshStatus = 400; clock += 3_600_000         // refresh refused: signed out
        assertNull(auth.accessToken())
        assertNull(store.session.first())
    }

    @Test fun signInLinkFromTheEmail() = runBlocking {
        val ok = auth.handleRedirect(Uri.parse("pk.parhlou.student://auth#access_token=link-token&refresh_token=r&expires_in=3600&type=magiclink"))
        assertTrue(ok is AuthResult.SignedIn)
        assertEquals("user-1", store.session.first()!!.userId)
        val expired = auth.handleRedirect(Uri.parse("pk.parhlou.student://auth#error=access_denied&error_description=Email+link+is+invalid+or+has+expired"))
        assertEquals("Email link is invalid or has expired", (expired as AuthResult.Failed).message)
        assertNull(auth.handleRedirect(Uri.parse("https://example.test/other")))
    }

    @Test fun googleSignInComesBackThroughTheSameLink() = runBlocking {
        assertEquals("https://sb.test/auth/v1/authorize?provider=google&redirect_to=pk.parhlou.student%3A%2F%2Fauth", auth.googleSignInUrl())
        val r = auth.handleRedirect(Uri.parse("pk.parhlou.student://auth#access_token=link-token&refresh_token=r2&expires_in=3600&provider_token=x"))
        assertTrue(r is AuthResult.SignedIn)
    }

    @Test fun progressAndSettingsSyncAndRestoreOnANewPhone() = runBlocking {
        auth.verifyCode("ali@example.test", "123456")
        store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.TEST_SERIES, 4, 45, completed = true, name = "Ali"))
        store.appendProgress("10|m-1-1|Q:80")
        store.appendProgress("11|paper:x|E:90")              // a verified mark: never uploaded by the phone
        store.appendProgress("12|m-1-1|W")
        assertTrue(sync.pushSettings(store.onboarding.first()))
        assertTrue(sync.pushEvents())
        assertEquals(listOf("Q:80", "W"), (0 until fake.events.length()).map { fake.events.getJSONObject(it).getString("code") })
        assertTrue(sync.pushEvents())                        // nothing new: no second upload
        assertEquals(2, fake.events.length())

        // A new phone: empty, signs in, gets settings and history back.
        store.clearAll()
        auth.verifyCode("ali@example.test", "123456")
        val progress = ProgressStore(store)
        val restored = Account(auth, sync, store).afterSignIn(progress)
        assertTrue(restored.completed)
        assertEquals("Ali", restored.name)
        assertEquals(Pathway.TEST_SERIES, restored.pathway)
        assertEquals(listOf(Evidence.QuizCompleted(80), Evidence.WrittenSubmitted), progress.evidence("m-1-1"))
    }

    @Test fun nothingIsSentWhenSignedOutOrOffline() = runBlocking {
        assertFalse(sync.register("Ali"))
        assertTrue(fake.calls.isEmpty())
        auth.verifyCode("ali@example.test", "123456")
        fake.online = false
        store.appendProgress("10|m-1-1|Q:80")
        assertFalse(sync.pushEvents())
        assertEquals(0, store.syncedLines())                 // retried next time
    }

    @Test fun signInScreenSendsTheLinkAndOffersGuestUse() {
        val account = Account(auth, sync, store)
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store, account = account) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Get started")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("Save your progress").assertExists()
        rule.onNodeWithTag("signin-name").performTextInput("Ali")
        rule.onNodeWithTag("signin-email").performTextInput("ali@example.test")
        rule.onNodeWithText("Email me a sign-in link").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Check your email")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("signin-code").performScrollTo().performTextInput("123456")
        rule.onNodeWithText("Sign in with code").performScrollTo().performClick()
        // Signed in: registered on the server and sent on to set-up.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Set up", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(fake.calls.any { it.second == "/rest/v1/rpc/register_learner" && it.third!!.contains("Ali") })
    }
}
