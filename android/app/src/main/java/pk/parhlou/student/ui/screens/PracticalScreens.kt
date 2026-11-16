package pk.parhlou.student.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.Practical
import pk.parhlou.student.ui.components.MathText
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet

/** One practical, as it is written in the practical notebook: aim, apparatus, procedure, readings, result, precautions, viva. */
@Composable
fun PracticalScreen(p: Practical, onBack: () -> Unit, onPrint: (() -> Unit)? = null) {
    ParhlouScreen(p.title, onBack = onBack) {
        Muted(p.label + if (!p.inExam) " · practice (not in this year's exam)" else "")
        if (!p.checked) {
            Sheet(Modifier.testTag("practical-book")) {
                Text("In your book", style = MaterialTheme.typography.titleMedium)
                p.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
                MathText(p.bookText, style = MaterialTheme.typography.bodyLarge)
            }
            return@ParhlouScreen
        }
        Part("🎯 Aim") { Text(p.aim, style = MaterialTheme.typography.bodyLarge) }
        if (p.apparatus.isNotEmpty()) Part("🧰 Apparatus") { p.apparatus.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
        if (p.diagram.isNotBlank() || p.figures.isNotEmpty()) Part("✏️ Diagram") {
            p.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
            if (p.diagram.isNotBlank()) Muted("Draw and label: ${p.diagram}")
        }
        Part("📋 Procedure") { p.procedure.forEachIndexed { i, s -> MathText("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) } }
        if (p.columns.isNotEmpty()) Part("📊 Observations") { ObservationTable(p.columns, p.rows) }
        if (p.calculation.isNotEmpty()) Part("🧮 Calculation") { p.calculation.forEach { MathText(it, style = MaterialTheme.typography.bodyLarge) } }
        Part("✅ Result") { MathText(p.result, style = MaterialTheme.typography.bodyLarge) }
        if (p.precautions.isNotEmpty()) Part("⚠️ Precautions") { p.precautions.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
        if (p.viva.isNotEmpty()) Part("🗣️ Viva questions") {
            p.viva.forEach { (q, a) ->
                Text(q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                MathText(a, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onPrint != null) SecondaryButton("Print or share (PDF)", onClick = onPrint, modifier = Modifier.testTag("practical-print"))
    }
}

@Composable
private fun Part(title: String, content: @Composable () -> Unit) {
    Sheet {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun ObservationTable(columns: List<String>, rows: List<List<String>>) {
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            columns.forEach { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 72.dp, max = 160.dp)) }
        }
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.forEach { MathText(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 72.dp, max = 160.dp).padding(vertical = 2.dp)) }
            }
        }
    }
}

/** The practical as printable notes. */
fun practicalNotes(p: Practical): List<Pair<String, List<String>>> = if (!p.checked) listOf(p.label to listOf(p.bookText)) else listOf(
    "Aim" to listOf(p.aim),
    "Apparatus" to p.apparatus,
    "Diagram" to listOfNotNull(p.diagram.takeIf { it.isNotBlank() }?.let { "Draw and label: $it" }),
    "Procedure" to p.procedure.mapIndexed { i, s -> "${i + 1}. $s" },
    "Observations" to (if (p.columns.isEmpty()) emptyList() else listOf(p.columns.joinToString("   |   ")) + p.rows.map { it.joinToString("   |   ") }),
    "Calculation" to p.calculation,
    "Result" to listOf(p.result),
    "Precautions" to p.precautions,
    "Viva questions" to p.viva.map { (q, a) -> "Q. $q\nA. $a" },
)
