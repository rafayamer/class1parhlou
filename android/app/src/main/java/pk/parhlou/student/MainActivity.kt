package pk.parhlou.student

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import pk.parhlou.student.data.Account
import pk.parhlou.student.data.AuthClient
import pk.parhlou.student.data.AuthResult
import pk.parhlou.student.data.SyncClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.LunaGateway
import pk.parhlou.student.data.BundleRepository
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.data.RemoteContent
import pk.parhlou.student.ui.ParhlouApp
import pk.parhlou.student.ui.components.LocalUpdateOffer
import pk.parhlou.student.ui.components.ProvideMotion
import pk.parhlou.student.ui.components.UpdateOffer
import pk.parhlou.student.ui.theme.ParhlouTheme
import pk.parhlou.student.update.AppUpdater
import pk.parhlou.student.update.UpdateInfo

class MainActivity : ComponentActivity() {
    private var account: Account? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSignInLink(intent)
    }

    /** A tapped sign-in link: store the session, or show why it didn't work. */
    private fun handleSignInLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val a = account ?: return
        lifecycleScope.launch {
            when (val r = a.auth.handleRedirect(uri)) {
                is AuthResult.Failed -> a.error.value = r.message
                is AuthResult.SignedIn -> a.error.value = null
                else -> Unit
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = LocalStore(applicationContext)
        val packed = FakeRepository()   // content packed in the app: used offline and until the first download
        val supabaseUrl = BuildConfig.PARHLOU_AI_URL.substringBefore("/functions/", "")
        val remote = RemoteContent(applicationContext, supabaseUrl, BuildConfig.SUPABASE_ANON_KEY, packed)
        val updater = AppUpdater(applicationContext, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_CODE)
        if (supabaseUrl.isNotBlank()) {
            val auth = AuthClient(supabaseUrl, BuildConfig.SUPABASE_ANON_KEY, store)
            account = Account(auth, SyncClient(supabaseUrl, BuildConfig.SUPABASE_ANON_KEY, auth, store), store)
        }
        if (savedInstanceState == null) handleSignInLink(intent)
        val account = account
        setContent {
            ParhlouTheme { ProvideMotion {
                val scope = rememberCoroutineScope()
                var repo by remember { mutableStateOf<ParhlouRepository>(packed) }
                var update by remember { mutableStateOf<UpdateInfo?>(null) }
                var installing by remember { mutableStateOf(false) }

                // Live content and update check: on open and every time the app comes back to the front.
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    scope.launch {
                        val subject = FakeRepository.MATHS_9
                        if (repo === packed) remote.cached(subject)?.let { repo = it }
                        remote.refresh(subject, (repo as? BundleRepository)?.version)?.let { repo = it }
                    }
                    scope.launch { update = updater.check() }
                }

                val ai = remember(repo) {
                    val demo = DemoBrain(repo)
                    if (BuildConfig.PARHLOU_AI_URL.isBlank()) demo
                    else LunaGateway(BuildConfig.PARHLOU_AI_URL, BuildConfig.SUPABASE_ANON_KEY, { store.installId() }, demo,
                        accessToken = { account?.auth?.accessToken() })
                }
                val offer = update?.let { info ->
                    UpdateOffer(info.build, info.publishedOn, installing) {
                        if (!installing) scope.launch { installing = true; updater.install(info); installing = false }
                    }
                }
                CompositionLocalProvider(LocalUpdateOffer provides offer) {
                    ParhlouApp(repo = repo, store = store, ai = ai, account = account,
                        versionLabel = "Parhlou ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                }
            } }
        }
    }
}
