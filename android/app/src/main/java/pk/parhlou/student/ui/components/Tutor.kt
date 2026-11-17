package pk.parhlou.student.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ustaad's moods: he cheers good work, waits patiently, worries when the streak breaks and gets a little cross if it stays broken. */
enum class UstadMood { HAPPY, THINKING, CHEER, SAD, CROSS }

/** How Ustaad feels about the student's streak today. */
fun moodFor(r: pk.parhlou.student.domain.RewardSummary): UstadMood {
    val away = r.week.dropLast(1).reversed().takeWhile { it == 0 }.count() // days since the last study day, before today
    val everStudied = r.totalXp > 0
    return when {
        r.goalMet -> UstadMood.CHEER
        r.studiedToday || r.streakDays > 0 -> UstadMood.HAPPY
        !everStudied -> UstadMood.HAPPY
        away >= 3 -> UstadMood.CROSS
        else -> UstadMood.SAD
    }
}

/** One short line from Ustaad that matches his mood. */
fun moodLine(r: pk.parhlou.student.domain.RewardSummary): String = when (moodFor(r)) {
    UstadMood.CHEER -> "Goal done today. Shabash!"
    UstadMood.HAPPY -> if (r.streakDays >= 2) "🔥 ${r.streakDays}-day streak. Keep it going!" else "Ready to study today?"
    UstadMood.SAD -> "Your streak broke. Let's start a new one today."
    UstadMood.CROSS -> "It's been a few days. Come back, one lesson today!"
    UstadMood.THINKING -> "Let's think it through."
}

/**
 * Ustaad, Parhlou's tutor: a young Pakistani maths teacher (short dark hair, trimmed beard, round glasses, white collar
 * and teal waistcoat), drawn in code with soft shading so it stays sharp at any size and works as the app icon.
 * He blinks now and then, and his eyebrows and mouth show his [mood]. Motion stops when the phone asks for less motion.
 */
@Composable
fun Ustad(size: Dp = 56.dp, mood: UstadMood = UstadMood.HAPPY, modifier: Modifier = Modifier, animate: Boolean = true) {
    val reduced = LocalReducedMotion.current || !animate
    val bob = rememberBob(reduced, 1400, times = 3)
    val blink = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(4) {
            kotlinx.coroutines.delay(2200L + it * 900L)
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
        }
    }
    val waistcoat = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.size(size).graphicsLayer { translationY = -bob * 2f * density }
            .semantics { contentDescription = "Ustaad, your tutor" },
    ) { drawUstaad(this.size.width, mood, blink.value, waistcoat) }
}

