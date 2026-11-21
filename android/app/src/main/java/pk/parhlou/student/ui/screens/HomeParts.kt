package pk.parhlou.student.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.ui.components.AccentButton
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.LocalReducedMotion
import pk.parhlou.student.ui.components.LocalSounds
import pk.parhlou.student.ui.components.Sfx
import pk.parhlou.student.ui.components.Ustad
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.countUp
import pk.parhlou.student.ui.theme.LocalBrand
import pk.parhlou.student.ui.theme.heroGradient
import kotlin.math.PI
import kotlin.math.sin

/** Short study tips Ustaad gives when he's tapped. */
private val TIPS = listOf(
    "Ten minutes every day beats three hours on Sunday.",
    "Got one wrong? That's exactly where your next marks are hiding.",
    "Write the formula first, then the numbers. Examiners give marks for it.",
    "Read the question twice. Board papers hide clues in the wording.",
    "Explain a topic to a friend. It's the fastest way to learn it.",
    "Past papers are gold: the same ideas come back every year.",
    "Stuck? Snap the question and ask me. That's what I'm here for.",
)

/**
 * Maths and science symbols drifting gently behind a card, like chalk dust in sunlight. They drift a few times and then
 * rest; with reduced motion they stay still.
 */
@Composable
fun FloatingGlyphs(color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val drift = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(3) { drift.snapTo(0f); drift.animateTo(1f, tween(7000, easing = LinearEasing)) }
    }
    val glyphs = remember {
        listOf(
            Triple("π", Offset(0.06f, 0.16f), 26), Triple("√x", Offset(0.80f, 0.10f), 22), Triple("x²", Offset(0.60f, 0.70f), 24),
            Triple("Σ", Offset(0.90f, 0.58f), 28), Triple("⚛", Offset(0.40f, 0.06f), 22), Triple("θ", Offset(0.22f, 0.78f), 22),
            Triple("∞", Offset(0.70f, 0.32f), 20), Triple("÷", Offset(0.12f, 0.50f), 20), Triple("Δ", Offset(0.50f, 0.42f), 18),
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        glyphs.forEachIndexed { i, (g, at, size) ->
            Text(
                g, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(maxWidth * at.x, maxHeight * at.y).graphicsLayer {
                    val phase = drift.value * 2f * PI.toFloat() + i
                    translationY = sin(phase) * 6.dp.toPx()
                    rotationZ = sin(phase * 0.7f) * 8f
                },
            )
        }
    }
}

/**
 * The top of Home: Ustaad, big, on an illustrated card, greeting the student with a speech bubble. Tapping him gets a new
 * study tip, a cheer and a click; the owner's message (or his mood line) shows first.
 */
