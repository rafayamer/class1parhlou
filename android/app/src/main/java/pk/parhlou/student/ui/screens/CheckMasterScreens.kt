package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Help
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
import pk.parhlou.student.domain.AreaScore
import pk.parhlou.student.domain.ExaminerReport
import pk.parhlou.student.domain.PaperAnalysis
import pk.parhlou.student.domain.PaperAnalyzer
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.ExamLog
import pk.parhlou.student.domain.IntegrityReport
import pk.parhlou.student.domain.Invigilator
import pk.parhlou.student.domain.BoardMarking
import pk.parhlou.student.domain.PaperResult
import pk.parhlou.student.domain.PaperSubmission
import pk.parhlou.student.domain.WrittenOutcome
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

const val DEMO_CHECKER_LABEL = "Marked by Ustaad"

/** Short label for a paper question: section letter plus number, e.g. "B3". */
/** The board's numbering ("Q2 (iii)", "Q5") when the paper is known; otherwise section letter and number. */
fun questionLabel(q: WrittenQuestion, blueprint: PaperBlueprint? = null): String =
    blueprint?.let { pk.parhlou.student.domain.BoardNumbering(it).label(q) } ?: "${'A' + q.section}${q.number}"

/** Result of the automatic check, the invigilator's report, and a re-check for every written answer. */
@Composable
fun CheckMasterScreen(
    blueprint: PaperBlueprint,
    paper: PaperContent,
    desk: ExamDesk,
    store: LocalStore,
    onExplain: (TutorSeed) -> Unit,
    onHome: () -> Unit,
    /** The topic and unit a question tests (by its note or module id), for the examiner's analysis. */
    topicOf: (String?) -> pk.parhlou.student.domain.TopicInfo? = { null },
    profile: () -> String = { "" },
    language: String = "English",
    studentName: String = "",
) {
    val id = blueprint.id
    val scope = rememberCoroutineScope()
    var submission by remember { mutableStateOf<PaperSubmission?>(null) }
    LaunchedEffect(id) { submission = desk.handedIn(id)?.first ?: desk.submission(paper) }
    val result by produceState<PaperResult?>(null, submission) {
        val done = desk.handedIn(id)
        value = submission?.let { s -> if (done != null && done.first === s) done.second else desk.check(blueprint, paper, s) }
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
                Muted("These are never counted as wrong. Compare with the model answer, retype clearly and re-check. Page photos are marked when you're online.")
            }
            // Board-style grade band and the examiner's remarks.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Grade ${BoardMarking.grade(r.percent)}", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("grade"))
                }
                Text("${r.percent}%" + if (r.percent >= 33) " · pass" else " · below the 33% pass mark", style = MaterialTheme.typography.titleMedium)
            }
            val remarks = BoardMarking.remarks(r, { questionLabel(it, blueprint) })
            if (remarks.isNotEmpty()) {
                Text("Examiner's remarks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("examiner-remarks"))
                remarks.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
            val firstTotal = first["total"]
            if (firstTotal != null && firstTotal != r.confirmed) Muted("At your first hand-in: ${fmt(firstTotal)} / ${fmt(r.total)}")
        }

        val analysis = remember(r) { PaperAnalyzer.analyse(blueprint, r, topicOf, { questionLabel(it, blueprint) }, report) }
        val examiner by produceState<ExaminerReport?>(null, analysis) {
            value = desk.examinerReport(blueprint.title, id, analysis, profile(), language, studentName)
        }
        ExaminerAnalysis(analysis, examiner, onPractise = { onExplain(TutorSeed(noteId = it)) })

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
                label = questionLabel(outcome.question, blueprint),
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
                Muted(skipped.joinToString(", ") { questionLabel(it.question, blueprint) })
            }
        }

        val context = LocalContext.current
        var pdfMessage by remember { mutableStateOf<String?>(null) }
        SecondaryButton("Save marked paper (PDF)", onClick = {
            scope.launch {
                val file = runCatching {
                    withContext(Dispatchers.IO) {
                        val photos = r.written.associate { o -> o.question.id to desk.photos(id, o.question.id) }
                        MarkedPdf.write(File(exportsDir(context), "parhlou-marked-$id.pdf"), blueprint, r, report, photos, DEMO_CHECKER_LABEL,
                            analysis = analysis, examiner = examiner)
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

/** The examiner's analysis: where the marks came from and went, strong and weak topics, and what to work on next. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExaminerAnalysis(a: PaperAnalysis, report: ExaminerReport?, onPractise: (String) -> Unit) {
    Sheet(Modifier.testTag("examiner-analysis")) {
        Text("Examiner's analysis", style = MaterialTheme.typography.titleLarge)
        if (report == null) {
            Muted("Your examiner is writing your analysis…")
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            StatusPill(if (report.byAi) "Written by Ustaad from your marks" else "Written from your marks", warning = false)
            Text(report.headline, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("analysis-headline"))
            Text(report.summary, style = MaterialTheme.typography.bodyLarge)
        }
        if (a.units.isNotEmpty()) {
            Text("By unit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            a.units.forEach { ScoreBar(it) }
        }
        if (a.types.size > 1) {
            Text("By question type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            a.types.forEach { ScoreBar(it) }
        }
        val lost = a.lost.filterValues { it > 0 }.entries.sortedByDescending { it.value }
        if (lost.isNotEmpty()) {
            Text("Where your marks went", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            lost.forEach { (k, v) -> ReportRow(k.label, "−${fmt(v)}") }
        }
    }
    if (report == null) return
    if (report.strengths.isNotEmpty()) Sheet(Modifier.testTag("analysis-strong")) {
        Text("Strong: keep it up", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        report.strengths.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    }
    if (report.weaknesses.isNotEmpty()) Sheet(Modifier.testTag("analysis-weak")) {
        Text("Needs hard work", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        report.weaknesses.forEach { w ->
            val score = (a.weak + a.needsWork).firstOrNull { it.title == w.topic }
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.topic + (score?.let { "  ·  ${it.percent}%" } ?: ""), style = MaterialTheme.typography.titleSmall)
                Muted("What went wrong: ${w.why}")
                Text("What to do: ${w.action}", style = MaterialTheme.typography.bodyMedium)
                score?.noteId?.let { note -> TextButton(onClick = { onPractise(note) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Work on this with Ustaad") } }
            }
        }
    }
    if (report.plan.isNotEmpty() || report.technique.isNotEmpty()) Sheet(Modifier.testTag("analysis-plan")) {
        if (report.plan.isNotEmpty()) {
            Text("Your plan for the next 7 days", style = MaterialTheme.typography.titleMedium)
            report.plan.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (report.technique.isNotEmpty()) {
            Text("Exam technique", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            report.technique.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (report.closing.isNotBlank()) Text(report.closing, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ScoreBar(s: AreaScore) {
    val color = when {
        s.percent >= 75 -> MaterialTheme.colorScheme.secondary
        s.percent >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${fmt(s.got)}/${fmt(s.max)} · ${s.percent}%", style = MaterialTheme.typography.bodyMedium)
        }
        androidx.compose.material3.LinearProgressIndicator(progress = { s.percent / 100f }, color = color,
            modifier = Modifier.fillMaxWidth().height(6.dp))
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
    label: String,
    onRecheck: (String) -> Unit,
    onExplain: () -> Unit,
) {
    val q = outcome.question
    var editing by remember(q.id) { mutableStateOf(false) }
    var text by remember(q.id, outcome.answer) { mutableStateOf(outcome.answer) }
    Sheet(Modifier.testTag("result-${q.id}")) {
        Text("Question $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    when (mark.examiners) {
        2 -> Muted("Double-marked: two markers agreed on every step.")
        3 -> Muted("Double-marked: the two markers disagreed on a step, so a head examiner decided, as in the board.")
        else -> Unit
    }
    when (mark.status) {
        MarkStatus.WAITING_FOR_AI ->
            Muted("$photos page photo${if (photos == 1) "" else "s"} saved. They're marked when you're online.")
        MarkStatus.COULD_NOT_CHECK ->
            Muted("The checker couldn't recognise the marking points in what you typed. That doesn't mean it is wrong. Compare with the model answer, then retype it clearly and re-check.")
        else -> Unit
    }
    mark.criteria.forEach { c ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (c.status) {
                CriterionStatus.MET -> Icon(Icons.Filled.CheckCircle, contentDescription = "Marks given", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                CriterionStatus.NOT_FOUND -> Icon(Icons.Filled.Cancel, contentDescription = "Not found", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                CriterionStatus.UNSURE -> Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Not checked", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${c.description}  (${fmt(c.awarded)}/${fmt(c.max)})", style = MaterialTheme.typography.bodyLarge)
                if (c.evidence.isNotBlank() && mark.status != MarkStatus.NOT_ATTEMPTED) {
                    Muted((if (c.status == CriterionStatus.MET) "Examiner saw: " else "Examiner's note: ") + c.evidence)
                }
                if (c.status == CriterionStatus.NOT_FOUND && mark.status != MarkStatus.NOT_ATTEMPTED) {
                    if (c.evidence.isBlank()) Muted("What: this step is missing from your answer.")
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
    if (mark.correction.isNotBlank() && mark.awarded < question.marks) Muted("Correction: ${mark.correction}")
    if (mark.awarded < question.marks && mark.status != MarkStatus.NOT_ATTEMPTED) {
        Muted("Try next: ${mark.followUp}")
    }
}
