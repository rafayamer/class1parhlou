package pk.parhlou.student.ui.screens

import pk.parhlou.student.ui.components.appear
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import pk.parhlou.student.ui.components.ChunkyButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.WrittenQuestion
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.testTag
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.TOPIC_PREFIX
import pk.parhlou.student.domain.QuizAnswer
import pk.parhlou.student.domain.QuizScorer
import pk.parhlou.student.domain.QuizSummary
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import pk.parhlou.student.ui.components.LocalReducedMotion
import pk.parhlou.student.ui.components.rememberStepTransition
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.CheckRow
import pk.parhlou.student.ui.components.ChoiceCard
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SampleBadge
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/** The parts of a section that open inside its page (the lesson and practice open full screen). */
enum class Stage(val label: String) { READ("Book solutions"), WRITE("Write answers"), IMPROVE("Improve") }

/**
 * A section's page: how well it's known (a mastery ring), one "Continue" for the next step, and every step as a card:
 * the lesson and practice open in the full-screen player; book solutions, written answers and improving open here.
 */
@Composable
fun ModuleScreen(
    module: Module,
    quiz: List<McqQuestion>,
    progress: ProgressStore,
    store: LocalStore,
    ai: ParhlouAi,
    /** Words that describe the course for a lesson search, e.g. "9th class Mathematics Punjab board". */
    courseLabel: String,
    onAskTutor: (TutorSeed) -> Unit,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit = {},
    onPractice: (String) -> Unit = {},
    /** The YouTube search for this section's lectures (VideoSearch.query). */
    videoQuery: String = pk.parhlou.student.domain.VideoSearch.query("", null, null, module.title),
) {
    var open by rememberSaveable { mutableStateOf<Stage?>(null) }
    LaunchedEffect(module.id) {
        if (progress.evidence(module.id).isEmpty()) progress.add(module.id, Evidence.ModuleOpened)
    }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }

    ParhlouScreen(module.title, onBack = { if (open != null) open = null else onBack() }) {
        val stage = open
        if (stage == null) {
            SectionHub(module, quiz.isNotEmpty(), progress, videoQuery, ai,
                onLesson = { onOpenLesson(module.id) }, onPractice = { onPractice(module.id) }, onOpen = { open = it },
                onAskTutor = { onAskTutor(TutorSeed(moduleId = module.id, question = "Help me with ${module.title}")) })
            return@ParhlouScreen
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { open = null }, modifier = Modifier.heightIn(min = 44.dp).testTag("all-steps")) { Text("← All steps") }
            Spacer(Modifier.weight(1f))
            Text(stage.label, style = MaterialTheme.typography.titleMedium)
        }
        AnimatedContent(targetState = stage, transitionSpec = rememberStepTransition(), label = "stage") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    Stage.READ -> ReadStage(module, progress) { open = if (module.writtenQuestions.isNotEmpty()) Stage.WRITE else null }
                    Stage.WRITE -> WriteStage(module, progress, store, ai) { open = Stage.IMPROVE }
                    Stage.IMPROVE -> ImproveStage(
                        module, progress, store, ai,
                        onAskTutor = { onAskTutor(TutorSeed(moduleId = module.id, noteId = module.writtenQuestions.firstOrNull()?.noteId)) },
                        onRetryQuiz = { onPractice(module.id) },
                    )
                }
            }
        }
    }
}

private enum class StepStatus { DONE, NEXT, TODO }