@Composable
fun HomeHero(name: String, line: String, mood: UstadMood, modifier: Modifier = Modifier) {
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()
    var tip by remember { mutableIntStateOf(-1) }
    var cheering by remember { mutableStateOf(false) }
    val said = if (tip < 0) line else TIPS[tip % TIPS.size]
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(heroGradient()).testTag("home-hero"),
    ) {
        // Soft light shapes and drifting symbols behind him.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), size.width * 0.42f, Offset(size.width * 0.92f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.30f, Offset(size.width * 0.05f, size.height * 1.05f))
            drawCircle(Color(0xFFFFC93C).copy(alpha = 0.18f), size.width * 0.10f, Offset(size.width * 0.86f, size.height * 0.88f))
        }
        FloatingGlyphs(Color.White.copy(alpha = 0.16f), Modifier.matchParentSize())
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable(onClickLabel = "Get a study tip from Ustaad") {
                        tip += 1
                        sounds?.play(Sfx.SELECT)
                        cheering = true
                        scope.launch { delay(1400); cheering = false }
                    }
                    .testTag("hero-ustaad"),
                contentAlignment = Alignment.Center,
            ) { Ustad(104.dp, if (cheering) UstadMood.CHEER else mood) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(if (name.isBlank()) "Assalam-o-Alaikum!" else "Assalam-o-Alaikum,", style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f))
                    if (name.isNotBlank()) Text("$name!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, maxLines = 1)
                }
                // His speech bubble, with a little tail pointing at him.
                Box {
                    Canvas(Modifier.size(12.dp, 14.dp).offset((-8).dp, 12.dp)) {
                        drawPath(Path().apply { moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height); close() }, Color.White)
                    }
                    AnimatedContent(said, transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(160))
                    }, label = "tip") { text ->
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1D2B33),
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("hero-line"))
                    }
                }
                Text(if (tip < 0) "Tap Ustaad for a study tip" else "Tap again for another tip", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

/**
 * Today's goal at a glance: a big ring that fills with today's XP, the streak with this week's days lit up, and the level.
 * Tapping it opens the progress screen.
 */
@Composable
fun GoalCard(r: RewardSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val gold = Color(0xFFFFB020)
    val flame = Color(0xFFFF7A00)
    pk.parhlou.student.ui.components.Chunky(
        modifier = modifier.fillMaxWidth().testTag("reward-bar").semantics(mergeDescendants = true) {
            contentDescription = "${r.xpToday} of ${r.dailyGoalXp} XP today, ${r.streakDays} day streak, level ${r.level}. Open your progress"
        },
        shape = RoundedCornerShape(24.dp), onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val xp = countUp(r.xpToday)
            pk.parhlou.student.ui.components.ProgressRing((r.xpToday.toFloat() / r.dailyGoalXp.coerceAtLeast(1)).coerceIn(0f, 1f), 96.dp, 11.dp,
                if (r.goalMet) brand.success else gold, MaterialTheme.colorScheme.surfaceVariant) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (r.goalMet) "🎉" else "⚡", fontSize = 16.sp)
                    Text("$xp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("of ${r.dailyGoalXp} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (r.goalMet) "Daily goal done!" else "Today's goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 18.sp)
                    Text(" ${countUp(r.streakDays)}-day streak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = if (r.studiedToday) flame else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // This week: a lit dot for each day studied, today ringed.
                val days = remember {
                    val today = java.time.LocalDate.now()
                    (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.ENGLISH) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    r.week.takeLast(7).forEachIndexed { i, xpDay ->
                        val isToday = i == 6
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (xpDay > 0) flame else MaterialTheme.colorScheme.surfaceVariant)
                                .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (xpDay > 0) "✓" else days.getOrElse(i) { "" }, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (xpDay > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("⭐ Level ${r.level} · ${r.toNextLevel} XP to the next", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A small open book with a play button that pulses: today's lesson, drawn in code. */
@Composable
private fun BookPlayArt(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(4) { pulse.animateTo(1f, tween(700, easing = FastOutSlowInEasing)); pulse.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
    }
    val primary = MaterialTheme.colorScheme.primary
    val page = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(84.dp)) {
        val w = size.width
        // Pages.
        for (side in listOf(-1f, 1f)) {
            val p = Path().apply {
                moveTo(w * 0.5f, w * 0.30f)
                quadraticTo(w * (0.5f + side * 0.22f), w * 0.20f, w * (0.5f + side * 0.46f), w * 0.26f)
                lineTo(w * (0.5f + side * 0.46f), w * 0.80f)
                quadraticTo(w * (0.5f + side * 0.22f), w * 0.74f, w * 0.5f, w * 0.84f)
                close()
            }
            drawPath(p, page)
            drawPath(p, primary.copy(alpha = 0.55f), style = Stroke(w * 0.025f))
            for (k in 0..3) {
                val y = w * (0.40f + k * 0.10f)
                drawLine(line, Offset(w * (0.5f + side * 0.10f), y), Offset(w * (0.5f + side * 0.38f), y - w * 0.02f), strokeWidth = w * 0.02f, cap = StrokeCap.Round)
            }
        }
        // Play button with a pulsing halo.
        val c = Offset(w * 0.5f, w * 0.54f)
        drawCircle(primary.copy(alpha = 0.25f * (1f - pulse.value)), w * (0.20f + 0.10f * pulse.value), c)
        drawCircle(primary, w * 0.18f, c)
        drawPath(Path().apply { moveTo(c.x - w * 0.055f, c.y - w * 0.085f); lineTo(c.x + w * 0.09f, c.y); lineTo(c.x - w * 0.055f, c.y + w * 0.085f); close() }, Color.White)
    }
}

/** Today's session: what it covers, how long, and one big button. */
@Composable
fun TodayCard(title: String, minutes: Int, onStart: () -> Unit, onBusy: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookPlayArt()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("⏱ $minutes min · lesson, practice and review", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BigButton("Start today's session", onClick = onStart, modifier = Modifier.testTag("start-session"))
        TextButton(onClick = onBusy, modifier = Modifier.heightIn(min = 44.dp).testTag("busy-day")) { Text("Busy day? 5 minutes") }
    }
}

/** Three stops of the student's path: the one just done, the one to do now (pulsing) and the next one. */
@Composable
private fun MiniPath(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(4) { pulse.animateTo(1f, tween(800)); pulse.animateTo(0f, tween(800)) }
    }
    val done = LocalBrand.current.success
    val now = MaterialTheme.colorScheme.primary
    val later = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(52.dp)) {
        val h = size.height
        val xs = listOf(size.width * 0.15f, size.width * 0.5f, size.width * 0.85f)
        val ys = listOf(h * 0.6f, h * 0.4f, h * 0.6f)
        drawLine(done, Offset(xs[0], ys[0]), Offset(xs[1], ys[1]), strokeWidth = h * 0.10f, cap = StrokeCap.Round)
        drawLine(later, Offset(xs[1], ys[1]), Offset(xs[2], ys[2]), strokeWidth = h * 0.10f, cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(h * 0.12f, h * 0.12f)))
        // Done: a green stop with a tick.
        drawCircle(done, h * 0.26f, Offset(xs[0], ys[0]))
        drawPath(Path().apply {
            moveTo(xs[0] - h * 0.11f, ys[0]); lineTo(xs[0] - h * 0.02f, ys[0] + h * 0.09f); lineTo(xs[0] + h * 0.12f, ys[0] - h * 0.09f)
        }, Color.White, style = Stroke(h * 0.06f, cap = StrokeCap.Round))
        // Now: bigger, with a pulsing halo and a play mark.
        drawCircle(now.copy(alpha = 0.25f * (1f - pulse.value)), h * (0.36f + 0.12f * pulse.value), Offset(xs[1], ys[1]))
        drawCircle(now, h * 0.33f, Offset(xs[1], ys[1]))
        drawPath(Path().apply {
            moveTo(xs[1] - h * 0.08f, ys[1] - h * 0.13f); lineTo(xs[1] + h * 0.13f, ys[1]); lineTo(xs[1] - h * 0.08f, ys[1] + h * 0.13f); close()
        }, Color.White)
        // Later: a grey stop with a lock.
        drawCircle(later, h * 0.26f, Offset(xs[2], ys[2]))
        drawRoundRect(Color.White, Offset(xs[2] - h * 0.10f, ys[2] - h * 0.02f), Size(h * 0.20f, h * 0.15f), CornerRadius(h * 0.03f))
        drawArc(Color.White, 180f, 180f, false, Offset(xs[2] - h * 0.065f, ys[2] - h * 0.12f), Size(h * 0.13f, h * 0.16f), style = Stroke(h * 0.04f))
    }
}

/** The next step on the path, with the path drawn around it. */
@Composable
fun NextStepCard(title: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp).testTag("do-this-now"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Do this now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        MiniPath()
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
        AccentButton("Continue", onClick = onContinue)
    }
}

/** One quest with a bar that fills to its target. */
@Composable
fun QuestRow(q: pk.parhlou.student.domain.Quests.Quest) {
    val brand = LocalBrand.current
    val shown = remember { Animatable(0f) }
    val target = (q.progress.toFloat() / q.target.coerceAtLeast(1)).coerceIn(0f, 1f)
    LaunchedEffect(target) { shown.animateTo(target, tween(800, easing = FastOutSlowInEasing)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (q.done) brand.successPale else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) { Text(if (q.done) "✅" else "🎯", fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(q.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${q.progress.coerceAtMost(q.target)}/${q.target}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(shown.value).height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (q.done) brand.success else Color(0xFFFFB020)))
            }
        }
    }
}

/** A coloured badge for a subject's book: its symbol on its own colour. */
@Composable
fun SubjectBadge(subjectId: String, modifier: Modifier = Modifier) {
    val (glyph, colour) = when {
        "math" in subjectId -> "∑" to Color(0xFF2F7FD8)
        "phy" in subjectId -> "⚛" to Color(0xFF5B5FE0)
        "chem" in subjectId -> "⚗" to Color(0xFFE0712B)
        "bio" in subjectId -> "🧬" to Color(0xFF1C9E5E)
        "cs" in subjectId -> "💻" to Color(0xFFC2459B)
        else -> "📘" to MaterialTheme.colorScheme.primary
    }
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(colour, androidx.compose.ui.graphics.lerp(colour, Color.Black, 0.25f)))),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

/** Ask Ustaad on Home: his face, the name, and the three things he's best at. */
@Composable
fun AskUstaadCard(onAsk: () -> Unit, modifier: Modifier = Modifier) {
    pk.parhlou.student.ui.components.Chunky(
        modifier = modifier.fillMaxWidth().testTag("home-ask"), colors = pk.parhlou.student.ui.components.ChunkyDefaults.primary(),
        shape = RoundedCornerShape(24.dp), onClick = onAsk, contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ustad(44.dp, UstadMood.HAPPY, animate = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ask Ustaad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Any question, any time", style = MaterialTheme.typography.labelMedium)
                }
                Text("›", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for ((icon, label) in listOf("📷" to "Snap it", "💡" to "Explain", "📄" to "Make a paper")) {
                    Text("$icon $label", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)).padding(vertical = 6.dp))
                }
            }
        }
    }
}
