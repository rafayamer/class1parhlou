package pk.parhlou.student.ui.screens

import pk.parhlou.student.data.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/**
 * One unit's exam kit: what the exam covers, what to learn word for word, the formula sheet, the most important
 * questions (each opens its step-by-step solution), likely short questions, how to write for full marks and the
 * mistakes that lose marks.
 */
@Composable
fun ExamKitScreen(chapter: Chapter, onOpenSolution: (moduleId: String, index: Int) -> Unit, onBack: () -> Unit) {
    val kit = chapter.kit
    ParhlouScreen("${chapter.word} ${chapter.number} exam kit", onBack = onBack) {
        Text(chapter.title, style = MaterialTheme.typography.titleLarge)
        if (kit == null) { Muted("The exam kit for this unit is on its way."); return@ParhlouScreen }
        val exam = kit.examLabel.ifBlank { "board exam" }
        if (!kit.inExam) {
            StatusPill("Not in the $exam", warning = true)
            Muted("The smart syllabus leaves this whole unit out of the $exam. Study it for understanding; it will not be examined.")
        } else if (kit.excluded.isNotEmpty()) {
            Sheet {
                Text("Left out of the $exam", style = MaterialTheme.typography.titleMedium)
                Muted("The smart syllabus skips these. They stay in the app as practice.")
                Text(kit.excluded.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (kit.definitions.isNotEmpty()) KitSection("Learn word for word", "Examiners give full marks for the book's exact words.") {
            kit.definitions.forEach { d ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(d.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (kit.formulas.isNotEmpty()) KitSection("Formula sheet", null) {
            kit.formulas.forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pk.parhlou.student.ui.components.MathText(f.formula, style = MaterialTheme.typography.titleMedium)
                    if (f.use.isNotBlank()) Muted(f.use)
                }
            }
        }
        if (kit.important.isNotEmpty()) {
            val refs = solutionIndex(listOf(pk.parhlou.student.data.Subject("", "", 0, "", true, listOf(chapter))))
            KitSection("Most important questions", "Most likely in the board paper. Tap one to see its full solution.") {
                kit.important.forEach { imp ->
                    val ref = refs.firstOrNull { it.solution.source.equals(imp.ref, ignoreCase = true) }
                    pk.parhlou.student.ui.components.TapCard(
                        onClick = { if (ref != null) onOpenSolution(ref.module.id, ref.index) },
                        enabled = ref != null,
                        modifier = Modifier.fillMaxWidth().testTag("kit-important"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(imp.ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            if (ref != null) Text(ref.solution.question, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                            if (imp.why.isNotBlank()) Muted(imp.why)
                        }
                    }
                }
            }
        }
        if (kit.shortTheory.isNotEmpty()) KitSection("Likely short questions", "Say your answer first, then check.") {
            kit.shortTheory.forEachIndexed { i, q ->
                var shown by rememberSaveable("${chapter.id}-short-$i") { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (shown) Text(q.answer, style = MaterialTheme.typography.bodyLarge)
                    else TextButton(onClick = { shown = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show answer") }
                }
            }
        }
        if (kit.fullMarks.isNotEmpty()) KitSection("Write it for full marks", null) { Bullets(kit.fullMarks) }
        if (kit.mistakes.isNotEmpty()) KitSection("Mistakes that lose marks", null) { Bullets(kit.mistakes) }
    }
}

@Composable
private fun KitSection(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Sheet {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Muted(subtitle)
        content()
    }
}

@Composable
private fun Bullets(lines: List<String>) {
    lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
}
