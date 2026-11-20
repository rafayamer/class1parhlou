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
 * Ustaad, Parhlou's tutor: a small, round teal scholar with big glossy eyes, expressive eyebrows, rosy cheeks, little arms
 * and a graduation cap with a gold tassel. A stylised character (not a realistic face), drawn in code so it stays sharp
 * at any size and is also the app icon. He breathes, blinks, swings his tassel, hops when he cheers and tilts his head
 * when he thinks; every motion plays a few times and then rests, and none play when the phone asks for less motion.
 */
@Composable
fun Ustad(size: Dp = 56.dp, mood: UstadMood = UstadMood.HAPPY, modifier: Modifier = Modifier, animate: Boolean = true) {
    val reduced = LocalReducedMotion.current || !animate
    val breathe = rememberBob(reduced, 1300, times = 4)
    val blink = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val tassel = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val hop = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val tilt by androidx.compose.animation.core.animateFloatAsState(
        if (mood == UstadMood.THINKING && !reduced) -7f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow), label = "tilt")
    androidx.compose.runtime.LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(4) {
            kotlinx.coroutines.delay(1900L + it * 1100L)
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(120))
        }
    }
    androidx.compose.runtime.LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(3) {
            tassel.animateTo(1f, tween(650)); tassel.animateTo(-1f, tween(1300)); tassel.animateTo(0f, tween(650))
            kotlinx.coroutines.delay(1200)
        }
    }
    androidx.compose.runtime.LaunchedEffect(mood, reduced) {
        if (reduced || mood != UstadMood.CHEER) return@LaunchedEffect
        repeat(2) {
            hop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f))
            hop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f))
        }
    }
    val body = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.size(size).graphicsLayer {
            translationY = -hop.value * 0.12f * this.size.height
            rotationZ = tilt
            scaleY = 1f + breathe * 0.025f
            scaleX = 1f - breathe * 0.012f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }.semantics { contentDescription = "Ustaad, your tutor" },
    ) { drawUstaad(this.size.width, mood, blink.value, body, tassel.value) }
}

