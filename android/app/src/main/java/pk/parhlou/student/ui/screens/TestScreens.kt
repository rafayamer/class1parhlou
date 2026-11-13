package pk.parhlou.student.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.parhlou.student.data.ExamDesk
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.BlueprintValidator
import pk.parhlou.student.domain.ExamClock
import pk.parhlou.student.domain.ExamEventType
import pk.parhlou.student.domain.ExamLog
import pk.parhlou.student.domain.MockPaper
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill
import java.io.File

/** Readiness pass mark for Test Series. Placeholder until it is set from real results (PRD §11). */
const val READINESS_PASS_PERCENT = 80

@Composable
fun TestsScreen(
    blueprints: List<PaperBlueprint>,
    /** Papers that have questions published. */
    available: Set<String>,
    readinessPassed: Boolean,
    onReadiness: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: (() -> Unit)?,
    /** Quarter/half/full mocks that can't be built yet (ready ones are already in [blueprints]). */
    pendingMocks: List<MockPaper.NeedsQuestions> = emptyList(),
) {
    ParhlouScreen("Papers", onBack = onBack) {
        Sheet {
            Text("Readiness check", style = MaterialTheme.typography.titleMedium)
            Muted("A short quiz before each paper. Pass mark $READINESS_PASS_PERCENT%. Failing it doesn't use up a paper.")
            if (readinessPassed) StatusPill("Passed", warning = false)
            else BigButton("Start readiness check", onClick = onReadiness)
        }
        if (blueprints.isEmpty()) Muted("No papers are published for your subjects yet.")
        blueprints.forEach { b ->
            val ready = b.id in available
            Card(onClick = { onOpen(b.id) }, enabled = readinessPassed && ready, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium)
                    Muted("${b.durationMinutes} minutes · ${fmt(b.totalMarks)} marks")
                    StatusPill(if (b.official) "Official pattern" else "Practice pattern — not official")
                    when {
                        !ready -> Muted("Not available yet: it needs checked questions for every unit.")
                        !readinessPassed -> Muted("Locked until you pass the readiness check.")
                    }
                }
            }
        }
        if (pendingMocks.isNotEmpty()) {
            Text("Mock papers", style = MaterialTheme.typography.titleLarge)
            pendingMocks.forEach { m ->
                Card(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${m.scope.label} (Units ${m.chapters.first().number}–${m.chapters.last().number})", style = MaterialTheme.typography.titleMedium)
                        StatusPill("Practice pattern — not official")
                        Muted(
                            if (m.missing.isNotEmpty()) "Needs questions for ${unitRanges(m.missing.map { it.number })} first."
                            else "Needs more questions of the right marks before it can be built.",
                        )
                    }
                }
            }
        }
    }
}

/** "Units 2–4, 7" from [2, 3, 4, 7]. */
internal fun unitRanges(numbers: List<Int>): String {
    val sorted = numbers.sorted()
    val parts = mutableListOf<String>()
    var start = sorted.firstOrNull() ?: return "no units"
    var prev = start
    for (n in sorted.drop(1) + Int.MIN_VALUE) {
        if (n == prev + 1) { prev = n; continue }
        parts += if (start == prev) "$start" else "$start–$prev"
        start = n; prev = n
    }
    return (if (sorted.size == 1) "Unit " else "Units ") + parts.joinToString(", ")
}

@Composable
fun ReadinessScreen(questions: List<McqQuestion>, onPassed: () -> Unit, onBack: () -> Unit) {
    ParhlouScreen("Readiness check", onBack = onBack) {
        Muted("You need $READINESS_PASS_PERCENT% on your first try. Passing shows you're ready; it doesn't complete any lessons.")
        var passed by remember { mutableStateOf(false) }
        QuizContent(
            questions = questions,
            passPercent = READINESS_PASS_PERCENT,
            onFinished = { if (it.firstTryPercent >= READINESS_PASS_PERCENT) passed = true },
            onNext = { if (passed) onPassed() },
            nextLabel = "Go to papers",
        )
    }
}

