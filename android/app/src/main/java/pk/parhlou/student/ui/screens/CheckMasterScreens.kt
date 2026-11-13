package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import java.io.File
import pk.parhlou.student.domain.MarkedPdf
import pk.parhlou.student.ui.exportsDir
import pk.parhlou.student.ui.shareFile
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.ExamDesk
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.ExamLog
import pk.parhlou.student.domain.IntegrityReport
import pk.parhlou.student.domain.Invigilator
import pk.parhlou.student.domain.PaperResult
import pk.parhlou.student.domain.PaperSubmission
import pk.parhlou.student.domain.WrittenOutcome
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

const val DEMO_CHECKER_LABEL = "Demo checker — practice marks, not official board marks"

/** Short label for a paper question: section letter plus number, e.g. "B3". */
fun questionLabel(q: WrittenQuestion): String = "${'A' + q.section}${q.number}"

/** Result of the automatic check, the invigilator's report, and a re-check for every written answer. */
@Composable
fun CheckMasterScreen(
    blueprint: PaperBlueprint,
    paper: PaperContent,
    desk: ExamDesk,
    store: LocalStore,
    onExplain: (TutorSeed) -> Unit,
    onHome: () -> Unit,
) {
    val id = blueprint.id
    val scope = rememberCoroutineScope()
    var submission by remember { mutableStateOf<PaperSubmission?>(null) }
    LaunchedEffect(id) { submission = desk.submission(paper) }
    val result by produceState<PaperResult?>(null, submission) {
        value = submission?.let { desk.check(blueprint, paper, it) }
    }
    val first by store.firstResult(id).collectAsState(initial = emptyMap())
    val log by store.examLog(id).collectAsState(initial = "")
    val report = Invigilator.report(ExamLog.parse(log), blueprint.durationMinutes)

    ParhlouScreen("Check Master") {
        StatusPill(DEMO_CHECKER_LABEL)
        val r = result
        if (r == null) {
            Sheet { Text("Checking your paper…", style = MaterialTheme.typography.titleMedium) }
            return@ParhlouScreen
        }

        Sheet {
            Text("Confirmed marks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${fmt(r.confirmed)} / ${fmt(r.total)}", style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag("confirmed-marks"))
            if (r.pending > 0) {
                Text("${fmt(r.pending)} more marks not checked yet", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary)
                Muted("These are never counted as wrong. Compare with the model answer, retype clearly and re-check. Page photos wait for the AI examiner.")
            }
            val firstTotal = first["total"]
            if (firstTotal != null && firstTotal != r.confirmed) Muted("At your first hand-in: ${fmt(firstTotal)} / ${fmt(r.total)}")
        }

        if (report != null) IntegrityCard(report)

        // Section A
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Section A · MCQs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${fmt(r.mcqMarks)} / ${r.mcq.size}", style = MaterialTheme.typography.titleMedium)
            }
            val wrong = r.mcq.filter { !it.correct }
            if (wrong.isEmpty()) Muted("All correct.")
            wrong.forEach { o ->
                val q = o.question
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(q.stem, style = MaterialTheme.typography.bodyLarge)
                    Muted("Your answer: ${o.chosen?.let { q.options[it] } ?: "not answered"}")
                    Text("Correct: ${q.options[q.sampleCorrectIndex]}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary)
                    Muted(q.rationales[q.sampleCorrectIndex].removePrefix("Correct. "))
                }
            }
        }

        // Written answers
        val attempted = r.written.filter { it.answer.isNotBlank() || it.photos > 0 }
        attempted.forEach { outcome ->
            WrittenResultCard(
                outcome = outcome,
                firstMark = first[outcome.question.id],
                attemptLimit = blueprint.sections.getOrNull(outcome.question.section)?.attempt,
                onRecheck = { newText ->
                    scope.launch {
                        store.saveDraft(desk.draftKey(id, outcome.question.id), newText)
                        submission = submission?.let { it.copy(written = it.written + (outcome.question.id to newText)) }
                    }
                },
                onExplain = { onExplain(TutorSeed(noteId = outcome.question.noteId)) },
            )
        }
        val skipped = r.written - attempted.toSet()
        if (skipped.isNotEmpty()) {
            Sheet {
                Text("Not attempted", style = MaterialTheme.typography.titleMedium)
                Muted(skipped.joinToString(", ") { questionLabel(it.question) })
            }
        }

        val context = LocalContext.current
        var pdfMessage by remember { mutableStateOf<String?>(null) }
        SecondaryButton("Save marked paper (PDF)", onClick = {
            scope.launch {
                val file = runCatching {
                    withContext(Dispatchers.IO) {
                        val photos = r.written.associate { o -> o.question.id to desk.photos(id, o.question.id) }
                        MarkedPdf.write(File(exportsDir(context), "parhlou-marked-$id.pdf"), blueprint, r, report, photos, DEMO_CHECKER_LABEL)
                    }
                }.getOrNull()
                if (file == null) pdfMessage = "Couldn't make the PDF. Free some space on the phone and try again."
                else { pdfMessage = null; shareFile(context, file, "application/pdf", "Save your marked paper") }
            }
        })
        pdfMessage?.let { Muted(it) }
        BigButton("Back to home", onClick = onHome)
    }
}

