package pk.parhlou.student.ui.screens

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
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
    val modules = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.map { m -> Triple(s, c, m) } } }
    if (modules.isEmpty()) return NextTask.Nothing
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

    ParhlouScreen(title = null) {
        UpdateBanner()
        // Greeting row
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Filled.Person, size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (onboarding.name.isBlank()) "Hello!" else "Hello, ${onboarding.name}!", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(onClick = { onPlaceholder("profile") }, shape = CircleShape,
                color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text(
            buildAnnotatedString {
                append("Ready to\n")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("study ") }
                append("today?")
            },
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.appear(1),
        )

        // Quick actions: tutor, book, scanner
        Row(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onTutor, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Ask a question", style = MaterialTheme.typography.labelLarge) }
            }
            QuickIcon(Icons.AutoMirrored.Filled.MenuBook, "Open my book", onSubjects)
            QuickIcon(Icons.Filled.PhotoCamera, "Scan answer sheets", { onPlaceholder("scanner") })
        }

        // Hero: the one thing to do now, with assessed progress
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.appear(3).fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Do this now", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium)
                        Text(task.title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                        Text(task.reason, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    ProgressRing(
                        fraction = if (moduleIds.isEmpty()) 0f else assessed / moduleIds.size.toFloat(),
                        label = "$assessed/${moduleIds.size}",
                        description = "$assessed of ${moduleIds.size} modules assessed",
                        track = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        progress = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Button(
                    onClick = go,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Continue", style = MaterialTheme.typography.labelLarge) }
            }
        }

        Box(Modifier.appear(4)) {
            WeekStrip(onboarding.studyDaysPerWeek, progress.history(), tasksToday = plan.size, onPlan = onPlanner, onHistory = onHistory)
        }

        Row(Modifier.appear(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("My book", Icons.AutoMirrored.Filled.MenuBook, onSubjects, Modifier.weight(1f))
            Tile("Papers", Icons.Filled.Quiz, onTests, Modifier.weight(1f))
            Tile("Journey", Icons.Filled.RocketLaunch, onJourney, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's tasks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onPlanner) { Text("Full plan") }
        }
        if (plan.isEmpty()) {
            TaskRow(Icons.Filled.PlayCircle, "Lesson", "${onboarding.minutesPerDay} min planned today", go)
            TaskRow(Icons.AutoMirrored.Filled.MenuBook, "Read from your book", "Book references for each module", onSubjects)
        } else plan.filterNot { it.target == (task as? NextTask.OpenModule)?.moduleId }.take(3).forEach { t ->
            TaskRow(t.kind.icon(), t.title, t.reason, onClick = { onPlanTask(t) })
        }

        subjects.forEach { s ->
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.name} · Class ${s.grade}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SampleBadge()
                }
                val ids = s.chapters.flatMap { c -> c.modules.map { it.id } }
                Muted("${progress.assessedCount(ids)} verified · ${progress.demoCheckedCount(ids)} demo-checked · ${ids.size} modules available")
                if (!s.editionVerified) Muted("Book edition not yet verified: ${s.editionLabel}")
            }
        }
        Spacer(Modifier.height(8.dp))
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
) {
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
            Muted("$studyDays study days planned")
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, start ->
                val isToday = i == todayIndex
                val isSelected = i == selected
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .selectable(selected = isSelected, role = Role.Tab) { selected = i }
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
    accountMessage: String? = null,
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your plan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("One monthly plan: PKR 3,999. Not connected in this build; access will come only from a verified Google Play subscription.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
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
        TaskRow(Icons.Filled.History, "Practice history", "Your quizzes and papers", onHistory)
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
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
            Text("Demo-checked: $demoChecked of ${ids.size} modules", style = MaterialTheme.typography.bodyLarge)
            Muted("Your rocket moves only on verified marks from the AI examiner. Demo-checked papers are good practice, but they don't move it yet. Watching or reading alone never counts.")
        }
    }
}

@Composable
fun SubjectsScreen(repo: ParhlouRepository, onboarding: OnboardingState, onOpen: (String) -> Unit, onBack: (() -> Unit)?) {
    ParhlouScreen("My book", onBack = onBack) {
        val subjects = onboarding.subjectIds.mapNotNull { repo.subject(it) }
        if (subjects.isEmpty()) Muted("You haven't chosen any available subjects.")
        subjects.forEach { s ->
            Card(onClick = { onOpen(s.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${s.name} · Class ${s.grade}", style = MaterialTheme.typography.titleMedium)
                    Muted("${s.chapters.size} units · ${s.editionLabel}")
                }
            }
        }
    }
}

@Composable
fun SubjectScreen(subject: Subject, progress: ProgressStore, onOpenModule: (String) -> Unit, onBack: () -> Unit) {
    // Current chapter = first one with modules not yet assessed; it starts expanded (PRD §6).
    val current = subject.chapters.firstOrNull { c ->
        c.modules.any { progress.state(it.id) !in setOf(LearningState.ASSESSED, LearningState.MASTERED) }
    } ?: subject.chapters.first()
    var expanded by remember { mutableStateOf(setOf(current.id)) }

    ParhlouScreen("${subject.name} ${subject.grade}", onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(subject.editionLabel)
        }
        subject.chapters.forEach { c ->
            val open = c.id in expanded
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Unit ${c.number}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
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
                    c.modules.forEach { m ->
                        HorizontalDivider()
                        Box(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onOpenModule(m.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${m.position}. ${m.title}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                StatusPill(progress.state(m.id).label, warning = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
