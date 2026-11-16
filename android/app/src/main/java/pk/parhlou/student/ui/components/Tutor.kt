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

enum class UstadMood { HAPPY, THINKING, CHEER }

/**
 * Ustad, Parhlou's tutor character: an original drawing (a kind teacher with a cap and round glasses), drawn in code
 * so it scales and follows the theme. It bobs gently unless the phone asks for reduced motion.
 */
@Composable
fun Ustad(size: Dp = 56.dp, mood: UstadMood = UstadMood.HAPPY, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val bob = rememberBob(reduced, 1400)
    val skin = Color(0xFFE2B48C)
    val cap = MaterialTheme.colorScheme.primary
    val ink = Color(0xFF2B2B2B)
    val beard = Color(0xFF5A4636)
    val accent = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { translationY = -bob * 3f * density }
            .semantics { contentDescription = "Ustaad, your tutor" },
    ) {
        val w = this.size.width
        val c = Offset(w / 2, w * 0.56f)
        val r = w * 0.36f
        // Shoulders
        drawArc(accent, 180f, 180f, useCenter = true, topLeft = Offset(w * 0.12f, w * 0.84f), size = Size(w * 0.76f, w * 0.4f))
        // Face and beard
        drawCircle(skin, r, c)
        drawArc(beard, 20f, 140f, useCenter = false, topLeft = Offset(c.x - r, c.y - r * 0.9f), size = Size(r * 2, r * 2),
            style = Stroke(width = r * 0.28f))
        // Cap (topi)
        drawArc(cap, 180f, 180f, useCenter = true, topLeft = Offset(c.x - r * 1.02f, c.y - r * 1.12f), size = Size(r * 2.04f, r * 1.2f))
        // Glasses
        val eyeY = c.y - r * 0.05f
        val lens = r * 0.27f
        val stroke = Stroke(width = r * 0.07f)
        drawCircle(ink, lens, Offset(c.x - r * 0.38f, eyeY), style = stroke)
        drawCircle(ink, lens, Offset(c.x + r * 0.38f, eyeY), style = stroke)
        drawLine(ink, Offset(c.x - r * 0.11f, eyeY), Offset(c.x + r * 0.11f, eyeY), strokeWidth = r * 0.06f)
        drawCircle(ink, r * 0.07f, Offset(c.x - r * 0.38f, eyeY))
        drawCircle(ink, r * 0.07f, Offset(c.x + r * 0.38f, eyeY))
        // Mouth
        val mouthTop = Offset(c.x - r * 0.3f, c.y + r * 0.18f)
        when (mood) {
            UstadMood.HAPPY -> drawArc(ink, 20f, 140f, useCenter = false, topLeft = mouthTop, size = Size(r * 0.6f, r * 0.4f), style = Stroke(r * 0.07f))
            UstadMood.CHEER -> drawArc(ink, 0f, 180f, useCenter = true, topLeft = mouthTop, size = Size(r * 0.6f, r * 0.5f))
            UstadMood.THINKING -> drawLine(ink, Offset(c.x - r * 0.2f, c.y + r * 0.42f), Offset(c.x + r * 0.22f, c.y + r * 0.36f), strokeWidth = r * 0.07f)
        }
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
