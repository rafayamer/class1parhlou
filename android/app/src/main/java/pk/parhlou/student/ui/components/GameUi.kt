package pk.parhlou.student.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import pk.parhlou.student.domain.Achievement
import pk.parhlou.student.domain.Game
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.ui.theme.LocalBrand

/** Share of the way from this level to the next, 0–1 (levels start at 50·n·(n−1) XP). */
fun levelProgress(r: RewardSummary): Float {
    val start = 50 * r.level * (r.level - 1)
    val next = 50 * (r.level + 1) * r.level
    return ((r.totalXp - start).toFloat() / (next - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** A ring that fills to [progress] when it first appears, with [content] in the middle. */
@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val shown = remember { Animatable(if (reduced) progress else 0f) }
    LaunchedEffect(progress) { if (reduced) shown.snapTo(progress) else shown.animateTo(progress, tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * shown.value, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * The student's card at the top of the League tab: avatar inside a level ring, XP to the next level, streak, league and
 * badges. A warm gradient so the game side of the app has its own look.
 */
@Composable
fun PlayerCard(name: String, avatar: String, rewards: RewardSummary, league: String?, badgeCount: Int, onInvite: () -> Unit) {
    val brand = LocalBrand.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            // The same deep teal-to-indigo in light and dark themes, so white text always reads clearly.
            .background(Brush.linearGradient(listOf(Color(0xFF0B7A7A), Color(0xFF4F53D6))))
            .padding(18.dp).testTag("player-card"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pct = levelProgress(rewards)
            ProgressRing(pct, 84.dp, 8.dp, brand.sun, Color.White.copy(alpha = 0.25f),
                Modifier.semantics { contentDescription = "Level ${rewards.level}, ${(pct * 100).toInt()} percent to the next level" }) {
                Text(avatar, fontSize = 38.sp)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Level ${rewards.level}", style = MaterialTheme.typography.titleMedium, color = brand.sun, fontWeight = FontWeight.Bold)
                Text("${rewards.toNextLevel} XP to level ${rewards.level + 1}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("🔥", "${countUp(rewards.streakDays)}", "day streak", Modifier.weight(1f))
            StatPill("⚡", "${countUp(rewards.totalXp, 1200)}", "total XP", Modifier.weight(1f))
            StatPill(if (league != null) "🏆" else "🏅", league ?: "$badgeCount", if (league != null) "league" else "badges", Modifier.weight(1f))
        }
        TextButton(onClick = onInvite, modifier = Modifier.heightIn(min = 44.dp).testTag("invite")) {
            Text("🤝  Invite a friend to study with you", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatPill(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
    }
}

/**
 * Today's chest. Locked until every quest is done; then it wobbles to invite a tap, and opening it pops out its XP.
 * [onOpen] records the reward and returns the XP it gave.
 */
@Composable
fun ChestCard(ready: Boolean, openedToday: Boolean, onOpen: () -> Int, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val reduced = LocalReducedMotion.current
    val fx = rememberFeedbackEffects(true)
    val scope = rememberCoroutineScope()
    val wobble = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    var won by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) {
        if (ready && !reduced) repeat(3) {
            wobble.animateTo(8f, tween(70)); wobble.animateTo(-8f, tween(140)); wobble.animateTo(0f, tween(70))
            kotlinx.coroutines.delay(900)
        }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (ready) brand.sun.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp).testTag("chest"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (openedToday || won != null) "🎉" else "🎁", fontSize = 40.sp,
            modifier = Modifier.graphicsLayer { rotationZ = wobble.value; scaleX = pop.value; scaleY = pop.value }
                .alpha(if (ready || openedToday || won != null) 1f else 0.45f))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            when {
                won != null -> { Text("+$won XP!", style = MaterialTheme.typography.titleLarge, color = brand.xp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("chest-won"))
                    Muted("Come back tomorrow for another chest.") }
                openedToday -> { Text("Chest opened", style = MaterialTheme.typography.titleMedium)
                    Muted("A new chest comes with tomorrow's quests.") }
                ready -> { Text("Your chest is ready!", style = MaterialTheme.typography.titleMedium)
                    Muted("You finished every quest today.") }
                else -> { Text("Daily chest", style = MaterialTheme.typography.titleMedium)
                    Muted("Finish all of today's quests to open it: ${Game.CHEST_MIN_XP}–${Game.CHEST_MAX_XP} bonus XP.") }
            }
        }
        if (ready && won == null) ChunkyButton("Open", onClick = {
            val xp = onOpen()
            won = xp
            fx.levelUp()
            if (!reduced) scope.launch { pop.snapTo(1.6f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow)) }
        }, colors = ChunkyDefaults.sun(), modifier = Modifier.testTag("open-chest"))
    }
}

/** Every achievement: earned ones in colour, locked ones dimmed with how far along the student is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementGrid(items: List<Achievement>, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    FlowRow(modifier.fillMaxWidth().testTag("achievements"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        items.forEach { a ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (a.earned) brand.sun.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp).testTag("achievement-${a.id}")
                    .semantics { contentDescription = "${a.title}: ${a.description}. ${if (a.earned) "Earned" else "${a.shownProgress} of ${a.target}"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (a.earned) a.icon else "🔒", fontSize = 28.sp, modifier = Modifier.alpha(if (a.earned) 1f else 0.6f))
                Text(a.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                    color = if (a.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (!a.earned) {
                    LinearProgressIndicator(progress = { a.shownProgress / a.target.toFloat() }, modifier = Modifier.width(64.dp).clip(RoundedCornerShape(4.dp)))
                    Text("${a.shownProgress}/${a.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Keep the last row's cards the same width as the others.
        repeat((3 - items.size % 3) % 3) { Box(Modifier.weight(1f)) }
    }
}

/** A full-screen moment for a level, streak milestone, badge or finished quests: confetti, a bouncing emoji, then on. */
@Composable
fun CelebrationDialog(c: Game.Celebration, onShare: () -> Unit, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val fx = rememberFeedbackEffects(true)
    val bounce = remember { Animatable(if (reduced) 1f else 0.3f) }
    LaunchedEffect(c) {
        if (c.kind == Game.Kind.STREAK) fx.streak() else fx.levelUp()
        if (!reduced) bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp).testTag("celebration"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(c.emoji, fontSize = 72.sp, modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value })
                Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(c.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChunkyButton("Continue", onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("celebration-ok"))
                TextButton(onClick = onShare, modifier = Modifier.heightIn(min = 44.dp)) { Text("Share with a friend") }
            }
            Confetti(Modifier.matchParentSizeOrFill())
        }
    }
}

private fun Modifier.matchParentSizeOrFill(): Modifier = this.fillMaxSize()
