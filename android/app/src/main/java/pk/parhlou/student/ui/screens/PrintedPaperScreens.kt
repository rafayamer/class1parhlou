package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.parhlou.scanner.ScannerFlow
import pk.parhlou.student.data.ExamDesk
import pk.parhlou.student.data.PaperBlueprint
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.domain.BoardNumbering
import pk.parhlou.student.domain.MyPaper
import pk.parhlou.student.domain.ScriptAssembler
import pk.parhlou.student.domain.ScriptPage
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/**
 * A paper written on paper, as in the real exam: scan every page in any order (bubble sheet too), the AI examiner reads the
 * handwriting and sorts the answers by the question numbers the student wrote, the student checks what was read, then the
 * paper is double-marked step by step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintedPaperScreen(
    blueprint: PaperBlueprint,
    paper: PaperContent,
    desk: ExamDesk,
    appScope: CoroutineScope,
    readsHandwriting: Boolean,
    onMarked: () -> Unit,
    onBack: () -> Unit,
) {
    val id = blueprint.id
    val store = remember(id) { desk.printedStore(id) }
    val numbering = remember(blueprint) { BoardNumbering(blueprint) }
    val questions = remember(paper) { paper.written.sortedWith(compareBy({ it.section }, { it.number })) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf<List<ScriptPage>?>(null) }
    var reading by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var marking by remember { mutableStateOf(false) }
    val assigned = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val scanned = remember(version) { store.pages().size }

    if (scanning) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { scanning = false; version++; pages = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                ScannerFlow(store = store, questions = emptyList(), status = "Scan every page: bubble sheet and answers, in any order",
                    onClose = { scanning = false; version++; pages = null })
            }
        }
    }

    ParhlouScreen("Mark my written paper", onBack = onBack) {
        Sheet {
            Text(blueprint.title, style = MaterialTheme.typography.titleMedium)
            Muted("1. Sit the printed paper with a clock: MCQs on the bubble sheet first, then the subjective part.")
            Muted("2. Scan every page: the bubble sheet and all your answer pages, in any order. Write question numbers like \"Q2 (iii)\" or \"5 b)\" on your answers.")
            Muted("3. Ustaad reads your handwriting and you check what it read. Then two AI examiners mark it step by step, like board examiners.")
        }
        if (!readsHandwriting) {
            Sheet {
                Text("Needs the AI examiner", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Muted("Reading handwriting needs an internet connection to Parhlou's AI examiner. Connect and open this page again.")
            }
            return@ParhlouScreen
        }
        Sheet {
            Text(if (scanned == 0) "No pages scanned yet" else "$scanned page${if (scanned == 1) "" else "s"} scanned", style = MaterialTheme.typography.titleMedium)
            SecondaryButton(if (scanned == 0) "Scan my pages" else "Add or fix pages", onClick = { scanning = true })
            if (scanned > 0 && pages == null && reading == null) {
                BigButton("Read my pages", onClick = {
                    reading = 0 to scanned
                    scope.launch {
                        val read = desk.readScript(blueprint, paper) { done, total -> reading = done to total }
                        withContext(Dispatchers.Main.immediate) { pages = read; reading = null }
                    }
                })
            }
        }
        reading?.let { (done, total) ->
            Sheet(Modifier.testTag("reading-pages")) {
                Text("Ustaad is reading page $done of $total…", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Muted("Each page takes a few seconds. Keep the app open.")
            }
        }
        val read = pages ?: return@ParhlouScreen
        val result = ScriptAssembler.assemble(read, paper, numbering, assigned)

        if (result.remarks.isNotEmpty()) Sheet(Modifier.testTag("script-remarks")) {
            Text("Please check", style = MaterialTheme.typography.titleMedium)
            result.remarks.forEach { Muted("•  $it") }
        }
        if (paper.mcqs.isNotEmpty()) Sheet {
            Text("Bubble sheet: ${result.mcq.size} of ${paper.mcqs.size} MCQs read", style = MaterialTheme.typography.titleMedium)
            if (result.mcq.isEmpty()) Muted("No bubble sheet found. Scan it too, or your MCQs score zero.")
            else Muted(paper.mcqs.mapIndexedNotNull { i, q -> result.mcq[q.id]?.let { "${i + 1}-${'A' + it}" } }.joinToString("  "))
        }
        result.loose.forEach { piece ->
            val key = "${piece.page}-${piece.index}"
            Sheet(Modifier.testTag("loose-$key")) {
                Text("Page ${piece.page}: which question is this?", style = MaterialTheme.typography.titleMedium)
                Text(piece.segment.text.take(300), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    questions.forEach { q ->
                        FilterChip(selected = assigned[key] == q.id, onClick = { assigned[key] = q.id }, label = { Text(numbering.label(q)) })
                    }
                    FilterChip(selected = assigned[key] == "", onClick = { assigned[key] = "" }, label = { Text("Rough work, ignore") })
                }
            }
        }
        Text("What Ustaad read", style = MaterialTheme.typography.titleLarge)
        questions.forEach { q ->
            val answer = result.answers[q.id]
            Sheet(Modifier.testTag("read-${q.id}")) {
                Text("${numbering.label(q)}  ·  ${q.text.take(90)}", style = MaterialTheme.typography.labelLarge)
                if (answer == null) Muted("Not answered")
                else {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) { Text(answer, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Muted("On page ${result.pagesOf[q.id].orEmpty().joinToString(", ")}")
                }
            }
        }
        Muted("If something was read wrongly, fix that page (retake it flat, in good light) and read again. The examiners also look at your pages themselves, so the photos decide.")
        if (marking) StatusPill("Two examiners are marking your paper…")
        else BigButton("Mark my paper", enabled = result.answers.isNotEmpty() || result.mcq.isNotEmpty(), onClick = {
            marking = true
            appScope.launch {
                desk.submitPrinted(blueprint, paper, result)
                withContext(Dispatchers.Main.immediate) { onMarked() }
            }
        })
        TextButton(onClick = { pages = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Read the pages again") }
    }
}

/** "Make my paper": the AI examiner chooses a full 2027-pattern paper of real board questions for this student. */
@Composable
fun MakePaperScreen(
    make: suspend () -> MyPaper?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    var made by remember { mutableStateOf<MyPaper?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { failed = false; made = make(); if (made == null) failed = true }
    ParhlouScreen("Make my paper", onBack = onBack) {
        val p = made
        when {
            failed -> Sheet {
                Text("Couldn't make a paper", style = MaterialTheme.typography.titleMedium)
                Muted("There aren't enough published questions for every part of the 2027 pattern yet.")
                SecondaryButton("Try again", onClick = { attempt++ })
            }
            p == null -> Sheet(Modifier.testTag("making-paper")) {
                Text("Ustaad is choosing your questions…", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Muted("Real board questions, most repeated first, more from your weak topics, and none you've already done.")
            }
            else -> {
                Sheet(Modifier.testTag("paper-made")) {
                    Text(p.blueprint.title, style = MaterialTheme.typography.titleLarge)
                    StatusPill(if (p.byAi) "Chosen by Ustaad (AI)" else "Chosen by the app (AI offline)", warning = !p.byAi)
                    Muted("2027 board pattern: ${p.paper.mcqs.size} MCQs, ${p.paper.written.size} written questions, ${p.blueprint.totalMarks.toInt()} marks.")
                    if (p.summary.isNotBlank()) Text(p.summary, style = MaterialTheme.typography.bodyLarge)
                    val board = (p.paper.mcqs.count { it.pastPapers.isNotEmpty() } + p.paper.written.count { it.pastPapers.isNotEmpty() })
                    Muted("$board of ${p.paper.mcqs.size + p.paper.written.size} questions came in real board papers.")
                }
                BigButton("Open the paper", onClick = { onOpen(p.blueprint.id) })
                Muted("Sit it on the phone, or print it and write it on paper like the real exam.")
            }
        }
    }
}
