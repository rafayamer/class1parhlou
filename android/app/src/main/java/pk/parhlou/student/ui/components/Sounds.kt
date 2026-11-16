package pk.parhlou.student.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import pk.parhlou.student.R

/** Parhlou's own UI sounds (made by scripts/make-sounds.py), with how loud each one plays. */
enum class Sfx(val res: Int, val volume: Float) {
    TAP(R.raw.ui_tap, 0.35f),
    SELECT(R.raw.ui_select, 0.5f),
    CORRECT(R.raw.ui_correct, 0.8f),
    WRONG(R.raw.ui_wrong, 0.7f),
    COMBO(R.raw.ui_combo, 0.75f),
    COMPLETE(R.raw.ui_complete, 0.85f),
    STREAK(R.raw.ui_streak, 0.8f),
    LEVEL_UP(R.raw.ui_levelup, 0.85f),
    XP(R.raw.ui_xp, 0.35f),
    UNLOCK(R.raw.ui_unlock, 0.7f),
}

/**
 * Plays the UI sounds through one small [SoundPool] (they are loaded once and play instantly), with matching haptics.
 * Sounds follow the student's switch in Profile and in every session; haptics always play.
 */
class SoundBoard(context: Context) {
    @Volatile var enabled: Boolean = true
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
    }.getOrNull()
    private val ids: Map<Sfx, Int> = Sfx.entries.associateWith { s -> runCatching { pool?.load(context, s.res, 1) ?: 0 }.getOrDefault(0) }

    fun play(s: Sfx, view: View? = null) {
        view?.let { haptic(it, s) }
        if (!enabled) return
        val id = ids[s] ?: return
        if (id != 0) runCatching { pool?.play(id, s.volume, s.volume, 1, 0, 1f) }
    }

    private fun haptic(v: View, s: Sfx) {
        val c = when (s) {
            Sfx.CORRECT, Sfx.COMBO, Sfx.UNLOCK -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Sfx.WRONG -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Sfx.COMPLETE, Sfx.LEVEL_UP, Sfx.STREAK -> HapticFeedbackConstants.LONG_PRESS
            Sfx.TAP, Sfx.SELECT -> HapticFeedbackConstants.VIRTUAL_KEY
            Sfx.XP -> HapticFeedbackConstants.CLOCK_TICK
        }
        v.performHapticFeedback(c)
    }

    fun release() { runCatching { pool?.release() } }
}

/** The app's sound board; null in previews and tests that don't provide one (then nothing plays). */
val LocalSounds = staticCompositionLocalOf<SoundBoard?> { null }

@Composable
fun rememberSoundBoard(enabled: Boolean): SoundBoard {
    val context = LocalContext.current.applicationContext
    val board = remember { SoundBoard(context) }
    DisposableEffect(board) { onDispose { board.release() } }
    board.enabled = enabled
    return board
}
