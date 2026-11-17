package pk.parhlou.student.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * The answer keyboard for maths and physics: the symbols a board answer needs that a phone keyboard lacks. It writes the same
 * plain Unicode the course and the marker use (fractions as (a)/(b), powers as x^(n), units as m s⁻¹), so marking is unchanged.
 * Letters and digits still come from the phone keyboard; this panel sits under the answer box.
 */
object MathKeys {
    /** A key: what it shows and what it types; "|" marks where the cursor goes (default: after the text). */
    data class Key(val label: String, val text: String = label)

    val BASIC = listOf(
        Key("+", " + "), Key("−", " − "), Key("×", " × "), Key("÷", " ÷ "), Key("=", " = "), Key("( )", "(|)"),
        Key("a/b", "(|)/()"), Key("xⁿ", "^(|)"), Key("x²", "²"), Key("x³", "³"), Key("√", "√(|)"), Key("∛", "∛(|)"),
        Key("±", "±"), Key("≠", " ≠ "), Key("≤", " ≤ "), Key("≥", " ≥ "), Key("π", "π"), Key("θ", "θ"), Key("°", "°"),
        Key("|x|", "|(|)|"), Key("x₁", "_|"), Key("∞", "∞"), Key("⇒", " ⇒ "),
    )
    val FUNCTIONS = listOf(
        Key("log", "log |"), Key("ln", "ln |"), Key("sin", "sin |"), Key("cos", "cos |"), Key("tan", "tan |"),
        Key("antilog", "antilog |"), Key("∴", "∴ "), Key("∵", "∵ "),
    )
    val SETS = listOf(
        Key("∈"), Key("∉"), Key("⊂"), Key("⊆"), Key("∪", " ∪ "), Key("∩", " ∩ "), Key("∅"), Key("{ }", "{|}"),
        Key("→", " → "), Key("⇔", " ⇔ "), Key("∧", " ∧ "), Key("∨", " ∨ "), Key("~"), Key("ℝ"), Key("ℕ"), Key("ℤ"), Key("ℚ"),
    )
    val UNITS = listOf(
        Key("×10ⁿ", " × 10^(|)"), Key("m"), Key("s"), Key("kg"), Key("N"), Key("J"), Key("W"), Key("Pa"), Key("A"), Key("V"),
        Key("Ω"), Key("°C"), Key("K"), Key("m s⁻¹", " m s⁻¹"), Key("m s⁻²", " m s⁻²"), Key("⁻¹"), Key("⁻²"),
        Key("Δ"), Key("μ"), Key("λ"), Key("ρ"), Key("η"), Key("Σ"),
    )

    fun tabs(physics: Boolean): List<Pair<String, List<Key>>> =
        listOf("x²" to BASIC, "f(x)" to FUNCTIONS) + if (physics) listOf("Units" to UNITS) else listOf("Sets" to SETS)

    /** Types [key] at the cursor (replacing any selection) and puts the cursor where the key says. */
    fun insert(value: TextFieldValue, key: Key): TextFieldValue {
        val start = minOf(value.selection.start, value.selection.end)
        val end = maxOf(value.selection.start, value.selection.end)
        val mark = key.text.indexOf('|')
        val typed = key.text.replace("|", "")
        val text = value.text.substring(0, start) + typed + value.text.substring(end)
        return TextFieldValue(text, TextRange(start + if (mark >= 0) mark else typed.length))
    }

    /** One step per line: a new line, or deletes the character before the cursor. */
    fun newLine(value: TextFieldValue) = insert(value, Key("⏎", "\n"))
    fun backspace(value: TextFieldValue): TextFieldValue {
        val s = value.selection
        if (!s.collapsed) return insert(value, Key("", ""))
        if (s.start == 0) return value
        return TextFieldValue(value.text.removeRange(s.start - 1, s.start), TextRange(s.start - 1))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MathKeyboard(value: TextFieldValue, onValue: (TextFieldValue) -> Unit, physics: Boolean, modifier: Modifier = Modifier) {
    val tabs = remember(physics) { MathKeys.tabs(physics) }
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.testTag("math-keyboard"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, (name, _) -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs[tab].second.forEach { k -> KeyCap(k.label, Modifier.testTag("key-${k.label}")) { onValue(MathKeys.insert(value, k)) } }
            KeyCap("⌫", Modifier.testTag("key-back")) { onValue(MathKeys.backspace(value)) }
            KeyCap("⏎ step", Modifier.testTag("key-enter")) { onValue(MathKeys.newLine(value)) }
        }
    }
}

@Composable
private fun KeyCap(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 48.dp).then(Modifier),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
