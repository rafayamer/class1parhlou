package pk.parhlou.student.ui.screens

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
import androidx.compose.material.icons.filled.MenuBook
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
        progress.state(it.third.id) !in setOf(LearningState.ASSESSED, LearningState.MASTERED, LearningState.READY_FOR_ASSESSMENT)
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
    onPlaceholder: (String) -> Unit,
) {
    val subjects = onboarding.subjectIds.mapNotNull { repo.subject(it) }
    val task = nextTask(subjects, onboarding.pathway, progress)

    ParhlouScreen(title = "Home") {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Do this now", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(task.title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                Text(task.reason, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        BigButton("Continue", onClick = {
            when (task) {
                is NextTask.OpenModule -> onOpenModule(task.moduleId)
                is NextTask.OpenTests -> onTests()
                NextTask.Nothing -> onSubjects()
            }
        })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Subjects", Icons.Filled.MenuBook, onSubjects, Modifier.weight(1f))
            Tile("Tests", Icons.Filled.Quiz, onTests, Modifier.weight(1f))
            Tile("Journey", Icons.Filled.RocketLaunch, onJourney, Modifier.weight(1f))
        }

        Sheet {
            Text("Today's plan", style = MaterialTheme.typography.titleMedium)
            Text("${onboarding.minutesPerDay} minutes · ${task.title}", style = MaterialTheme.typography.bodyLarge)
            Muted("Reviews will appear here once you have test results to revise from.")
        }

        subjects.forEach { s ->
            val moduleIds = s.chapters.flatMap { c -> c.modules.map { it.id } }
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.name} · Class ${s.grade}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SampleBadge()
                }
                Muted("${progress.assessedCount(moduleIds)} of ${moduleIds.size} available modules assessed")
                if (!s.editionVerified) Muted("Book edition not yet verified: ${s.editionLabel}")
            }
        }

        Sheet {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Muted("Plan: not connected in this build. Paid features will need a verified Google Play subscription.")
            Row {
                TextButton(onClick = { onPlaceholder("guardian") }) {
                    Icon(Icons.Filled.FamilyRestroom, contentDescription = null); Text("  Add a parent")
                }
                TextButton(onClick = { onPlaceholder("profile") }) {
                    Icon(Icons.Filled.Person, contentDescription = null); Text("  Profile & privacy")
                }
            }
        }
    }
}

@Composable
fun JourneyScreen(repo: ParhlouRepository, onboarding: OnboardingState, progress: ProgressStore, onBack: () -> Unit) {
    val ids = onboarding.subjectIds.mapNotNull { repo.subject(it) }
        .flatMap { s -> s.chapters.flatMap { c -> c.modules.map { it.id } } }
    val assessed = progress.assessedCount(ids)
    ParhlouScreen("Journey", onBack = onBack) {
        Sheet {
            Icon(Icons.Filled.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp))
            Text("Next milestone", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { if (ids.isEmpty()) 0f else assessed / ids.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp)
                    .semantics { contentDescription = "$assessed of ${ids.size} modules assessed" },
            )
            Text("$assessed of ${ids.size} modules assessed", style = MaterialTheme.typography.bodyLarge)
            Muted("Your rocket only moves when a test proves you've learned something. Watching or reading alone doesn't count.")
        }
    }
}

@Composable
fun SubjectsScreen(repo: ParhlouRepository, onboarding: OnboardingState, onOpen: (String) -> Unit, onBack: () -> Unit) {
    ParhlouScreen("Subjects", onBack = onBack) {
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
