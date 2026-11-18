package pk.parhlou.student.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import pk.parhlou.scanner.ScanQuestion
import pk.parhlou.scanner.ScannerFlow
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import pk.parhlou.student.domain.PaperPhases
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
    /** Opens "Make my paper" (a full board-pattern paper); null when no board pattern is published. */
    onMakePaper: (() -> Unit)? = null,
    /** Textbook exercises ("1.1", "1.2" …) that can be printed as a paper. */
    exercises: List<String> = emptyList(),
    /** Prints one exercise as a paper; true = with the worked answers. */
    onExercisePdf: ((String, Boolean) -> Unit)? = null,
    attemptsLeft: (String) -> Int = { pk.parhlou.student.domain.PaperAttempts.MAX },
    /** The student's subjects (id to name); with more than one, a switch picks whose papers are shown. */
    subjects: List<Pair<String, String>> = emptyList(),
    selectedSubject: String? = null,
    onSubject: (String) -> Unit = {},
    /** "Exercise" for Mathematics (exercises 1.1, 1.2 …); "Chapter exercise" for subjects with one exercise per chapter. */
    exerciseLabel: String = "Exercise",
    /** The Guess paper (most repeated board questions in the board pattern) and a line on how it was made. */
    guessId: String? = null,
    guessNote: String? = null,
    /** Real board papers rebuilt from the bank, newest first. */
    pastIds: List<String> = emptyList(),
) {
    var picker by rememberSaveable { mutableStateOf("") } // "", "exercise", "chapter"
    val guess = blueprints.firstOrNull { it.id == guessId }
    val past = pastIds.mapNotNull { id -> blueprints.firstOrNull { it.id == id } }
    var showAllPast by rememberSaveable { mutableStateOf(false) }
    val board = blueprints.filter { it.official && it.id != guessId && it.id !in pastIds }
    val chapterTests = blueprints.filter { !it.official && it.chapterId != null }
    val half = blueprints.firstOrNull { it.id.endsWith("mock-half") }
    val mine = blueprints.filter { !it.official && it.chapterId == null && !it.id.endsWith("mock-half") && it.id != guessId && it.id !in pastIds }
    ParhlouScreen("Papers", onBack = onBack) {
        // One line of subject chips that scrolls sideways, so five subjects fit on any phone.
        if (subjects.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
            subjects.forEach { (id, name) ->
                androidx.compose.material3.FilterChip(selected = id == selectedSubject, onClick = { onSubject(id) }, label = { Text(name, maxLines = 1, softWrap = false) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("papers-subject-$id"))
            }
        }
        if (!readinessPassed) Sheet(Modifier.testTag("readiness")) {
            Text("🔒 Quick check first", style = MaterialTheme.typography.titleMedium)
            Muted("A short quiz. Pass $READINESS_PASS_PERCENT% to unlock papers.")
            BigButton("Start the check", onClick = onReadiness)
        }

        // The flagship: the paper most likely to come, from how often each question came in real board papers.
        if (guess != null) Sheet(Modifier.testTag("guess-paper")) {
            Text("🔮 Guess paper 2027", style = MaterialTheme.typography.titleLarge)
            Muted("The 2027 board pattern, filled with the questions that came most often in Punjab board papers (2018–2024).")
            guessNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            BigButton("Open the guess paper", enabled = readinessPassed && guess.id in available, onClick = { onOpen(guess.id) },
                modifier = Modifier.testTag("paper-${guess.id}"))
        }

        Text("Board papers", style = MaterialTheme.typography.titleLarge)
        if (board.isEmpty()) Muted("Coming soon.")
        board.forEach { PaperCard(it, it.id in available, readinessPassed, attemptsLeft(it.id), onOpen) }

        Text("Make a paper", style = MaterialTheme.typography.titleLarge)
        val tiles = listOf(
            Triple("📘", exerciseLabel, "scope-exercise") to (exercises.isNotEmpty() && onExercisePdf != null),
            Triple("📗", "Chapter", "scope-chapter") to (readinessPassed && chapterTests.isNotEmpty()),
            Triple("📙", "Half book", "scope-half") to (readinessPassed && half != null && half.id in available),
            Triple("📕", "Full book", "scope-full") to (readinessPassed && onMakePaper != null),
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (t, enabled) ->
                    pk.parhlou.student.ui.components.TapCard(enabled = enabled, modifier = Modifier.weight(1f).testTag(t.third), shape = RoundedCornerShape(20.dp), onClick = {
                        when (t.third) {
                            "scope-exercise" -> picker = "exercise"
                            "scope-chapter" -> picker = "chapter"
                            "scope-half" -> half?.let { onOpen(it.id) }
                            else -> onMakePaper?.invoke()
                        }
                    }) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t.first, style = MaterialTheme.typography.headlineSmall)
                            Text(t.second, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        // The choice opens right under the tiles.
        if (picker == "exercise") ScopePicker("Choose an exercise", onClose = { picker = "" }) {
            var chosen by remember { mutableStateOf<String?>(null) }
            FlowChips(exercises, chosen) { chosen = it }
            chosen?.let { e ->
                BigButton("Questions PDF", onClick = { onExercisePdf?.invoke(e, false) }, modifier = Modifier.testTag("exercise-pdf"))
                pk.parhlou.student.ui.components.SecondaryButton("With answers", onClick = { onExercisePdf?.invoke(e, true) })
            }
        }
        if (picker == "chapter") ScopePicker("Choose a chapter", onClose = { picker = "" }) {
            chapterTests.forEach { b -> PaperCard(b, b.id in available, readinessPassed, attemptsLeft(b.id), onOpen) }
        }

        if (past.isNotEmpty()) {
            Text("Real past papers", style = MaterialTheme.typography.titleLarge)
            Muted("Punjab board papers, question for question, keeping the questions that are still in the 2027 syllabus.")
            (if (showAllPast) past else past.take(6)).forEach { PaperCard(it, it.id in available, readinessPassed, attemptsLeft(it.id), onOpen) }
            if (past.size > 6) TextButton(onClick = { showAllPast = !showAllPast }, modifier = Modifier.heightIn(min = 48.dp).testTag("all-past")) {
                Text(if (showAllPast) "Show fewer" else "Show all ${past.size} past papers")
            }
        }

        if (mine.isNotEmpty()) {
            Text("My papers", style = MaterialTheme.typography.titleLarge)
            mine.forEach { PaperCard(it, it.id in available, readinessPassed, attemptsLeft(it.id), onOpen) }
        }
        pendingMocks.forEach { m ->
            Muted("${m.scope.label}: " + if (m.missing.isNotEmpty()) "ready once ${unitRanges(m.missing.map { it.number })} are in the app." else "coming soon.")
        }
    }

}

/** One paper: its name, then time, marks and tries left. */
@Composable
private fun PaperCard(b: PaperBlueprint, ready: Boolean, unlocked: Boolean, triesLeft: Int, onOpen: (String) -> Unit) {
    pk.parhlou.student.ui.components.TapCard(onClick = { onOpen(b.id) }, enabled = unlocked && ready, modifier = Modifier.fillMaxWidth().testTag("paper-${b.id}"),
        shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(b.title, style = MaterialTheme.typography.titleMedium)
            Muted(if (!ready) "Coming soon" else "⏱ ${b.durationMinutes} min · ${fmt(b.totalMarks)} marks · $triesLeft ${if (triesLeft == 1) "try" else "tries"} left")
        }
    }
}

@Composable
private fun ScopePicker(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Sheet(Modifier.testTag("scope-picker")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, chosen: String?, onChoose: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { e ->
            androidx.compose.material3.FilterChip(selected = e == chosen, onClick = { onChoose(e) }, label = { Text(if ('.' in e) e else "Chapter $e") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("exercise-$e"))
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
    /** Hand-ins so far and the best score, for the 3-attempt rule. */
    attemptsUsed: Int = 0,
    bestPercent: Int? = null,
    /** Makes the printable paper; true = with the marking scheme. Null hides printing. */
    onPrint: ((Boolean) -> Unit)? = null,
    /** Opens the scan-and-mark flow for a paper written on paper. */
    onWrittenOnPaper: (() -> Unit)? = null,
    /** Starts the exam written on paper: clock on, PDF opened to print. */
    onStartOnPaper: (() -> Unit)? = null,
    /** The running paper is being written on paper. */
    onPaper: Boolean = false,
    /** Abandons the running paper: it is cleared and does not use a try. */
    onQuit: (() -> Unit)? = null,
) {
    val problems = BlueprintValidator.problems(blueprint)
    val attemptsLeft = (pk.parhlou.student.domain.PaperAttempts.MAX - attemptsUsed).coerceAtLeast(0)
    val started = ExamLog.startedAt(examLog) != null
    val submitted = ExamLog.isSubmitted(examLog)
    ParhlouScreen("Before you start", onBack = onBack) {
        Sheet {
            Text(blueprint.title, style = MaterialTheme.typography.titleLarge)
            Text("⏱ ${blueprint.durationMinutes} min · ${fmt(blueprint.totalMarks)} marks", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    attemptsLeft == 0 -> "All ${pk.parhlou.student.domain.PaperAttempts.MAX} tries used" + (bestPercent?.let { " · best $it%" } ?: "")
                    attemptsUsed == 0 -> "🔁 ${pk.parhlou.student.domain.PaperAttempts.MAX} tries"
                    else -> "🔁 Try ${attemptsUsed + 1} of ${pk.parhlou.student.domain.PaperAttempts.MAX}" + (bestPercent?.let { " · best $it%" } ?: "")
                },
                style = MaterialTheme.typography.bodyLarge, color = if (attemptsLeft == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            var info by remember { mutableStateOf(false) }
            TextButton(onClick = { info = !info }, modifier = Modifier.heightIn(min = 48.dp).testTag("paper-info")) { Text(if (info) "ⓘ Hide details" else "ⓘ How it works") }
            if (info) {
                blueprint.sections.filter { it.count > 0 }.forEach { s ->
                    Muted("${s.type}: answer ${s.attempt} of ${s.count}, ${fmt(s.marksEach)} marks each" + (s.minutes?.let { " ($it min)" } ?: ""))
                }
                Muted("Tap your MCQ choices; type your working or add photos of your pages. Extra answers beyond what's asked aren't counted.")
                Muted("The timer keeps running if you leave. Ustaad is locked until you hand in.")
            }
        }
        FocusModeCard()
        val running = started && !submitted
        if (running) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
            val clock = ExamClock(ExamLog.startedAt(examLog)!!, blueprint.durationMinutes)
            Sheet(Modifier.testTag("paper-running")) {
                Text("⏱ ${clock.label(now)} left", style = MaterialTheme.typography.headlineMedium)
                Muted(if (onPaper) "You're writing on paper. Ask Ustaad is locked until you hand in." else "Your paper is running. Ask Ustaad is locked until you hand in.")
            }
            if (onQuit != null) QuitPaper(onQuit)
        }
        if (!started && !submitted && attemptsLeft > 0 && problems.isEmpty()) {
            Text("How will you write?", style = MaterialTheme.typography.titleLarge)
            if (onStartOnPaper != null) Sheet(Modifier.testTag("print-paper")) {
                Text("✍️ On paper, like the board", style = MaterialTheme.typography.titleMedium)
                Muted("The paper opens to print. The clock starts, Ask Ustaad locks. Write, then scan your pages and hand in.")
                BigButton("Start on paper", onClick = onStartOnPaper, modifier = Modifier.testTag("start-on-paper"))
            }
            Sheet {
                Text("📱 In the app", style = MaterialTheme.typography.titleMedium)
                Muted("Tap MCQs, type working with the maths keyboard, or add photos per question.")
                SecondaryButton("Start in the app", onClick = onStart)
            }
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
                if (attemptsLeft == 0) Sheet(Modifier.testTag("attempts-used")) {
                    Text("No attempts left", style = MaterialTheme.typography.titleMedium)
                    Muted("You've sat this paper ${pk.parhlou.student.domain.PaperAttempts.MAX} times" + (bestPercent?.let { "; your best score, $it%, stands" } ?: "") +
                        ". Move on to the next paper; your weak topics from it are already in your plan.")
                }
                else if (!confirm) SecondaryButton("Sit this paper again ($attemptsLeft ${if (attemptsLeft == 1) "try" else "tries"} left)", onClick = { confirm = true })
                else Sheet {
                    Text("Start again?", style = MaterialTheme.typography.titleMedium)
                    Muted("Your answers, photos and report for this paper will be removed from this phone. This uses attempt ${attemptsUsed + 1} of ${pk.parhlou.student.domain.PaperAttempts.MAX}.")
                    BigButton("Yes, clear and start again", onClick = { confirm = false; onReset() })
                    SecondaryButton("Cancel", onClick = { confirm = false })
                }
            }
            started -> if (onPaper && onWrittenOnPaper != null) BigButton("Scan my pages and hand in", onClick = onWrittenOnPaper, modifier = Modifier.testTag("continue-on-paper"))
                else BigButton("Continue your paper", onClick = onStart, enabled = problems.isEmpty())
            attemptsLeft == 0 -> Sheet(Modifier.testTag("attempts-used")) {
                Text("No attempts left", style = MaterialTheme.typography.titleMedium)
                Muted("You've used all ${pk.parhlou.student.domain.PaperAttempts.MAX} attempts at this paper" + (bestPercent?.let { "; your best score is $it%" } ?: "") + ".")
            }
            else -> Unit
        }
        if (onPrint != null && problems.isEmpty()) {
            var menu by remember { mutableStateOf(false) }
            var warn by remember { mutableStateOf(false) }
            TextButton(onClick = { menu = !menu }, modifier = Modifier.heightIn(min = 48.dp).testTag("print-copy")) { Text("⋯ Print a copy") }
            if (menu) Row {
                TextButton(onClick = { onPrint(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Questions") }
                TextButton(onClick = { if (submitted) onPrint(true) else warn = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("With marking scheme") }
            }
            if (warn) AlertDialog(
                onDismissRequest = { warn = false },
                title = { Text("This shows the answers") },
                text = { Text("Use the marking scheme only after you've sat the paper, or it can't show what you really know.") },
                confirmButton = { TextButton(onClick = { warn = false; onPrint(true) }) { Text("Print anyway") } },
                dismissButton = { TextButton(onClick = { warn = false }) { Text("Cancel") } },
            )
        }
    }
}

/** "Quit this paper": asks once, then the paper is cleared without using a try, and Ask Ustaad opens again. */
@Composable
fun QuitPaper(onQuit: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("quit-paper")) { Text("Quit this paper") }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Quit this paper?") },
        text = { Text("Your answers and scanned pages for it are removed. It won't count as a try, and Ask Ustaad opens again.") },
        confirmButton = { TextButton(onClick = { ask = false; onQuit() }, modifier = Modifier.testTag("quit-confirm")) { Text("Quit paper") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Keep going") } },
    )
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
    /** Opened from the Home camera button: show the scanner straight away. */
    openScanner: Boolean = false,
) {
    val id = blueprint.id
    val log by store.examLog(id).collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        store.appendExamEvent(id, ExamEventType.STARTED, System.currentTimeMillis())
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    // Do Not Disturb for as long as the paper is open; the phone's own setting comes back on hand-in or leaving.
    val focusContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(id) {
        pk.parhlou.student.ui.FocusMode.on(focusContext)
        onDispose { pk.parhlou.student.ui.FocusMode.off(focusContext) }
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
    // The scanner opens inside the paper, so the invigilator doesn't count scanning as leaving it.
    var scanning by rememberSaveable { mutableStateOf(openScanner) }
    var scanVersion by remember { mutableIntStateOf(0) }
    var confirmUnmatched by remember { mutableStateOf(0) }
    val questions = remember(paper) {
        paper.written.sortedWith(compareBy({ it.section }, { it.number })).map { ScanQuestion(it.id, questionLabel(it, blueprint)) }
    }
    if (scanning && startedAt != null) {
        // Its own full-screen window: the camera preview must not be drawn inside the app's glass layer, where it
        // can stay black on some phones. A dialog window doesn't stop the paper, so the invigilator logs nothing.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { scanning = false; scanVersion++ },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false,
            ),
        ) {
            ScannerFlow(
                store = desk.scanStore(id), questions = questions,
                status = ExamClock(startedAt, blueprint.durationMinutes).label(now),
                onClose = { scanning = false; scanVersion++ },
            )
        }
    }
    val handIn = {
        submitting = true
        appScope.launch {
            desk.submit(blueprint, paper)
            // Storage may resume this coroutine on a background thread; navigation must run on the main thread.
            withContext(Dispatchers.Main.immediate) { onSubmitted() }
        }
        Unit
    }
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
        val objectiveIn by store.objectiveHandedIn(id).collectAsState(initial = null)
        val phases = PaperPhases(startedAt, blueprint.sections.firstOrNull()?.minutes?.takeIf { paper.written.isNotEmpty() }, objectiveIn)
        val objectiveOpen = phases.objectiveOpen(now) && !locked
        if (phases.twoParts) Sheet(Modifier.testTag("paper-phase")) {
            if (objectiveOpen) {
                Text("Part 1 · Objective (MCQs)", style = MaterialTheme.typography.titleMedium)
                Text("${phases.objectiveClock().label(now)} left for the MCQs", style = MaterialTheme.typography.titleLarge)
                Muted("As in the exam hall, the MCQ sheet is collected when its time ends. Then the subjective part opens.")
                TextButton(onClick = { appScope.launch { store.handInObjective(id, System.currentTimeMillis()) } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hand-in-objective")) { Text("Hand in MCQs and start the subjective part") }
            } else {
                Text("Part 2 · Subjective", style = MaterialTheme.typography.titleMedium)
                Muted("The MCQ sheet has been collected (${choices.size} of ${paper.mcqs.size} answered). Answer the written questions below.")
            }
        }
        blueprint.sections.forEachIndexed { index, section ->
            if (phases.twoParts && index > 0 && objectiveOpen) return@forEachIndexed
            val letter = 'A' + index
            Text("Section $letter · ${section.type}", style = MaterialTheme.typography.titleLarge)
            Muted(
                if (section.attempt == section.count) "Answer all ${section.count}. ${fmt(section.marksEach)} mark${if (section.marksEach == 1.0) "" else "s"} each."
                else "Answer any ${section.attempt} of ${section.count}. ${fmt(section.marksEach)} marks each. If you answer more, only the first ${section.attempt} are checked.",
            )
            if (index == 0) {
                paper.mcqs.forEachIndexed { i, q ->
                    McqCard(i + 1, q, choices[q.id], enabled = !locked && (!phases.twoParts || objectiveOpen)) { option ->
                        appScope.launch { store.saveMcqChoice(id, q.id, option) }
                    }
                }
            } else {
                paper.written.filter { it.section == index }.sortedBy { it.number }.forEach { q ->
                    WrittenAnswerCard(id, q, questionLabel(q, blueprint), store, desk, enabled = !locked, scanVersion = scanVersion)
                }
            }
        }

        if (paper.written.isNotEmpty()) {
            val scanned = remember(scanVersion) { desk.scanStore(id).pages().size }
            val unmatched = remember(scanVersion) { desk.unassignedScans(id) }
            Sheet {
                Text("Answers on paper", style = MaterialTheme.typography.titleMedium)
                Muted(
                    when {
                        scanned == 0 -> "Wrote on paper? Scan your pages, then choose which question each page answers."
                        unmatched > 0 -> "$scanned pages scanned · $unmatched not matched to a question yet."
                        else -> "$scanned pages scanned and matched to questions."
                    },
                )
                SecondaryButton(if (scanned == 0) "Scan answer pages" else "Scan or review pages", onClick = { scanning = true }, enabled = !locked)
            }
        }

        BigButton(if (submitting) "Checking your paper…" else "Hand in paper", enabled = !submitting, onClick = {
            val unmatched = desk.unassignedScans(id)
            if (unmatched > 0) confirmUnmatched = unmatched else handIn()
        })
    }
    if (confirmUnmatched > 0) {
        AlertDialog(
            onDismissRequest = { confirmUnmatched = 0 },
            title = { Text("Some pages aren't matched") },
            text = { Text("$confirmUnmatched scanned ${if (confirmUnmatched == 1) "page isn't" else "pages aren't"} matched to a question, so ${if (confirmUnmatched == 1) "it" else "they"} won't be marked.") },
            confirmButton = { TextButton(onClick = { confirmUnmatched = 0; scanning = true }) { Text("Match pages") } },
            dismissButton = { TextButton(onClick = { confirmUnmatched = 0; handIn() }) { Text("Hand in anyway") } },
        )
    }
}

@Composable
private fun McqCard(number: Int, q: McqQuestion, chosen: Int?, enabled: Boolean, onChoose: (Int) -> Unit) {
    Sheet {
        Text("$number. ${q.stem}", style = MaterialTheme.typography.titleMedium)
        pk.parhlou.student.ui.components.PastPaperTag(q.pastPapers)
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
private fun WrittenAnswerCard(paperId: String, q: WrittenQuestion, label: String, store: LocalStore, desk: ExamDesk, enabled: Boolean, scanVersion: Int = 0) {
    val scope = rememberCoroutineScope()
    val key = desk.draftKey(paperId, q.id)
    val saved by store.draft(key).collectAsState(initial = null)
    var text by remember(key) { mutableStateOf<String?>(null) }
    var photoVersion by remember(key) { mutableIntStateOf(0) }
    val photos = remember(key, photoVersion, scanVersion) { desk.photos(paperId, q.id) }
    val added = remember(key, photoVersion) { desk.galleryPhotos(paperId, q.id).size }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) scope.launch { desk.addPhotos(paperId, q.id, uris); photoVersion++ }
    }
    Sheet {
        Row {
            Text("$label. ${q.text}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("[${fmt(q.marks)}]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        pk.parhlou.student.ui.components.PastPaperTag(q.pastPapers)
        var shown by remember(key) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
        var loaded by remember(key) { mutableStateOf(false) }
        LaunchedEffect(saved) {
            val s = saved
            if (!loaded && s != null) { shown = androidx.compose.ui.text.input.TextFieldValue(s, androidx.compose.ui.text.TextRange(s.length)); loaded = true }
        }
        val update = { v: androidx.compose.ui.text.input.TextFieldValue ->
            shown = v; loaded = true
            if (v.text != text) { text = v.text; scope.launch { store.saveDraft(key, v.text) } }
        }
        var keys by rememberSaveable(key) { mutableStateOf(false) }
        OutlinedTextField(
            value = shown,
            onValueChange = update,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).testTag("answer-${q.id}"),
            placeholder = { Text("Type your answer and working, one step per line") },
        )
        TextButton(enabled = enabled, onClick = { keys = !keys }, modifier = Modifier.heightIn(min = 48.dp).testTag("keys-${q.id}")) {
            Text(if (keys) "Hide ∑ symbols" else "∑ Maths symbols")
        }
        if (keys && enabled) pk.parhlou.student.ui.components.MathKeyboard(shown, update, physics = paperId.contains("phy-") || paperId.contains("chem-"))
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { photos.take(5).forEach { PhotoThumb(it) } }
            val scanned = photos.size - added
            Muted(
                "${photos.size} page photo${if (photos.size == 1) "" else "s"} attached" +
                    (if (scanned > 0) " ($scanned scanned)" else "") + ". Photos are marked when you're online.",
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = enabled,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Add page photos") }
            if (added > 0) {
                TextButton(
                    enabled = enabled,
                    onClick = { scope.launch { desk.removePhotos(paperId, q.id); photoVersion++ } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Remove added photos") }
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

/** Opened from the Home camera button when no paper is in progress: pick the paper whose answers you're scanning. */
@Composable
fun ScanChooserScreen(
    papers: List<PaperBlueprint>,
    readinessPassed: Boolean,
    onStart: (String) -> Unit,
    onReadiness: () -> Unit,
    onBack: () -> Unit,
) {
    ParhlouScreen("Scan answer sheets", onBack = onBack) {
        Muted("Scanned pages belong to a paper, so each page can be matched to the question it answers. Choose the paper you're writing.")
        when {
            papers.isEmpty() -> Sheet {
                Text("No papers with written questions yet", style = MaterialTheme.typography.titleMedium)
                Muted("Scanning is for written answers. It will be available when a paper with written questions is published.")
            }
            !readinessPassed -> Sheet {
                Text("Pass the readiness check first", style = MaterialTheme.typography.titleMedium)
                Muted("Papers unlock after a short readiness quiz. Then you can start a paper and scan your answers.")
                BigButton("Take the readiness check", onClick = onReadiness)
            }
            else -> papers.forEach { b ->
                Sheet {
                    Text(b.title, style = MaterialTheme.typography.titleMedium)
                    Muted("${b.durationMinutes} minutes · ${fmt(b.totalMarks)} marks. Starting it starts the timer and the invigilator.")
                    SecondaryButton("Start and scan", onClick = { onStart(b.id) })
                }
            }
        }
    }
}

/** Asks once for permission to silence the phone during papers; hidden once it's allowed. */
@Composable
private fun FocusModeCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var allowed by remember { mutableStateOf(pk.parhlou.student.ui.FocusMode.allowed(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = pk.parhlou.student.ui.FocusMode.allowed(context) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (!allowed) Sheet(Modifier.testTag("focus-mode")) {
        Text("🔕 Silence your phone during papers", style = MaterialTheme.typography.titleMedium)
        Muted("Do Not Disturb turns on when you start and off when you hand in.")
        SecondaryButton("Allow", onClick = { pk.parhlou.student.ui.FocusMode.askPermission(context) })
    }
}
