package pk.parhlou.student.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.domain.LearningState
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.data.Subject
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.domain.Rewards
import pk.parhlou.student.ui.components.LocalReducedMotion
import kotlin.math.sin

private val Gold = Color(0xFFF4B400)
private val Flame = Color(0xFFFF7A00)

/**
 * The section to practise today: a weak section first, then the first examined section not yet strong, then the
 * weakest examined section.
 */
fun todaysModule(subjects: List<Subject>, progress: ProgressStore): Module? {
    val all = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.filter { it.inExam && (c.kit?.inExam != false) } } }
        .ifEmpty { subjects.flatMap { s -> s.chapters.flatMap { it.modules } } }
    return all.firstOrNull { progress.state(it.id) == LearningState.REPAIR_REQUIRED }
        ?: all.firstOrNull { Rewards.sectionLevel(progress.evidence(it.id)) < 2 }
        ?: all.minByOrNull { Rewards.sectionLevel(progress.evidence(it.id)) }
}

/** Streak, today's XP against the goal, and level, at the top of Home. */
@Composable
fun RewardBar(r: RewardSummary, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Open your progress", onClick = onClick).testTag("reward-bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("🔥", "${r.streakDays}", if (r.streakDays == 1) "day streak" else "day streak", if (r.studiedToday) Flame else MaterialTheme.colorScheme.outline,
            "${r.streakDays} day streak" + if (r.freezes > 0) ", ${r.freezes} streak freeze" + (if (r.freezes > 1) "s" else "") else "", Modifier.weight(1f))
        Chip("⚡", "${r.xpToday}/${r.dailyGoalXp}", if (r.goalMet) "goal met!" else "XP today", Gold,
            "${r.xpToday} of ${r.dailyGoalXp} XP today", Modifier.weight(1f))
        Chip("⭐", "Lv ${r.level}", "${r.toNextLevel} XP to go", MaterialTheme.colorScheme.primary, "Level ${r.level}", Modifier.weight(1f))
    }
}

@Composable
private fun Chip(icon: String, value: String, label: String, color: Color, description: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surface, modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$icon $value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * The learning path: every unit's sections as round steps on a winding road. The section for today bounces;
 * each ring fills to 5 levels as the section is mastered. Any step can be opened.
 */
@Composable
fun LearningPath(subjects: List<Subject>, progress: ProgressStore, current: String?, onStart: (String) -> Unit, onKit: (String) -> Unit) {
    val chapters = subjects.flatMap { it.chapters }
    val currentChapter = chapters.firstOrNull { c -> c.modules.any { it.id == current } }?.id
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) chapters else {
        val i = chapters.indexOfFirst { it.id == currentChapter }.coerceAtLeast(0)
        chapters.drop(i).take(1)
    }
    Column(Modifier.fillMaxWidth().testTag("learning-path"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your path", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (showAll) "Show less" else "All units") }
        }
        var n = 0
        shown.forEach { c ->
            UnitHeader(c, onKit)
            c.modules.forEach { m ->
                PathNode(m, Rewards.sectionLevel(progress.evidence(m.id)), m.id == current, n++, inExam = m.inExam && c.kit?.inExam != false) { onStart(m.id) }
            }
        }
    }
}

@Composable
private fun UnitHeader(c: Chapter, onKit: (String) -> Unit) {
    val out = c.kit?.inExam == false
    Surface(shape = RoundedCornerShape(20.dp),
        color = if (out) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val fg = if (out) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                Text("Unit ${c.number}" + if (out) " · not in this year's exam" else "", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
                Text(c.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.kit != null) TextButton(onClick = { onKit(c.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Exam kit", color = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
            }
        }
    }
}

@Composable
private fun PathNode(m: Module, level: Int, current: Boolean, index: Int, inExam: Boolean, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val bounce = pk.parhlou.student.ui.components.rememberBob(reduced || !current, 700, times = 5)
    val shift = (sin(index * 0.9) * 70).dp
    val done = level >= 2
    val fill = when {
        !inExam -> MaterialTheme.colorScheme.surfaceVariant
        current -> MaterialTheme.colorScheme.primaryContainer
        done -> Gold
        else -> MaterialTheme.colorScheme.surface
    }
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (current) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(x = shift).graphicsLayer { translationY = -bounce * 6f * density }) {
            Text("Start", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.offset(x = shift).size(76.dp).graphicsLayer { translationY = -bounce * 6f * density }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, -90f, 360f, false, style = st)
                if (level > 0) drawArc(ring, -90f, 360f * level / 5f, false, style = st)
            }
            Surface(onClick = onClick, shape = CircleShape, color = fill, shadowElevation = if (current) 6.dp else 1.dp,
                modifier = Modifier.size(60.dp).testTag("path-${m.id}").semantics {
                    contentDescription = "${m.title}, level $level of 5" + if (current) ", today's session" else ""
                }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(when { done -> Icons.Filled.Check; current -> Icons.Filled.PlayArrow; else -> Icons.Filled.Star }, contentDescription = null,
                        tint = if (done && inExam) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(m.title.replace(Regex("^\\d+(\\.\\d+)*\\s*"), ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(x = shift).width(150.dp))
    }
}
