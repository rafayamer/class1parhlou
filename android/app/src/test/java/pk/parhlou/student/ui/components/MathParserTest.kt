package pk.parhlou.student.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.ui.components.MathNode.Frac
import pk.parhlou.student.ui.components.MathNode.Root
import pk.parhlou.student.ui.components.MathNode.Txt

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MathParserTest {
    @Test fun fractionsBetweenGroupsNumbersAndLetters() {
        assertEquals(listOf(Frac(listOf(Txt("x + 1")), listOf(Txt("x − 2")))), MathParser.parse("(x + 1)/(x − 2)"))
        assertEquals(listOf(Txt("x = "), Frac(listOf(Txt("5")), listOf(Txt("9")))), MathParser.parse("x = 5/9"))
        assertEquals(listOf(Frac(listOf(Txt("p")), listOf(Txt("q")))), MathParser.parse("p/q"))
        assertEquals(listOf(Txt("tan 30° = "), Frac(listOf(Txt("BC")), listOf(Txt("AB")))), MathParser.parse("tan 30° = BC/AB"))
    }

    @Test fun proseIsLeftAlone() {
        assertFalse(MathParser.hasMath("Students and/or teachers"))
        assertEquals(listOf(Txt("speed in km/h")), MathParser.parse("speed in km/h"))
        assertEquals(listOf(Txt("Write the answer.")), MathParser.parse("Write the answer."))
    }

    @Test fun rootsAndNesting() {
        assertEquals(listOf(Root(listOf(Txt("a + b")))), MathParser.parse("√(a + b)"))
        val n = MathParser.parse("3/(√(2) + 1)")
        assertTrue(n.single() is Frac)
        assertTrue((n.single() as Frac).den.any { it is Root })
    }

    @Test fun powersBecomeSuperscripts() {
        assertEquals("x2 + 103", MathParser.powers("x^2 + 10^3").text)
        assertEquals("an+1", MathParser.powers("a^(n+1)").text)
    }

    /** Every string in the course parses without losing a character of text. */
    @Test fun realCourseStringsKeepTheirText() {
        val path = System.getProperty("parhlou.courseBundle") ?: return
        val json = org.json.JSONObject(java.io.File(path).readText())
        val strings = mutableListOf<String>()
        val chapters = json.getJSONObject("subject").getJSONArray("chapters")
        for (c in 0 until chapters.length()) {
            val mods = chapters.getJSONObject(c).getJSONArray("modules")
            for (m in 0 until mods.length()) {
                val sols = mods.getJSONObject(m).optJSONArray("solutions") ?: continue
                for (s in 0 until sols.length()) {
                    val o = sols.getJSONObject(s)
                    strings += o.getString("question"); strings += o.optString("answer")
                    val st = o.getJSONArray("steps"); for (k in 0 until st.length()) strings += st.getString(k)
                }
            }
        }
        fun flat(n: List<MathNode>): String = n.joinToString("") { when (it) { is Txt -> it.text; is Frac -> flat(it.num) + flat(it.den); is Root -> flat(it.body) } }
        val strip = Regex("[\\s()/√]")
        for (s in strings) assertEquals(s, strip.replace(s, ""), strip.replace(flat(MathParser.parse(s)), ""))
        println("Parsed ${strings.size} course strings; ${strings.count { MathParser.hasMath(it) }} have maths layout")
    }
}
