package pk.parhlou.student

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.ui.ParhlouApp
import pk.parhlou.student.ui.theme.ParhlouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = LocalStore(applicationContext)
        setContent {
            ParhlouTheme {
                // Offline sample repository; replaced by the Supabase-backed repository in a later milestone.
                ParhlouApp(repo = FakeRepository(), store = store)
            }
        }
    }
}
