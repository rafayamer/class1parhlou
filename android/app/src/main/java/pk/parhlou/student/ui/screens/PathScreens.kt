package pk.parhlou.student.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.blur
import pk.parhlou.student.data.word

import androidx.compose.foundation.layout.PaddingValues
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
import pk.parhlou.student.domain.Rewards
import pk.parhlou.student.ui.components.LocalReducedMotion
import kotlin.math.sin

private val Gold = Color(0xFFF4B400)

/**
 * The section to practise today: a weak section first, then the first examined section not yet strong, then the
 * weakest examined section.
 */
fun todaysModule(subjects: List<Subject>, progress: ProgressStore, day: Long = java.time.LocalDate.now().toEpochDay()): Module? {
    fun examModules(s: Subject) = s.chapters.flatMap { c -> c.modules.filter { it.inExam && (c.kit?.inExam != false) } }
        .ifEmpty { s.chapters.flatMap { it.modules } }
    val all = subjects.flatMap(::examModules)
    // Something to fix comes first, whatever the subject.
    all.firstOrNull { progress.state(it.id) == LearningState.REPAIR_REQUIRED }?.let { return it }
    // Otherwise the subjects take turns, one per day, so every subject moves forward (not Maths first until it's finished).
    fun level(m: Module) = Rewards.sectionLevel(progress.evidence(m.id), m.lesson != null)
    val open = subjects.filter { s -> examModules(s).any { level(it) < Rewards.LEARNED_LEVEL } }
    if (open.isNotEmpty()) {
        val s = open[Math.floorMod(day, open.size.toLong()).toInt()]
        return examModules(s).first { level(it) < Rewards.LEARNED_LEVEL }
    }
    return all.minByOrNull { level(it) }
}

/**
 * The learning path: every unit's sections as round steps on a winding road. The section for today bounces;
 * each ring fills to 5 levels as the section is mastered. Any step can be opened.
 */
@Composable
fun LearningPath(subjects: List<Subject>, progress: ProgressStore, current: String?, onStart: (String) -> Unit, onKit: (String) -> Unit, showAllDefault: Boolean = false) {
    val chapters = subjects.flatMap { it.chapters }
    val currentChapter = chapters.firstOrNull { c -> c.modules.any { it.id == current } }?.id
    var showAll by rememberSaveable { mutableStateOf(showAllDefault) }
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
        // Free students see Premium chapters blurred, with a tag; opening one shows what Premium unlocks.
        val access = pk.parhlou.student.ui.components.LocalPremium.current.access
        shown.forEach { c ->
            val locked = subjects.firstNotNullOfOrNull { s -> s.chapters.indexOf(c).takeIf { it >= 0 }?.let { !access.chapter(it, s.chapters.size) } } ?: false
            UnitHeader(c, onKit, locked)
            Column(if (locked) Modifier.blur(2.5.dp).graphicsLayer { alpha = 0.55f } else Modifier) {
                c.modules.forEach { m ->
                    PathNode(m, Rewards.sectionLevel(progress.evidence(m.id), m.lesson != null), m.id == current, n++, inExam = m.inExam && c.kit?.inExam != false, unit = c.number) { onStart(m.id) }
                }
            }
        }
    }
}

@Composable
private fun UnitHeader(c: Chapter, onKit: (String) -> Unit, locked: Boolean = false) {
    val out = c.kit?.inExam == false
    val (face, lip) = unitColour(c.number)
    pk.parhlou.student.ui.components.Chunky(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = if (out) pk.parhlou.student.ui.components.ChunkyDefaults.disabled() else pk.parhlou.student.ui.components.ChunkyColors(face, lip, Color.White),
        shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val fg = if (out) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                Text("${c.word} ${c.number}" + if (out) " · not in this year's exam" else "", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
                Text(c.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (locked) Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 2.dp).testTag("unit-locked-${c.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
                    Text(" Premium", style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
                }
            }
            if (c.kit != null) TextButton(onClick = { onKit(c.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Exam kit", color = if (out) MaterialTheme.colorScheme.primary else Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Each unit has its own colour on the path, so the road is easy to follow. */
@Composable
private fun unitColour(n: Int): Pair<Color, Color> = pk.parhlou.student.ui.theme.LocalBrand.current.units.let { it[(n - 1).mod(it.size)] }

@Composable
private fun PathNode(m: Module, level: Int, current: Boolean, index: Int, inExam: Boolean, unit: Int = 1, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val bounce = pk.parhlou.student.ui.components.rememberBob(reduced || !current, 700, times = 5)
    val shift = (sin(index * 0.9) * 70).dp
    val done = level >= Rewards.LEARNED_LEVEL
    val fill = when {
        !inExam -> MaterialTheme.colorScheme.surfaceVariant
        current -> MaterialTheme.colorScheme.primaryContainer
        done -> Gold
        else -> MaterialTheme.colorScheme.surface
    }
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (current) Surface(shape = RoundedCornerShape(12.dp), color = unitColour(unit).first,
            modifier = Modifier.offset(x = shift).graphicsLayer { translationY = -bounce * 6f * density }) {
            Text("Start", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.offset(x = shift).size(76.dp).graphicsLayer { translationY = -bounce * 6f * density }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, -90f, 360f, false, style = st)
                if (level > 0) drawArc(ring, -90f, 360f * level / 5f, false, style = st)
            }
            val brand = pk.parhlou.student.ui.theme.LocalBrand.current
            val (uFace, uLip) = unitColour(unit)
            val nodeColors = when {
                !inExam -> pk.parhlou.student.ui.components.ChunkyDefaults.disabled()
                done -> pk.parhlou.student.ui.components.ChunkyColors(brand.sun, brand.sunLip, brand.onSun)
                current -> pk.parhlou.student.ui.components.ChunkyColors(uFace, uLip, Color.White)
                else -> pk.parhlou.student.ui.components.ChunkyColors(MaterialTheme.colorScheme.surface, brand.lineLip, uFace, brand.line)
            }
            pk.parhlou.student.ui.components.Chunky(
                modifier = Modifier.width(60.dp).testTag("path-${m.id}").semantics(mergeDescendants = true) {
                    contentDescription = "${m.title}, level $level of 5" + if (current) ", today's session" else ""
                },
                colors = nodeColors, shape = CircleShape, lip = 5.dp, onClick = onClick, sound = pk.parhlou.student.ui.components.Sfx.SELECT,
                contentPadding = PaddingValues(0.dp), contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(55.dp), contentAlignment = Alignment.Center) {
                    Icon(when { done -> Icons.Filled.Check; current -> Icons.Filled.PlayArrow; else -> Icons.Filled.Star }, contentDescription = null,
                        modifier = Modifier.size(30.dp))
                }
            }
        }
        Text(m.title.replace(Regex("^\\d+(\\.\\d+)*\\s*"), ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(x = shift).width(150.dp))
        if (!inExam) Text("Not in 2027 exam", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.offset(x = shift).width(150.dp).testTag("not-in-exam-${m.id}"))
    }
}
