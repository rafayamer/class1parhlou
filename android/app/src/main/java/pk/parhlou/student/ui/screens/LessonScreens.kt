package pk.parhlou.student.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.data.Lesson
import pk.parhlou.student.domain.Rewards
import pk.parhlou.student.ui.components.ChunkyButton
import pk.parhlou.student.ui.components.ChunkyDefaults
import pk.parhlou.student.ui.components.Confetti
import pk.parhlou.student.ui.components.MathText
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.PlayerBottom
import pk.parhlou.student.ui.components.PlayerScaffold
import pk.parhlou.student.ui.components.StepCard
import pk.parhlou.student.ui.components.StepLabel
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.UstadSays
import pk.parhlou.student.ui.components.rememberFeedbackEffects
import pk.parhlou.student.ui.components.rememberStepTransition
import pk.parhlou.student.ui.theme.LocalBrand

/** One screen of a lesson, in the order a home tutor teaches. */
private sealed interface LessonStep {
    data object Recap : LessonStep
    data class Idea(val index: Int) : LessonStep
    data object Example : LessonStep
    data class Turn(val index: Int) : LessonStep
    data object Tips : LessonStep
}

private fun stepsOf(lesson: Lesson): List<LessonStep> = buildList {
    if (lesson.recap.isNotEmpty()) add(LessonStep.Recap)
    lesson.explain.indices.forEach { add(LessonStep.Idea(it)) }
    if (lesson.example != null) add(LessonStep.Example)
    lesson.yourTurn.indices.forEach { add(LessonStep.Turn(it)) }
    if (lesson.examTips.isNotEmpty() || lesson.mistakes.isNotEmpty()) add(LessonStep.Tips)
}

/**
 * A section taught like a home tutor (decision D-022), in the same full-screen player as practice sessions: a quick recap
 * of last time, one idea per screen, a worked example revealed step by step with the reason for each step, the student's
 * own try with hints before the solution, and how the topic is asked and marked. Ends with XP and "Practise now".
 * [onFinished] records the lesson as done (once).
 */