@Composable
private fun IntegrityCard(report: IntegrityReport) {
    Sheet {
        Text("Invigilator report", style = MaterialTheme.typography.titleMedium)
        ReportRow("Time used", "${Invigilator.duration(report.usedMillis)} of ${Invigilator.duration(report.allowedMillis)}")
        ReportRow("Left the paper", if (report.timesAway == 0) "Never" else "${report.timesAway} time${if (report.timesAway == 1) "" else "s"}")
        if (report.timesAway > 0) {
            ReportRow("Time away", "${Invigilator.duration(report.totalAwayMillis)} (longest ${Invigilator.duration(report.longestAwayMillis)})")
        }
        ReportRow("Finished", if (report.finishedOnTime) "On time" else "${Invigilator.duration(report.overrunMillis)} after time was up")
        Muted("Recorded on this phone only. Nobody else receives it.")
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WrittenResultCard(
    outcome: WrittenOutcome,
    firstMark: Double?,
    attemptLimit: Int?,
    onRecheck: (String) -> Unit,
    onExplain: () -> Unit,
) {
    val q = outcome.question
    var editing by remember(q.id) { mutableStateOf(false) }
    var text by remember(q.id, outcome.answer) { mutableStateOf(outcome.answer) }
    Sheet(Modifier.testTag("result-${q.id}")) {
        Text("Question ${questionLabel(q)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(q.text, style = MaterialTheme.typography.titleMedium)
        if (!outcome.counted) {
            Muted("Not counted: you answered more than ${attemptLimit ?: "the allowed number of"} questions in this section, and only the first ones are checked.")
        }
        WrittenMarkDetails(q, outcome.mark, photos = outcome.photos)
        if (firstMark != null && firstMark != outcome.mark.awarded) {
            Muted("First check: ${fmt(firstMark)} / ${fmt(q.marks)}. Re-checked now: ${fmt(outcome.mark.awarded)} / ${fmt(q.marks)}.")
        }
        if (editing) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("recheck-${q.id}"),
                placeholder = { Text("Type your answer and working clearly") },
            )
            BigButton("Check again", onClick = { editing = false; onRecheck(text) })
        } else {
            SecondaryButton("Re-check this question", onClick = { editing = true })
            if (q.noteId != null && outcome.mark.awarded < q.marks) {
                TextButton(onClick = onExplain, modifier = Modifier.heightIn(min = 48.dp)) { Text("Explain this mistake") }
            }
        }
    }
}

/** Marks for one written answer, criterion by criterion, with what was missing, why it matters and how to fix it. */
@Composable
fun WrittenMarkDetails(question: WrittenQuestion, mark: WrittenMark, photos: Int = 0) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${fmt(mark.awarded)} / ${fmt(question.marks)}", style = MaterialTheme.typography.titleLarge)
        StatusPill(mark.status.label, warning = mark.status != MarkStatus.CHECKED)
    }
    when (mark.status) {
        MarkStatus.WAITING_FOR_AI ->
            Muted("$photos page photo${if (photos == 1) "" else "s"} saved. The demo checker can't read handwriting, so these marks wait for the AI examiner. Type your working to have it checked now.")
        MarkStatus.COULD_NOT_CHECK ->
            Muted("The checker couldn't recognise the marking points in what you typed. That doesn't mean it is wrong. Compare with the model answer, then retype it clearly and re-check.")
        else -> Unit
    }
    mark.criteria.forEach { c ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (c.status) {
                CriterionStatus.MET -> Icon(Icons.Filled.CheckCircle, contentDescription = "Marks given", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                CriterionStatus.NOT_FOUND -> Icon(Icons.Filled.Cancel, contentDescription = "Not found", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                CriterionStatus.UNSURE -> Icon(Icons.Filled.Help, contentDescription = "Not checked", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${c.description}  (${fmt(c.awarded)}/${fmt(c.max)})", style = MaterialTheme.typography.bodyLarge)
                if (c.status == CriterionStatus.NOT_FOUND && mark.status != MarkStatus.NOT_ATTEMPTED) {
                    Muted("What: this step is missing from your answer.")
                    Muted("Why: ${c.why}")
                    Muted("How: ${c.how}")
                }
            }
        }
    }
    var showModel by remember(question.id) { mutableStateOf(false) }
    if (showModel) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Model answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                question.modelAnswer.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
    TextButton(onClick = { showModel = !showModel }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (showModel) "Hide model answer" else "Show model answer")
    }
    if (mark.awarded < question.marks && mark.status != MarkStatus.NOT_ATTEMPTED) {
        Muted("Try next: ${mark.followUp}")
    }
}
