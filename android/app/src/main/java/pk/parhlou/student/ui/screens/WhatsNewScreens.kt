package pk.parhlou.student.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet

/** One release's notes, shipped inside the app as assets/whats-new.json (newest first). */
data class ReleaseNotes(val title: String, val date: String, val changes: List<String>, val yourRequests: List<String>)

object WhatsNew {
    fun parse(json: String): List<ReleaseNotes> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            fun list(k: String) = o.optJSONArray(k)?.let { l -> List(l.length()) { l.getString(it) } }.orEmpty()
            ReleaseNotes(o.optString("title"), o.optString("date"), list("changes"), list("yourRequests"))
        }
    }.getOrDefault(emptyList())

    fun load(context: Context): List<ReleaseNotes> =
        runCatching { context.assets.open("whats-new.json").bufferedReader().use { it.readText() } }.map(::parse).getOrDefault(emptyList())
}

@Composable
fun WhatsNewScreen(notes: List<ReleaseNotes>, versionLabel: String, onDone: () -> Unit) {
    ParhlouScreen("What's new", onBack = onDone) {
        Muted(versionLabel)
        if (notes.isEmpty()) Muted("No notes for this build.")
        notes.forEachIndexed { i, n ->
            Sheet {
                Text(n.title, style = MaterialTheme.typography.titleLarge)
                if (n.date.isNotBlank()) Muted(n.date)
                n.changes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
                if (n.yourRequests.isNotEmpty()) {
                    Text("Your requests in this update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        n.yourRequests.forEach { Text("✓  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (i == 0) BigButton("Start learning", onClick = onDone)
        }
    }
}
