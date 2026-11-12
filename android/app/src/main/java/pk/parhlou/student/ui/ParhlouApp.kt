package pk.parhlou.student.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.ui.screens.AssessmentStartScreen
import pk.parhlou.student.ui.screens.ExamScreen
import pk.parhlou.student.ui.screens.HomeScreen
import pk.parhlou.student.ui.screens.JourneyScreen
import pk.parhlou.student.ui.screens.ModuleScreen
import pk.parhlou.student.ui.screens.OnboardingScreen
import pk.parhlou.student.ui.screens.PlaceholderScreen
import pk.parhlou.student.ui.screens.ReadinessScreen
import pk.parhlou.student.ui.screens.ResultScreen
import pk.parhlou.student.ui.screens.SubjectScreen
import pk.parhlou.student.ui.screens.SubjectsScreen
import pk.parhlou.student.ui.screens.TestsScreen
import pk.parhlou.student.ui.screens.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SUBJECTS = "subjects"
    const val SUBJECT = "subject/{id}"
    const val MODULE = "module/{id}"
    const val TESTS = "tests"
    const val READINESS = "readiness"
    const val ASSESSMENT = "assessment/{id}"
    const val EXAM = "exam/{id}"
    const val RESULT = "result"
    const val JOURNEY = "journey"
    const val PLACEHOLDER = "soon/{key}"
}

private val placeholders = mapOf(
    "guardian" to ("Add a parent" to "Linking a parent needs verified accounts on the server. It arrives with guardian reports."),
    "profile" to ("Profile & privacy" to "Account settings, data export and deletion arrive when sign-in is connected."),
    "tutor" to ("AI tutor" to "The tutor answers only from approved, cited sources. It arrives after the content library is verified."),
)

@Composable
fun ParhlouApp(repo: ParhlouRepository, store: LocalStore) {
    val onboarding by store.onboarding.collectAsState(initial = null)
    val state = onboarding
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val nav = rememberNavController()
    val progress = remember { ProgressStore() }
    val scope = rememberCoroutineScope()
    var readinessPassed by rememberSaveable { mutableStateOf(false) }
    val subjectIds = state.subjectIds.toList()

    NavHost(nav, startDestination = if (state.completed) Routes.HOME else Routes.WELCOME) {
        composable(Routes.WELCOME) { WelcomeScreen(onStart = { nav.navigate(Routes.ONBOARDING) }) }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                repo = repo,
                initial = state,
                onSave = { s ->
                    scope.launch { store.saveOnboarding(s) }
                    if (s.completed) nav.navigate(Routes.HOME) { popUpTo(0) }
                },
                onExit = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repo = repo, onboarding = state, progress = progress,
                onOpenModule = { nav.navigate("module/$it") },
                onSubjects = { nav.navigate(Routes.SUBJECTS) },
                onTests = { nav.navigate(Routes.TESTS) },
                onJourney = { nav.navigate(Routes.JOURNEY) },
                onPlaceholder = { nav.navigate("soon/$it") },
            )
        }
        composable(Routes.SUBJECTS) {
            SubjectsScreen(repo, state, onOpen = { nav.navigate("subject/$it") }, onBack = nav.back())
        }
        composable(Routes.SUBJECT) { entry ->
            val subject = repo.subject(entry.arguments?.getString("id").orEmpty())
            if (subject == null) Missing(nav) else
                SubjectScreen(subject, progress, onOpenModule = { nav.navigate("module/$it") }, onBack = nav.back())
        }
        composable(Routes.MODULE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val module = repo.module(id)
            if (module == null) Missing(nav) else ModuleScreen(
                module = module, quiz = repo.moduleQuiz(id), progress = progress, store = store,
                onAskTutor = { nav.navigate("soon/tutor") }, onBack = nav.back(),
            )
        }
        composable(Routes.TESTS) {
            TestsScreen(
                blueprints = subjectIds.flatMap { repo.blueprints(it) },
                readinessPassed = readinessPassed,
                onReadiness = { nav.navigate(Routes.READINESS) },
                onOpen = { nav.navigate("assessment/$it") },
                onBack = nav.back(),
            )
        }
        composable(Routes.READINESS) {
            ReadinessScreen(
                questions = subjectIds.flatMap { repo.readinessQuiz(it) }.take(10),
                onPassed = { readinessPassed = true; nav.popBackStack() },
                onBack = nav.back(),
            )
        }
        composable(Routes.ASSESSMENT) { entry ->
            val b = subjectIds.flatMap { repo.blueprints(it) }.firstOrNull { it.id == entry.arguments?.getString("id") }
            if (b == null) Missing(nav) else
                AssessmentStartScreen(b, onStart = { nav.navigate("exam/${b.id}") }, onBack = nav.back())
        }
        composable(Routes.EXAM) { entry ->
            val b = subjectIds.flatMap { repo.blueprints(it) }.firstOrNull { it.id == entry.arguments?.getString("id") }
            if (b == null) Missing(nav) else ExamScreen(
                b, store,
                onSubmit = { nav.navigate(Routes.RESULT) { popUpTo(Routes.HOME) } },
                onBack = nav.back(),
            )
        }
        composable(Routes.RESULT) { ResultScreen(onHome = { nav.popBackStack(Routes.HOME, inclusive = false) }) }
        composable(Routes.JOURNEY) { JourneyScreen(repo, state, progress, onBack = nav.back()) }
        composable(Routes.PLACEHOLDER) { entry ->
            val (title, msg) = placeholders[entry.arguments?.getString("key")] ?: ("Coming later" to "This part isn't built yet.")
            PlaceholderScreen(title, msg, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.back(): () -> Unit = { popBackStack() }

@Composable
private fun Missing(nav: NavHostController) =
    PlaceholderScreen("Not found", "This item isn't available for your board and class.", onBack = { nav.popBackStack() })
