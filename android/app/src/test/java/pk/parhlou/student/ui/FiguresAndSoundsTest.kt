package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.student.domain.Expr
import pk.parhlou.student.ui.components.FigureSpec
import pk.parhlou.student.ui.components.FigureView
import pk.parhlou.student.ui.components.Sfx
import pk.parhlou.student.ui.components.SoundBoard
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Ustaad's figures are drawn exactly from numbers, and every UI sound is in the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h1600dp")
class FiguresAndSoundsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun expressionsAreReadSafely() {
        assertEquals(1.0, Expr.parse("2*x - 3")!!(2.0), 1e-9)
        assertEquals(7.0, Expr.parse("3x + 1")!!(2.0), 1e-9)
        assertEquals(6.0, Expr.parse("2(x+1)")!!(2.0), 1e-9)
        assertEquals(0.0, Expr.parse("x^2 - 4")!!(-2.0), 1e-9)
        assertEquals(3.0, Expr.parse("√(x)")!!(9.0), 1e-9)
        assertEquals(1.0, Expr.parse("sin(pi/2)")!!(0.0), 1e-9)
        assertEquals("2x − 3", pk.parhlou.student.ui.components.pretty("2*x-3"))
        assertEquals("x² − 4", pk.parhlou.student.ui.components.pretty("x^2-4"))
        assertTrue(Expr.parse("sqrt(x)")!!(-1.0).isNaN())     // a gap, not a crash
        assertTrue(Expr.parse("1/x")!!(0.0).isNaN())
        assertNull(Expr.parse("System.exit(0)"))
        assertNull(Expr.parse("x+(1"))
        assertNull(Expr.parse("y = 2x"))
    }

    private val figures = listOf(
        """{"kind":"graph","title":"y = 2x − 3 and y = x² − 4","functions":["2*x-3","x^2-4"],"xMin":-5,"xMax":5,"yMin":-8,"yMax":8,"points":[{"x":1.5,"y":0,"label":"A"}]}""",
        """{"kind":"graph","title":"x + y ≤ 4","functions":[],"shade":{"expression":"4-x","side":"below","strict":false},"xMin":-2,"xMax":6,"yMin":-2,"yMax":6}""",
        """{"kind":"number_line","title":"−2 ≤ x < 3","intervals":[{"from":-2,"to":3,"fromClosed":true,"toClosed":false,"label":"−2 ≤ x < 3"}],"xMin":-10,"xMax":10}""",
        """{"kind":"coordinate","title":"Distance AB","points":[{"x":-1,"y":2,"label":"A"},{"x":3,"y":5,"label":"B"}],"segments":[{"from":"A","to":"B","label":"5"}],"xMin":-3,"xMax":5,"yMin":-1,"yMax":7}""",
        """{"kind":"geometry","title":"Right triangle ABC","points":[{"x":0,"y":0,"label":"B"},{"x":4,"y":0,"label":"C"},{"x":0,"y":3,"label":"A"}],"segments":[{"from":"B","to":"C","label":"4 cm"},{"from":"A","to":"B","label":"3 cm"},{"from":"A","to":"C","label":"5 cm"}],"angles":[{"at":"B","label":"","right":true},{"at":"C","label":"θ","right":false}]}""",
        """{"kind":"venn","title":"A ∪ B","sets":[{"name":"A","only":["1"]},{"name":"B","only":["4","5"]}],"both":["2","3"],"outside":["6","7"]}""",
        """{"kind":"bar","title":"Marks","categories":["Ali","Sara","Hina"],"values":[45,60,52]}""",
        """{"kind":"pie","title":"Time spent","categories":["Study","Sleep","Play"],"values":[6,8,4]}""",
        """{"kind":"table","title":"Table of values","headers":["x","-1","0","1","2"],"rows":[["y","-5","-3","-1","1"]]}""",
    )

    @Test fun everyKindOfFigureIsDrawn() {
        val specs = figures.map { FigureSpec.parse(JSONObject(it)) }
        specs.forEach { assertNotNull(it) }
        val dir = System.getProperty("parhlou.screenshotDir")
        var dark by androidx.compose.runtime.mutableStateOf(false)
        var half by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            ParhlouTheme(dark = dark) {
                Surface {
                    Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        (if (half == 0) specs.take(4) else specs.drop(4)).forEach { FigureView(it!!, Modifier.padding(bottom = 12.dp)) }
                    }
                }
            }
        }
        for ((d, h) in listOf(false to 0, false to 1, true to 0, true to 1)) {
            dark = d; half = h
            rule.waitForIdle()
            (if (h == 0) listOf("graph", "number_line", "coordinate") else listOf("geometry", "venn", "bar", "pie", "table"))
                .forEach { assertTrue(it, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("figure-$it"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) }
            if (dir != null) {
                val view = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bmp))
                File(dir).mkdirs()
                File(dir, "figures-${if (d) "dark" else "light"}-$h.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private val physicsFigures = listOf(
        """{"kind":"forces","title":"Book resting on a table","forces":[{"label":"W = 20 N","angle":270,"size":20},{"label":"R = 20 N","angle":90,"size":20},{"label":"F = 5 N","angle":0,"size":5}]}""",
        """{"kind":"circuit","title":"Series circuit","components":[{"type":"battery","label":"6 V"},{"type":"switch","label":""},{"type":"resistor","label":"2 Ω"},{"type":"bulb","label":""},{"type":"ammeter","label":""}],"arrangement":"series"}""",
        """{"kind":"circuit","title":"Parallel circuit","components":[{"type":"cell","label":"1.5 V"},{"type":"bulb","label":"L1"},{"type":"bulb","label":"L2"},{"type":"voltmeter","label":""}],"arrangement":"parallel"}""",
    )

    @Test fun physicsDiagramsAreDrawn() {
        val specs = physicsFigures.map { FigureSpec.parse(JSONObject(it)) }
        specs.forEach { assertNotNull(it) }
        assertEquals(3, specs[0]!!.forces.size)
        assertTrue(specs[2]!!.parallel)
        val dir = System.getProperty("parhlou.screenshotDir")
        var dark by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            ParhlouTheme(dark = dark) {
                Surface { Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()).padding(12.dp)) { specs.forEach { FigureView(it!!, Modifier.padding(bottom = 12.dp)) } } }
            }
        }
        for (d in listOf(false, true)) {
            dark = d
            rule.waitForIdle()
            listOf("forces", "circuit").forEach { assertTrue(it, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("figure-$it"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) }
            if (dir != null) {
                val view = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bmp))
                File(dir).mkdirs()
                File(dir, "physics-figures-${if (d) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun everySoundIsBundled() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Sfx.entries.forEach { s -> ctx.resources.openRawResource(s.res).use { assertTrue("${s.name} is empty", it.available() > 1000) } }
        val board = SoundBoard(ctx)
        board.enabled = false
        board.play(Sfx.CORRECT)   // muted: nothing plays, nothing fails
        board.release()
    }

    @Test fun anAnswerKeepsItsFigure() {
        val a = pk.parhlou.student.ai.UstaadAnswer.parse(JSONObject("""{"kind":"CHAT","reply":"Here is the graph.","intent":"CONCEPT","figure":${figures[0]}}"""))!!
        assertEquals("CONCEPT", a.intent)
        assertEquals("graph", FigureSpec.parse(JSONObject(a.figure))!!.kind)
        val back = pk.parhlou.student.ai.UstaadAnswer.parse(a.toJson())!!
        assertEquals(a.figure.length > 10, back.figure.isNotBlank())
        val bad = pk.parhlou.student.ai.UstaadAnswer.parse(JSONObject("""{"kind":"CHAT","reply":"x","figure":{"kind":"hologram"}}"""))!!
        assertEquals("", bad.figure)
    }
}
