package pk.parhlou.student.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/** The calm card style used on Home: surface colour, a hairline border and generous rounding. */
fun Modifier.homeCard(color: Color, line: Color) = this.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color)
    .border(1.dp, line, RoundedCornerShape(22.dp))

/** Fine concentric arcs in a corner of the hero card: quiet decoration that reads as a premium surface. */
@Composable
fun HeroArcs(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.98f, size.height * 0.02f)
        for (i in 1..5) drawCircle(Color.White.copy(alpha = 0.07f), size.minDimension * 0.22f * i, c, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.06f), size.width * 0.32f, Offset(size.width * 0.0f, size.height * 1.1f))
    }
}

/**
 * The top of Home, one card: Ustaad greets the student (a tap on him gives a study tip), with Ask Ustaad and three quick
 * starts underneath. The owner's message (or his mood line) shows first.
 */
@Composable
fun HomeHero(
    name: String, line: String, mood: UstadMood, modifier: Modifier = Modifier,
    /** Opens Ask Ustaad; null hides the ask bar (switched off in the owner panel). */
    onAsk: (() -> Unit)? = null,
) {
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()
    var tip by remember { mutableIntStateOf(-1) }
    var cheering by remember { mutableStateOf(false) }
    val said = if (tip < 0) line else TIPS[tip % TIPS.size]
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(heroGradient()).testTag("home-hero")) {
        HeroArcs(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (name.isBlank()) "Assalam-o-Alaikum" else "Assalam-o-Alaikum,", style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.78f))
                    if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(said, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tip") { text ->
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.testTag("hero-line"))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(84.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .clickable(onClickLabel = "Get a study tip from Ustaad") {
                            tip += 1
                            sounds?.play(Sfx.SELECT)
                            cheering = true
                            scope.launch { delay(1400); cheering = false }
                        }
                        .testTag("hero-ustaad"),
                    contentAlignment = Alignment.Center,
                ) { Ustad(84.dp, if (cheering) UstadMood.CHEER else mood) }
            }
            if (onAsk != null) {
                // Ask Ustaad, as a search-style field, and three quick starts.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .clickable(onClickLabel = "Ask Ustaad", onClick = onAsk).padding(horizontal = 14.dp, vertical = 10.dp).testTag("home-ask"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ask Ustaad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1D2B33))
                        Text("Any question, any subject", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5E6E77))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickStart(Icons.Filled.PhotoCamera, "Scan question", onAsk, Modifier.weight(1f))
                    QuickStart(Icons.Filled.Lightbulb, "Explain topic", onAsk, Modifier.weight(1f))
                    QuickStart(Icons.Filled.Description, "Create paper", onAsk, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickStart(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.13f)).clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * Today's goal at a glance: a ring that fills with today's XP, the streak with this week's days, and the level.
 * Tapping it opens the progress screen.
 */