@Composable
private fun SectionHub(
    module: Module,
    hasPractice: Boolean,
    progress: ProgressStore,
    videoQuery: String,
    ai: ParhlouAi,
    onLesson: () -> Unit,
    onPractice: () -> Unit,
    onOpen: (Stage) -> Unit,
    onAskTutor: () -> Unit,
) {
    val context = LocalContext.current
    val brand = pk.parhlou.student.ui.theme.LocalBrand.current
    val ev = progress.evidence(module.id)
    val level = pk.parhlou.student.domain.Rewards.sectionLevel(ev, module.lesson != null)
    val taught = module.lesson == null || Evidence.LessonCompleted in ev
    val quizzes = ev.filterIsInstance<Evidence.QuizCompleted>()
    val practised = quizzes.isNotEmpty()
    val wrote = Evidence.WrittenSubmitted in ev
    val read = Evidence.ReadingChecked in ev
    // The one thing to do next.
    val next: Pair<String, () -> Unit> = when {
        !taught -> "Start the lesson" to onLesson
        hasPractice && !practised -> "Practise this section" to onPractice
        module.writtenQuestions.isNotEmpty() && !wrote -> "Write a board answer" to { onOpen(Stage.WRITE) }
        else -> "Practise again" to onPractice
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pk.parhlou.student.ui.components.ProgressRing(level / 5f, 76.dp, 8.dp, brand.success, MaterialTheme.colorScheme.surface,
            Modifier.semantics { contentDescription = "Mastery level $level of 5" }) {
            Text("$level/5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (level) { 0 -> "Not started"; 5 -> "Mastered"; else -> "Level $level of 5" },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(when {
                    level >= 5 -> "Exam ready. Keep it fresh with practice."
                    level >= pk.parhlou.student.domain.Rewards.LEARNED_LEVEL -> "Learned! Score 90% or write a board answer to fill the ring."
                    else -> "Lesson, then practice: 70% or more and this section is learned."
                }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (!module.inExam) StatusPill("Not in the exam · practice", warning = true)
        }
    }
    ChunkyButton(next.first, onClick = next.second, modifier = Modifier.testTag("section-continue"))

    Text("Your steps", style = MaterialTheme.typography.titleLarge)
    module.lesson?.let { l ->
        SectionStep("📖", "Lesson", "${l.explain.size} idea${if (l.explain.size == 1) "" else "s"}" +
            (if (l.example != null) ", a worked example" else "") + (if (l.yourTurn.isNotEmpty()) " and your turn" else ""),
            if (taught) StepStatus.DONE else StepStatus.NEXT, "step-lesson", onLesson)
    }
    if (hasPractice) SectionStep("🎯", "Practice", if (practised) "Best first try: ${quizzes.maxOf { it.firstTryPercent }}%" else "Quick questions with instant feedback",
        when { practised -> StepStatus.DONE; taught -> StepStatus.NEXT; else -> StepStatus.TODO }, "step-practice", onPractice)
    if (module.solutions.isNotEmpty() || module.readingTasks.isNotEmpty())
        SectionStep("📘", "Book solutions", "Every textbook question, step by step", if (read) StepStatus.DONE else StepStatus.TODO, "step-solutions") { onOpen(Stage.READ) }
    if (module.writtenQuestions.isNotEmpty())
        SectionStep("✍️", "Write answers", "Board-style answers, checked for you",
            when { wrote -> StepStatus.DONE; taught && practised -> StepStatus.NEXT; else -> StepStatus.TODO }, "step-write") { onOpen(Stage.WRITE) }
    SectionStep("🔁", "Improve", "What to fix next, and why", StepStatus.TODO, "step-improve") { onOpen(Stage.IMPROVE) }

    VideoLessons(module, videoQuery, ai)
    TextButton(onClick = onAskTutor, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("💬 Ask Ustaad about this section") }
}

/**
 * Video lectures for a section: the checked teacher video if there is one, then the top YouTube lectures found by the
 * server (shown here, each opening that exact video), else a well-aimed YouTube search.
 */
