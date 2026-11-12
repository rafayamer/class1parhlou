package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.domain.StudyPlan
import androidx.compose.animation.AnimatedContent
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.rememberStepTransition
import pk.parhlou.student.ui.components.CheckRow
import pk.parhlou.student.ui.components.ChoiceCard
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp))
            Spacer(Modifier.height(16.dp))
            Text("Parhlou", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("پڑھ لو", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "Study from your book, practise with quizzes and sit real-style papers for your board exams.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            BigButton("Get started", onClick = onStart)
        }
    }
}

/** Four-step setup (PRD §4). Each step is saved as soon as it is completed. */
@Composable
fun OnboardingScreen(
    repo: ParhlouRepository,
    initial: OnboardingState,
    onSave: (OnboardingState) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var step by remember { mutableIntStateOf(firstIncompleteStep(initial)) }
    fun update(s: OnboardingState) { state = s; onSave(s) }

    ParhlouScreen(
        title = "Set up · step ${step + 1} of 4",
        onBack = { if (step == 0) onExit() else step-- },
    ) {
        LinearProgressIndicator(
            progress = { (step + 1) / 4f },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Step ${step + 1} of 4" },
        )
        AnimatedContent(targetState = step, transitionSpec = rememberStepTransition(), label = "onboardingStep") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    0 -> ConsentStep(state.consentAcknowledged) { update(state.copy(consentAcknowledged = it)) }
                    1 -> AcademicStep(repo, state) { update(it) }
                    2 -> PathwayStep(state.pathway) { update(state.copy(pathway = it)) }
                    else -> AvailabilityStep(state) { update(it) }
                }
            }
        }
        val canContinue = when (step) {
            0 -> state.consentAcknowledged
            1 -> state.boardCode != null && state.grade != null && state.medium != null && state.subjectIds.isNotEmpty()
            2 -> state.pathway != null
            else -> state.studyDaysPerWeek > 0 && state.minutesPerDay > 0
        }
        BigButton(
            text = if (step == 3) "Finish setup" else "Continue",
            enabled = canContinue,
            onClick = { if (step < 3) step++ else update(state.copy(completed = true)) },
        )
    }
}

private fun firstIncompleteStep(s: OnboardingState): Int = when {
    !s.consentAcknowledged -> 0
    s.boardCode == null || s.grade == null || s.medium == null || s.subjectIds.isEmpty() -> 1
    s.pathway == null -> 2
    else -> 3
}

@Composable
private fun ConsentStep(acknowledged: Boolean, onChange: (Boolean) -> Unit) {
    Text("Welcome to your academy", style = MaterialTheme.typography.titleLarge)
    Sheet {
        Text("What we store", style = MaterialTheme.typography.titleMedium)
        Muted("Your name, class, subjects, your answers and your test results. Photos of answer sheets are kept only for a short time to mark them.")
        Text("Who can see it", style = MaterialTheme.typography.titleMedium)
        Muted("Only you, and a parent or teacher you have linked and verified. Marks are never public.")
        Text("If you are under 18", style = MaterialTheme.typography.titleMedium)
        Muted("A parent or guardian will be asked to approve your account before paid features are used.")
    }
    CheckRow("I have read this and want to continue", checked = acknowledged, onChange = onChange)
    Muted("Account sign-in is added when the backend is connected. This build keeps your choices on this phone only.")
}

@Composable
private fun AcademicStep(repo: ParhlouRepository, state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    Text("Your board and class", style = MaterialTheme.typography.titleLarge)
    repo.boards().forEach { b ->
        ChoiceCard(
            title = b.name,
            subtitle = if (b.available) null else "Coming later",
            selected = state.boardCode == b.code,
            enabled = b.available,
            onClick = { onChange(state.copy(boardCode = b.code, subjectIds = emptySet())) },
        )
    }
    if (state.boardCode != null) {
        Text("Class", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(9, 10).forEach { g ->
                Column(Modifier.weight(1f)) {
                    ChoiceCard("Class $g", selected = state.grade == g,
                        onClick = { onChange(state.copy(grade = g, subjectIds = emptySet())) })
                }
            }
        }
    }
    if (state.grade != null) {
        Text("Medium", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                ChoiceCard("English", selected = state.medium == "English", onClick = { onChange(state.copy(medium = "English")) })
            }
            Column(Modifier.weight(1f)) {
                ChoiceCard("Urdu", subtitle = "Coming later", selected = false, enabled = false, onClick = {})
            }
        }
    }
    if (state.boardCode != null && state.grade != null && state.medium != null) {
        Text("Subjects", style = MaterialTheme.typography.titleMedium)
        val options = repo.subjectOptions(state.boardCode, state.grade)
        options.forEach { s ->
            val selected = s.id in state.subjectIds
            ChoiceCard(
                title = s.name, subtitle = s.note, selected = selected, enabled = s.available,
                onClick = {
                    onChange(state.copy(subjectIds = if (selected) state.subjectIds - s.id else state.subjectIds + s.id))
                },
            )
        }
        if (options.none { it.available }) {
            Muted("No subjects are ready for Class ${state.grade} yet. We won't show you another board's course instead.")
        }
    }
}

@Composable
private fun PathwayStep(selected: Pathway?, onChange: (Pathway) -> Unit) {
    Text("How do you want to study?", style = MaterialTheme.typography.titleLarge)
    Pathway.entries.forEach { p ->
        ChoiceCard(title = p.label, subtitle = p.description, selected = selected == p, onClick = { onChange(p) })
    }
    Muted("Both are part of the same monthly plan. You can switch later; switching never marks skipped lessons as done.")
}

@Composable
private fun AvailabilityStep(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    Text("Your study time", style = MaterialTheme.typography.titleLarge)
    Sheet {
        Text("Days per week: ${state.studyDaysPerWeek}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.studyDaysPerWeek.toFloat(), valueRange = 1f..7f, steps = 5,
            onValueChange = { onChange(state.copy(studyDaysPerWeek = it.toInt())) },
            modifier = Modifier.semantics { contentDescription = "Study days per week" },
        )
        Text("Minutes per day: ${state.minutesPerDay}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.minutesPerDay.toFloat(), valueRange = 15f..180f, steps = 10,
            onValueChange = { onChange(state.copy(minutesPerDay = (it.toInt() / 15) * 15)) },
            modifier = Modifier.semantics { contentDescription = "Study minutes per day" },
        )
    }
    // Placeholder effort figures until real module data exists; the exam date is not yet confirmed.
    val f = StudyPlan.feasibility(
        modulesRemaining = 60, minutesPerModule = 75,
        daysPerWeek = state.studyDaysPerWeek, minutesPerDay = state.minutesPerDay, weeksUntilExam = null,
    )
    Sheet {
        Text("Your plan", style = MaterialTheme.typography.titleMedium)
        Text(f.message, style = MaterialTheme.typography.bodyLarge)
        Muted("Estimate only: based on example module lengths, not your real course yet.")
    }
}

@Composable
fun PlaceholderScreen(title: String, message: String, onBack: () -> Unit) {
    ParhlouScreen(title = title, onBack = onBack) {
        Sheet {
            Text("Not available yet", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
        SecondaryButton("Back", onClick = onBack)
    }
}
