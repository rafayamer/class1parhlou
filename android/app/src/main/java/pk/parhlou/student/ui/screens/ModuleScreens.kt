package pk.parhlou.student.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.QuizAnswer
import pk.parhlou.student.domain.QuizScorer
import pk.parhlou.student.domain.QuizSummary
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.CheckRow
import pk.parhlou.student.ui.components.ChoiceCard
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SampleBadge
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

enum class Stage(val label: String) { LEARN("1 Learn"), UNDERSTAND("2 Quiz"), READ("3 Book"), WRITE("4 Write"), IMPROVE("5 Improve") }

@Composable
fun ModuleScreen(
    module: Module,
    quiz: List<McqQuestion>,
    progress: ProgressStore,
    store: LocalStore,
    onAskTutor: () -> Unit,
    onBack: () -> Unit,
) {
    var stage by rememberSaveable { mutableStateOf(Stage.LEARN) }
    LaunchedEffect(module.id) {
        if (progress.evidence(module.id).isEmpty()) progress.add(module.id, Evidence.ModuleOpened)
    }

    ParhlouScreen(module.title, onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleBadge()
            StatusPill(progress.state(module.id).label, warning = false)
        }
        // The five-stage rail (PRD §7). Every stage can be reopened; evidence is never overwritten.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stage.entries.forEach { s ->
                FilterChip(
                    selected = stage == s, onClick = { stage = s }, label = { Text(s.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        when (stage) {
            Stage.LEARN -> LearnStage(module, progress) { stage = Stage.UNDERSTAND }
            Stage.UNDERSTAND -> QuizContent(
                questions = quiz,
                passPercent = null,
                onFinished = { summary -> progress.add(module.id, Evidence.QuizCompleted(summary.firstTryPercent)) },
                onNext = { stage = Stage.READ },
            )
            Stage.READ -> ReadStage(module, progress) { stage = Stage.WRITE }
            Stage.WRITE -> WriteStage(module, progress, store) { stage = Stage.IMPROVE }
            Stage.IMPROVE -> ImproveStage(module, progress, onAskTutor)
        }
    }
}

@Composable
private fun LearnStage(module: Module, progress: ProgressStore, onNext: () -> Unit) {
    val context = LocalContext.current
    Sheet {
        Text("Teacher video", style = MaterialTheme.typography.titleMedium)
        val video = module.video
        if (video == null) {
            Muted("No teacher video has been linked and checked for this module yet. You can study from your book in step 3.")
        } else {
            Text("${video.teacher} · ${video.channel}", style = MaterialTheme.typography.bodyLarge)
            Muted("Opens in YouTube. Parhlou does not copy or download videos.")
            SecondaryButton("Watch on YouTube", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
            })
            SecondaryButton("I studied this video", onClick = { progress.add(module.id, Evidence.VideoStudyConfirmed) })
            Muted("This records that you studied. It doesn't count as mastering the topic.")
        }
    }
    BigButton("Next: quiz", onClick = onNext)
}

@Composable
private fun ReadStage(module: Module, progress: ProgressStore, onNext: () -> Unit) {
    val ref = module.bookReference
    Sheet {
        Text("Your book", style = MaterialTheme.typography.titleMedium)
        if (ref == null) Muted("No book reference yet.")
        else {
            Text(ref.text, style = MaterialTheme.typography.bodyLarge)
            if (!ref.verified) Muted("Page numbers are shown only after they are checked against the official book.")
        }
    }
    val done = remember(module.id) { mutableStateListOf<Int>() }
    Sheet {
        Text("Tasks", style = MaterialTheme.typography.titleMedium)
        module.readingTasks.forEachIndexed { i, task ->
            CheckRow(task, checked = i in done, onChange = { if (it) done += i else done -= i })
        }
    }
    BigButton("Next: write", onClick = {
        if (done.size == module.readingTasks.size) progress.add(module.id, Evidence.ReadingChecked)
        onNext()
    })
}

@Composable
private fun WriteStage(module: Module, progress: ProgressStore, store: LocalStore, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    module.writtenPrompts.forEachIndexed { i, prompt ->
        val key = "${module.id}_w$i"
        val saved by store.draft(key).collectAsState(initial = null)
        var text by remember(key) { mutableStateOf<String?>(null) }
        val value = text ?: saved ?: ""
        Sheet {
            Text(prompt, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = value,
                onValueChange = { new -> text = new; scope.launch { store.saveDraft(key, new) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                    .semantics { contentDescription = "Your answer" },
                placeholder = { Text("Write your answer. It saves as you type.") },
            )
            Muted("Saved on this phone.")
        }
    }
    var submitted by remember(module.id) { mutableStateOf(Evidence.WrittenSubmitted in progress.evidence(module.id)) }
    if (submitted) {
        Sheet {
            Text("Answer submitted", style = MaterialTheme.typography.titleMedium)
            Muted("Written feedback needs the marking service, which isn't connected in this build. Nothing has been marked.")
        }
        BigButton("Next: improve", onClick = onNext)
    } else {
        BigButton("Submit answer", onClick = { progress.add(module.id, Evidence.WrittenSubmitted); submitted = true })
    }
}

@Composable
private fun ImproveStage(module: Module, progress: ProgressStore, onAskTutor: () -> Unit) {
    val quiz = progress.evidence(module.id).filterIsInstance<Evidence.QuizCompleted>()
    Sheet {
        Text("Where you are", style = MaterialTheme.typography.titleMedium)
        Text(progress.state(module.id).label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (quiz.isEmpty()) Muted("Do the quiz to see what to work on.")
        else Muted("Best first-try quiz score: ${quiz.maxOf { it.firstTryPercent }}%")
        Muted("A module is only marked assessed after a chapter test, not from practice alone.")
    }
    SecondaryButton("Ask the tutor about this topic", onClick = onAskTutor)
}

/** Shared MCQ runner for module quizzes and Test Series readiness checks. */
@Composable
fun QuizContent(
    questions: List<McqQuestion>,
    passPercent: Int?,
    onFinished: (QuizSummary) -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Next: book",
) {
    if (questions.isEmpty()) {
        Muted("No reviewed questions are available here yet.")
        return
    }
    var round by remember { mutableIntStateOf(0) }
    val order = remember(round) { if (round == 0) questions else questions.shuffled() }
    var index by remember(round) { mutableIntStateOf(0) }
    var history by remember(round) { mutableStateOf(emptyList<QuizAnswer>()) }
    var chosen by remember(round, index) { mutableStateOf<Int?>(null) }
    var summary by remember(round) { mutableStateOf<QuizSummary?>(null) }

    val done = summary
    if (done != null) {
        Sheet {
            Text("Quiz finished", style = MaterialTheme.typography.titleMedium)
            Text("First try: ${done.firstTryCorrect} of ${done.questions} (${done.firstTryPercent}%)", style = MaterialTheme.typography.titleLarge)
            Muted("Corrected after feedback: ${done.eventuallyCorrect} of ${done.questions}")
            if (passPercent != null) {
                Text(if (done.firstTryPercent >= passPercent) "Passed. Your paper is unlocked." else "Not yet. Review the explanations and try again.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
        val passed = passPercent == null || done.firstTryPercent >= passPercent
        if (passed) BigButton(nextLabel, onClick = onNext)
        SecondaryButton("Try again with the questions shuffled", onClick = { round++ })
        return
    }

    val q = order[index]
    Muted("Question ${index + 1} of ${order.size}" + (passPercent?.let { " · pass mark $it%" } ?: ""))
    Sheet { Text(q.stem, style = MaterialTheme.typography.titleLarge) }
    q.options.forEachIndexed { i, option ->
        val picked = chosen
        if (picked == null) {
            ChoiceCard(
                title = option,
                selected = false,
                onClick = {
                    chosen = i
                    history = QuizScorer.record(history, q.id, i, q.sampleCorrectIndex)
                },
            )
        } else {
            AnswerCard(
                option = option,
                rationale = q.rationales.getOrNull(i),
                isCorrect = i == q.sampleCorrectIndex,
                isChosen = i == picked,
            )
        }
    }
    val c = chosen
    if (c != null) {
        Text(if (c == q.sampleCorrectIndex) "Correct." else "Not quite. The right answer is highlighted.",
            style = MaterialTheme.typography.titleMedium,
            color = if (c == q.sampleCorrectIndex) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        BigButton(if (index == order.lastIndex) "See result" else "Next question", onClick = {
            if (index == order.lastIndex) {
                val s = QuizScorer.summarise(order.map { it.id }, history)
                summary = s
                onFinished(s)
            } else index++
        })
    }
}

/** An option after answering: the right answer in green, a wrong choice in red, every option explained. */
@Composable
private fun AnswerCard(option: String, rationale: String?, isCorrect: Boolean, isChosen: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        isCorrect -> scheme.primaryContainer
        isChosen -> scheme.error.copy(alpha = 0.14f)
        else -> scheme.surface
    }
    val content = if (isCorrect) scheme.onPrimaryContainer else scheme.onSurface
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = if (isChosen && !isCorrect) androidx.compose.foundation.BorderStroke(1.5.dp, scheme.error) else null,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(option, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                when {
                    isCorrect -> Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = content)
                    isChosen -> Text("Your answer", style = MaterialTheme.typography.labelMedium, color = scheme.error)
                }
            }
            if (rationale != null) Text(rationale, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
