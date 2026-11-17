package pk.parhlou.student.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The answer keyboard types the plain Unicode the marker reads, with the cursor where the student writes next. */
class MathKeysTest {
    private fun type(vararg steps: Any): TextFieldValue = steps.fold(TextFieldValue("")) { v, s ->
        when (s) {
            is MathKeys.Key -> MathKeys.insert(v, s)
            is String -> MathKeys.insert(v, MathKeys.Key(s))
            else -> v
        }
    }
    private fun key(label: String) = (MathKeys.BASIC + MathKeys.FUNCTIONS + MathKeys.SETS + MathKeys.UNITS).first { it.label == label }

    @Test fun fractionPutsTheCursorInTheNumerator() {
        val v = type(key("a/b"), "x + 1")
        assertEquals("(x + 1)/()", v.text)
    }

    @Test fun powersRootsAndUnits() {
        assertEquals("x^(n+1)", type("x", key("xⁿ"), "n+1").text)
        assertEquals("√(16)", type(key("√"), "16").text)
        assertEquals("v = 5 m s⁻¹", type("v", key("="), "5", key("m s⁻¹")).text)
        assertEquals("3.0 × 10^(8)", type("3.0", key("×10ⁿ"), "8").text)
    }

    @Test fun replacesASelectionAndDeletes() {
        val v = MathKeys.insert(TextFieldValue("a+b", TextRange(1, 2)), key("×"))
        assertEquals("a × b", v.text)
        assertEquals("a ×b", MathKeys.backspace(TextFieldValue("a × b", TextRange(4))).text)
        assertEquals("a\n", MathKeys.newLine(TextFieldValue("a", TextRange(1))).text)
    }

    @Test fun physicsGetsUnitsAndMathsGetsSets() {
        assertEquals("Units", MathKeys.tabs(physics = true).last().first)
        assertEquals("Sets", MathKeys.tabs(physics = false).last().first)
    }

    @Test fun answersTypedOnTheKeyboardAreReadByTheMarker() {
        val typed = type("x", key("="), key("a/b"), "3").text.replace("()", "(4)")
        val f = pk.parhlou.student.domain.Expr.parse(typed.substringAfter("= "))!!
        assertEquals(0.75, f(0.0), 1e-9)
        assertEquals(8.0, pk.parhlou.student.domain.Expr.parse(type(key("√"), "64").text)!!(0.0), 1e-9)
    }
}
