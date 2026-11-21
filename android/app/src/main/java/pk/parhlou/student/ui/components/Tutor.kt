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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
    UstadMood.HAPPY -> if (r.streakDays >= 2) "${r.streakDays}-day streak. Keep it going!" else "Ready to study today?"
    UstadMood.SAD -> "Your streak broke. Let's start a new one today."
    UstadMood.CROSS -> "It's been a few days. Come back, one lesson today!"
    UstadMood.THINKING -> "Let's think it through."
}

/**
 * Ustaad, Parhlou's tutor: a realistic portrait of a Pakistani teacher (warm brown skin, combed black hair, a groomed beard,
 * glasses, a white kameez and a waistcoat in the brand colour), shown like a profile photo in a soft circle. Drawn in code
 * (UstaadPortrait.kt), so he stays sharp at any size, follows the owner's brand colour and is also the app icon. He breathes,
 * blinks, raises his eyebrows hello, nods when he cheers and tilts his head when he thinks; every motion plays a few times
 * and then rests, and none play when the phone asks for less motion.
 */
@Composable
fun Ustad(size: Dp = 56.dp, mood: UstadMood = UstadMood.HAPPY, modifier: Modifier = Modifier, animate: Boolean = true) {
    val reduced = LocalReducedMotion.current || !animate
    val breathe = rememberBob(reduced, 1600, times = 4)
    val blink = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val lift = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val nod = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val tilt by androidx.compose.animation.core.animateFloatAsState(
        if (mood == UstadMood.THINKING && !reduced) -6f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow), label = "tilt")
    androidx.compose.runtime.LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        repeat(4) {
            kotlinx.coroutines.delay(1900L + it * 1100L)
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(120))
        }
    }
    // A friendly eyebrow raise when he appears or his mood changes.
    androidx.compose.runtime.LaunchedEffect(mood, reduced) {
        if (reduced || mood == UstadMood.CROSS || mood == UstadMood.SAD) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        lift.animateTo(1f, tween(220)); kotlinx.coroutines.delay(450); lift.animateTo(0f, tween(320))
    }
    androidx.compose.runtime.LaunchedEffect(mood, reduced) {
        if (reduced || mood != UstadMood.CHEER) return@LaunchedEffect
        repeat(2) {
            nod.animateTo(1f, tween(170))
            nod.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 320f))
        }
    }
    val coat = MaterialTheme.colorScheme.primary
    // A light backdrop in both themes, so his dark hair and beard always stand out.
    val backTop = androidx.compose.ui.graphics.lerp(coat, Color.White, 0.80f)
    val backBottom = androidx.compose.ui.graphics.lerp(coat, Color.White, 0.60f)
    Canvas(
        modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .semantics { contentDescription = "Ustaad, your tutor" },
    ) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(backTop, backBottom)))
        drawUstaad(this.size.width, mood, blink.value, coat, lift.value, tilt, nod.value, breathe)
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
