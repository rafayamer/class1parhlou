package pk.parhlou.student.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp

/*
 * Ustaad's portrait, drawn in code: a realistic head-and-shoulders study of a Pakistani teacher, lit from the upper left.
 * Everything is drawn in a 1 x 1 square (scaled to the canvas), so the numbers below are fractions of the picture's side.
 * Realism comes from layers: soft skin shading, detailed eyes, and hair, brows and beard built from hundreds of fine
 * strands. The strands are made once from a fixed seed, so he looks exactly the same on every frame and every phone.
 */

/** Fixed pseudo-random numbers (same sequence every time). */
private class Seeded(private var s: Long) {
    fun next(): Float {
        s = s * 6364136223846793005L + 1442695040888963407L
        return ((s ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }
    fun range(a: Float, b: Float) = a + (b - a) * next()
}

private fun path(block: Path.() -> Unit) = Path().apply(block)

// ---- Shapes (unit square) ----

private val face = path {
    moveTo(0.5f, 0.115f)
    cubicTo(0.625f, 0.115f, 0.703f, 0.205f, 0.706f, 0.33f)
    cubicTo(0.71f, 0.405f, 0.705f, 0.455f, 0.694f, 0.495f)
    cubicTo(0.682f, 0.555f, 0.648f, 0.603f, 0.600f, 0.636f)
    cubicTo(0.568f, 0.657f, 0.535f, 0.667f, 0.5f, 0.668f)
    cubicTo(0.465f, 0.667f, 0.432f, 0.657f, 0.400f, 0.636f)
    cubicTo(0.352f, 0.603f, 0.318f, 0.555f, 0.306f, 0.495f)
    cubicTo(0.295f, 0.455f, 0.29f, 0.405f, 0.294f, 0.33f)
    cubicTo(0.297f, 0.205f, 0.375f, 0.115f, 0.5f, 0.115f)
    close()
}

/** Beard: from the sideburns, along the jaw, fuller under the chin; its top edge runs over the cheeks to the moustache. */
private val beard = path {
    moveTo(0.295f, 0.43f)
    cubicTo(0.315f, 0.495f, 0.365f, 0.528f, 0.425f, 0.545f)
    cubicTo(0.45f, 0.552f, 0.47f, 0.546f, 0.5f, 0.548f)
    cubicTo(0.53f, 0.546f, 0.55f, 0.552f, 0.575f, 0.545f)
    cubicTo(0.635f, 0.528f, 0.685f, 0.495f, 0.705f, 0.43f)
    cubicTo(0.711f, 0.49f, 0.700f, 0.565f, 0.654f, 0.625f)
    cubicTo(0.612f, 0.672f, 0.556f, 0.688f, 0.5f, 0.689f)
    cubicTo(0.444f, 0.688f, 0.388f, 0.672f, 0.346f, 0.625f)
    cubicTo(0.30f, 0.565f, 0.289f, 0.49f, 0.295f, 0.43f)
    close()
}

private val moustache = path {
    moveTo(0.436f, 0.562f)
    cubicTo(0.448f, 0.545f, 0.475f, 0.538f, 0.5f, 0.544f)
    cubicTo(0.525f, 0.538f, 0.552f, 0.545f, 0.564f, 0.562f)
    cubicTo(0.545f, 0.557f, 0.525f, 0.558f, 0.5f, 0.561f)
    cubicTo(0.475f, 0.558f, 0.455f, 0.557f, 0.436f, 0.562f)
    close()
}

private val hair = path {
    moveTo(0.292f, 0.37f)
    cubicTo(0.272f, 0.25f, 0.30f, 0.10f, 0.47f, 0.072f)
    cubicTo(0.62f, 0.05f, 0.735f, 0.14f, 0.712f, 0.37f)
    lineTo(0.700f, 0.37f)
    cubicTo(0.700f, 0.30f, 0.690f, 0.25f, 0.655f, 0.228f)
    cubicTo(0.60f, 0.205f, 0.52f, 0.205f, 0.46f, 0.215f)
    cubicTo(0.40f, 0.222f, 0.345f, 0.225f, 0.318f, 0.255f)
    cubicTo(0.302f, 0.29f, 0.302f, 0.33f, 0.304f, 0.37f)
    close()
}

/** The forehead hairline (used for the soft edge of short hairs). */
private fun hairlineY(x: Float): Float {
    val t = ((x - 0.318f) / (0.655f - 0.318f)).coerceIn(0f, 1f)
    return 0.255f - 0.04f * kotlin.math.sin(t * Math.PI.toFloat()) - 0.012f * t
}

private fun ear(left: Boolean) = path {
    fun x(v: Float) = if (left) v else 1f - v
    moveTo(x(0.302f), 0.372f)
    cubicTo(x(0.286f), 0.352f, x(0.262f), 0.358f, x(0.261f), 0.40f)
    cubicTo(x(0.260f), 0.44f, x(0.270f), 0.476f, x(0.291f), 0.492f)
    cubicTo(x(0.300f), 0.498f, x(0.307f), 0.492f, x(0.307f), 0.480f)
    close()
}
private val earLeft = ear(true)
private val earRight = ear(false)
private fun concha(left: Boolean) = path {
    fun x(v: Float) = if (left) v else 1f - v
    moveTo(x(0.298f), 0.392f)
    cubicTo(x(0.282f), 0.382f, x(0.272f), 0.395f, x(0.274f), 0.42f)
    cubicTo(x(0.276f), 0.445f, x(0.284f), 0.462f, x(0.297f), 0.468f)
}
private val conchaLeft = concha(true)
private val conchaRight = concha(false)

// ---- Strands (made once) ----

/** Beard texture: short hairs growing down and slightly outwards, in three tones. */
private val beardStrands: List<Path> = run {
    val r = Seeded(7)
    val tones = List(3) { Path() }
    repeat(1250) {
        val x = r.range(0.28f, 0.72f)
        val y = r.range(0.42f, 0.70f)
        val ang = kotlin.math.atan2(1f, (x - 0.5f) * 1.4f) + r.range(-0.35f, 0.35f)
        val len = r.range(0.008f, 0.017f)
        val p = tones[(r.next() * 3).toInt().coerceAtMost(2)]
        p.moveTo(x, y)
        p.lineTo(x + kotlin.math.cos(ang) * len, y + kotlin.math.sin(ang) * len)
    }
    tones
}

private val moustacheStrands: Path = run {
    val r = Seeded(19)
    val p = Path()
    repeat(220) {
        val x = r.range(0.436f, 0.564f)
        val y = r.range(0.538f, 0.56f)
        val dir = if (x < 0.5f) -1f else 1f
        val ang = kotlin.math.atan2(1f, dir * r.range(0.4f, 1.1f))
        val len = r.range(0.006f, 0.012f)
        p.moveTo(x, y)
        p.lineTo(x + kotlin.math.cos(ang) * len, y + kotlin.math.sin(ang) * len)
    }
    p
}

/** Hair combed from a side parting on his left: strands from the hairline sweeping up and over. */
private val hairStrands: List<Path> = run {
    val r = Seeded(31)
    val tones = List(3) { Path() }
    repeat(420) {
        val x0 = r.range(0.30f, 0.70f)
        val y0 = hairlineY(x0) + r.range(-0.004f, 0.01f)
        val right = x0 > 0.40f
        val ex = if (right) x0 + r.range(0.06f, 0.16f) else x0 - r.range(0.04f, 0.09f)
        val ey = r.range(0.055f, 0.13f)
        val cx = (x0 + ex) / 2f + (if (right) -0.02f else 0.02f)
        val cy = (y0 + ey) / 2f - r.range(0.0f, 0.04f)
        val p = tones[if (r.next() < 0.18f) 2 else if (r.next() < 0.5f) 1 else 0]
        p.moveTo(x0, y0)
        p.quadraticTo(cx, cy, ex, ey)
    }
    // Short hair at the sides, above the ears, combed back.
    repeat(140) {
        val left = r.next() < 0.5f
        val y0 = r.range(0.24f, 0.37f)
        val x0 = if (left) r.range(0.285f, 0.315f) else r.range(0.685f, 0.715f)
        val p = tones[if (r.next() < 0.5f) 1 else 0]
        p.moveTo(x0, y0)
        p.quadraticTo(x0 + (if (left) 0.008f else -0.008f), y0 - 0.03f, x0 + (if (left) 0.02f else -0.02f), y0 - r.range(0.05f, 0.08f))
    }
    tones
}

/** Fine hairs crossing the hairline, so the edge is soft like real hair. */
private val hairlineStrands: Path = run {
    val r = Seeded(43)
    val p = Path()
    repeat(160) {
        val x = r.range(0.32f, 0.655f)
        val y = hairlineY(x) + r.range(0.0f, 0.008f)
        p.moveTo(x, y)
        p.lineTo(x + r.range(0.002f, 0.012f), y - r.range(0.008f, 0.016f))
    }
    p
}

private fun browStrands(seed: Long): List<Pair<Float, Float>> {
    val r = Seeded(seed)
    return List(34) { i -> (i / 33f + r.range(-0.02f, 0.02f)).coerceIn(0f, 1f) to r.range(-0.4f, 0.4f) }
}
private val browLeft = browStrands(61)
private val browRight = browStrands(67)

// ---- Colours (sampled from a photo reference) ----

private val SkinHi = Color(0xFFEDBCA0)
private val Skin = Color(0xFFD69A78)
private val SkinLo = Color(0xFFBC7D5F)
private val SkinDeep = Color(0xFF8E5440)
private val Shade = Color(0xFF5A2E22)
private val HairDark = Color(0xFF110D0C)
private val HairMid = Color(0xFF261C18)
private val HairLight = Color(0xFF54443B)
private val Lip = Color(0xFFAE6157)
private val LipDark = Color(0xFF7E3F38)
private val MouthIn = Color(0xFF3A1513)
private val Frame = Color(0xFF16161A)
private val Shirt = Color(0xFFF6F6F7)
private val ShirtShade = Color(0xFFC7C9CF)

/**
 * The drawing itself, also used to make the launcher icon: a head-and-shoulders portrait in a [w]-pixel square, shoulders
 * reaching the bottom edge, with no background. [blink] closes the eyes (0..1), [lift] raises the eyebrows (0..1), [tilt]
 * turns the head (degrees), [nod] dips the head (0..1) and [breathe] lifts the shoulders (0..1).
 */
fun DrawScope.drawUstaad(
    w: Float, mood: UstadMood, blink: Float = 0f, waistcoat: Color = Color(0xFF0B8A8A),
    lift: Float = 0f, tilt: Float = 0f, nod: Float = 0f, breathe: Float = 0f,
) = scale(w, w, pivot = Offset.Zero) {
    val smile = mood == UstadMood.HAPPY || mood == UstadMood.CHEER
    // A soft shadow behind him.
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent), Offset(0.52f, 0.55f), 0.45f), 0.45f, Offset(0.52f, 0.55f))

    // ---- Shoulders and clothes (rise a little with each breath).
    scale(1f, 1f + breathe * 0.02f, pivot = Offset(0.5f, 1f)) {
        // Neck: lit from the left, in the shadow of the jaw and beard at the top.
        val neck = path { moveTo(0.405f, 0.58f); lineTo(0.595f, 0.58f); lineTo(0.612f, 0.77f); lineTo(0.388f, 0.77f); close() }
        drawPath(neck, Brush.horizontalGradient(listOf(SkinLo, Skin, SkinLo, SkinDeep), startX = 0.39f, endX = 0.61f))
        drawPath(neck, Brush.verticalGradient(listOf(Shade.copy(alpha = 0.75f), Shade.copy(alpha = 0.25f), Color.Transparent), startY = 0.62f, endY = 0.74f))
        // Kameez.
        val shirt = path { moveTo(-0.02f, 1.02f); cubicTo(0.0f, 0.86f, 0.15f, 0.79f, 0.36f, 0.748f); lineTo(0.64f, 0.748f); cubicTo(0.85f, 0.79f, 1.0f, 0.86f, 1.02f, 1.02f); close() }
        drawPath(shirt, Brush.linearGradient(listOf(Shirt, Color(0xFFE9EAED), ShirtShade), Offset(0.15f, 0.78f), Offset(0.95f, 1.0f)))
        for ((a, b) in listOf(Offset(0.20f, 0.86f) to Offset(0.27f, 1.0f), Offset(0.80f, 0.86f) to Offset(0.74f, 1.0f)))
            drawLine(ShirtShade.copy(alpha = 0.5f), a, b, strokeWidth = 0.006f, cap = StrokeCap.Round)
        // Band collar, with the neck's shadow on it.
        val collar = path { moveTo(0.388f, 0.752f); quadraticTo(0.5f, 0.80f, 0.612f, 0.752f); lineTo(0.606f, 0.718f); quadraticTo(0.5f, 0.764f, 0.394f, 0.718f); close() }
        drawPath(collar, Brush.verticalGradient(listOf(Color(0xFFDADCE1), Shirt, Color(0xFFE2E3E7)), startY = 0.718f, endY = 0.80f))
        drawPath(collar, ShirtShade, style = Stroke(0.0025f))
        drawPath(path { moveTo(0.40f, 0.74f); quadraticTo(0.5f, 0.785f, 0.60f, 0.74f) }, Shade.copy(alpha = 0.18f), style = Stroke(0.008f))
        drawLine(ShirtShade, Offset(0.5f, 0.792f), Offset(0.5f, 1.02f), strokeWidth = 0.003f)
        for (y in listOf(0.83f, 0.89f)) {
            drawCircle(Color(0xFFE6E7EA), 0.0065f, Offset(0.507f, y))
            drawCircle(ShirtShade, 0.0065f, Offset(0.507f, y), style = Stroke(0.0015f))
        }
        // Waistcoat: two panels meeting low in a V, lit from the left.
        val coatHi = lerp(waistcoat, Color.White, 0.18f)
        val coatLo = lerp(waistcoat, Color.Black, 0.40f)
        for (side in listOf(-1f, 1f)) {
            val panel = path {
                moveTo(0.5f + side * 0.40f, 1.02f)
                cubicTo(0.5f + side * 0.38f, 0.90f, 0.5f + side * 0.30f, 0.82f, 0.5f + side * 0.17f, 0.776f)
                lineTo(0.5f + side * 0.075f, 0.772f)
                lineTo(0.5f + side * 0.012f, 1.02f)
                close()
            }
            drawPath(panel, Brush.linearGradient(listOf(coatHi, waistcoat, coatLo), Offset(0.12f, 0.80f), Offset(0.92f, 1.02f)))
            drawPath(panel, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent), startY = 0.77f, endY = 0.84f))
            drawLine(coatLo, Offset(0.5f + side * 0.075f, 0.772f), Offset(0.5f + side * 0.012f, 1.02f), strokeWidth = 0.006f)
            drawLine(Color.White.copy(alpha = if (side < 0) 0.22f else 0.08f), Offset(0.5f + side * 0.071f, 0.78f),
                Offset(0.5f + side * 0.010f, 1.02f), strokeWidth = 0.002f)
        }
        for (y in listOf(0.86f, 0.935f)) {
            val ex = 0.5f + 0.075f - 0.063f * (y - 0.772f) / 0.248f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF5D77A), Color(0xFFB8861C)), Offset(ex + 0.012f, y - 0.003f), 0.01f), 0.0085f, Offset(ex + 0.014f, y))
        }
        drawLine(coatLo, Offset(0.24f, 0.885f), Offset(0.34f, 0.878f), strokeWidth = 0.006f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF233E8C), Offset(0.296f, 0.84f), Size(0.012f, 0.044f), CornerRadius(0.006f))
        drawLine(Color(0xFFD0D5DC), Offset(0.305f, 0.844f), Offset(0.305f, 0.872f), strokeWidth = 0.003f)
    }

    // ---- Head: dips when he nods, turns when he thinks, rises a little with each breath.
    translate(0f, nod * 0.022f - breathe * 0.01f) { rotate(tilt, pivot = Offset(0.5f, 0.64f)) {
        // Ears, the right one in shadow.
        drawPath(earLeft, Brush.horizontalGradient(listOf(SkinLo, Skin), startX = 0.26f, endX = 0.307f))
        drawPath(earRight, Brush.horizontalGradient(listOf(SkinLo, SkinDeep), startX = 0.693f, endX = 0.74f))
        drawPath(conchaLeft, SkinDeep.copy(alpha = 0.7f), style = Stroke(0.006f, cap = StrokeCap.Round))
        drawPath(conchaRight, Shade.copy(alpha = 0.6f), style = Stroke(0.006f, cap = StrokeCap.Round))

        // Skin, in layers (with skin under the beard where it grows past the jaw).
        drawPath(beard, SkinLo)
        drawPath(face, Brush.radialGradient(listOf(SkinHi, Skin, SkinLo), Offset(0.43f, 0.33f), 0.34f))
        clipPath(face) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, SkinDeep.copy(alpha = 0.55f)), startX = 0.56f, endX = 0.712f), Offset(0.5f, 0f), Size(0.25f, 0.7f))
            drawRect(Brush.horizontalGradient(listOf(Shade.copy(alpha = 0.22f), Color.Transparent), startX = 0.29f, endX = 0.335f), Offset(0.28f, 0f), Size(0.06f, 0.7f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(0.46f, 0.26f), 0.10f), 0.10f, Offset(0.46f, 0.26f))
            // Eye sockets.
            for (side in listOf(-1f, 1f)) {
                val c = Offset(0.5f + side * 0.084f, 0.382f)
                drawCircle(Brush.radialGradient(listOf(Shade.copy(alpha = if (side < 0) 0.20f else 0.28f), Color.Transparent), c, 0.058f), 0.058f, c)
            }
            // Cheekbones catch the light.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), Offset(0.395f, 0.448f), 0.055f), 0.055f, Offset(0.395f, 0.448f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), Offset(0.612f, 0.448f), 0.04f), 0.04f, Offset(0.612f, 0.448f))
            if (smile) for (x in listOf(0.385f, 0.615f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFD9786A).copy(alpha = 0.16f), Color.Transparent), Offset(x, 0.465f), 0.045f), 0.045f, Offset(x, 0.465f))
            // Shadow from the hair on the forehead.
            drawRect(Brush.verticalGradient(listOf(Shade.copy(alpha = 0.30f), Color.Transparent), startY = 0.21f, endY = 0.26f), Offset(0.28f, 0.2f), Size(0.44f, 0.07f))
            // Nose: bridge light, shadow side, tip, wings, nostrils and the shadow under it.
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)), startY = 0.39f, endY = 0.48f),
                Offset(0.487f, 0.39f), Size(0.016f, 0.085f))
            drawOval(Brush.horizontalGradient(listOf(Color.Transparent, Shade.copy(alpha = 0.30f)), startX = 0.505f, endX = 0.535f), Offset(0.505f, 0.40f), Size(0.03f, 0.085f))
            drawOval(Brush.radialGradient(listOf(SkinHi.copy(alpha = 0.9f), Skin.copy(alpha = 0f)), Offset(0.498f, 0.474f), 0.022f), Offset(0.476f, 0.455f), Size(0.044f, 0.04f))
            drawOval(SkinLo.copy(alpha = 0.85f), Offset(0.452f, 0.476f), Size(0.03f, 0.026f))
            drawOval(SkinDeep.copy(alpha = 0.85f), Offset(0.518f, 0.476f), Size(0.03f, 0.026f))
            drawOval(Brush.radialGradient(listOf(SkinHi, Skin), Offset(0.495f, 0.48f), 0.03f), Offset(0.468f, 0.462f), Size(0.064f, 0.036f))
            for (x in listOf(0.477f, 0.509f)) drawOval(Color(0xFF3B201C).copy(alpha = 0.85f), Offset(x, 0.493f), Size(0.016f, 0.007f))
            drawOval(Brush.radialGradient(listOf(Shade.copy(alpha = 0.45f), Color.Transparent), Offset(0.5f, 0.507f), 0.04f), Offset(0.46f, 0.498f), Size(0.08f, 0.02f))
            // Folds from the nose to the mouth, deeper when he smiles.
            val fold = if (smile) 0.22f else 0.12f
            for (side in listOf(-1f, 1f)) drawPath(path {
                moveTo(0.5f + side * 0.045f, 0.492f); quadraticTo(0.5f + side * 0.066f, 0.515f, 0.5f + side * 0.058f, 0.548f)
            }, Shade.copy(alpha = fold), style = Stroke(0.007f, cap = StrokeCap.Round))
        }

        // Beard: a soft dark base, then the hairs, clipped to its shape.
        clipPath(beard) {
            drawRect(Brush.verticalGradient(listOf(HairMid.copy(alpha = 0.08f), HairMid.copy(alpha = 0.45f), HairDark.copy(alpha = 0.80f)), startY = 0.45f, endY = 0.64f),
                Offset(0.27f, 0.40f), Size(0.46f, 0.32f))
            val tones = listOf(HairDark, HairMid, HairLight)
            val strength = listOf(0.9f, 0.85f, 0.35f)
            beardStrands.forEachIndexed { i, p ->
                drawPath(p, Brush.verticalGradient(listOf(tones[i].copy(alpha = 0.18f * strength[i]), tones[i].copy(alpha = strength[i])), startY = 0.45f, endY = 0.60f),
                    style = Stroke(0.0024f, cap = StrokeCap.Round))
            }
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), startX = 0.55f, endX = 0.72f), Offset(0.5f, 0.38f), Size(0.23f, 0.34f))
        }

        // Mouth and lips (over the beard), by mood.
        drawMouth(mood)
        drawPath(moustache, HairDark.copy(alpha = 0.9f))
        clipPath(moustache) { drawPath(moustacheStrands, HairMid, style = Stroke(0.0024f, cap = StrokeCap.Round)) }
        drawPath(moustacheStrands, HairDark.copy(alpha = 0.5f), style = Stroke(0.0018f, cap = StrokeCap.Round))

        // Eyes.
        val open = (1f - blink).coerceIn(0.05f, 1f) * when (mood) { UstadMood.CHEER -> 0.7f; UstadMood.HAPPY -> 0.9f; UstadMood.CROSS -> 0.8f; else -> 1f }
        val look = when (mood) { UstadMood.THINKING -> Offset(0.006f, -0.006f); UstadMood.SAD -> Offset(0f, 0.004f); else -> Offset.Zero }
        for (side in listOf(-1f, 1f)) drawEye(0.5f + side * 0.084f, 0.388f, side, open, look, smile)

        // Glasses: their soft shadow on the skin, the lenses, then the frame with a highlight.
        clipPath(face) {
            for (side in listOf(-1f, 1f)) {
                val x = 0.5f + side * 0.084f - 0.048f
                drawLine(Color.Black.copy(alpha = 0.07f), Offset(x + 0.012f, 0.429f), Offset(x + 0.092f, 0.429f), strokeWidth = 0.006f, cap = StrokeCap.Round)
            }
        }
        for (side in listOf(-1f, 1f)) {
            val x = 0.5f + side * 0.084f - 0.048f
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(x, 0.359f), Size(0.096f, 0.062f), CornerRadius(0.016f))
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), Offset(x, 0.359f), Offset(x + 0.05f, 0.40f)),
                Offset(x, 0.359f), Size(0.096f, 0.062f), CornerRadius(0.016f))
            drawRoundRect(Frame, Offset(x, 0.359f), Size(0.096f, 0.062f), CornerRadius(0.016f), style = Stroke(0.0045f))
            drawLine(Frame, Offset(x + 0.012f, 0.3595f), Offset(x + 0.084f, 0.3595f), strokeWidth = 0.0065f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x + 0.016f, 0.3575f), Offset(x + 0.05f, 0.3575f), strokeWidth = 0.0015f, cap = StrokeCap.Round)
            drawLine(Frame, Offset(0.5f + side * 0.132f, 0.372f), Offset(0.5f + side * 0.205f, 0.378f), strokeWidth = 0.004f, cap = StrokeCap.Round)
        }
        drawPath(path { moveTo(0.452f, 0.373f); quadraticTo(0.5f, 0.360f, 0.548f, 0.373f) }, Frame, style = Stroke(0.0045f))

        // Eyebrows (over the frame's top), carrying the mood.
        for (side in listOf(-1f, 1f)) drawBrow(side, mood, lift)

        // Hair: a dark base, strands, highlights on the lit side, a soft hairline and sideburns.
        drawPath(hair, Brush.linearGradient(listOf(HairMid, HairDark), Offset(0.35f, 0.08f), Offset(0.7f, 0.35f)))
        clipPath(hair) {
            val tones = listOf(HairDark, HairMid, HairLight.copy(alpha = 0.7f))
            hairStrands.forEachIndexed { i, p -> drawPath(p, tones[i], style = Stroke(if (i == 2) 0.0022f else 0.0032f, cap = StrokeCap.Round)) }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset(0.42f, 0.11f), 0.09f), 0.09f, Offset(0.42f, 0.11f))
        }
        drawPath(hairlineStrands, HairMid.copy(alpha = 0.55f), style = Stroke(0.002f, cap = StrokeCap.Round))
        clipPath(face) {
            for (side in listOf(-1f, 1f)) {
                val edge = if (side < 0) 0.29f else 0.71f
                val c = Offset(edge, 0.405f)
                drawOval(Brush.radialGradient(listOf(HairDark.copy(alpha = 0.85f), HairMid.copy(alpha = 0.45f), Color.Transparent), c, 0.05f),
                    Offset(edge - 0.03f, 0.335f), Size(0.06f, 0.14f))
            }
        }
    } }
}

