package pk.parhlou.student.ui.screens

import pk.parhlou.student.data.word

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import pk.parhlou.student.ui.components.CircleIcon
import pk.parhlou.student.ui.components.appear
import pk.parhlou.student.ui.components.ProgressRing
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.TaskRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.data.Subject
import pk.parhlou.student.domain.LearningState
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.UpdateBanner
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SampleBadge
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill
import pk.parhlou.student.ui.components.Tile

/** What the Continue button should open, decided from evidence (PRD §5, §10). */
sealed interface NextTask {
    val title: String
    val reason: String
    data class OpenModule(val moduleId: String, override val title: String, override val reason: String) : NextTask
    data class OpenTests(override val title: String, override val reason: String) : NextTask
    data object Nothing : NextTask {
        override val title = "Choose a subject"
        override val reason = "No published lessons are available for your subjects yet."
    }
}

fun nextTask(subjects: List<Subject>, pathway: Pathway?, progress: ProgressStore): NextTask {
    val all = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.map { m -> Triple(s, c, m) } } }
    if (all.isEmpty()) return NextTask.Nothing
    // Sections the exam leaves out stay available, but the next step always points at examined content.
    val modules = all.filter { it.third.inExam }.ifEmpty { all }
    if (pathway == Pathway.TEST_SERIES) {
        return NextTask.OpenTests("Take your readiness check", "Test Series: a short quiz unlocks your next paper.")
    }
    val repair = modules.firstOrNull { progress.state(it.third.id) == LearningState.REPAIR_REQUIRED }
    if (repair != null) {
        return NextTask.OpenModule(repair.third.id, "Fix: ${repair.third.title}", "Your last test showed a weak area here.")
    }
    val open = modules.firstOrNull {
        progress.state(it.third.id) !in setOf(
            LearningState.ASSESSED, LearningState.MASTERED, LearningState.READY_FOR_ASSESSMENT, LearningState.DEMO_CHECKED,
        )
    }
    return if (open != null) {
        val started = progress.state(open.third.id) != LearningState.NOT_STARTED
        NextTask.OpenModule(
            open.third.id,
            "${if (started) "Continue" else "Start"}: ${open.third.title}",
            "${open.first.name} · Unit ${open.second.number} ${open.second.title}",
        )
    } else {
        NextTask.OpenTests("Take your chapter test", "You've practised every available module.")
    }
}

