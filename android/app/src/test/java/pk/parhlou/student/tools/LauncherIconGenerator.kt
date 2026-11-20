package pk.parhlou.student.tools

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.drawUstaad
import java.io.File

/**
 * Draws the launcher icons from Ustaad's face (the same code the app draws him with).
 * Run only when asked: ./gradlew :app:testDebugUnitTest --tests '*LauncherIconGenerator' -PiconOut=app/src/main/res
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LauncherIconGenerator {
    private val teal = Color(0xFF0B8A8A)
    private val sun = Color(0xFFFFC93C)

    private fun render(px: Int, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(px.toFloat(), px.toFloat()), draw)
        return img.asAndroidBitmap()
    }

    @Test fun generate() {
        val out = System.getProperty("parhlou.iconOut")
        assumeTrue("set -PiconOut to write the icons", out != null)
        val densities = mapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)
        for ((name, d) in densities) {
            val dir = File(out!!, "mipmap-$name").apply { mkdirs() }
            // Legacy icon (before Android 8): the brand gradient in a rounded square, Ustaad in the middle.
            val legacy = (48 * d).toInt()
            val l = render(legacy) {
                drawRoundRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0B7A7A), Color(0xFF4F53D6)), Offset.Zero, Offset(size.width, size.height)),
                    Offset.Zero, size, CornerRadius(size.width * 0.24f))
                val s = size.width * 0.80f
                translate(size.width * 0.10f, size.width * 0.09f) { drawUstaad(s, UstadMood.HAPPY, waistcoat = Color(0xFF14B8A6)) }
            }
            File(dir, "ic_launcher.png").outputStream().use { l.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "ic_launcher_round.png").outputStream().use { l.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Adaptive foreground (108 dp): Ustaad inside the 66 dp safe zone, over the gradient background drawable.
            val fg = (108 * d).toInt()
            val f = render(fg) {
                val s = size.width * 0.62f
                translate(size.width * 0.19f, size.width * 0.18f) { drawUstaad(s, UstadMood.HAPPY, waistcoat = Color(0xFF14B8A6)) }
            }
            File(dir, "ic_launcher_foreground.png").outputStream().use { f.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
