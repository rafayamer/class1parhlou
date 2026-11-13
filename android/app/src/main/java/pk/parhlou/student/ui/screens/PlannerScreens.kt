package pk.parhlou.student.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import pk.parhlou.student.domain.PlanKind
import pk.parhlou.student.domain.PlanTask
import pk.parhlou.student.domain.WeakTopic
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.TaskRow

fun PlanKind.icon(): ImageVector = when (this) {
    PlanKind.REPAIR -> Icons.Filled.Build
    PlanKind.REVIEW -> Icons.Filled.Refresh
    PlanKind.LEARN -> Icons.Filled.PlayCircle
    PlanKind.PAPER -> Icons.Filled.Quiz
    PlanKind.READINESS -> Icons.Filled.TaskAlt
}

/** Today's plan: each task says why it was chosen (PRD §10). */
@Composable
fun PlannerScreen(
    tasks: List<PlanTask>,
    minutesPerDay: Int,
    weakTopics: List<WeakTopic>,
    onTask: (PlanTask) -> Unit,
    onWeakTopics: () -> Unit,
    onBook: () -> Unit,
    onBack: () -> Unit,
) {
    ParhlouScreen("Today's plan", onBack = onBack) {
        Muted("Up to $minutesPerDay minutes today. Fixing mistakes comes first, then reviews that are due, then new work.")
        if (tasks.isEmpty()) {
            Sheet {
                Text("Nothing planned yet", style = MaterialTheme.typography.titleMedium)
                Muted("Start a module from your book. Your plan grows from your quizzes, practice and papers.")
            }
            SecondaryButton("Open my book", onClick = onBook)
        }
        tasks.forEach { t ->
            TaskRow(t.kind.icon(), t.title, "${t.reason} · ${t.minutes} min", onClick = { onTask(t) })
        }
        Text("Weak topics", style = MaterialTheme.typography.titleLarge)
        if (weakTopics.isEmpty()) Muted("No weak topics. They appear when you get a question wrong in a quiz, practice or paper.")
        else SecondaryButton("See ${weakTopics.size} weak ${if (weakTopics.size == 1) "topic" else "topics"}", onClick = onWeakTopics)
    }
}

/** Topics behind recent mistakes, most urgent first. "Fix this" opens the tutor on that topic. */
@Composable
fun WeakTopicsScreen(topics: List<WeakTopic>, onFix: (WeakTopic) -> Unit, onBack: () -> Unit) {
    ParhlouScreen("Weak topics", onBack = onBack) {
        Muted("From your real mistakes only. A topic leaves this list when you get it right again.")
        if (topics.isEmpty()) Muted("Nothing to fix right now.")
        topics.forEach { t ->
            TaskRow(Icons.AutoMirrored.Filled.MenuBook, t.title,
                "Missed ${if (t.recentMisses == 1) "once" else "${t.recentMisses} times"} · Fix this", onClick = { onFix(t) })
        }
    }
}