@Composable
fun HomeScreen(
    repo: ParhlouRepository,
    onboarding: OnboardingState,
    progress: ProgressStore,
    onOpenModule: (String) -> Unit,
    onSubjects: () -> Unit,
    onTests: () -> Unit,
    onJourney: () -> Unit,
    onTutor: () -> Unit,
    onPlaceholder: (String) -> Unit,
    /** Today's plan from [pk.parhlou.student.domain.StudyPlanner]; empty shows the basic tasks. */
    plan: List<pk.parhlou.student.domain.PlanTask> = emptyList(),
    onPlanTask: (pk.parhlou.student.domain.PlanTask) -> Unit = {},
    onPlanner: () -> Unit = {},
    onHistory: () -> Unit = {},
    onScan: () -> Unit = {},
    /** Step-by-step solutions of every textbook question. */
    onSolutions: () -> Unit = onSubjects,
    /** Opens a guided session: a section id (null = today's pick) and its length in minutes. */
    onStartSession: (String?, Int) -> Unit = { _, _ -> },
    onKit: (String) -> Unit = {},
    onProgress: () -> Unit = {},
) {
    val subjects = onboarding.subjectIds.mapNotNull { repo.subject(it) }
    val task = nextTask(subjects, onboarding.pathway, progress)
    val moduleIds = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.map { it.id } } }
    val assessed = progress.assessedCount(moduleIds)
    val go = {
        when (task) {
            is NextTask.OpenModule -> onOpenModule(task.moduleId)
            is NextTask.OpenTests -> onTests()
            NextTask.Nothing -> onSubjects()
        }
    }

    val rewards = pk.parhlou.student.domain.Rewards.summary(progress.history(), onboarding.minutesPerDay, System.currentTimeMillis())
    val yesterday = pk.parhlou.student.domain.Rewards.summary(progress.history(), onboarding.minutesPerDay, System.currentTimeMillis() - 86_400_000L)
    val today = todaysModule(subjects, progress)
    Box(Modifier.fillMaxSize()) {
    ParhlouScreen(title = null) {
        UpdateBanner()
        // Ustaad greets the student; his mood follows the streak.
        Row(Modifier.appear(0).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            pk.parhlou.student.ui.components.Ustad(56.dp, pk.parhlou.student.ui.components.moodFor(rewards))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (onboarding.name.isBlank()) "Assalam-o-Alaikum!" else "Assalam-o-Alaikum, ${onboarding.firstName}!", style = MaterialTheme.typography.titleLarge)
                Text(pk.parhlou.student.ui.components.moodLine(rewards), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Ask Ustaad: one big tap, near the top, never covering anything.
        pk.parhlou.student.ui.components.Chunky(
            modifier = Modifier.fillMaxWidth().testTag("home-ask"), colors = pk.parhlou.student.ui.components.ChunkyDefaults.primary(),
            shape = RoundedCornerShape(22.dp), onClick = onTutor, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Ask Ustaad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("📷 💡 📄", style = MaterialTheme.typography.titleMedium)
            }
        }
        // Goals: streak, today's XP against the goal, level; and how yesterday's goal went.
        RewardBar(rewards, Modifier.appear(1), onClick = onProgress)
        if (yesterday.xpToday > 0 || yesterday.goalMet) Text(
            if (yesterday.goalMet) "✅ Yesterday's goal done (${yesterday.xpToday} XP)" else "Yesterday: ${yesterday.xpToday} of ${yesterday.dailyGoalXp} XP",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Today's session.
        if (today != null) Sheet(Modifier.appear(2)) {
            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(today.title.replace(Regex("^\\d+(\\.\\d+)*\\s*"), ""), style = MaterialTheme.typography.titleMedium)
            BigButton("Start today's session", onClick = { onStartSession(null, onboarding.minutesPerDay) }, modifier = Modifier.testTag("start-session"))
            TextButton(onClick = { onStartSession(null, 5) }, modifier = Modifier.heightIn(min = 44.dp).testTag("busy-day")) { Text("Busy day? 5 minutes") }
        }

        // Your journey: one card per subject (book).
        Text("Your journey", style = MaterialTheme.typography.titleLarge)
        // "Do this now" is the plan's first task (a topic to fix comes first), else the next lesson.
        val first = plan.firstOrNull()?.takeIf { onboarding.pathway != Pathway.TEST_SERIES }
        val now: Pair<String, () -> Unit> = if (first != null) first.title to { onPlanTask(first) } else task.title to go
        subjects.forEach { s -> SubjectJourneyCard(s, progress, now.first, now.second, onSubjects) }

        Box(Modifier.appear(4)) {
            val titles = remember(repo, subjects) {
                val papers = subjects.flatMap { repo.blueprints(it.id) }.associate { it.id to it.title };
                { key: String -> repo.module(key)?.title ?: papers[key.removePrefix(pk.parhlou.student.data.ProgressStore.PAPER_PREFIX)] ?: "practice" }
            }
            WeekStrip(onboarding.studyDaysPerWeek, progress.history(), tasksToday = plan.size, onPlan = onPlanner, onHistory = onHistory,
                entries = studyEntries(progress.history(), titles))
        }
        TextButton(onClick = onPlanner, modifier = Modifier.heightIn(min = 44.dp)) { Text("Full plan") }
    }
    }
}

/**
 * One subject on Home: how much of its path is done, the marks the student is heading for, the weakest and strongest
 * topic, and the next thing to do. Tapping the card opens the book.
 */