private fun DrawScope.drawEye(ex: Float, ey: Float, side: Float, open: Float, look: Offset, smile: Boolean) {
    val a = 0.038f
    val inner = Offset(ex - side * a, ey + 0.002f)
    val outer = Offset(ex + side * a, ey - 0.001f)
    val top = 0.021f * open
    val bottom = 0.012f * open * (if (smile) 0.7f else 1f)
    val eye = path {
        moveTo(inner.x, inner.y)
        quadraticTo(ex - side * 0.004f, ey - 2f * top, outer.x, outer.y)
        quadraticTo(ex, ey + 2f * bottom, inner.x, inner.y)
        close()
    }
    drawPath(eye, Brush.radialGradient(listOf(Color(0xFFF2ECE6), Color(0xFFD9CCC3), Color(0xFFB9A79D)), Offset(ex, ey), a))
    clipPath(eye) {
        val iris = Offset(ex + look.x, ey - 0.001f + look.y)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6E4932), Color(0xFF41281A), Color(0xFF1C110A)), iris, 0.0155f), 0.0155f, iris)
        drawCircle(Color(0xFF110A06), 0.0155f, iris, style = Stroke(0.0025f))
        drawCircle(Color(0xFF070404), 0.0058f, iris)
        drawCircle(Color.White.copy(alpha = 0.92f), 0.0034f, iris + Offset(-0.005f, -0.005f))
        drawCircle(Color.White.copy(alpha = 0.55f), 0.0015f, iris + Offset(0.005f, 0.004f))
        // The upper lid's shadow on the eye.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent), startY = ey - top, endY = ey - top + 0.012f),
            Offset(ex - a, ey - 0.03f), Size(2f * a, 0.05f))
    }
    // Lashes along the upper lid (heavier at the outer corner), the lower lid, the crease above, and the shadow under the eye.
    val lid = path { moveTo(inner.x, inner.y); quadraticTo(ex - side * 0.004f, ey - 2f * top, outer.x, outer.y) }
    drawPath(lid, Color(0xFF1A110E), style = Stroke(0.0042f, cap = StrokeCap.Round))
    drawPath(path { moveTo(ex, ey - top); quadraticTo(ex + side * 0.025f, ey - top * 0.8f, outer.x + side * 0.002f, outer.y - 0.001f) },
        Color(0xFF1A110E), style = Stroke(0.0035f, cap = StrokeCap.Round))
    drawPath(path { moveTo(inner.x, inner.y); quadraticTo(ex, ey + 2f * bottom, outer.x, outer.y) }, SkinDeep.copy(alpha = 0.55f), style = Stroke(0.002f))
    drawPath(path {
        moveTo(ex - side * 0.03f, ey - top - 0.006f); quadraticTo(ex, ey - top - 0.020f, ex + side * 0.035f, ey - top - 0.006f)
    }, SkinDeep.copy(alpha = 0.55f), style = Stroke(0.0028f, cap = StrokeCap.Round))
    val under = if (smile) 0.014f else 0.022f
    drawPath(path {
        moveTo(ex - side * 0.03f, ey + under); quadraticTo(ex, ey + under + 0.010f, ex + side * 0.032f, ey + under - 0.002f)
    }, Shade.copy(alpha = 0.22f), style = Stroke(0.006f, cap = StrokeCap.Round))
}