@Composable
fun LessonPlayer(
    lesson: Lesson,
    moduleId: String,
    title: String,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onAskTutor: (String) -> Unit,
    onFinished: () -> Unit,
    onPractice: () -> Unit,
    onClose: () -> Unit,
) {
    val steps = remember(lesson) { stepsOf(lesson) }
    val fx = rememberFeedbackEffects(soundOn)
    val brand = LocalBrand.current
    var at by rememberSaveable(moduleId) { mutableIntStateOf(0) }
    var done by rememberSaveable(moduleId) { mutableStateOf(false) }
    if (steps.isEmpty()) { LessonEnd(title, onPractice, onClose); return }
    if (done) { LessonEnd(title, onPractice, onClose); return }
    val step = steps[at.coerceIn(0, steps.lastIndex)]
    BackHandler { if (at > 0) at-- else onClose() }
    val next: () -> Unit = { if (at < steps.lastIndex) at++ else { onFinished(); fx.finish(); done = true } }

    // State the bottom button depends on, kept above the scaffold. Each "your turn" question starts fresh.
    var revealed by rememberSaveable(moduleId) { mutableStateOf("") }
    var exampleShown by rememberSaveable(moduleId) { mutableIntStateOf(1) }
    val turn = (step as? LessonStep.Turn)?.index ?: -1
    var typed by rememberSaveable(moduleId, turn) { mutableStateOf("") }
    var tries by rememberSaveable(moduleId, turn) { mutableIntStateOf(0) }
    var correct by rememberSaveable(moduleId, turn) { mutableStateOf(false) }
    var solved by rememberSaveable(moduleId, turn) { mutableStateOf(false) }
    val ex = lesson.example

    PlayerScaffold(
        progress = (at + 1f) / (steps.size + 1), onClose = onClose, closeLabel = "Close lesson",
        soundOn = soundOn, onToggleSound = onToggleSound,
        screenKey = at,
        // A newly revealed example step, or the result of "Check", scrolls into view.
        revealKey = when (step) {
            LessonStep.Example -> exampleShown.takeIf { it > 1 }
            is LessonStep.Turn -> "$tries-$correct-$solved".takeIf { tries > 0 || correct || solved }
            else -> null
        },
        bottom = {
            PlayerBottom {
                when (step) {
                    LessonStep.Recap -> ChunkyButton("Start the lesson", onClick = next, modifier = Modifier.testTag("lesson-next"))
                    is LessonStep.Idea -> ChunkyButton(
                        when { step.index < lesson.explain.lastIndex -> "Next idea"; ex != null -> "Show me an example"; lesson.yourTurn.isNotEmpty() -> "Now your turn"; else -> "Exam tips" },
                        onClick = next, modifier = Modifier.testTag("lesson-next"))
                    LessonStep.Example -> if (ex != null && exampleShown < ex.steps.size) {
                        ChunkyButton("Next step", onClick = { exampleShown++ }, modifier = Modifier.testTag("lesson-next"))
                    } else ChunkyButton(if (lesson.yourTurn.isNotEmpty()) "Now your turn" else "Exam tips", onClick = next, modifier = Modifier.testTag("lesson-next"))
                    is LessonStep.Turn -> {
                        val p = lesson.yourTurn[step.index]
                        if (!correct && !solved) {
                            ChunkyButton("Check", enabled = typed.isNotBlank(), colors = ChunkyDefaults.success(), modifier = Modifier.testTag("lesson-check"), onClick = {
                                if (TextMatch.matchesAny(typed, p.accept)) { correct = true; fx.correct() }
                                else { tries++; fx.wrong(); if (tries > p.hints.size) solved = true }
                            })
                            TextButton(onClick = { solved = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Show me the solution") }
                        } else ChunkyButton(
                            when { step.index < lesson.yourTurn.lastIndex -> "Next question"; lesson.examTips.isNotEmpty() || lesson.mistakes.isNotEmpty() -> "Exam tips"; else -> "Finish lesson" },
                            onClick = next, colors = if (correct) ChunkyDefaults.success() else ChunkyDefaults.primary(), modifier = Modifier.testTag("lesson-next"))
                    }
                    LessonStep.Tips -> ChunkyButton("Finish lesson", onClick = next, colors = ChunkyDefaults.sun(), modifier = Modifier.testTag("lesson-finish"))
                }
                if (at > 0) TextButton(onClick = { at-- }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Back a step") }
            }
        },
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(targetState = at.coerceIn(0, steps.lastIndex), transitionSpec = rememberStepTransition(), label = "lesson-step") { shownAt ->
            val s = steps[shownAt]
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    LessonStep.Recap -> {
                        UstadSays("Quick recap: two questions from last time. Answer in your head, then check.", size = 48.dp)
                        lesson.recap.forEachIndexed { i, item ->
                            val shown = revealed.split(',').contains("$i")
                            StepCard {
                                StepLabel("Question ${i + 1}")
                                MathText(item.question, style = MaterialTheme.typography.titleMedium)
                                if (shown) MathText(item.answer, style = MaterialTheme.typography.bodyLarge, color = brand.success)
                                else TextButton(onClick = { revealed = "$revealed,$i" }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Show answer") }
                            }
                        }
                    }
                    is LessonStep.Idea -> {
                        val c = lesson.explain[s.index]
                        UstadSays(if (s.index == 0) "Let me explain, one idea at a time." else "Next idea. Take your time with it.", size = 48.dp)
                        StepCard {
                            StepLabel("Idea ${s.index + 1} of ${lesson.explain.size}")
                            Text(c.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            MathText(c.body, style = MaterialTheme.typography.bodyLarge)
                            c.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
                        }
                    }
                    LessonStep.Example -> if (ex != null) {
                        UstadSays("Watch me solve it, one step at a time. Tap Why? for the reason.", size = 48.dp)
                        StepCard(accent = MaterialTheme.colorScheme.primary) {
                            StepLabel("Example")
                            MathText(ex.question, style = MaterialTheme.typography.titleMedium)
                        }
                        ex.steps.take(exampleShown).forEachIndexed { i, st ->
                            var why by rememberSaveable("$moduleId-why-$i") { mutableStateOf(false) }
                            StepCard {
                                StepLabel("Step ${i + 1}", color = brand.success)
                                MathText(st.text, style = MaterialTheme.typography.bodyLarge)
                                if (why) Muted(st.why)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (st.why.isNotBlank()) TextButton(onClick = { why = !why }, modifier = Modifier.heightIn(min = 44.dp)) { Text(if (why) "Hide why" else "Why?") }
                                    TextButton(onClick = { onAskTutor("Explain this step: ${st.text}") }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Ask Ustaad") }
                                }
                            }
                        }
                        if (exampleShown >= ex.steps.size && ex.answer.isNotBlank()) StepCard(accent = brand.success) {
                            StepLabel("Answer", color = brand.success)
                            MathText(ex.answer, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is LessonStep.Turn -> {
                        val p = lesson.yourTurn[s.index]
                        UstadSays(if (correct) "Excellent! That's exactly right." else "Your turn! Work it out in your notebook, then type your answer.",
                            if (tries > 0 && !correct) UstadMood.THINKING else UstadMood.HAPPY, size = 48.dp)
                        StepCard {
                            if (lesson.yourTurn.size > 1) StepLabel("Question ${s.index + 1} of ${lesson.yourTurn.size}")
                            MathText(p.question, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = typed, onValueChange = { typed = it }, enabled = !correct && !solved,
                                modifier = Modifier.fillMaxWidth().testTag("your-turn-answer"), placeholder = { Text("Your answer") },
                            )
                            // A tutor nudges: first a hint, then a bigger hint, then the worked solution.
                            if (!correct && tries > 0) {
                                val hint = p.hints.getOrNull(tries - 1)
                                Text(if (hint != null) "Not yet. Hint: $hint" else "Not yet. Look at the worked solution below.",
                                    color = brand.danger, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                        if (correct || solved) StepCard(accent = if (correct) brand.success else MaterialTheme.colorScheme.primary) {
                            if (correct) Text("Correct!", style = MaterialTheme.typography.titleLarge, color = brand.success, fontWeight = FontWeight.Bold)
                            StepLabel("Solution")
                            p.solution.forEach { MathText(it, style = MaterialTheme.typography.bodyLarge) }
                            MathText("Answer: ${p.answer}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    LessonStep.Tips -> {
                        UstadSays("Here's how it's asked in the exam, and the mistakes that lose marks.", size = 48.dp)
                        if (lesson.examTips.isNotEmpty()) StepCard(accent = brand.sun) {
                            StepLabel("Exam tips", color = MaterialTheme.colorScheme.onSurface)
                            lesson.examTips.forEach { MathText("• $it", style = MaterialTheme.typography.bodyLarge) }
                        }
                        if (lesson.mistakes.isNotEmpty()) StepCard(accent = brand.danger) {
                            StepLabel("Mistakes that lose marks", color = brand.danger)
                            lesson.mistakes.forEach { MathText("• $it", style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
        }
    }
}

/** The end of a lesson: XP, confetti, and the natural next step (practice). */
@Composable
private fun LessonEnd(title: String, onPractice: () -> Unit, onClose: () -> Unit) {
    val brand = LocalBrand.current
    PlayerScaffold(
        progress = 1f, onClose = onClose, closeLabel = "Close lesson",
        bottom = {
            PlayerBottom {
                ChunkyButton("Practise now", onClick = onPractice, colors = ChunkyDefaults.success(), modifier = Modifier.testTag("lesson-practice"))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Back to the section") }
            }
        },
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UstadSays("Lesson done! Now practise it so it sticks.", UstadMood.CHEER)
        StepCard(accent = brand.sun, modifier = Modifier.testTag("lesson-done")) {
            Text("Lesson complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("+${Rewards.LESSON_XP} XP", style = MaterialTheme.typography.titleLarge, color = brand.xp, fontWeight = FontWeight.Bold)
            Muted("Practice turns what you just learned into marks.")
        }
    }
    Confetti()
}