@Composable
private fun SubjectJourneyCard(s: Subject, progress: ProgressStore, taskTitle: String, go: () -> Unit, onOpen: () -> Unit) {
    val modules = s.chapters.flatMap { c -> c.modules.filter { it.inExam }.map { c to it } }
    val levels = modules.map { (_, m) -> pk.parhlou.student.domain.Rewards.sectionLevel(progress.evidence(m.id)) }
    val covered = if (modules.isEmpty()) 0 else levels.count { it >= 2 } * 100 / modules.size
    // Expected marks: from paper results when there are any, else from how well each section is known.
    val papers = progress.history().filter { it.key.startsWith(pk.parhlou.student.data.ProgressStore.PAPER_PREFIX) }
        .mapNotNull { (it.evidence as? pk.parhlou.student.domain.Evidence.DemoCheckedExam)?.percent ?: (it.evidence as? pk.parhlou.student.domain.Evidence.ExamMarked)?.percent }
    val pct = if (papers.isNotEmpty()) papers.takeLast(3).average() else if (levels.isEmpty()) 0.0 else levels.average() / 5 * 100
    val started = papers.isNotEmpty() || levels.any { it > 0 }
    val ranked = modules.zip(levels).filter { it.second > 0 || papers.isNotEmpty() }
    val weak = modules.zip(levels).minByOrNull { it.second }?.first?.second?.title?.replace(Regex("^\\d+(\\.\\d+)*\\s*"), "")
    val strong = ranked.maxByOrNull { it.second }?.takeIf { it.second >= 2 }?.first?.second?.title?.replace(Regex("^\\d+(\\.\\d+)*\\s*"), "")
    val examIn = pk.parhlou.student.domain.ExamCountdown.daysLeft(s.examDate)
    pk.parhlou.student.ui.components.Chunky(modifier = Modifier.fillMaxWidth().testTag("journey-${s.id}"), shape = RoundedCornerShape(22.dp), onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.name} · Class ${s.grade}", style = MaterialTheme.typography.titleMedium)
                    if (examIn != null) Text("📅 $examIn days to the exam", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("home-countdown"))
                }
                ProgressRing(covered / 100f, "$covered%", "$covered% of the path done", MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, size = 64.dp)
            }
            // Facts appear once there is something real to say; a new student sees one plain line instead of dashes.
            if (started || strong != null || weak != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val outOf = pk.parhlou.student.domain.PaperAssembler.patternFor(s.id).totalMarks.toInt()
                if (started) JourneyFact("🎯", "${(pct * outOf / 100).toInt().coerceIn(0, outOf)}/$outOf", "expected", Modifier.weight(1f))
                if (strong != null) JourneyFact("💪", strong, "strong", Modifier.weight(1f))
                if (weak != null) JourneyFact("⚠️", weak, "work on", Modifier.weight(1f))
            } else Muted("Do a few lessons and quizzes, and your expected marks and strong and weak topics show here.")
            Text("Do this now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(taskTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            pk.parhlou.student.ui.components.AccentButton("Continue", onClick = go)
        }
    }
}

