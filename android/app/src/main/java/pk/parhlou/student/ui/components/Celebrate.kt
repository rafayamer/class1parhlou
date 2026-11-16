package pk.parhlou.student.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
        val colors = listOf(Color(0xFF0B8A8A), Color(0xFFFFC93C), Color(0xFF5B5FE0), Color(0xFFE5484D), Color(0xFF1C9E5E), Color(0xFFE0712B))
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

/** Sounds and haptics for right and wrong answers, combos and the end of a session, like a game. Sounds can be muted. */
class FeedbackEffects(private val view: android.view.View, private val sounds: SoundBoard?) {
    var soundOn = true
    private fun play(s: Sfx) {
        if (sounds == null) { if (s != Sfx.TAP) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); return }
        val was = sounds.enabled
        sounds.enabled = was && soundOn
        sounds.play(s, view)
        sounds.enabled = was
    }
    fun correct() = play(Sfx.CORRECT)
    fun wrong() = play(Sfx.WRONG)
    fun combo() = play(Sfx.COMBO)
    fun select() = play(Sfx.SELECT)
    fun finish() = play(Sfx.COMPLETE)
    fun streak() = play(Sfx.STREAK)
    fun levelUp() = play(Sfx.LEVEL_UP)
    fun xp() = play(Sfx.XP)
}

@Composable
fun rememberFeedbackEffects(soundOn: Boolean): FeedbackEffects {
    val view = LocalView.current
    val sounds = LocalSounds.current
    return remember(view, sounds) { FeedbackEffects(view, sounds) }.also { it.soundOn = soundOn }
}
