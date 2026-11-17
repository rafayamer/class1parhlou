package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.data.Lesson
import pk.parhlou.student.data.PracticeStep
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/** The parts of a lesson, in the order a home tutor teaches them. */
private enum class LessonPart { RECAP, EXPLAIN, EXAMPLE, YOUR_TURN, TIPS }

/**
 * A module taught like a home tutor (decision D-022): a quick recap of last time, one idea at a time, a worked example
 * revealed step by step with the reason for each step, the student's own try with hints before the solution, and
 * how the topic is asked and marked in the board exam.
 */
@Composable
fun TutorLesson(lesson: Lesson, moduleId: String, onAskTutor: (String) -> Unit, onFinished: () -> Unit) {
    val parts = remember(lesson) {
        buildList {
            if (lesson.recap.isNotEmpty()) add(LessonPart.RECAP)
            if (lesson.explain.isNotEmpty()) add(LessonPart.EXPLAIN)
            if (lesson.example != null) add(LessonPart.EXAMPLE)
            if (lesson.yourTurn.isNotEmpty()) add(LessonPart.YOUR_TURN)
            if (lesson.examTips.isNotEmpty() || lesson.mistakes.isNotEmpty()) add(LessonPart.TIPS)
        }
    }
    var partIndex by rememberSaveable(moduleId) { mutableIntStateOf(0) }
    if (parts.isEmpty()) return
    val part = parts[partIndex.coerceIn(0, parts.lastIndex)]
    val next: () -> Unit = { if (partIndex < parts.lastIndex) partIndex++ else onFinished() }

    LinearProgressIndicator(progress = { (partIndex + 1f) / parts.size }, modifier = Modifier.fillMaxWidth())
    when (part) {
        LessonPart.RECAP -> Recap(lesson, moduleId, next)
        LessonPart.EXPLAIN -> Explain(lesson, moduleId, next)
        LessonPart.EXAMPLE -> Example(lesson, moduleId, onAskTutor, next)
        LessonPart.YOUR_TURN -> YourTurn(lesson.yourTurn, moduleId, next)
        LessonPart.TIPS -> Tips(lesson, onFinished)
    }
    if (partIndex > 0) TextButton(onClick = { partIndex-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back a step") }
}

@Composable
private fun <T> remember(key: Any, block: () -> T): T = androidx.compose.runtime.remember(key) { block() }

@Composable
private fun Recap(lesson: Lesson, moduleId: String, onNext: () -> Unit) {
    Text("Quick recap", style = MaterialTheme.typography.titleLarge)
    Muted("Two questions from last time. Answer in your head, then check.")
    lesson.recap.forEachIndexed { i, item ->
        var shown by rememberSaveable("$moduleId-recap-$i") { mutableStateOf(false) }
        Sheet {
            Text(item.question, style = MaterialTheme.typography.titleMedium)
            if (shown) Text(item.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            else TextButton(onClick = { shown = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show answer") }
        }
    }
    BigButton("Start today's lesson", onClick = onNext)
}

@Composable
private fun Explain(lesson: Lesson, moduleId: String, onNext: () -> Unit) {
    var card by rememberSaveable("$moduleId-card") { mutableIntStateOf(0) }
    val c = lesson.explain[card.coerceIn(0, lesson.explain.lastIndex)]
    Text("I'll explain", style = MaterialTheme.typography.titleLarge)
    Muted("Idea ${card + 1} of ${lesson.explain.size}")
    Sheet {
        Text(c.title, style = MaterialTheme.typography.titleMedium)
        Text(c.body, style = MaterialTheme.typography.bodyLarge)
        c.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
    }
    if (card < lesson.explain.lastIndex) BigButton("Next idea", onClick = { card++ })
    else BigButton("Show me an example", onClick = onNext)
}

@Composable
private fun Example(lesson: Lesson, moduleId: String, onAskTutor: (String) -> Unit, onNext: () -> Unit) {
    val ex = lesson.example ?: return
    var shown by rememberSaveable("$moduleId-steps") { mutableIntStateOf(1) }
    Text("Watch me solve it", style = MaterialTheme.typography.titleLarge)
    Sheet {
        Text(ex.question, style = MaterialTheme.typography.titleMedium)
    }
    ex.steps.take(shown).forEachIndexed { i, step ->
        var why by rememberSaveable("$moduleId-why-$i") { mutableStateOf(false) }
        Sheet {
            Text("Step ${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step.why.isNotBlank()) TextButton(onClick = { why = !why }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (why) "Hide why" else "Why?") }
                TextButton(onClick = { onAskTutor("Explain this step: ${step.text}") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ask the tutor") }
            }
            if (why) Muted(step.why)
        }
    }
    if (shown < ex.steps.size) BigButton("Next step", onClick = { shown++ })
    else {
        if (ex.answer.isNotBlank()) Sheet { Text("Answer: ${ex.answer}", style = MaterialTheme.typography.titleMedium) }
        BigButton("Now your turn", onClick = onNext)
    }
}

@Composable
private fun YourTurn(steps: List<PracticeStep>, moduleId: String, onNext: () -> Unit) {
    var index by rememberSaveable("$moduleId-turn") { mutableIntStateOf(0) }
    val p = steps[index.coerceIn(0, steps.lastIndex)]
    var typed by rememberSaveable("$moduleId-turn-$index") { mutableStateOf("") }
    var tries by rememberSaveable("$moduleId-tries-$index") { mutableIntStateOf(0) }
    var correct by rememberSaveable("$moduleId-ok-$index") { mutableStateOf(false) }
    var showSolution by rememberSaveable("$moduleId-sol-$index") { mutableStateOf(false) }
    Text("Your turn", style = MaterialTheme.typography.titleLarge)
    if (steps.size > 1) Muted("Question ${index + 1} of ${steps.size}")
    Sheet {
        Text(p.question, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = typed, onValueChange = { typed = it }, enabled = !correct && !showSolution,
            modifier = Modifier.fillMaxWidth().testTag("your-turn-answer"),
            placeholder = { Text("Your answer") },
        )
        // A tutor nudges: first a hint, then a bigger hint, then the worked solution.
        when {
            correct -> { StatusPill("Correct!", warning = false); Muted("Well done. The full working is below.") }
            tries > 0 -> {
                val hint = p.hints.getOrNull(tries - 1)
                Text(
                    if (hint != null) "Not yet. Hint: $hint" else "Not yet. Look at the worked solution below.",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (correct || showSolution) {
            Text("Solution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            p.solution.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text("Answer: ${p.answer}", style = MaterialTheme.typography.titleMedium)
        }
    }
    if (!correct && !showSolution) {
        BigButton("Check my answer", enabled = typed.isNotBlank(), onClick = {
            if (TextMatch.matchesAny(typed, p.accept)) correct = true
            else { tries++; if (tries > p.hints.size) showSolution = true }
        })
        SecondaryButton("Show me the solution", onClick = { showSolution = true })
    } else if (index < steps.lastIndex) BigButton("Next question", onClick = { index++ })
    else BigButton("Exam tips", onClick = onNext)
}

@Composable
private fun Tips(lesson: Lesson, onFinished: () -> Unit) {
    Text("How it's asked in the exam", style = MaterialTheme.typography.titleLarge)
    if (lesson.examTips.isNotEmpty()) Sheet {
        Text("Exam tips", style = MaterialTheme.typography.titleMedium)
        lesson.examTips.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    }
    if (lesson.mistakes.isNotEmpty()) Sheet {
        Text("Mistakes that lose marks", style = MaterialTheme.typography.titleMedium)
        lesson.mistakes.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    }
    BigButton("Finish lesson: go to the quiz", onClick = onFinished)
}