@Composable
private fun JourneyFact(icon: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One of the three equal quick actions on Home: a chunky tile with an icon over its label. */
@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String?, onClick: () -> Unit, modifier: Modifier) {
    pk.parhlou.student.ui.components.Chunky(
        modifier = modifier.then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        shape = RoundedCornerShape(18.dp), onClick = onClick, contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
private fun QuickIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** This week's days with today highlighted. A calendar only: it shows no invented sessions. */
@Composable
private fun WeekStrip(
    studyDays: Int,
    history: List<pk.parhlou.student.domain.LoggedEvidence>,
    tasksToday: Int,
    onPlan: () -> Unit,
    onHistory: () -> Unit,
    entries: List<StudyEntry> = emptyList(),
) {
    var calendarDay by remember { mutableStateOf<Long?>(null) }
    calendarDay?.let { StudyCalendarSheet(entries, it, onDismiss = { calendarDay = null }) }
    // Seven days starting on the week's first day; tap a day to see what was done (or what's planned).
    val days = remember {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        List(7) { cal.timeInMillis.also { cal.add(java.util.Calendar.DAY_OF_MONTH, 1) } }
    }
    val now = System.currentTimeMillis()
    val todayIndex = days.indexOfLast { it <= now }.coerceAtLeast(0)
    var selected by rememberSaveable { mutableIntStateOf(todayIndex) }
    val dayEnd = { i: Int -> if (i < 6) days[i + 1] else days[6] + 24 * 60 * 60 * 1000L }
    val summaries = remember(history.size, days) { days.indices.map { pk.parhlou.student.domain.DaySummary.of(history, days[it], dayEnd(it)) } }
    val dayName = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
    val longName = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault())
    val dayNumber = java.text.SimpleDateFormat("d", java.util.Locale.getDefault())
    Sheet {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { calendarDay = System.currentTimeMillis() }, modifier = Modifier.heightIn(min = 44.dp).testTag("open-calendar")) { Text("📅 Calendar") }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, start ->
                val isToday = i == todayIndex
                val isSelected = i == selected
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .selectable(selected = isSelected, role = Role.Tab) { selected = i; calendarDay = start }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .semantics { contentDescription = longName.format(java.util.Date(start)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(dayName.format(java.util.Date(start)).take(2), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(dayNumber.format(java.util.Date(start)), style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    }
                    // A dot under days with recorded practice.
                    Box(Modifier.size(6.dp).clip(CircleShape).background(
                        if (summaries[i].empty) Color.Transparent else MaterialTheme.colorScheme.secondary))
                }
            }
        }
        val title = if (selected == todayIndex) "Today" else longName.format(java.util.Date(days[selected]))
        val done = summaries[selected]
        Text(title, style = MaterialTheme.typography.titleSmall)
        when {
            selected == todayIndex -> {
                Muted(
                    (if (done.empty) "Nothing practised yet today. " else "Done so far: ${done.describe()}. ") +
                        if (tasksToday > 0) "$tasksToday ${if (tasksToday == 1) "task is" else "tasks are"} in today's plan." else "",
                )
                TextButton(onClick = onPlan, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open today's plan") }
            }
            selected < todayIndex -> {
                Muted(if (done.empty) "No practice recorded on this day." else "You did ${done.describe()}.")
                if (!done.empty) TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("See practice history") }
            }
            else -> Muted("Coming up. Your plan for this day is made on the day, from what you practise and miss before then.")
        }
    }
}