private fun DrawScope.drawBrow(side: Float, mood: UstadMood, lift: Float) {
    val ex = 0.5f + side * 0.084f
    val (innerDy, outerDy) = when (mood) {
        UstadMood.CROSS -> 0.010f to -0.004f
        UstadMood.SAD -> -0.010f to 0.006f
        UstadMood.THINKING -> if (side > 0) -0.012f to -0.010f else 0.002f to 0f
        UstadMood.CHEER -> -0.006f to -0.004f
        else -> 0f to 0f
    }
    val up = lift * 0.012f
    val knit = if (mood == UstadMood.CROSS) side * -0.004f else 0f
    val x0 = ex - side * 0.044f + knit
    val x1 = ex + side * 0.052f
    fun yAt(t: Float): Float {
        val base = 0.347f + (innerDy + (outerDy - innerDy) * t) - up
        val arch = -0.008f * kotlin.math.sin((t * 0.85f + 0.05f) * Math.PI.toFloat())
        return base + arch
    }
    fun xAt(t: Float) = x0 + (x1 - x0) * t
    // A tapered dark base (thick at the inner end), then hairs that slant outwards.
    val shape = path {
        moveTo(xAt(0f), yAt(0f) - 0.0065f)
        for (i in 1..10) { val t = i / 10f; lineTo(xAt(t), yAt(t) - 0.0065f * (1f - t * 0.75f)) }
        for (i in 10 downTo 0) { val t = i / 10f; lineTo(xAt(t), yAt(t) + 0.0055f * (1f - t * 0.75f)) }
        close()
    }
    drawPath(shape, Color(0xFF1E1513).copy(alpha = 0.72f))
    val strands = if (side < 0) browLeft else browRight
    val p = Path()
    for ((t, jitter) in strands) {
        val x = xAt(t); val y = yAt(t) + 0.004f * (1f - t * 0.6f) - 0.002f
        val len = 0.011f * (1f - t * 0.4f)
        val ang = (if (t < 0.15f) -1.35f else -0.45f) + jitter * 0.4f
        p.moveTo(x, y)
        p.lineTo(x + side * kotlin.math.cos(ang) * len, y + kotlin.math.sin(ang) * len)
    }
    drawPath(p, Color(0xFF120C0A), style = Stroke(0.0019f, cap = StrokeCap.Round))
    if (mood == UstadMood.CROSS) for (x in listOf(0.492f, 0.508f))
        drawLine(Shade.copy(alpha = 0.28f), Offset(x, 0.336f), Offset(x + (x - 0.5f) * 0.3f, 0.352f), strokeWidth = 0.003f, cap = StrokeCap.Round)
}