@Composable
fun GoalCard(r: RewardSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val primary = MaterialTheme.colorScheme.primary
    val flame = Color(0xFFF2711C)
    Row(
        modifier.homeCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClickLabel = "Open your progress", onClick = onClick).padding(16.dp).testTag("reward-bar")
            .semantics(mergeDescendants = true) {
                contentDescription = "${r.xpToday} of ${r.dailyGoalXp} XP today, ${r.streakDays} day streak, level ${r.level}. Open your progress"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val xp = countUp(r.xpToday)
        pk.parhlou.student.ui.components.ProgressRing((r.xpToday.toFloat() / r.dailyGoalXp.coerceAtLeast(1)).coerceIn(0f, 1f), 88.dp, 9.dp,
            if (r.goalMet) brand.success else primary, MaterialTheme.colorScheme.surfaceVariant) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$xp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("/ ${r.dailyGoalXp} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (r.goalMet) "Daily goal complete" else "Today's goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = if (r.studiedToday) flame else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${countUp(r.streakDays)}-day streak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            // This week: each day's initial, filled when studied, today outlined.
            val days = remember {
                val today = java.time.LocalDate.now()
                (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.ENGLISH) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                r.week.takeLast(7).forEachIndexed { i, xpDay ->
                    val studied = xpDay > 0
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (studied) flame else MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (i == 6) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(days.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                            color = if (studied) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Level ${r.level} · ${r.toNextLevel} XP to the next", style = MaterialTheme.typography.labelMedium,
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
        repeat(3) { pulse.animateTo(1f, tween(800, easing = FastOutSlowInEasing)); pulse.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
    }
    val primary = MaterialTheme.colorScheme.primary
    val page = MaterialTheme.colorScheme.primaryContainer
    val line = primary.copy(alpha = 0.35f)
    Canvas(modifier.size(76.dp)) {
        val w = size.width
        for (side in listOf(-1f, 1f)) {
            val p = Path().apply {
                moveTo(w * 0.5f, w * 0.30f)
                quadraticTo(w * (0.5f + side * 0.22f), w * 0.20f, w * (0.5f + side * 0.46f), w * 0.26f)
                lineTo(w * (0.5f + side * 0.46f), w * 0.80f)
                quadraticTo(w * (0.5f + side * 0.22f), w * 0.74f, w * 0.5f, w * 0.84f)
                close()
            }
            drawPath(p, page)
            for (k in 0..3) {
                val y = w * (0.40f + k * 0.10f)
                drawLine(line, Offset(w * (0.5f + side * 0.10f), y), Offset(w * (0.5f + side * 0.38f), y - w * 0.02f), strokeWidth = w * 0.02f, cap = StrokeCap.Round)
            }
        }
        val c = Offset(w * 0.5f, w * 0.54f)
        drawCircle(primary.copy(alpha = 0.22f * (1f - pulse.value)), w * (0.20f + 0.08f * pulse.value), c)
        drawCircle(primary, w * 0.17f, c)
        drawPath(Path().apply { moveTo(c.x - w * 0.05f, c.y - w * 0.08f); lineTo(c.x + w * 0.085f, c.y); lineTo(c.x - w * 0.05f, c.y + w * 0.08f); close() }, Color.White)
    }
}

/** Today's session: what it covers, how long, and one button. */
@Composable
fun TodayCard(title: String, minutes: Int, onStart: () -> Unit, onBusy: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.homeCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookPlayArt()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$minutes min · lesson, practice and review", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        repeat(3) { pulse.animateTo(1f, tween(900)); pulse.animateTo(0f, tween(900)) }
    }
    val done = LocalBrand.current.success
    val now = MaterialTheme.colorScheme.primary
    val later = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(48.dp)) {
        val h = size.height
        val xs = listOf(size.width * 0.15f, size.width * 0.5f, size.width * 0.85f)
        val y = h * 0.5f
        drawLine(done, Offset(xs[0], y), Offset(xs[1], y), strokeWidth = h * 0.08f, cap = StrokeCap.Round)
        drawLine(later, Offset(xs[1], y), Offset(xs[2], y), strokeWidth = h * 0.08f, cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(h * 0.10f, h * 0.10f)))
        drawCircle(done, h * 0.24f, Offset(xs[0], y))
        drawPath(Path().apply {
            moveTo(xs[0] - h * 0.10f, y); lineTo(xs[0] - h * 0.02f, y + h * 0.08f); lineTo(xs[0] + h * 0.11f, y - h * 0.08f)
        }, Color.White, style = Stroke(h * 0.055f, cap = StrokeCap.Round))
        drawCircle(now.copy(alpha = 0.22f * (1f - pulse.value)), h * (0.34f + 0.12f * pulse.value), Offset(xs[1], y))
        drawCircle(now, h * 0.32f, Offset(xs[1], y))
        drawPath(Path().apply {
            moveTo(xs[1] - h * 0.07f, y - h * 0.12f); lineTo(xs[1] + h * 0.12f, y); lineTo(xs[1] - h * 0.07f, y + h * 0.12f); close()
        }, Color.White)
        drawCircle(later, h * 0.24f, Offset(xs[2], y))
        drawRoundRect(Color.White, Offset(xs[2] - h * 0.09f, y - h * 0.02f), Size(h * 0.18f, h * 0.14f), CornerRadius(h * 0.03f))
        drawArc(Color.White, 180f, 180f, false, Offset(xs[2] - h * 0.06f, y - h * 0.11f), Size(h * 0.12f, h * 0.15f), style = Stroke(h * 0.035f))
    }
}

/** The next step on the path, with the path drawn around it. */
@Composable
fun NextStepCard(title: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.homeCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant).padding(16.dp).testTag("do-this-now"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Do this now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
        Icon(if (q.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = if (q.done) "Done" else "Not done yet",
            tint = if (q.done) brand.success else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(q.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${q.progress.coerceAtMost(q.target)}/${q.target}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(shown.value).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (q.done) brand.success else MaterialTheme.colorScheme.primary))
            }
        }
    }
}

/** A coloured badge for a subject's book: its icon on its own colour. */
@Composable
fun SubjectBadge(subjectId: String, modifier: Modifier = Modifier) {
    val (icon, colour) = when {
        "math" in subjectId -> Icons.Filled.Calculate to Color(0xFF2F6FD0)
        "phy" in subjectId -> Icons.Filled.ElectricBolt to Color(0xFF5B5FE0)
        "chem" in subjectId -> Icons.Filled.Science to Color(0xFFD9692A)
        "bio" in subjectId -> Icons.Filled.Biotech to Color(0xFF1C9E5E)
        "cs" in subjectId -> Icons.Filled.Computer to Color(0xFFB8418F)
        else -> Icons.AutoMirrored.Filled.MenuBook to MaterialTheme.colorScheme.primary
    }
    Box(modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colour.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(24.dp))
    }
}

/** Home's invitation to Premium: what it unlocks and the price, one tap to the Premium screen. */
@Composable
fun PremiumInvite(pricePkr: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(heroGradient()).clickable(onClickLabel = "See Premium", onClick = onOpen)
            .padding(16.dp).testTag("home-premium"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Unlock Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Every chapter, board papers and Ask Ustaad · ${pk.parhlou.student.domain.Premium.rupees(pricePkr)} a month",
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Home's line for Premium students: credits left this month and when they renew; one tap to the Premium screen. */
@Composable
fun CreditsLine(credits: pk.parhlou.student.ai.Allowance, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "See your credits", onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp).testTag("home-credits"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Premium · ⚡ ${pk.parhlou.student.domain.Credits.format(credits.balance)} credits left", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(if (credits.premiumUntil != null && credits.premiumUntil == credits.resetsOn) "Premium ends ${prettyDate(credits.resetsOn)}"
                else "Renews ${prettyDate(credits.resetsOn)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(64.dp).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(credits.leftShare).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        }
    }
}
