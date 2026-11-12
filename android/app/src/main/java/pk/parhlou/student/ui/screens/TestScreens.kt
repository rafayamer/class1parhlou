package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.domain.BlueprintValidator
import pk.parhlou.student.domain.ExamClock
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/** Readiness pass mark for Test Series. Placeholder until academic reviewers set it (PRD §11). */
const val READINESS_PASS_PERCENT = 80

@Composable
fun TestsScreen(
    blueprints: List<PaperBlueprint>,
    readinessPassed: Boolean,
    onReadiness: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: (() -> Unit)?,
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
            Card(onClick = { onOpen(b.id) }, enabled = readinessPassed, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium)
                    Muted("${b.durationMinutes} minutes · ${fmt(b.totalMarks)} marks")
                    StatusPill(if (b.official) "Official pattern" else "Practice pattern — not official")
                    if (!readinessPassed) Muted("Locked until you pass the readiness check.")
                }
            }
        }
    }
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
fun AssessmentStartScreen(blueprint: PaperBlueprint, onStart: () -> Unit, onBack: () -> Unit) {
    val problems = BlueprintValidator.problems(blueprint)
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
            Muted("Write your answers on paper. The timer keeps running if you leave the app.")
            Muted("When you finish, you'll photograph your pages. Scanning and marking are not in this build yet.")
        }
        if (problems.isNotEmpty()) {
            Sheet {
                Text("This paper can't be started", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                problems.forEach { Muted(it) }
            }
        }
        BigButton("Start paper", onClick = onStart, enabled = problems.isEmpty())
    }
}

@Composable
fun ExamScreen(blueprint: PaperBlueprint, store: LocalStore, onSubmit: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(blueprint.id) {
        startedAt = store.examStartedAt(blueprint.id, System.currentTimeMillis())
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val start = startedAt
    ParhlouScreen(blueprint.title, onBack = onBack) {
        if (start == null) { Muted("Loading…"); return@ParhlouScreen }
        val clock = ExamClock(start, blueprint.durationMinutes)
        Sheet {
            Text("Time left", style = MaterialTheme.typography.bodyMedium)
            Text(
                clock.label(now), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (clock.isExpired(now)) Text("Time is up. Put your pen down and submit.", color = MaterialTheme.colorScheme.error)
        }
        blueprint.sections.forEach { s ->
            Sheet {
                Text(s.type, style = MaterialTheme.typography.titleMedium)
                Muted("Answer ${s.attempt} of ${s.count} · ${fmt(s.marksEach)} marks each")
                Muted("Questions appear here once a reviewed paper is published for your edition.")
            }
        }
        SecondaryButton("Scan answer sheets (coming next)", onClick = {}, enabled = false)
        BigButton("Submit paper", onClick = { scope.launch { store.clearExam(blueprint.id); onSubmit() } })
    }
}

@Composable
fun ResultScreen(onHome: () -> Unit) {
    ParhlouScreen("Paper submitted") {
        Sheet {
            StatusPill("Marking pending")
            Text("Your paper hasn't been marked.", style = MaterialTheme.typography.titleLarge)
            Muted("Marking with detailed feedback arrives in a later version. No score has been given, and your progress hasn't changed.")
        }
        BigButton("Back to home", onClick = onHome)
    }
}

internal fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
