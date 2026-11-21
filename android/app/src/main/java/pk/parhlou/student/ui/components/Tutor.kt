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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * Ustaad, Parhlou's tutor: a friendly Pakistani teacher (warm brown skin, neat black hair, a trimmed beard, glasses, a white
 * kameez and a waistcoat in the brand colour), shown like a profile photo in a soft circle. Drawn in code, so he stays sharp
 * at any size, follows the owner's brand colour and is also the app icon. He breathes, blinks, raises his eyebrows hello,
 * nods with a thumbs-up when he cheers and rests his chin on his hand when he thinks; every motion plays a few times and then
 * rests, and none play when the phone asks for less motion.
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
 * The drawing itself, also used to make the launcher icon: a head-and-shoulders portrait in a [w]-pixel square, shoulders
 * reaching the bottom edge, with no background. [blink] closes the eyes (0..1), [lift] raises the eyebrows (0..1), [tilt]
 * turns the head (degrees), [nod] dips the head (0..1) and [breathe] lifts the shoulders (0..1).
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUstaad(
    w: Float, mood: UstadMood, blink: Float = 0f, waistcoat: Color = Color(0xFF0B8A8A),
    lift: Float = 0f, tilt: Float = 0f, nod: Float = 0f, breathe: Float = 0f,
) {
    fun o(x: Float, y: Float) = Offset(x * w, y * w)
    fun sz(x: Float, y: Float) = Size(x * w, y * w)
    fun path(block: androidx.compose.ui.graphics.Path.() -> Unit) = androidx.compose.ui.graphics.Path().apply(block)
    fun androidx.compose.ui.graphics.Path.m(x: Float, y: Float) = moveTo(x * w, y * w)
    fun androidx.compose.ui.graphics.Path.l(x: Float, y: Float) = lineTo(x * w, y * w)
    fun androidx.compose.ui.graphics.Path.q(x1: Float, y1: Float, x2: Float, y2: Float) = quadraticTo(x1 * w, y1 * w, x2 * w, y2 * w)
    fun androidx.compose.ui.graphics.Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * w, y1 * w, x2 * w, y2 * w, x3 * w, y3 * w)
    fun line(width: Float) = Stroke(width * w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)

    val skinHi = Color(0xFFEDC4A2); val skin = Color(0xFFD49D76); val skinLo = Color(0xFFB57A53); val skinDeep = Color(0xFF8C583A)
    val hair = Color(0xFF1C1715); val hairHi = Color(0xFF453832); val beard = Color(0xFF2B231F)
    val lip = Color(0xFFA55A4E); val mouthIn = Color(0xFF5B2222)
    val frame = Color(0xFF262A33)
    val kameez = Color(0xFFF7F4EF); val kameezLo = Color(0xFFD8D1C6)
    val coat = waistcoat
    val coatHi = androidx.compose.ui.graphics.lerp(coat, Color.White, 0.22f)
    val coatLo = androidx.compose.ui.graphics.lerp(coat, Color.Black, 0.30f)
    val gold = Color(0xFFE6B52E)
    val cx = 0.5f

    // ---- Shoulders: white kameez, a waistcoat over it, a band collar, a pen in the pocket. They rise as he breathes.
    scale(1f, 1f + breathe * 0.025f, pivot = o(cx, 1f)) { translate(0f, -0.035f * w) {
        val shoulders = path { m(0.02f, 1.08f); c(0.04f, 0.87f, 0.15f, 0.80f, 0.31f, 0.775f); l(0.69f, 0.775f); c(0.85f, 0.80f, 0.96f, 0.87f, 0.98f, 1.08f); close() }
        drawPath(shoulders, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(kameez, kameezLo), startY = 0.77f * w, endY = w))
        // Neck, shaded under the chin.
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(skinDeep, skinLo, skin), startY = 0.60f * w, endY = 0.79f * w),
            o(0.42f, 0.58f), sz(0.16f, 0.19f))
        // Band collar of the kameez and the placket below it.
        val collar = path { m(0.405f, 0.778f); q(cx, 0.818f, 0.595f, 0.778f); l(0.588f, 0.745f); q(cx, 0.782f, 0.412f, 0.745f); close() }
        drawPath(collar, kameez)
        drawPath(collar, kameezLo, style = line(0.005f))
        drawLine(kameezLo, o(cx, 0.80f), o(cx, 1.08f), strokeWidth = 0.006f * w)
        // Waistcoat: two front panels, sleeveless, meeting low in a V.
        for (side in listOf(-1f, 1f)) {
            val panel = path {
                m(cx + side * 0.37f, 1.08f); c(cx + side * 0.36f, 0.88f, cx + side * 0.30f, 0.81f, cx + side * 0.19f, 0.786f)
                l(cx + side * 0.088f, 0.786f); l(cx + side * 0.012f, 1.08f); close()
            }
            drawPath(panel, androidx.compose.ui.graphics.Brush.linearGradient(listOf(coatHi, coat, coatLo), o(cx + side * 0.34f, 0.78f), o(cx, 1.08f)))
            drawLine(coatLo, o(cx + side * 0.088f, 0.786f), o(cx + side * 0.012f, 1.08f), strokeWidth = 0.009f * w)
        }
        for (y in listOf(0.87f, 0.95f)) {
            val edge = cx + 0.088f - 0.076f * (y - 0.786f) / 0.234f
            drawCircle(gold, 0.011f * w, o(edge + 0.018f, y))
        }
        drawLine(coatLo, o(0.25f, 0.895f), o(0.35f, 0.885f), strokeWidth = 0.008f * w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(Color(0xFF2F5BD3), o(0.30f, 0.835f), sz(0.017f, 0.055f), androidx.compose.ui.geometry.CornerRadius(0.008f * w))
        drawLine(Color(0xFFCDD3DA), o(0.313f, 0.84f), o(0.313f, 0.875f), strokeWidth = 0.004f * w)
    } }

    // ---- Head: dips when he nods, turns when he thinks, and rises a little with each breath.
    translate(0f, (nod * 0.025f - breathe * 0.012f) * w) {
    rotate(tilt, pivot = o(cx, 0.64f)) {
        // Ears.
        for (side in listOf(-1f, 1f)) {
            val ex = if (side < 0) 0.245f else 0.685f
            drawOval(skinLo, o(ex, 0.35f), sz(0.07f, 0.12f))
            drawOval(skinDeep.copy(alpha = 0.45f), o(ex + 0.016f, 0.375f), sz(0.034f, 0.068f))
        }
        // Face: lit from the upper left.
        val face = path {
            m(cx, 0.14f); c(0.64f, 0.14f, 0.72f, 0.24f, 0.72f, 0.37f); c(0.72f, 0.50f, 0.66f, 0.62f, cx, 0.665f)
            c(0.34f, 0.62f, 0.28f, 0.50f, 0.28f, 0.37f); c(0.28f, 0.24f, 0.36f, 0.14f, cx, 0.14f); close()
        }
        drawPath(face, androidx.compose.ui.graphics.Brush.radialGradient(listOf(skinHi, skin, skinLo), center = o(0.45f, 0.33f), radius = 0.36f * w))
        // A neat, trimmed beard along the jaw and chin, fading in on the cheeks.
        val beardOutline = path {
            m(cx, 0.14f); c(0.645f, 0.14f, 0.726f, 0.24f, 0.726f, 0.37f); c(0.722f, 0.51f, 0.662f, 0.632f, cx, 0.677f)
            c(0.338f, 0.632f, 0.278f, 0.51f, 0.278f, 0.37f); c(0.274f, 0.24f, 0.355f, 0.14f, cx, 0.14f); close()
        }
        val beardArea = path {
            m(0.20f, 0.45f); l(0.27f, 0.45f); c(0.33f, 0.515f, 0.42f, 0.530f, cx, 0.533f); c(0.58f, 0.530f, 0.67f, 0.515f, 0.73f, 0.45f)
            l(0.80f, 0.45f); l(0.80f, 0.75f); l(0.20f, 0.75f); close()
        }
        clipPath(beardOutline) {
            drawPath(beardArea, androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(beard.copy(alpha = 0.30f), beard.copy(alpha = 0.72f), beard.copy(alpha = 0.86f)), startY = 0.45f * w, endY = 0.62f * w))
        }
        // Hair with a side parting, and sideburns down to the beard.
        val hairPath = path {
            m(0.275f, 0.40f); c(0.245f, 0.24f, 0.33f, 0.085f, cx, 0.085f); c(0.68f, 0.085f, 0.765f, 0.22f, 0.725f, 0.40f)
            c(0.715f, 0.32f, 0.69f, 0.255f, 0.62f, 0.225f); c(0.54f, 0.20f, 0.44f, 0.245f, 0.36f, 0.235f)
            c(0.31f, 0.255f, 0.29f, 0.32f, 0.285f, 0.40f); close()
        }
        drawPath(hairPath, hair)
        drawPath(path { m(0.37f, 0.15f); c(0.45f, 0.115f, 0.56f, 0.112f, 0.64f, 0.155f) }, hairHi, style = line(0.013f))
        drawPath(path { m(0.40f, 0.105f); q(0.385f, 0.15f, 0.36f, 0.20f) }, hairHi.copy(alpha = 0.7f), style = line(0.006f))
        clipPath(face) {
            for (side in listOf(-1f, 1f)) {
                val x = if (side < 0) 0.278f else 0.700f
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hair, beard.copy(alpha = 0.5f)), startY = 0.33f * w, endY = 0.48f * w),
                    o(x, 0.33f), sz(0.022f, 0.15f))
            }
        }
        // Nose.
        drawPath(path { m(0.512f, 0.425f); q(0.53f, 0.468f, 0.522f, 0.49f) }, skinLo, style = line(0.009f))
        drawPath(path { m(0.475f, 0.494f); q(cx, 0.513f, 0.525f, 0.494f) }, skinDeep.copy(alpha = 0.7f), style = line(0.008f))
        drawCircle(skinHi.copy(alpha = 0.6f), 0.008f * w, o(0.505f, 0.476f))
        // Eyes: almond-shaped, dark brown, and they look up when he thinks.
        val ey = 0.405f
        val open = (1f - blink).coerceIn(0.06f, 1f) * when (mood) { UstadMood.CHEER -> 0.72f; UstadMood.CROSS -> 0.8f; else -> 1f }
        val look = when (mood) { UstadMood.THINKING -> Offset(0.008f, -0.010f); UstadMood.SAD -> Offset(0f, 0.006f); else -> Offset.Zero }
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 0.083f
            val eye = path { m(ex - 0.042f, ey); q(ex - 0.005f, ey - 0.046f * open, ex + 0.042f, ey - 0.002f); q(ex + 0.003f, ey + 0.030f * open, ex - 0.042f, ey); close() }
            drawPath(eye, Color(0xFFFBF7F2))
            clipPath(eye) {
                val iris = o(ex + look.x, ey - 0.003f + look.y)
                drawCircle(Color(0xFF4B2E20), 0.020f * w, iris)
                drawCircle(Color(0xFF120D0B), 0.0095f * w, iris)
                drawCircle(Color.White, 0.0055f * w, iris + o(-0.007f, -0.007f))
            }
            drawPath(path { m(ex - 0.044f, ey + 0.001f); q(ex - 0.005f, ey - 0.046f * open, ex + 0.044f, ey - 0.003f) }, Color(0xFF2A1E1A), style = line(0.009f))
        }
        // Glasses: thin dark frames with a glint, temples running back to the ears.
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 0.083f
            drawRoundRect(Color.White.copy(alpha = 0.10f), o(ex - 0.066f, ey - 0.047f), sz(0.132f, 0.092f), androidx.compose.ui.geometry.CornerRadius(0.03f * w))
            drawRoundRect(frame, o(ex - 0.066f, ey - 0.047f), sz(0.132f, 0.092f), androidx.compose.ui.geometry.CornerRadius(0.03f * w), style = Stroke(0.011f * w))
            drawLine(Color.White.copy(alpha = 0.5f), o(ex + 0.018f, ey - 0.036f), o(ex + 0.045f, ey - 0.010f), strokeWidth = 0.007f * w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(frame, o(ex + side * 0.066f, ey - 0.015f), o(cx + side * 0.215f, ey - 0.022f), strokeWidth = 0.009f * w)
        }
        drawPath(path { m(cx - 0.017f, ey - 0.015f); q(cx, ey - 0.028f, cx + 0.017f, ey - 0.015f) }, frame, style = line(0.010f))
        // Eyebrows carry the mood (and lift to say hello).
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 0.083f
            val y = 0.338f - lift * 0.016f - (if (mood == UstadMood.CHEER) 0.008f else 0f)
            val (inner, outer) = when (mood) {
                UstadMood.CROSS -> 0.016f to -0.006f
                UstadMood.SAD -> -0.014f to 0.008f
                UstadMood.THINKING -> if (side < 0) -0.014f to -0.012f else 0.002f to 0f
                else -> 0f to 0.002f
            }
            drawPath(path {
                m(ex - side * 0.045f, y + inner); q(ex + side * 0.004f, y - 0.014f + (inner + outer) / 2f, ex + side * 0.052f, y + outer)
            }, hair, style = line(0.017f))
        }
        // A little warmth in the cheeks when he's pleased.
        if (mood == UstadMood.HAPPY || mood == UstadMood.CHEER) for (x in listOf(0.335f, 0.59f))
            drawOval(Color(0xFFE07A6E).copy(alpha = 0.16f), o(x, 0.448f), sz(0.075f, 0.038f))
        // Moustache and mouth.
        drawPath(path {
            m(0.435f, 0.572f); c(0.45f, 0.548f, 0.48f, 0.545f, cx, 0.553f); c(0.52f, 0.545f, 0.55f, 0.548f, 0.565f, 0.572f)
            c(0.54f, 0.565f, 0.52f, 0.566f, cx, 0.570f); c(0.48f, 0.566f, 0.46f, 0.565f, 0.435f, 0.572f); close()
        }, hair)
        when (mood) {
            UstadMood.CHEER -> {
                val mouth = path { m(0.448f, 0.578f); q(cx, 0.589f, 0.552f, 0.578f); q(0.545f, 0.642f, cx, 0.644f); q(0.455f, 0.642f, 0.448f, 0.578f); close() }
                drawPath(mouth, mouthIn)
                clipPath(mouth) {
                    drawRect(Color.White, o(0.44f, 0.575f), sz(0.12f, 0.024f))
                    drawOval(Color(0xFFD9686A), o(0.47f, 0.617f), sz(0.06f, 0.04f))
                }
            }
            UstadMood.HAPPY -> {
                drawPath(path { m(0.465f, 0.600f); q(cx, 0.620f, 0.535f, 0.600f) }, lip, style = line(0.011f))
                drawPath(path { m(0.448f, 0.579f); q(cx, 0.612f, 0.552f, 0.579f) }, mouthIn, style = line(0.012f))
            }
            UstadMood.SAD -> drawPath(path { m(0.46f, 0.600f); q(cx, 0.578f, 0.54f, 0.600f) }, mouthIn, style = line(0.011f))
            UstadMood.CROSS -> drawLine(mouthIn, o(0.463f, 0.592f), o(0.537f, 0.588f), strokeWidth = 0.012f * w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
            UstadMood.THINKING -> drawPath(path { m(0.474f, 0.593f); q(0.51f, 0.588f, 0.54f, 0.582f) }, mouthIn, style = line(0.011f))
        }
    }
    }

    // ---- Hands, in front: chin on his hand when he thinks, a thumbs-up when he cheers.
    if (mood == UstadMood.THINKING) {
        val sleeve = path { m(0.62f, 1.08f); c(0.62f, 0.86f, 0.60f, 0.78f, 0.585f, 0.72f); l(0.665f, 0.715f); c(0.70f, 0.80f, 0.74f, 0.90f, 0.78f, 1.08f); close() }
        drawPath(sleeve, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(kameez, kameezLo), startY = 0.70f * w, endY = w))
        drawRoundRect(skin, o(0.555f, 0.635f), sz(0.11f, 0.095f), androidx.compose.ui.geometry.CornerRadius(0.04f * w))
        drawRoundRect(skin, o(0.62f, 0.555f), sz(0.032f, 0.095f), androidx.compose.ui.geometry.CornerRadius(0.016f * w))
        drawRoundRect(skinLo, o(0.62f, 0.555f), sz(0.032f, 0.095f), androidx.compose.ui.geometry.CornerRadius(0.016f * w), style = Stroke(0.004f * w))
        for (y in listOf(0.662f, 0.688f)) drawLine(skinLo, o(0.57f, y), o(0.635f, y), strokeWidth = 0.005f * w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    if (mood == UstadMood.CHEER) {
        val arm = path { m(0.79f, 1.08f); c(0.79f, 0.92f, 0.795f, 0.86f, 0.80f, 0.80f); l(0.905f, 0.80f); c(0.905f, 0.86f, 0.915f, 0.94f, 0.935f, 1.08f); close() }
        drawPath(arm, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(kameez, kameezLo), startY = 0.80f * w, endY = w))
        drawRoundRect(skin, o(0.785f, 0.69f), sz(0.13f, 0.12f), androidx.compose.ui.geometry.CornerRadius(0.04f * w))
        for (y in listOf(0.722f, 0.750f, 0.778f)) drawLine(skinLo, o(0.80f, y), o(0.865f, y), strokeWidth = 0.005f * w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(skin, o(0.795f, 0.60f), sz(0.042f, 0.115f), androidx.compose.ui.geometry.CornerRadius(0.021f * w))
        drawRoundRect(skinLo, o(0.795f, 0.60f), sz(0.042f, 0.115f), androidx.compose.ui.geometry.CornerRadius(0.021f * w), style = Stroke(0.004f * w))
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
