package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.UstaadPrefs
import pk.parhlou.student.domain.StudentProfile
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.UstadSays

/** "My Ustaad": what Ask Ustaad knows about the student, and how it explains things to them. Everything is editable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyUstaadScreen(profile: StudentProfile, onChange: (UstaadPrefs) -> Unit, onBack: () -> Unit) {
    val p = profile.prefs
    var confirm by remember { mutableStateOf(false) }
    ParhlouScreen("My Ustaad", onBack = onBack) {
        UstadSays("I learn how you study, so I can explain things the way that works for you. Only a short summary is shared with me, never your email.")
        Sheet {
            Text("What Ustaad knows", style = MaterialTheme.typography.titleLarge)
            Text("${profile.name.ifBlank { "You" }} · ${profile.classLine}", style = MaterialTheme.typography.bodyLarge)
            profile.exam?.let { Muted("Exam: $it") }
            Muted("Level ${profile.level} · ${profile.streak}-day streak")
            Text("Strong topics", style = MaterialTheme.typography.titleSmall)
            Muted(profile.strong.take(6).joinToString(" · ").ifBlank { "Not enough practice yet. Sessions and tests will show your strengths." })
            Text("Needs more practice", style = MaterialTheme.typography.titleSmall)
            Muted(profile.weak.take(6).joinToString(" · ").ifBlank { "No weak topics right now. Well done!" })
        }
        Sheet {
            Text("How Ustaad talks to you", style = MaterialTheme.typography.titleLarge)
            Text("Language", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UstaadPrefs.LANGUAGES.forEach { l ->
                    FilterChip(selected = p.language == l, onClick = { onChange(p.copy(language = l)) }, label = { Text(l) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("lang-$l"))
                }
            }
            Text("Explanations", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UstaadPrefs.STYLES.forEach { s ->
                    FilterChip(selected = p.style == s, onClick = { onChange(p.copy(style = s)) }, label = { Text(s) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("Your target in every subject", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(70, 80, 90, 100).forEach { t ->
                    FilterChip(selected = p.targetMarks == t, onClick = { onChange(p.copy(targetMarks = if (p.targetMarks == t) 0 else t)) },
                        label = { Text("$t%") }, modifier = Modifier.heightIn(min = 48.dp).testTag("target-$t"))
                }
            }
        }
        TextButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("ustaad-forget")) { Text("Clear what Ustaad knows about me") }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start fresh with Ustaad?") },
            text = { Text("Ustaad will stop using your past practice to personalise answers. Your progress, streak and marks are not deleted.") },
            confirmButton = { TextButton(onClick = { confirm = false; onChange(p.copy(forgetBefore = System.currentTimeMillis())) }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