@Composable
private fun VideoLessons(module: Module, query: String, ai: ParhlouAi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember(module.id) { mutableStateOf<List<pk.parhlou.student.ai.LessonVideo>?>(null) }
    var looking by remember(module.id) { mutableStateOf(false) }
    val search = pk.parhlou.student.domain.VideoSearch.searchUrl(query)
    pk.parhlou.student.ui.components.StepCard(modifier = Modifier.testTag("video")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎬", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Video lectures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Watch a teacher explain it, then come back to practise.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        module.video?.let { v ->
            VideoRow("▶  ${v.teacher}", v.channel) { pk.parhlou.student.ui.Links.open(context, v.url) }
        }
        val list = found
        when {
            list == null -> TextButton(onClick = {
                looking = true
                scope.launch {
                    val r = ai.lessonVideos(query).orEmpty()
                    looking = false
                    if (r.isEmpty()) pk.parhlou.student.ui.Links.open(context, search) else found = r.take(4)
                }
            }, enabled = !looking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("find-videos")) {
                Text(if (looking) "Finding lectures…" else "Find lectures for this section")
            }
            else -> {
                list.forEach { v ->
                    val url = pk.parhlou.student.domain.VideoSearch.watchUrl(v.id) ?: return@forEach
                    VideoRow("▶  ${v.title}", v.channel) { pk.parhlou.student.ui.Links.open(context, url) }
                }
                TextButton(onClick = { pk.parhlou.student.ui.Links.open(context, search) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("More on YouTube") }
            }
        }
        Text("Opens in YouTube. Parhlou doesn't copy videos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VideoRow(title: String, channel: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (channel.isNotBlank()) Text(channel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionStep(icon: String, title: String, subtitle: String, status: StepStatus, tag: String, onClick: () -> Unit) {
    val order = remember(tag) { listOf("step-lesson", "step-practice", "step-solutions", "step-write", "step-improve").indexOf(tag) + 2 }
    val brand = pk.parhlou.student.ui.theme.LocalBrand.current
    val accent = when (status) { StepStatus.DONE -> brand.success; StepStatus.NEXT -> MaterialTheme.colorScheme.primary; StepStatus.TODO -> brand.line }
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag).then(Modifier.appear(order)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (status) {
                StepStatus.DONE -> Text("✓ Done", style = MaterialTheme.typography.labelLarge, color = brand.success, fontWeight = FontWeight.Bold)
                StepStatus.NEXT -> Text("Next ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                StepStatus.TODO -> Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
    if (module.solutions.isNotEmpty()) BookSolutions(module)
    BigButton("Next: write", onClick = {
        if (done.size == module.readingTasks.size) progress.add(module.id, Evidence.ReadingChecked)
        onNext()
    })
}

private fun writtenDraftKey(module: Module, q: WrittenQuestion) = "${module.id}_${q.id}"

@Composable
private fun WriteStage(module: Module, progress: ProgressStore, store: LocalStore, ai: ParhlouAi, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var submitted by remember(module.id) { mutableStateOf(Evidence.WrittenSubmitted in progress.evidence(module.id)) }
    // Topic evidence is recorded once per "Check my answer", not every time the marks are shown again.
    var justChecked by remember(module.id) { mutableStateOf(false) }
    if (module.writtenQuestions.isEmpty()) {
        Muted("No written practice is published for this module yet.")
        return
    }
    module.writtenQuestions.forEach { q ->
        val key = writtenDraftKey(module, q)
        val saved by store.draft(key).collectAsState(initial = null)
        var text by remember(key) { mutableStateOf<String?>(null) }
        val answer = text ?: saved ?: ""
        Sheet {
            Row {
                Text(q.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("  [${fmt(q.marks)}]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!submitted) {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { new -> text = new; scope.launch { store.saveDraft(key, new) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("practice-${q.id}"),
                    placeholder = { Text("Write your answer and working. It saves as you type.") },
                )
                Muted("Saved on this phone.")
            } else {
                Muted("Your answer:")
                Text(answer.ifBlank { "(nothing written)" }, style = MaterialTheme.typography.bodyLarge)
                val mark by produceState<WrittenMark?>(null, q.id, answer) { value = ai.markWritten(q, answer, 0) }
                StatusPill(DEMO_CHECKER_LABEL)
                val checked = mark
                if (checked == null) Muted("Checking…") else WrittenMarkDetails(q, checked)
                LaunchedEffect(checked) {
                    val note = q.noteId
                    if (justChecked && checked != null && note != null) {
                        when {
                            checked.criteria.any { it.status == CriterionStatus.NOT_FOUND } -> progress.add(TOPIC_PREFIX + note, Evidence.TopicMissed)
                            checked.status == MarkStatus.CHECKED -> progress.add(TOPIC_PREFIX + note, Evidence.TopicCorrect)
                        }
                    }
                }
            }
        }
    }
    if (submitted) {
        SecondaryButton("Edit my answer and check again", onClick = { submitted = false })
        BigButton("Next: improve", onClick = onNext)
    } else {
        BigButton("Check my answer", onClick = {
            progress.add(module.id, Evidence.WrittenSubmitted)
            justChecked = true
            submitted = true
        })
    }
}

@Composable
private fun ImproveStage(
    module: Module,
    progress: ProgressStore,
    store: LocalStore,
    ai: ParhlouAi,
    onAskTutor: () -> Unit,
    onRetryQuiz: () -> Unit,
) {
    val evidence = progress.evidence(module.id)
    val quiz = evidence.filterIsInstance<Evidence.QuizCompleted>()
    Sheet {
        Text("Where you are", style = MaterialTheme.typography.titleMedium)
        Text(progress.state(module.id).label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        if (quiz.isEmpty()) Muted("Do a practice to see what to work on.")
        else Muted("Best first-try practice score: ${quiz.maxOf { it.firstTryPercent }}%")
        Muted("A module only counts as verified after a properly marked chapter test, not from practice alone.")
    }
    if (Evidence.WrittenSubmitted in evidence) {
        module.writtenQuestions.forEach { q ->
            val saved by store.draft(writtenDraftKey(module, q)).collectAsState(initial = null)
            val answer = saved.orEmpty()
            val mark by produceState<WrittenMark?>(null, q.id, answer) { value = ai.markWritten(q, answer, 0) }
            val m = mark
            if (m != null) {
                val missing = m.criteria.filter { it.status == CriterionStatus.NOT_FOUND }
                Sheet {
                    Text("Written practice: ${fmt(m.awarded)} / ${fmt(q.marks)}", style = MaterialTheme.typography.titleMedium)
                    when {
                        m.status == MarkStatus.COULD_NOT_CHECK || m.status == MarkStatus.NOT_ATTEMPTED ->
                            Muted("Go back to Write answers, compare with the model answer and write it out clearly.")
                        missing.isEmpty() -> Muted("Every marking point was found. Well done.")
                        else -> missing.forEach { c ->
                            Text("What was missing: ${c.description}", style = MaterialTheme.typography.bodyLarge)
                            Muted("Why it matters: ${c.why}")
                            Muted("How to fix it: ${c.how}")
                        }
                    }
                    Text("Try next: ${q.followUp}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        Muted("Write a board answer (Write answers) to get it checked.")
    }
    BigButton("Practise again", onClick = onRetryQuiz)
    SecondaryButton("Ask Ustaad about this topic", onClick = onAskTutor)
}

/** Shared MCQ runner for module quizzes and Test Series readiness checks. */
@Composable
fun QuizContent(
    questions: List<McqQuestion>,
    passPercent: Int?,
    onFinished: (QuizSummary) -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Next: book",
    onAskTutor: ((McqQuestion, Int) -> Unit)? = null,
    /** Called for every answer: the question, whether it was right, and which attempt it was (1 = first try). */
    onAnswered: (McqQuestion, Boolean, Int) -> Unit = { _, _, _ -> },
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

    AnimatedContent(targetState = index, transitionSpec = rememberStepTransition(), label = "question") { shownIndex ->
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val q = order[shownIndex]
    Muted("Question ${shownIndex + 1} of ${order.size}" + (passPercent?.let { " · pass mark $it%" } ?: ""))
    Sheet { Text(q.stem, style = MaterialTheme.typography.titleLarge) }
    Crossfade(targetState = chosen, animationSpec = tween(if (LocalReducedMotion.current) 0 else 250), label = "reveal") { picked ->
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    q.options.forEachIndexed { i, option ->
        if (picked == null) {
            ChoiceCard(
                title = option,
                selected = false,
                onClick = {
                    chosen = i
                    history = QuizScorer.record(history, q.id, i, q.sampleCorrectIndex)
                    history.last().let { a -> onAnswered(q, a.correct, a.attemptNo) }
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
    }
    }
    val c = chosen
    if (c != null) {
        Text(if (c == q.sampleCorrectIndex) "Correct." else "Not quite. The right answer is highlighted.",
            style = MaterialTheme.typography.titleMedium,
            color = if (c == q.sampleCorrectIndex) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        if (c != q.sampleCorrectIndex && onAskTutor != null) {
            SecondaryButton("Ask Ustaad why", onClick = { onAskTutor(q, c) })
        }
        BigButton(if (index == order.lastIndex) "See result" else "Next question", onClick = {
            if (index == order.lastIndex) {
                val s = QuizScorer.summarise(order.map { it.id }, history)
                summary = s
                onFinished(s)
            } else index++
        })
    }
    }
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
