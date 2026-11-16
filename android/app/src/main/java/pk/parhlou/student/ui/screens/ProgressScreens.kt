package pk.parhlou.student.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.UstadSays
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Every badge, in the order a student usually earns them. */
private val ALL_BADGES = listOf(
    "first-session" to "First steps", "streak-3" to "On a roll", "streak-7" to "Week warrior",
    "board-paper" to "Board ready", "xp-1000" to "1,000 XP", "streak-30" to "Unstoppable",
)

/** XP this week as a bar chart, level progress, streak and badges. */
@Composable
fun ProgressScreen(r: RewardSummary, daysToExam: Int?, examName: String, onBack: () -> Unit) {
    ParhlouScreen("Your progress", onBack = onBack) {
        UstadSays(when {
            r.goalMet -> "Daily goal done! Every day like this adds marks in the exam."
            r.streakDays >= 3 -> "${r.streakDays} days in a row. Consistency is how toppers are made."
            else -> "Small daily sessions beat one long night before the exam. Let's build your streak."
        })
        if (daysToExam != null) Sheet(Modifier.testTag("countdown")) {
            Text("$daysToExam days", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Muted("until your ${examName.ifBlank { "board exam" }} (approximate start; the board announces the exact date).")
        }
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Level ${r.level}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${r.totalXp} XP", style = MaterialTheme.typography.titleMedium)
            }
            val start = 50 * r.level * (r.level - 1)
            val end = 50 * (r.level + 1) * r.level
            LinearProgressIndicator(progress = { ((r.totalXp - start).toFloat() / (end - start)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp))
            Muted("${r.toNextLevel} XP to level ${r.level + 1}")
        }
        Sheet(Modifier.testTag("week-chart")) {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            WeekChart(r.week, r.dailyGoalXp)
            Muted("Daily goal: ${r.dailyGoalXp} XP · streak ${r.streakDays} day${if (r.streakDays == 1) "" else "s"}" +
                if (r.freezes > 0) " · ${r.freezes} streak freeze${if (r.freezes > 1) "s" else ""} saved" else "")
        }
        Sheet {
            Text("Badges", style = MaterialTheme.typography.titleLarge)
            val earned = r.badges.map { it.id }.toSet()
            ALL_BADGES.forEach { (id, title) ->
                val have = id in earned
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, if (have) Color(0xFFF4B400) else MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (have) "🏅" else "🔒", style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Muted(r.badges.firstOrNull { it.id == id }?.description ?: hint(id))
                        }
                    }
                }
            }
        }
    }
}

private fun hint(id: String) = when (id) {
    "first-session" -> "Finish your first guided session"
    "streak-3" -> "Study 3 days in a row"
    "streak-7" -> "Study 7 days in a row"
    "streak-30" -> "Study 30 days in a row"
    "board-paper" -> "Sit a full 75-mark board paper"
    else -> "Earn 1,000 XP"
}

@Composable
private fun WeekChart(week: List<Int>, goal: Int) {
    val bar = MaterialTheme.colorScheme.primary
    val today = MaterialTheme.colorScheme.primaryContainer
    val goalLine = Color(0xFFF4B400)
    val max = maxOf(goal, week.maxOrNull() ?: 0, 1)
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {
        contentDescription = days.zip(week).joinToString { (d, xp) -> "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} $xp XP" }
    }) {
        val slot = size.width / 7f
        val w = slot * 0.55f
        week.forEachIndexed { i, xp ->
            val h = size.height * xp / max
            drawRoundRect(if (i == 6) today else bar, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(8f, 8f))
        }
        val gy = size.height - size.height * goal / max
        drawLine(goalLine, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 4f)
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center, fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal)
                Text("${week[i]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
