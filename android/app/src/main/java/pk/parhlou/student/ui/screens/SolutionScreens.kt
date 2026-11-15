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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.Subject
import pk.parhlou.student.ui.components.ParhlouScreen
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

/** Where a solution lives, for browsing by unit and exercise and for search. */
data class SolutionRef(val chapter: Chapter, val module: Module, val index: Int, val solution: BookSolution, val group: String)

private val EXERCISE_LABEL = Regex("(?i)^\\s*(review\\s*ex(?:ercise)?\\.?\\s*(\\d+)|ex(?:ercise)?\\.?\\s*(\\d+(?:\\.\\d+)?))")

/** "Exercise 2.3", "Review exercise 2" or "Worked examples", from a solution's source label. */
fun solutionGroup(s: BookSolution): String {
    if (s.kind == SolutionKind.EXAMPLE) return "Worked examples"
    val m = EXERCISE_LABEL.find(s.source) ?: return "Other questions"
    return if (m.groupValues[2].isNotEmpty()) "Review exercise ${m.groupValues[2]}" else "Exercise ${m.groupValues[3]}"
}

fun solutionIndex(subjects: List<Subject>): List<SolutionRef> = subjects.flatMap { subject ->
    subject.chapters.flatMap { c -> c.modules.flatMap { m -> m.solutions.mapIndexed { i, s -> SolutionRef(c, m, i, s, solutionGroup(s)) } } }
}

private fun groupOrder(g: String): Pair<Int, Double> = when {
    g.startsWith("Exercise") -> 0 to (g.removePrefix("Exercise ").toDoubleOrNull() ?: 0.0)
    g.startsWith("Review") -> 1 to 0.0
    g.startsWith("Worked") -> 2 to 0.0
    else -> 3 to 0.0
}

/**
 * Every textbook question with its step-by-step solution: unit → exercise → question, or search by
 * exercise number ("2.3 Q4") or words from the question.
 */
@Composable
fun SolutionsFinderScreen(subjects: List<Subject>, onOpen: (String, Int) -> Unit, onBack: () -> Unit) {
    val all = remember(subjects) { solutionIndex(subjects) }
    var query by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf<String?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val title = when {
        group != null -> group!!
        unit != null -> all.firstOrNull { it.chapter.id == unit }?.let { "Unit ${it.chapter.number}" } ?: "Solutions"
        else -> "Solutions"
    }
    BackHandler(enabled = query.isBlank() && (unit != null || group != null)) { if (group != null) group = null else unit = null }
    ParhlouScreen(title, onBack = { if (query.isBlank() && group != null) group = null else if (query.isBlank() && unit != null) unit = null else onBack() }) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("solution-search"),
            placeholder = { Text("Search, e.g. 2.3 Q4 or logarithm") },
        )
        if (all.isEmpty()) { Muted("Step-by-step solutions appear here once your course content is published."); return@ParhlouScreen }
        if (query.isNotBlank()) {
            val hits = search(all, query)
            Muted(if (hits.isEmpty()) "Nothing found. Try the exercise number, like 2.3, or a word from the question." else "${hits.size} found")
            hits.take(60).forEach { ref -> QuestionRow(ref, showWhere = true) { onOpen(ref.module.id, ref.index) } }
            return@ParhlouScreen
        }
        val u = unit
        val g = group
        when {
            u == null -> {
                Muted("Every example and exercise question in your textbook, solved step by step in the book's method.")
                all.groupBy { it.chapter }.forEach { (c, refs) ->
                    val exercises = refs.count { it.solution.kind == SolutionKind.EXERCISE }
                    TaskRowLike("Unit ${c.number}: ${c.title}", "$exercises exercise questions · ${refs.size - exercises} examples") { unit = c.id }
                }
            }
            g == null -> all.filter { it.chapter.id == u }.groupBy { it.group }.toList().sortedBy { groupOrder(it.first).first * 1000 + groupOrder(it.first).second }
                .forEach { (name, refs) -> TaskRowLike(name, "${refs.size} questions") { group = name } }
            else -> all.filter { it.chapter.id == u && it.group == g }.forEach { ref -> QuestionRow(ref, showWhere = false) { onOpen(ref.module.id, ref.index) } }
        }
    }
}

private fun search(all: List<SolutionRef>, query: String): List<SolutionRef> {
    val q = query.lowercase().trim()
    // "2.3 q4", "ex 2.3 q 4(ii)" → match the source label; otherwise words of the question.
    val compactQ = q.replace(Regex("[^a-z0-9.()]"), "")
    val words = q.split(Regex("\\s+")).filter { it.length > 1 }
    return all.filter { ref ->
        val src = ref.solution.source.lowercase().replace(Regex("[^a-z0-9.()]"), "").replace("exercise", "ex")
        val text = ref.solution.question.lowercase()
        (compactQ.isNotEmpty() && (src.contains(compactQ.replace("exercise", "ex")) || src.removePrefix("ex").startsWith(compactQ.removePrefix("ex")))) ||
            (words.isNotEmpty() && words.all { text.contains(it) })
    }
}

@Composable
private fun TaskRowLike(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(subtitle)
        }
    }
}

@Composable
private fun QuestionRow(ref: SolutionRef, showWhere: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (showWhere) "Unit ${ref.chapter.number} · ${ref.solution.source}" else ref.solution.source,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(ref.solution.question, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (!ref.solution.checked) Muted("Answer from your textbook")
        }
    }
}

/** One solution on its own screen. */
@Composable
fun SolutionDetailScreen(module: Module, index: Int, onAskTutor: (String) -> Unit, onBack: () -> Unit) {
    val s = module.solutions.getOrNull(index)
    ParhlouScreen(s?.source ?: "Solution", onBack = onBack) {
        if (s == null) { Muted("This solution is no longer available."); return@ParhlouScreen }
        Muted(module.title)
        SolutionCard(s, "${module.id}-detail-$index", onAskTutor)
    }
}

@Composable
private fun SolutionCard(s: BookSolution, key: String, onAskTutor: ((String) -> Unit)? = null) {
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
            if (s.note.isNotBlank()) Muted(s.note)
        }
        if (onAskTutor != null) TextButton(
            onClick = { onAskTutor(if (steps in 1..s.steps.size) "Explain this step of ${s.source}: ${s.steps[steps - 1]}" else "Help me start ${s.source}: ${s.question}") },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (steps > 0) "Ask the tutor about this step" else "Ask the tutor for a hint") }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (steps < s.steps.size) {
                TextButton(onClick = { steps++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (steps == 0) "Show first step" else "Next step") }
                TextButton(onClick = { steps = s.steps.size }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Full solution") }
            } else if (steps > 0) TextButton(onClick = { steps = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hide solution") }
        }
    }
}
