package pk.parhlou.student.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** Pieces of a maths string: text (with ^powers), a stacked fraction, or a square root over a group. */
sealed interface MathNode {
    data class Txt(val text: String) : MathNode
    data class Frac(val num: List<MathNode>, val den: List<MathNode>) : MathNode
    data class Root(val body: List<MathNode>) : MathNode
}

/**
 * Turns the course's plain-text maths ("(x + 1)/(x − 2)", "√(a + b)", "x^2", "10^-3") into stacked fractions, roots
 * and superscripts. Prose is left alone: a "/" only becomes a fraction between numbers, single letters or brackets.
 */
object MathParser {
    private const val GROUP = """\((?:[^()]|\([^()]*\))*\)"""
    // A number (with an optional letter, "2x"), a capital segment or point name ("BC", "AB"), or one letter.
    private const val ATOM = """√?(?:\d+(?:\.\d+)?[a-zA-Zπθ]?|[A-Z]{2,3}(?![a-z])|[a-zA-Zπθ])(?:\^\(?-?[\w.]+\)?|[⁰¹²³⁴⁵⁶⁷⁸⁹⁻]+)?"""
    private val FRACTION = Regex("""(?<![\w)/])($GROUP|$ATOM)\s?/\s?($GROUP|$ATOM)(?![\w(/])""")
    private val ROOT = Regex("""√($GROUP)""")
    private val WORDISH = Regex("""^[a-z]{2,}$|^[A-Z][a-z]+$""") // ordinary words ("km", "and", "Total"), not "BC"

    fun hasMath(s: String): Boolean = FRACTION.containsMatchIn(s) || ROOT.containsMatchIn(s) || s.contains('^')

    fun parse(s: String): List<MathNode> {
        val out = mutableListOf<MathNode>()
        var i = 0
        while (i < s.length) {
            val f = FRACTION.find(s, i)
            val r = ROOT.find(s, i)
            val next = listOfNotNull(f, r).minByOrNull { it.range.first }
            if (next == null) { out += MathNode.Txt(s.substring(i)); break }
            if (next.range.first > i) out += MathNode.Txt(s.substring(i, next.range.first))
            if (next === f) {
                val (a, b) = f.groupValues[1] to f.groupValues[2]
                if (WORDISH.matches(a) || WORDISH.matches(b)) out += MathNode.Txt(f.value)
                else out += MathNode.Frac(parse(unwrap(a)), parse(unwrap(b)))
            } else out += MathNode.Root(parse(unwrap(next.groupValues[1])))
            i = next.range.last + 1
        }
        return out.filter { it !is MathNode.Txt || it.text.isNotEmpty() }
    }

    private fun unwrap(g: String) = if (g.startsWith("(") && g.endsWith(")")) g.substring(1, g.length - 1) else g

    /** "x^2", "10^-3", "a^(n+1)" → superscripts. */
    fun powers(text: String): AnnotatedString = buildAnnotatedString {
        val re = Regex("""\^(\(([^()]*)\)|-?[\w.]+)""")
        var i = 0
        for (m in re.findAll(text)) {
            append(text.substring(i, m.range.first))
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.72.em)) { append(m.groupValues[2].ifEmpty { m.groupValues[1] }) }
            i = m.range.last + 1
        }
        append(text.substring(i))
    }
}

/**
 * Text with real maths layout. Plain sentences render as ordinary [Text]; strings with fractions or roots are laid out
 * word by word with stacked fractions and root bars. Screen readers hear the original text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MathText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val nodes = remember(text) { if (MathParser.hasMath(text)) MathParser.parse(text) else null }
    if (nodes == null || nodes.none { it !is MathNode.Txt }) {
        Text(MathParser.powers(text), style = style, color = color, modifier = modifier)
        return
    }
    FlowRow(
        // Screen readers (and searches) see the whole sentence as one text, not word by word.
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { Nodes(nodes, style, color) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.FlowRowScope.Nodes(nodes: List<MathNode>, style: TextStyle, color: Color) {
    for (n in nodes) when (n) {
        // Words wrap one by one, so long sentences still flow across lines.
        is MathNode.Txt -> Regex("""\S+\s*|\s+""").findAll(n.text).forEach { Text(MathParser.powers(it.value), style = style, color = color) }
        is MathNode.Frac -> Fraction(n, style, color)
        is MathNode.Root -> RootBox(n, style, color)
    }
}

@Composable
private fun Inline(nodes: List<MathNode>, style: TextStyle, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (n in nodes) when (n) {
            is MathNode.Txt -> Text(MathParser.powers(n.text), style = style, color = color)
            is MathNode.Frac -> Fraction(n, style, color)
            is MathNode.Root -> RootBox(n, style, color)
        }
    }
}

@Composable
private fun Fraction(f: MathNode.Frac, style: TextStyle, color: Color) {
    val small = style.copy(fontSize = style.fontSize * 0.88f)
    val line = if (color == Color.Unspecified) LocalContentColor.current else color
    Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Inline(f.num, small, color)
        Box(Modifier.fillMaxWidth().height(1.5.dp).drawBehind { drawRect(line) })
        Inline(f.den, small, color)
    }
}

@Composable
private fun RootBox(r: MathNode.Root, style: TextStyle, color: Color) {
    val line = if (color == Color.Unspecified) LocalContentColor.current else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("√", style = style, color = color)
        Box(Modifier.drawBehind { drawLine(line, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 1.5.dp.toPx()) }.padding(top = 3.dp)) {
            Inline(r.body, style, color)
        }
    }
}