@Composable
fun AssessmentStartScreen(
    blueprint: PaperBlueprint,
    examLog: String,
    onStart: () -> Unit,
    onResult: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val problems = BlueprintValidator.problems(blueprint)
    val started = ExamLog.startedAt(examLog) != null
    val submitted = ExamLog.isSubmitted(examLog)
    ParhlouScreen("Before you start", onBack = onBack) {
        Sheet {
            Text(blueprint.title, style = MaterialTheme.typography.titleLarge)
            Text("Time: ${blueprint.durationMinutes} minutes", style = MaterialTheme.typography.bodyLarge)
            Text("Total: ${fmt(blueprint.totalMarks)} marks", style = MaterialTheme.typography.bodyLarge)
            blueprint.sections.forEach { s ->
                Muted("${s.type}: answer ${s.attempt} of ${s.count}, ${fmt(s.marksEach)} marks each" +
                    (s.minutes?.let { " ($it min)" } ?: ""))
            }
            if (!blueprint.official) StatusPill("Practice pattern — not an official board paper")
        }
        Sheet {
            Text("How it works", style = MaterialTheme.typography.titleMedium)
            Muted("Tap your choice for each MCQ. For written questions, type your working, or add photos of your pages.")
            Muted("If you answer more questions than asked, only the first ones are checked.")
            Muted("Check Master marks your paper as soon as you hand it in. Typed answers are checked now; page photos wait for the AI examiner.")
        }
        Sheet {
            Text("Invigilator", style = MaterialTheme.typography.titleMedium)
            Muted("The timer keeps running if you close the app. The invigilator records how long you take and each time you leave the paper, then shows you a report. It stays on this phone.")
            Muted("The AI tutor is locked until you hand in.")
        }
        if (problems.isNotEmpty()) {
            Sheet {
                Text("This paper can't be started", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                problems.forEach { Muted(it) }
            }
        }
        when {
            submitted -> {
                BigButton("See your result", onClick = onResult)
                var confirm by remember { mutableStateOf(false) }
                if (!confirm) SecondaryButton("Sit this paper again", onClick = { confirm = true })
                else Sheet {
                    Text("Start again?", style = MaterialTheme.typography.titleMedium)
                    Muted("Your answers, photos and report for this paper will be removed from this phone.")
                    BigButton("Yes, clear and start again", onClick = { confirm = false; onReset() })
                    SecondaryButton("Cancel", onClick = { confirm = false })
                }
            }
            started -> BigButton("Continue your paper", onClick = onStart, enabled = problems.isEmpty())
            else -> BigButton("Start paper", onClick = onStart, enabled = problems.isEmpty())
        }
    }
}

/**
 * The paper itself. MCQs are answered by tapping; written questions take typed working and page photos.
 * Everything saves as it is entered. The invigilator log is written from [appScope] so it survives leaving this screen.
 */
@Composable
fun ExamScreen(
    blueprint: PaperBlueprint,
    paper: PaperContent,
    store: LocalStore,
    desk: ExamDesk,
    appScope: CoroutineScope,
    onSubmitted: () -> Unit,
    onBack: () -> Unit,
) {
    val id = blueprint.id
    val log by store.examLog(id).collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        store.appendExamEvent(id, ExamEventType.STARTED, System.currentTimeMillis())
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    // Invigilator: note every time the paper leaves the screen (app closed, another app, another screen) and returns.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, id) {
        val observer = LifecycleEventObserver { _, event ->
            val type = when (event) {
                Lifecycle.Event.ON_STOP -> ExamEventType.LEFT
                Lifecycle.Event.ON_START -> ExamEventType.RETURNED
                else -> null
            }
            if (type != null) appScope.launch { store.appendExamEvent(id, type, System.currentTimeMillis()) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val startedAt = log?.let { ExamLog.startedAt(it) }
    ParhlouScreen(blueprint.title, onBack = onBack) {
        if (startedAt == null) { Muted("Loading…"); return@ParhlouScreen }
        val clock = ExamClock(startedAt, blueprint.durationMinutes)
        val locked = clock.isExpired(now) || submitting
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Time left", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusPill("Invigilator on", warning = false)
            }
            Text(
                clock.label(now), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (clock.isExpired(now)) Text("Time is up. Your answers are locked. Hand in your paper.", color = MaterialTheme.colorScheme.error)
        }

        val choices by store.mcqChoices(id).collectAsState(initial = emptyMap())
        blueprint.sections.forEachIndexed { index, section ->
            val letter = 'A' + index
            Text("Section $letter · ${section.type}", style = MaterialTheme.typography.titleLarge)
            Muted(
                if (section.attempt == section.count) "Answer all ${section.count}. ${fmt(section.marksEach)} mark${if (section.marksEach == 1.0) "" else "s"} each."
                else "Answer any ${section.attempt} of ${section.count}. ${fmt(section.marksEach)} marks each. If you answer more, only the first ${section.attempt} are checked.",
            )
            if (index == 0) {
                paper.mcqs.forEachIndexed { i, q ->
                    McqCard(i + 1, q, choices[q.id], enabled = !locked) { option ->
                        appScope.launch { store.saveMcqChoice(id, q.id, option) }
                    }
                }
            } else {
                paper.written.filter { it.section == index }.sortedBy { it.number }.forEach { q ->
                    WrittenAnswerCard(id, q, store, desk, enabled = !locked)
                }
            }
        }

        BigButton(if (submitting) "Checking your paper…" else "Hand in paper", enabled = !submitting, onClick = {
            submitting = true
            appScope.launch {
                desk.submit(blueprint, paper)
                // Storage may resume this coroutine on a background thread; navigation must run on the main thread.
                withContext(Dispatchers.Main.immediate) { onSubmitted() }
            }
        })
    }
}

@Composable
private fun McqCard(number: Int, q: McqQuestion, chosen: Int?, enabled: Boolean, onChoose: (Int) -> Unit) {
    Sheet {
        Text("$number. ${q.stem}", style = MaterialTheme.typography.titleMedium)
        q.options.forEachIndexed { i, option ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = chosen == i, enabled = enabled, role = Role.RadioButton) { onChoose(i) }
                    .testTag("mcq-${q.id}-$i"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen == i, onClick = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Text(option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun WrittenAnswerCard(paperId: String, q: WrittenQuestion, store: LocalStore, desk: ExamDesk, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    val key = desk.draftKey(paperId, q.id)
    val saved by store.draft(key).collectAsState(initial = null)
    var text by remember(key) { mutableStateOf<String?>(null) }
    var photoVersion by remember(key) { mutableIntStateOf(0) }
    val photos = remember(key, photoVersion) { desk.photos(paperId, q.id) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) scope.launch { desk.addPhotos(paperId, q.id, uris); photoVersion++ }
    }
    Sheet {
        Row {
            Text("${questionLabel(q)}. ${q.text}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("[${fmt(q.marks)}]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text ?: saved ?: "",
            onValueChange = { new -> text = new; scope.launch { store.saveDraft(key, new) } },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).testTag("answer-${q.id}"),
            placeholder = { Text("Type your answer and working") },
        )
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { photos.take(5).forEach { PhotoThumb(it) } }
            Muted("${photos.size} page photo${if (photos.size == 1) "" else "s"} attached. Photos wait for the AI examiner.")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = enabled,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Add page photos") }
            if (photos.isNotEmpty()) {
                TextButton(
                    enabled = enabled,
                    onClick = { scope.launch { desk.removePhotos(paperId, q.id); photoVersion++ } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Remove photos") }
            }
        }
    }
}

@Composable
private fun PhotoThumb(file: File) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 160 && bounds.outHeight / (sample * 2) >= 160) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, contentDescription = "Attached page", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp)) }
    }
}

internal fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
