package pk.parhlou.student.tools

import androidx.compose.ui.graphics.drawscope.clipPath
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
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

/** Ustaad in every mood, for review: ./gradlew :app:testPrototypeDebugUnitTest --tests '*MascotShotsTest' -PscreenshotDir=/tmp/shots */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MascotShotsTest {
    @Test fun moods() {
        val dir = System.getProperty("parhlou.screenshotDir")
        assumeTrue(dir != null)
        val side = 360f
        val moods = UstadMood.entries
        val img = ImageBitmap((side * moods.size).toInt(), side.toInt())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(side * moods.size, side)) {
            drawRect(Color(0xFFF7F9F8))
            // As the app shows him: a portrait in a soft circle.
            moods.forEachIndexed { i, m ->
                translate(i * side, 0f) {
                    val circle = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, side, side)) }
                    clipPath(circle) {
                        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFCFE8E8), Color(0xFF9DD0D0))))
                        drawUstaad(side, m)
                    }
                }
            }
        }
        File(dir!!).mkdirs()
        File(dir, "mascot.png").outputStream().use { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
