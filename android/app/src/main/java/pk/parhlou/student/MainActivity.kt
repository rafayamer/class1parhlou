package pk.parhlou.student

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.LunaGateway
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.ui.ParhlouApp
import pk.parhlou.student.ui.components.ProvideMotion
import pk.parhlou.student.ui.theme.ParhlouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = LocalStore(applicationContext)
        setContent {
            ParhlouTheme { ProvideMotion {
                // Offline sample repository; replaced by the Supabase-backed repository in a later milestone.
                val repo = remember { FakeRepository() }
                val ai = remember {
                    val demo = DemoBrain(repo)
                    if (BuildConfig.PARHLOU_AI_URL.isBlank()) demo
                    else LunaGateway(BuildConfig.PARHLOU_AI_URL, BuildConfig.SUPABASE_ANON_KEY, { store.installId() }, demo)
                }
                ParhlouApp(repo = repo, store = store, ai = ai)
            } }
        }
    }
}