/** The drawing itself, also used to make the launcher icon. [w] is the square's side in pixels. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUstaad(
    w: Float, mood: UstadMood, blink: Float = 0f, waistcoat: Color = Color(0xFF0B8A8A), tassel: Float = 0f,
) {
    val body = waistcoat
    val bodyLight = androidx.compose.ui.graphics.lerp(body, Color.White, 0.35f)
    val bodyDark = androidx.compose.ui.graphics.lerp(body, Color.Black, 0.22f)
    val navy = Color(0xFF1C2541)
    val gold = Color(0xFFFFC93C)
    val cx = w / 2f
    val round = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.045f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

    // Soft ground shadow.
    drawOval(Color.Black.copy(alpha = 0.10f), Offset(cx - w * 0.28f, w * 0.90f), Size(w * 0.56f, w * 0.07f))

    // Arms (behind the body): up when he cheers, a hand at the chin when he thinks.
    val armY = w * 0.66f
    for (side in listOf(-1f, 1f)) {
        val up = mood == UstadMood.CHEER
        val think = mood == UstadMood.THINKING && side > 0
        val sx = cx + side * w * 0.31f
        val end = when {
            up -> Offset(cx + side * w * 0.44f, w * 0.40f)
            think -> Offset(cx + w * 0.16f, w * 0.70f)
            else -> Offset(cx + side * w * 0.40f, w * 0.80f)
        }
        drawLine(bodyDark, Offset(sx, armY), end, strokeWidth = w * 0.10f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }

    // Body: a soft rounded shape with light from the top left.
    val top = w * 0.27f
    drawRoundRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bodyLight, body, bodyDark), startY = top, endY = w * 0.92f),
        Offset(cx - w * 0.34f, top), Size(w * 0.68f, w * 0.65f), androidx.compose.ui.geometry.CornerRadius(w * 0.30f),
    )
    // Belly patch and a little shine.
    drawOval(Color.White.copy(alpha = 0.18f), Offset(cx - w * 0.17f, w * 0.75f), Size(w * 0.34f, w * 0.13f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(cx - w * 0.25f, top + w * 0.05f), Size(w * 0.14f, w * 0.07f))

    // Eyes: big and glossy; they look up when he thinks.
    val eyeY = w * 0.50f
    val open = (1f - blink).coerceIn(0.08f, 1f) * (if (mood == UstadMood.CHEER) 0.85f else 1f)
    val look = if (mood == UstadMood.THINKING) Offset(w * 0.018f, -w * 0.028f) else if (mood == UstadMood.SAD) Offset(0f, w * 0.015f) else Offset.Zero
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * w * 0.135f
        drawOval(Color.White, Offset(ex - w * 0.085f, eyeY - w * 0.095f * open), Size(w * 0.17f, w * 0.19f * open))
        if (open > 0.3f) {
            val p = Offset(ex, eyeY) + look
            drawCircle(navy, w * 0.058f, p)
            drawCircle(Color.White, w * 0.021f, p + Offset(-w * 0.018f, -w * 0.022f))
            drawCircle(Color.White.copy(alpha = 0.8f), w * 0.009f, p + Offset(w * 0.020f, w * 0.018f))
        }
    }

    // Eyebrows show the mood.
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * w * 0.135f
        val y = eyeY - w * 0.135f - (if (mood == UstadMood.CHEER) w * 0.02f else 0f)
        val (inner, outer) = when (mood) {
            UstadMood.CROSS -> w * 0.035f to -w * 0.015f
            UstadMood.SAD -> -w * 0.030f to w * 0.015f
            UstadMood.THINKING -> if (side < 0) -w * 0.025f to -w * 0.025f else 0f to 0f
            else -> -w * 0.008f to w * 0.004f
        }
        drawLine(navy, Offset(ex - side * w * 0.05f, y + inner), Offset(ex + side * w * 0.05f, y + outer), strokeWidth = w * 0.028f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }

    // Rosy cheeks.
    for (side in listOf(-1f, 1f)) drawOval(Color(0xFFFF7896).copy(alpha = 0.75f), Offset(cx + side * w * 0.225f - w * 0.045f, w * 0.585f), Size(w * 0.09f, w * 0.05f))

    // Mouth.
    val my = w * 0.655f
    when (mood) {
        UstadMood.CHEER -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - w * 0.085f, my - w * 0.01f)
                quadraticTo(cx, my + w * 0.13f, cx + w * 0.085f, my - w * 0.01f)
                close()
            }
            drawPath(path, navy)
            drawOval(Color(0xFFFF6B81), Offset(cx - w * 0.035f, my + w * 0.035f), Size(w * 0.07f, w * 0.04f))
        }
        UstadMood.HAPPY -> drawArc(navy, 20f, 140f, false, Offset(cx - w * 0.075f, my - w * 0.06f), Size(w * 0.15f, w * 0.10f), style = round)
        UstadMood.SAD -> drawArc(navy, 205f, 130f, false, Offset(cx - w * 0.06f, my + w * 0.005f), Size(w * 0.12f, w * 0.08f), style = round)
        UstadMood.CROSS -> drawLine(navy, Offset(cx - w * 0.055f, my + w * 0.02f), Offset(cx + w * 0.055f, my + w * 0.01f), strokeWidth = w * 0.04f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        UstadMood.THINKING -> {
            drawCircle(navy, w * 0.028f, Offset(cx + w * 0.02f, my + w * 0.015f))
            // A hand at his chin, in front.
            drawCircle(bodyDark, w * 0.055f, Offset(cx + w * 0.13f, my + w * 0.075f))
            drawCircle(body, w * 0.042f, Offset(cx + w * 0.125f, my + w * 0.07f))
        }
    }

    // Graduation cap: band, board, and a gold tassel that swings.
    drawRoundRect(navy, Offset(cx - w * 0.17f, w * 0.20f), Size(w * 0.34f, w * 0.11f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
    val board = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, w * 0.08f); lineTo(cx + w * 0.33f, w * 0.17f); lineTo(cx, w * 0.26f); lineTo(cx - w * 0.33f, w * 0.17f); close()
    }
    drawPath(board, navy)
    drawPath(board, Color.White.copy(alpha = 0.10f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.01f))
    val knob = Offset(cx, w * 0.17f)
    val swing = tassel * w * 0.035f
    val tip = Offset(cx + w * 0.26f + swing, w * 0.34f)
    drawLine(gold, knob, Offset(cx + w * 0.24f + swing * 0.6f, w * 0.19f), strokeWidth = w * 0.018f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(gold, Offset(cx + w * 0.24f + swing * 0.6f, w * 0.19f), tip, strokeWidth = w * 0.018f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(gold, w * 0.03f, knob)
    drawRoundRect(gold, Offset(tip.x - w * 0.022f, tip.y - w * 0.01f), Size(w * 0.044f, w * 0.075f), androidx.compose.ui.geometry.CornerRadius(w * 0.015f))
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
