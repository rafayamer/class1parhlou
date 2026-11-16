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
            // Legacy icon (before Android 8): a teal rounded square with a sun ring, Ustaad inside.
            val legacy = (48 * d).toInt()
            val l = render(legacy) {
                drawRoundRect(teal, Offset.Zero, size, CornerRadius(size.width * 0.22f))
                drawCircle(sun, size.width * 0.40f, Offset(size.width / 2, size.width / 2))
                drawCircle(Color.White, size.width * 0.36f, Offset(size.width / 2, size.width / 2))
                val s = size.width * 0.78f
                translate(size.width * 0.11f, size.width * 0.12f) { scale(1f, Offset.Zero) { drawUstaad(s, UstadMood.HAPPY, waistcoat = teal) } }
            }
            File(dir, "ic_launcher.png").outputStream().use { l.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "ic_launcher_round.png").outputStream().use { l.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Adaptive foreground (108 dp, the face inside the 66 dp safe zone), over a teal background layer.
            val fg = (108 * d).toInt()
            val f = render(fg) {
                drawCircle(sun, size.width * 0.31f, Offset(size.width / 2, size.width / 2))
                drawCircle(Color.White, size.width * 0.285f, Offset(size.width / 2, size.width / 2))
                val s = size.width * 0.58f
                translate(size.width * 0.21f, size.width * 0.20f) { drawUstaad(s, UstadMood.HAPPY, waistcoat = teal) }
            }
            File(dir, "ic_launcher_foreground.png").outputStream().use { f.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