@Composable
fun ProfileScreen(
    repo: ParhlouRepository,
    onboarding: OnboardingState,
    progress: ProgressStore,
    onPlaceholder: (String) -> Unit,
    onClearDevice: () -> Unit,
    /** Signed-in email, or null when signed out. */
    email: String? = null,
    accountsAvailable: Boolean = false,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onExport: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onHistory: () -> Unit = { onPlaceholder("history") },
    versionLabel: String = "",
    onWhatsNew: () -> Unit = {},
    onMyUstaad: () -> Unit = {},
    /** Current reminder setting, e.g. "Every day at 6 pm" or "Off"; tapping cycles through the choices. */
    reminderLabel: String = "Every day at 6 pm",
    onReminder: () -> Unit = {},
    accountMessage: String? = null,
    soundOn: Boolean = true,
    onToggleSound: () -> Unit = {},
    /** Loads what's left of this month's AI allowance; null result = not available (offline or no server). */
    allowance: suspend () -> pk.parhlou.student.ai.Allowance? = { null },
    onSendFeedback: suspend (String) -> Boolean = { false },
    onEditProfile: () -> Unit = {},
) {
    val subjects = onboarding.subjectIds.mapNotNull { repo.subject(it) }
    val ids = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.map { it.id } } }
    var confirmClear by remember { mutableStateOf(false) }
    ParhlouScreen("Profile") {
        UpdateBanner()
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.Filled.Person, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(onboarding.name.ifBlank { "Learner" }, style = MaterialTheme.typography.titleLarge)
                    Muted(listOfNotNull(
                        if (onboarding.boardCode == "PB") "Punjab" else onboarding.boardCode,
                        onboarding.grade?.let { "Class $it" },
                        onboarding.pathway?.label,
                    ).joinToString(" · "))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Quizzes done", progress.quizzesCompleted().toString(), Modifier.weight(1f))
            Stat("Modules assessed", "${progress.assessedCount(ids)}", Modifier.weight(1f))
            Stat("Papers handed in", progress.papersSubmitted.toString(), Modifier.weight(1f))
        }
        if (accountsAvailable) {
            Sheet {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                if (email == null) {
                    Muted("Not signed in. Your progress is only on this phone.")
                    BigButton("Sign in to save progress", onClick = onSignIn)
                } else {
                    Text(email, style = MaterialTheme.typography.bodyLarge)
                    Muted("Progress and settings are saved to your account.")
                    SecondaryButton("Download my data", onClick = onExport)
                    SecondaryButton("Sign out", onClick = onSignOut)
                }
                accountMessage?.let { Muted(it) }
            }
        }
        TaskRow(Icons.Filled.Person, "Edit profile", "Name, email, phone, board, subjects", onEditProfile)
        TaskRow(Icons.Filled.History, "Practice history", "Your quizzes and papers", onHistory)
        TaskRow(Icons.Filled.SmartToy, "My Ustaad", "What Ustaad knows about you and how it explains", onMyUstaad)
        TaskRow(Icons.Filled.NewReleases, "What's new", "Everything this update added", onWhatsNew)
        // This month's Ustaad time, as things the student can still do.
        val left by androidx.compose.runtime.produceState<pk.parhlou.student.ai.Allowance?>(null) { value = allowance() }
        left?.let { a ->
            Sheet(Modifier.testTag("allowance")) {
                Text("This month with Ustaad", style = MaterialTheme.typography.titleMedium)
                a.lines().forEach { (icon, line) -> Text("$icon  $line", style = MaterialTheme.typography.bodyLarge) }
                Muted("More on ${a.resetsOn}. Unused time carries over for up to 4 months.")
            }
        }
        var feedbackOpen by remember { mutableStateOf(false) }
        TaskRow(Icons.Filled.Feedback, "Send feedback", "Tell us what to fix or add", { feedbackOpen = !feedbackOpen })
        if (feedbackOpen) FeedbackBox(onSend = onSendFeedback, onClose = { feedbackOpen = false })
        TaskRow(Icons.Filled.Notifications, "Daily reminder", reminderLabel, onReminder)
        TaskRow(if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, "Sounds",
            if (soundOn) "On: taps, right and wrong, rewards" else "Off (vibration only)", onToggleSound)
        TaskRow(Icons.Filled.FamilyRestroom, "Add a parent", "Coming later", { onPlaceholder("guardian") })
        var confirmDelete by remember { mutableStateOf(false) }
        if (email != null) {
            if (!confirmDelete) SecondaryButton("Delete my account", onClick = { confirmDelete = true })
            else Sheet {
                Text("Delete your account and all your data?", style = MaterialTheme.typography.titleMedium)
                Muted("Your answers, marks and progress are removed from the server and this phone. This can't be undone.")
                BigButton("Yes, delete everything", onClick = { confirmDelete = false; onDeleteAccount() })
                SecondaryButton("Cancel", onClick = { confirmDelete = false })
            }
        }
        if (!confirmClear) {
            SecondaryButton("Clear this phone", onClick = { confirmClear = true })
        } else {
            Sheet {
                Text("Remove your setup, drafts and timers from this phone?", style = MaterialTheme.typography.titleMedium)
                Muted("Use this on a shared phone. It can't be undone.")
                BigButton("Yes, clear this phone", onClick = onClearDevice)
                SecondaryButton("Cancel", onClick = { confirmClear = false })
            }
        }
        if (versionLabel.isNotBlank()) Muted(versionLabel)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    pk.parhlou.student.ui.components.TapCard(modifier = modifier, shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun JourneyScreen(repo: ParhlouRepository, onboarding: OnboardingState, progress: ProgressStore, onBack: () -> Unit) {
    val ids = onboarding.subjectIds.mapNotNull { repo.subject(it) }
        .flatMap { s -> s.chapters.flatMap { c -> c.modules.map { it.id } } }
    val assessed = progress.assessedCount(ids)
    val demoChecked = progress.demoCheckedCount(ids)
    ParhlouScreen("Journey", onBack = onBack) {
        Sheet {
            Icon(Icons.Filled.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(48.dp))
            Text("Next milestone", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { if (ids.isEmpty()) 0f else assessed / ids.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp)
                    .semantics { contentDescription = "$assessed of ${ids.size} modules verified" },
            )
            Text("Verified: $assessed of ${ids.size} modules", style = MaterialTheme.typography.bodyLarge)
            Text("Practised: $demoChecked of ${ids.size} modules", style = MaterialTheme.typography.bodyLarge)
            Muted("Your rocket moves on marked papers. Watching or reading alone doesn't count.")
        }
    }
}

@Composable
fun SubjectsScreen(
    repo: ParhlouRepository, onboarding: OnboardingState, onOpen: (String) -> Unit, onBack: (() -> Unit)?,
    onSolutions: () -> Unit = {},
    /** Adds a subject the board offers that the student hasn't chosen yet. */
    onAddSubject: ((String) -> Unit)? = null,
    /** Opens Edit profile, where subjects are added and removed. */
    onEditSubjects: (() -> Unit)? = null,
) {
    ParhlouScreen("Your books", onBack = onBack) {
        val subjects = onboarding.subjectIds.mapNotNull { repo.subject(it) }
        if (subjects.isEmpty()) Muted("You haven't chosen any available subjects.")
        // Solutions live inside each book (its "Solutions" tab), so the list shows only the books.
        subjects.forEach { s ->
            pk.parhlou.student.ui.components.TapCard(onClick = { onOpen(s.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${s.name} · Class ${s.grade}", style = MaterialTheme.typography.titleMedium)
                    Muted("${s.chapters.size} ${s.chapters.firstOrNull()?.word?.lowercase()?.let { "${it}s" } ?: "units"} · ${s.editionLabel}")
                }
            }
        }
        val board = onboarding.boardCode
        val grade = onboarding.grade
        val more = if (onAddSubject != null && board != null && grade != null)
            repo.subjectOptions(board, grade).filter { it.available && it.id !in onboarding.subjectIds } else emptyList()
        if (more.isNotEmpty()) Sheet(Modifier.testTag("add-subject")) {
            Text("Add a subject", style = MaterialTheme.typography.titleMedium)
            more.forEach { o -> SecondaryButton("+ ${o.name}", onClick = { onAddSubject?.invoke(o.id) }, modifier = Modifier.testTag("add-${o.id}")) }
        }
        if (onEditSubjects != null) androidx.compose.material3.TextButton(onClick = onEditSubjects,
            modifier = Modifier.heightIn(min = 48.dp).testTag("edit-subjects")) { Text("Add or remove subjects") }
    }
}

@Composable
fun SubjectScreen(subject: Subject, progress: ProgressStore, onOpenModule: (String) -> Unit, onBack: () -> Unit, onOpenKit: (String) -> Unit = {},
                  onStartSession: (String) -> Unit = onOpenModule, onSolutions: () -> Unit = {}, onOpenPractical: (String) -> Unit = {}) {
    // Current chapter = first one with modules not yet assessed; it starts expanded (PRD §6).
    val current = subject.chapters.firstOrNull { c ->
        c.modules.any { progress.state(it.id) !in setOf(LearningState.ASSESSED, LearningState.MASTERED) }
    } ?: subject.chapters.first()
    var expanded by remember { mutableStateOf(setOf(current.id)) }

    // A book has one place to learn: its path. "Units" lists every section; "Solutions" finds any textbook question.
    var view by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    ParhlouScreen("${subject.name} ${subject.grade}", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (listOf("Path", "Units") + if (subject.practicals.isNotEmpty()) listOf("Practicals") else emptyList()).forEachIndexed { i, t ->
                ChoiceChip(t, view == i, Modifier.weight(1f)) { view = i }
            }
            ChoiceChip("Solutions", false, Modifier.weight(1f), onSolutions)
        }
        if (view == 2 && subject.practicals.isNotEmpty()) {
            PracticalList(subject, onOpenPractical)
            return@ParhlouScreen
        }
        if (view == 0) {
            LearningPath(listOf(subject), progress, todaysModule(listOf(subject), progress)?.id, onStart = onStartSession, onKit = onOpenKit, showAllDefault = true)
            return@ParhlouScreen
        }
        subject.chapters.forEach { c ->
            val open = c.id in expanded
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.word} ${c.number}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        if (c.kit?.inExam == false) StatusPill("Not in the ${c.kit.examLabel.ifBlank { "exam" }}", warning = true)
                    }
                    IconButton(onClick = { expanded = if (open) expanded - c.id else expanded + c.id }) {
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (open) "Collapse unit ${c.number}" else "Expand unit ${c.number}")
                    }
                }
                if (open) {
                    if (c.modules.isEmpty()) {
                        Muted("Not published yet. This unit has no reviewed lessons.")
                    }
                    if (c.kit != null) TextButton(onClick = { onOpenKit(c.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Exam kit: definitions, formulas, important questions", modifier = Modifier.weight(1f))
                    }
                    c.modules.forEach { m ->
                        HorizontalDivider()
                        Box(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onOpenModule(m.id) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.position}. ${m.title}", style = MaterialTheme.typography.bodyLarge)
                                    if (!m.inExam && c.kit?.inExam != false) Muted("Not in the ${c.kit?.examLabel?.ifBlank { null } ?: "exam"} · practice")
                                }
                                StatusPill(progress.state(m.id).label, warning = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A segment of a two- or three-way switch: teal when chosen. */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brand = pk.parhlou.student.ui.theme.LocalBrand.current
    pk.parhlou.student.ui.components.Chunky(
        modifier = modifier,
        colors = if (selected) pk.parhlou.student.ui.components.ChunkyColors(MaterialTheme.colorScheme.primaryContainer, brand.lip, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary)
        else pk.parhlou.student.ui.components.ChunkyDefaults.plain(),
        shape = RoundedCornerShape(14.dp), lip = 3.dp, onClick = onClick, contentPadding = PaddingValues(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
}

/** Feedback goes straight to the owner's inbox. It opens in place, under the button. */
@Composable
private fun FeedbackBox(onSend: suspend (String) -> Boolean, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(0) } // 0 writing, 1 sending, 2 sent, 3 failed
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Sheet(Modifier.testTag("feedback")) {
        if (state == 2) {
            Text("Thank you! We read every message.", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
        } else {
            androidx.compose.material3.OutlinedTextField(text, { text = it.take(3000) }, placeholder = { Text("What should we fix or add?") },
                minLines = 3, modifier = Modifier.fillMaxWidth().testTag("feedback-text"))
            if (state == 3) Text("Couldn't send. Check your internet and try again.", color = MaterialTheme.colorScheme.error)
            BigButton(if (state == 1) "Sending…" else "Send", enabled = text.isNotBlank() && state != 1, modifier = Modifier.testTag("feedback-send"), onClick = {
                state = 1
                scope.launch { state = if (onSend(text.trim())) 2 else 3 }
            })
        }
    }
}

/** A science book's practicals, chapter by chapter. */
@Composable
private fun PracticalList(subject: Subject, onOpen: (String) -> Unit) {
    subject.practicals.groupBy { it.unit }.toSortedMap().forEach { (unit, list) ->
        val chapter = subject.chapters.firstOrNull { it.number == unit }
        Text("${chapter?.word ?: "Chapter"} $unit${chapter?.let { " · ${it.title}" } ?: ""}", style = MaterialTheme.typography.titleMedium)
        list.forEach { p ->
            pk.parhlou.student.ui.components.TapCard(onClick = { onOpen(p.id) }, modifier = Modifier.fillMaxWidth().testTag("practical-${p.id}"), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("🧪 ${p.title}", style = MaterialTheme.typography.bodyLarge)
                    Muted(p.label + if (!p.inExam) " · practice (not in this year's exam)" else "")
                }
            }
        }
    }
}