/** The drawing itself, also used to make the launcher icon. [w] is the square's side in pixels. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUstaad(w: Float, mood: UstadMood, blink: Float = 0f, waistcoat: Color = Color(0xFF0B8A8A)) {
    val skin = Color(0xFFE0AC84); val skinShade = Color(0xFFC98E66); val skinLight = Color(0xFFF0C6A2)
    val hair = Color(0xFF231A15); val ink = Color(0xFF231A15); val lip = Color(0xFFB0624E)
    val cx = w / 2; val cy = w * 0.46f
    val fw = w * 0.30f; val fh = w * 0.36f  // half width and half height of the face
    // Body: shoulders in a teal waistcoat over a white collar.
    drawRoundRect(waistcoat, Offset(w * 0.14f, w * 0.80f), Size(w * 0.72f, w * 0.34f), androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
    val collar = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - w * 0.12f, w * 0.80f); lineTo(cx, w * 0.93f); lineTo(cx + w * 0.12f, w * 0.80f); close()
    }
    drawPath(collar, Color.White)
    // Neck.
    drawRect(skinShade, Offset(cx - w * 0.09f, cy + fh * 0.7f), Size(w * 0.18f, w * 0.14f))
    // Ears.
    drawOval(skinShade, Offset(cx - fw - w * 0.06f, cy - w * 0.05f), Size(w * 0.10f, w * 0.15f))
    drawOval(skinShade, Offset(cx + fw - w * 0.04f, cy - w * 0.05f), Size(w * 0.10f, w * 0.15f))
    // Face with soft shading (light from the top left).
    drawOval(androidx.compose.ui.graphics.Brush.radialGradient(listOf(skinLight, skin, skinShade), center = Offset(cx - fw * 0.3f, cy - fh * 0.35f), radius = fw * 2.1f),
        Offset(cx - fw, cy - fh), Size(fw * 2, fh * 2))
    // Trimmed beard along the jaw and chin.
    val beard = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - fw * 0.97f, cy + fh * 0.18f)
        cubicTo(cx - fw * 0.92f, cy + fh * 1.08f, cx + fw * 0.92f, cy + fh * 1.08f, cx + fw * 0.97f, cy + fh * 0.18f)
        cubicTo(cx + fw * 0.80f, cy + fh * 0.58f, cx + fw * 0.38f, cy + fh * 0.74f, cx, cy + fh * 0.72f)
        cubicTo(cx - fw * 0.38f, cy + fh * 0.74f, cx - fw * 0.80f, cy + fh * 0.58f, cx - fw * 0.97f, cy + fh * 0.18f)
        close()
    }
    drawPath(beard, hair.copy(alpha = 0.85f))
    // Moustache: two soft strokes above the lip.
    for (side in listOf(-1f, 1f)) drawArc(hair, if (side < 0) 200f else 200f, 140f, false,
        Offset(cx + (if (side < 0) -fw * 0.40f else 0f), cy + fh * 0.33f), Size(fw * 0.40f, fh * 0.16f), style = Stroke(w * 0.022f))
    // Hair: a neat short cut with a side parting.
    val top = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - fw * 1.03f, cy - fh * 0.12f)
        cubicTo(cx - fw * 1.12f, cy - fh * 1.28f, cx + fw * 1.12f, cy - fh * 1.32f, cx + fw * 1.03f, cy - fh * 0.12f)
        // Hairline: a side parting on his right, a soft fringe sweeping across the forehead.
        cubicTo(cx + fw * 0.95f, cy - fh * 0.55f, cx + fw * 0.55f, cy - fh * 0.80f, cx + fw * 0.15f, cy - fh * 0.72f)
        cubicTo(cx - fw * 0.25f, cy - fh * 0.64f, cx - fw * 0.65f, cy - fh * 0.66f, cx - fw * 0.88f, cy - fh * 0.45f)
        cubicTo(cx - fw * 0.98f, cy - fh * 0.32f, cx - fw * 1.0f, cy - fh * 0.2f, cx - fw * 1.03f, cy - fh * 0.12f)
        close()
    }
    drawPath(top, hair)
    drawArc(Color.White.copy(alpha = 0.18f), 200f, 60f, false, Offset(cx - fw * 0.8f, cy - fh * 1.05f), Size(fw * 1.2f, fh * 0.9f),
        style = Stroke(w * 0.012f))
    // Eyebrows: raised when happy, lowered inward when cross, tilted up inward when sad.
    val browY = cy - fh * 0.30f
    val (inner, outer) = when (mood) {
        UstadMood.CROSS -> fh * 0.10f to -fh * 0.06f
        UstadMood.SAD -> -fh * 0.10f to fh * 0.04f
        UstadMood.CHEER -> -fh * 0.10f to -fh * 0.10f
        UstadMood.THINKING -> -fh * 0.02f to -fh * 0.12f
        UstadMood.HAPPY -> -fh * 0.04f to -fh * 0.04f
    }
    for (side in listOf(-1f, 1f)) {
        drawLine(hair, Offset(cx + side * fw * 0.14f, browY + inner), Offset(cx + side * fw * 0.66f, browY + outer), strokeWidth = w * 0.035f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    // Eyes: whites, iris, pupil and a light spot; the lids close when he blinks.
    val eyeY = cy - fh * 0.08f
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * fw * 0.40f
        val open = (1f - blink).coerceIn(0.08f, 1f) * (if (mood == UstadMood.CHEER) 0.75f else 1f)
        drawOval(Color.White, Offset(ex - w * 0.055f, eyeY - w * 0.035f * open), Size(w * 0.11f, w * 0.07f * open))
        if (open > 0.3f) {
            drawCircle(Color(0xFF4A3426), w * 0.028f, Offset(ex, eyeY))
            drawCircle(ink, w * 0.014f, Offset(ex, eyeY))
            drawCircle(Color.White, w * 0.007f, Offset(ex + w * 0.010f, eyeY - w * 0.010f))
        }
    }
    // Round glasses.
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFF1B1B1B), w * 0.085f, Offset(cx + side * fw * 0.40f, eyeY), style = Stroke(w * 0.016f))
    drawLine(Color(0xFF1B1B1B), Offset(cx - fw * 0.13f, eyeY), Offset(cx + fw * 0.13f, eyeY), strokeWidth = w * 0.014f)
    // Nose.
    drawArc(skinShade, 30f, 120f, false, Offset(cx - w * 0.035f, cy + fh * 0.05f), Size(w * 0.07f, w * 0.05f), style = Stroke(w * 0.014f))
    // Cheeks glow when he is pleased.
    if (mood == UstadMood.HAPPY || mood == UstadMood.CHEER) for (side in listOf(-1f, 1f))
        drawCircle(Color(0xFFE8857A).copy(alpha = 0.28f), w * 0.045f, Offset(cx + side * fw * 0.62f, cy + fh * 0.22f))
    // Mouth.
    val my = cy + fh * 0.55f
    when (mood) {
        UstadMood.CHEER -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - fw * 0.36f, my - fh * 0.04f); quadraticTo(cx, my + fh * 0.36f, cx + fw * 0.36f, my - fh * 0.04f); close()
            }
            drawPath(p, Color(0xFF6B2A22)); drawRect(Color.White, Offset(cx - fw * 0.26f, my - fh * 0.02f), Size(fw * 0.52f, fh * 0.07f))
        }
        UstadMood.HAPPY -> drawArc(lip, 15f, 150f, false, Offset(cx - fw * 0.30f, my - fh * 0.14f), Size(fw * 0.60f, fh * 0.26f), style = Stroke(w * 0.024f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round))
        UstadMood.SAD -> drawArc(lip, 200f, 140f, false, Offset(cx - fw * 0.24f, my + fh * 0.02f), Size(fw * 0.48f, fh * 0.22f), style = Stroke(w * 0.022f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round))
        UstadMood.CROSS -> drawLine(lip, Offset(cx - fw * 0.22f, my + fh * 0.06f), Offset(cx + fw * 0.22f, my + fh * 0.02f), strokeWidth = w * 0.024f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        UstadMood.THINKING -> drawLine(lip, Offset(cx - fw * 0.14f, my + fh * 0.04f), Offset(cx + fw * 0.22f, my), strokeWidth = w * 0.022f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/**
 * A gentle up-and-down that plays a few times and then rests (never endless: saves battery and lets the screen settle).
 * Returns 0..1.
 */
@Composable
fun rememberBob(reduced: Boolean, periodMs: Int, times: Int = 4): Float {
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(times) { a.animateTo(1f, tween(periodMs)); a.animateTo(0f, tween(periodMs)) }
    }
    return a.value
}

/** Ustad saying something, in a speech bubble. */
@Composable
fun UstadSays(text: String, mood: UstadMood = UstadMood.HAPPY, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ustad(size, mood)
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.weight(1f),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}
