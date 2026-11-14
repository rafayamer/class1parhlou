package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.SolutionKind
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/** Every example and exercise question of the section, solved step by step in the textbook's method. */
@Composable
fun BookSolutions(module: Module) {
    var showExamples by rememberSaveable(module.id) { mutableStateOf(false) }
    val examples = module.solutions.filter { it.kind == SolutionKind.EXAMPLE }
    val exercise = module.solutions.filter { it.kind == SolutionKind.EXERCISE }
    Text("Book solutions", style = MaterialTheme.typography.titleLarge)
    Muted("Every question in this section of your textbook. Try it in your notebook first, then check one step at a time.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showExamples, onClick = { showExamples = false }, label = { Text("Exercise (${exercise.size})") },
            modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = showExamples, onClick = { showExamples = true }, label = { Text("Examples (${examples.size})") },
            modifier = Modifier.heightIn(min = 48.dp))
    }
    (if (showExamples) examples else exercise).forEachIndexed { i, s -> SolutionCard(s, "${module.id}-${if (showExamples) "e" else "x"}-$i") }
}

@Composable
private fun SolutionCard(s: BookSolution, key: String) {
    var steps by rememberSaveable(key) { mutableIntStateOf(0) }
    var typed by rememberSaveable("$key-typed") { mutableStateOf("") }
    var verdict by rememberSaveable("$key-verdict") { mutableStateOf<Boolean?>(null) }
    Sheet {
        Text(s.source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(s.question, style = MaterialTheme.typography.titleMedium)
        if (!s.checked) {
            Text("Answer (from your textbook): ${s.answer.ifBlank { "see your textbook" }}", style = MaterialTheme.typography.bodyLarge)
            Muted("A step-by-step solution for this question is still being checked. Ask the tutor if you're stuck.")
            return@Sheet
        }
        if (s.accept.isNotEmpty() && verdict != true) {
            OutlinedTextField(value = typed, onValueChange = { typed = it; verdict = null }, modifier = Modifier.fillMaxWidth().testTag("try-$key"),
                placeholder = { Text("Your final answer") })
            TextButton(onClick = { verdict = TextMatch.matchesAny(typed, s.accept) }, enabled = typed.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Check my answer") }
            if (verdict == false) Text("Not yet. Look at the first step and try again.", color = MaterialTheme.colorScheme.tertiary)
        }
        if (verdict == true) StatusPill("Correct!", warning = false)
        s.steps.take(steps).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (steps >= s.steps.size && steps > 0) {
            if (s.figure.isNotBlank()) Muted("Figure: ${s.figure}")
            if (s.answer.isNotBlank()) Text("Answer: ${s.answer}", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (steps < s.steps.size) {
                TextButton(onClick = { steps++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (steps == 0) "Show first step" else "Next step") }
                TextButton(onClick = { steps = s.steps.size }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Full solution") }
            } else if (steps > 0) TextButton(onClick = { steps = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hide solution") }
        }
    }
}