private fun DrawScope.drawMouth(mood: UstadMood) {
    val round = Stroke(0.0035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mood) {
        UstadMood.HAPPY, UstadMood.CHEER -> {
            val big = mood == UstadMood.CHEER
            val cw = if (big) 0.058f else 0.052f
            val cy = if (big) 0.560f else 0.564f
            val depth = if (big) 0.060f else 0.034f
            val opening = path {
                moveTo(0.5f - cw, cy); quadraticTo(0.5f, cy + 0.012f, 0.5f + cw, cy)
                quadraticTo(0.5f, cy + depth * 1.25f, 0.5f - cw, cy); close()
            }
            // Lower lip around the opening.
            drawPath(path {
                moveTo(0.5f - cw, cy); quadraticTo(0.5f, cy + depth * 1.25f + 0.032f, 0.5f + cw, cy)
                quadraticTo(0.5f, cy + depth * 1.25f, 0.5f - cw, cy); close()
            }, Brush.verticalGradient(listOf(LipDark, Lip, Color(0xFFC07468)), startY = cy, endY = cy + depth + 0.03f))
            drawPath(opening, MouthIn)
            clipPath(opening) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF4EFE9), Color(0xFFD9D0C6)), startY = cy, endY = cy + 0.02f),
                    Offset(0.5f - cw, cy - 0.01f), Size(2f * cw, if (big) 0.03f else 0.026f))
                for (x in listOf(0.484f, 0.5f, 0.516f)) drawLine(Color(0xFFB8AEA4).copy(alpha = 0.5f), Offset(x, cy), Offset(x, cy + 0.02f), strokeWidth = 0.0012f)
                if (big) drawOval(Color(0xFFC25A5E), Offset(0.475f, cy + depth * 0.75f), Size(0.05f, 0.03f))
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    startX = 0.5f - cw, endX = 0.5f + cw), Offset(0.5f - cw, cy - 0.01f), Size(2f * cw, 0.08f))
            }
            drawOval(Color.White.copy(alpha = 0.16f), Offset(0.488f, cy + depth * 0.625f + 0.007f), Size(0.024f, 0.006f))
        }
        else -> {
            // Closed lips: the mouth line's shape carries the mood.
            val (l, mid, r) = when (mood) {
                UstadMood.SAD -> Triple(0.578f, 0.566f, 0.578f)
                UstadMood.CROSS -> Triple(0.571f, 0.570f, 0.571f)
                else -> Triple(0.574f, 0.571f, 0.567f) // thinking: a little to one side
            }
            val cw = if (mood == UstadMood.CROSS) 0.044f else 0.049f
            val thin = if (mood == UstadMood.CROSS) 0.6f else 1f
            drawPath(path {
                moveTo(0.5f - cw, l); quadraticTo(0.5f, mid + 0.026f * thin, 0.5f + cw, r)
                quadraticTo(0.5f, mid + 0.004f, 0.5f - cw, l); close()
            }, Brush.verticalGradient(listOf(LipDark, Lip, Color(0xFFC07468)), startY = mid, endY = mid + 0.026f))
            drawOval(Color.White.copy(alpha = 0.16f * thin), Offset(0.49f, mid + 0.010f), Size(0.022f, 0.006f))
            drawPath(path { moveTo(0.5f - cw, l); quadraticTo(0.5f, mid - 0.004f, 0.5f + cw, r); quadraticTo(0.5f, mid - 0.012f, 0.5f - cw, l); close() }, LipDark)
            drawPath(path { moveTo(0.5f - cw, l); quadraticTo(0.5f, mid + 0.003f, 0.5f + cw, r) }, Color(0xFF3E1C19), style = round)
        }
    }
}
