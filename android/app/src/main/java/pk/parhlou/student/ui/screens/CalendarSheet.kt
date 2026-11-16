package pk.parhlou.student.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.LoggedEvidence
import pk.parhlou.student.ui.components.Muted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One thing the student did, for the calendar: when, what and how it went. */
data class StudyEntry(val at: Long, val icon: String, val title: String, val detail: String)

/** Turns the progress log into calendar entries; repeated "opened" and topic marks are left out (they're noise). */
fun studyEntries(history: List<LoggedEvidence>, titleOf: (String) -> String): List<StudyEntry> = history.mapNotNull { e ->
    val name = titleOf(e.key)
    when (val ev = e.evidence) {
        is Evidence.SessionCompleted -> StudyEntry(e.at, "⚡", "Lesson session", "+${ev.xp} XP")
        is Evidence.QuizCompleted -> StudyEntry(e.at, "✅", "Quiz · $name", "${ev.firstTryPercent}% first try")
        is Evidence.ExamMarked -> StudyEntry(e.at, "📝", "Paper · $name", "${ev.percent}%")
        is Evidence.DemoCheckedExam -> StudyEntry(e.at, "📝", "Paper · $name", "${ev.percent}%")
        Evidence.ExamPendingReview -> StudyEntry(e.at, "📝", "Paper · $name", "handed in")
        Evidence.LessonCompleted -> StudyEntry(e.at, "📖", "Lesson · $name", "finished")
        Evidence.WrittenSubmitted -> StudyEntry(e.at, "✍️", "Written practice · $name", "submitted")
        Evidence.ReadingChecked -> StudyEntry(e.at, "📚", "Book reading · $name", "done")
        else -> null
    }
}.sortedBy { it.at }

/**
 * The study calendar: a month at a time, a dot on every day with practice, and below it what was done on the chosen day,
 * with the time of each lesson, quiz and paper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCalendarSheet(entries: List<StudyEntry>, initialDay: Long, onDismiss: () -> Unit) {
    var day by remember { mutableLongStateOf(startOfDay(initialDay)) }
    var month by remember { mutableLongStateOf(startOfMonth(initialDay)) }
    val active = remember(entries) { entries.map { startOfDay(it.at) }.toSet() }
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val dayTitle = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().testTag("study-calendar"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = addMonths(month, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(monthName.format(Date(month)), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = addMonths(month, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            val cal = Calendar.getInstance().apply { timeInMillis = month }
            val first = cal.firstDayOfWeek
            val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val lead = (cal.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
            val names = (0..6).map { i -> Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (first - 1 + i) % 7 + 1) } }
                .map { SimpleDateFormat("EEEEE", Locale.getDefault()).format(it.time) }
            Row(Modifier.fillMaxWidth()) { names.forEach { Text(it, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) } }
            val cells = lead + days
            for (week in 0 until (cells + 6) / 7) Row(Modifier.fillMaxWidth()) {
                for (d in 0..6) {
                    val n = week * 7 + d - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val start = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, n) }.timeInMillis
                            val chosen = start == day
                            val today = start == startOfDay(System.currentTimeMillis())
                            Column(Modifier.size(42.dp).clip(CircleShape)
                                .background(if (chosen) MaterialTheme.colorScheme.primary else if (today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { day = start }.semantics { contentDescription = dayTitle.format(Date(start)) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$n", style = MaterialTheme.typography.labelLarge,
                                    color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                Box(Modifier.size(5.dp).clip(CircleShape).background(
                                    if (start in active) (if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary) else Color.Transparent))
                            }
                        }
                    }
                }
            }
            Text(dayTitle.format(Date(day)), style = MaterialTheme.typography.titleMedium)
            val list = entries.filter { startOfDay(it.at) == day }
            if (list.isEmpty()) Muted(if (day > System.currentTimeMillis()) "Nothing yet. This day is still ahead." else "No study recorded on this day.")
            list.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(e.icon, style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                        Text(e.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(time.format(Date(e.at)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}

private fun startOfDay(t: Long) = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun startOfMonth(t: Long) = Calendar.getInstance().apply { timeInMillis = startOfDay(t); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
private fun addMonths(t: Long, n: Int) = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.MONTH, n) }.timeInMillis
