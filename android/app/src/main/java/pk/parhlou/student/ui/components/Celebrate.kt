package pk.parhlou.student.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import kotlin.math.sin
import kotlin.random.Random

/** Confetti falling over the screen for about two seconds (skipped when the phone asks for reduced motion). */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 70) {
    if (LocalReducedMotion.current) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val parts = remember {
        val rnd = Random(7)
        val colors = listOf(Color(0xFF7BC74D), Color(0xFFF4B400), Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFAB47BC), Color(0xFF26A69A))
        List(pieces) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.6f + 0.4f, colors[it % colors.size]) to rnd.nextFloat() * 360f }
    }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        parts.forEachIndexed { i, (p, spin) ->
            val (x0, speed, color) = p
            val y = (t.value * speed * 1.4f - 0.15f) * size.height
            val x = x0 * size.width + sin((t.value * 8 + i) * 1.3f) * 18f
            rotate(spin + t.value * 540f, Offset(x, y)) {
                drawRect(color.copy(alpha = 1f - t.value * 0.6f), Offset(x - 6f, y - 3f), Size(12f, 6f))
            }
        }
    }
}

/** Haptics and short tones for right and wrong answers, like a game. Sounds can be muted. */
class FeedbackEffects(private val view: android.view.View, private val tones: ToneGenerator?) {
    var soundOn = true
    fun correct() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        if (soundOn) runCatching { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }
    fun wrong() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        if (soundOn) runCatching { tones?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }
    }
    fun finish() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (soundOn) runCatching { tones?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 400) }
    }
}

@Composable
fun rememberFeedbackEffects(soundOn: Boolean): FeedbackEffects {
    val view = LocalView.current
    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
    DisposableEffect(tones) { onDispose { runCatching { tones?.release() } } }
    return remember(view, tones) { FeedbackEffects(view, tones) }.also { it.soundOn = soundOn }
}
