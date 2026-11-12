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
import pk.parhlou.student.ui.screens.CheckMasterScreen
import pk.parhlou.student.ui.screens.TutorSeed
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.data.ExamDesk
import androidx.compose.runtime.produceState
import pk.parhlou.student.ui.screens.SubjectScreen
import pk.parhlou.student.ui.screens.SubjectsScreen
import pk.parhlou.student.ui.screens.TestsScreen
import pk.parhlou.student.ui.screens.WelcomeScreen
import pk.parhlou.student.ui.screens.TutorScreen
import pk.parhlou.student.ui.screens.ProfileScreen
import pk.parhlou.student.ui.components.NavItem
import pk.parhlou.student.ui.components.GlassTabBar
import pk.parhlou.student.ui.components.GlassTabBarHeight
import pk.parhlou.student.ui.components.LocalBottomBarSpace
import pk.parhlou.student.ui.components.LocalReducedMotion
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavBackStackEntry
import com.styropyr0.prismal.initializePrismal
import com.styropyr0.prismal.sources.prismalGlassLayer
import androidx.compose.ui.platform.LocalContext
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.currentBackStackEntryAsState

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
    const val RESULT = "checked/{id}"
    const val JOURNEY = "journey"
    const val PLACEHOLDER = "soon/{key}"
    const val TUTOR = "tutor"
    const val PROFILE = "profile"
}

/** Main destinations shown in the floating pill bar. */
private val navItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Filled.Home),
    NavItem(Routes.SUBJECTS, "Book", Icons.AutoMirrored.Filled.MenuBook),
    NavItem(Routes.TESTS, "Papers", Icons.Filled.Quiz),
    NavItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

private val placeholders = mapOf(
    "guardian" to ("Add a parent" to "Linking a parent needs verified accounts on the server. It arrives with guardian reports."),
    "profile" to ("Profile & privacy" to "Account settings, data export and deletion arrive when sign-in is connected."),
    "scanner" to ("Scan answer sheets" to "The camera scanner for up to 25 pages isn't built yet. In a paper you can already add page photos from your gallery."),
    "history" to ("Practice history" to "Your saved quiz and paper history appears here once your account is connected."),
)

@Composable
fun ParhlouApp(repo: ParhlouRepository, store: LocalStore, ai: ParhlouAi = remember(repo) { DemoBrain(repo) }) {
    val context = LocalContext.current
    remember { initializePrismal(context) } // glass shaders must be loaded before the first frame
    val scope = rememberCoroutineScope()
    val progress = remember { ProgressStore(store, scope) }
    val progressLoaded by produceState(false) { progress.load(); value = true }
    val onboarding by store.onboarding.collectAsState(initial = null)
    val state = onboarding
    if (state == null || !progressLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val nav = rememberNavController()
    val desk = remember { ExamDesk(context.applicationContext, repo, store, ai, progress) }
    val readinessPassed by store.readinessPassed.collectAsState(initial = false)
    val activeExam by store.activeExam.collectAsState(initial = null)
    var tutorSeed by remember { mutableStateOf<TutorSeed?>(null) }
    fun openTutor(seed: TutorSeed?) { tutorSeed = seed; nav.navigate(Routes.TUTOR) }
    val subjectIds = state.subjectIds.toList()
    val blueprints = subjectIds.flatMap { repo.blueprints(it) }
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = current in navItems.map { it.route }
    fun goTop(route: String) = nav.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Everything the app draws is recorded into this layer so the glass tab bar can blur/refract it.
    val backdrop = rememberPrismalGlassLayer()
    val reduced = LocalReducedMotion.current
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) EnterTransition.None
        else fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) ExitTransition.None else fadeOut(tween(180))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) EnterTransition.None
        else fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    NavHost(
        nav,
        startDestination = if (state.completed) Routes.HOME else Routes.WELCOME,
        modifier = Modifier.fillMaxSize().prismalGlassLayer(backdrop),
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = exit,
    ) {
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
        composable(Routes.HOME) { WithTabBarSpace {
            HomeScreen(
                repo = repo, onboarding = state, progress = progress,
                onOpenModule = { nav.navigate("module/$it") },
                onSubjects = { nav.navigate(Routes.SUBJECTS) },
                onTests = { nav.navigate(Routes.TESTS) },
                onJourney = { nav.navigate(Routes.JOURNEY) },
                onTutor = { openTutor(null) },
                onPlaceholder = { nav.navigate("soon/$it") },
            )
        } }
        composable(Routes.TUTOR) {
            TutorScreen(ai, state.subjectIds, tutorSeed, examActive = activeExam != null, onBack = nav.back())
        }
        composable(Routes.PROFILE) { WithTabBarSpace {
            ProfileScreen(
                repo, state, progress,
                onPlaceholder = { nav.navigate("soon/$it") },
                onClearDevice = { scope.launch { store.clearAll() } },
            )
        } }
        composable(Routes.SUBJECTS) { WithTabBarSpace {
            SubjectsScreen(repo, state, onOpen = { nav.navigate("subject/$it") }, onBack = null)
        } }
        composable(Routes.SUBJECT) { entry ->
            val subject = repo.subject(entry.arguments?.getString("id").orEmpty())
            if (subject == null) Missing(nav) else
                SubjectScreen(subject, progress, onOpenModule = { nav.navigate("module/$it") }, onBack = nav.back())
        }
        composable(Routes.MODULE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val module = repo.module(id)
            val subject = subjectIds.mapNotNull { repo.subject(it) }
                .firstOrNull { s -> s.chapters.any { c -> c.modules.any { it.id == id } } }
            if (module == null) Missing(nav) else ModuleScreen(
                module = module, quiz = repo.moduleQuiz(id), progress = progress, store = store, ai = ai,
                courseLabel = "class ${subject?.grade ?: ""} ${subject?.name ?: ""} Punjab board",
                onAskTutor = { openTutor(it) }, onBack = nav.back(),
            )
        }
        composable(Routes.TESTS) { WithTabBarSpace {
            TestsScreen(
                blueprints = blueprints,
                available = blueprints.filter { repo.paper(it.id) != null }.map { it.id }.toSet(),
                readinessPassed = readinessPassed,
                onReadiness = { nav.navigate(Routes.READINESS) },
                onOpen = { nav.navigate("assessment/$it") },
                onBack = null,
            )
        } }
        composable(Routes.READINESS) {
            ReadinessScreen(
                questions = subjectIds.flatMap { repo.readinessQuiz(it) }.take(10),
                onPassed = { scope.launch { store.setReadinessPassed(true) }; nav.popBackStack() },
                onBack = nav.back(),
            )
        }
        composable(Routes.ASSESSMENT) { entry ->
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { repo.paper(it.id) }
            if (b == null || paper == null) Missing(nav) else {
                val log by store.examLog(b.id).collectAsState(initial = "")
                AssessmentStartScreen(
                    blueprint = b, examLog = log,
                    onStart = { nav.navigate("exam/${b.id}") },
                    onResult = { nav.navigate("checked/${b.id}") },
                    onReset = { scope.launch { desk.reset(paper) } },
                    onBack = nav.back(),
                )
            }
        }
        composable(Routes.EXAM) { entry ->
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { repo.paper(it.id) }
            if (b == null || paper == null) Missing(nav) else ExamScreen(
                blueprint = b, paper = paper, store = store, desk = desk, appScope = scope,
                onSubmitted = { nav.navigate("checked/${b.id}") { popUpTo(Routes.HOME) } },
                onBack = nav.back(),
            )
        }
        composable(Routes.RESULT) { entry ->
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { repo.paper(it.id) }
            if (b == null || paper == null) Missing(nav) else CheckMasterScreen(
                blueprint = b, paper = paper, desk = desk, store = store,
                onExplain = { openTutor(it) },
                onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.JOURNEY) { JourneyScreen(repo, state, progress, onBack = nav.back()) }
        composable(Routes.PLACEHOLDER) { entry ->
            val (title, msg) = placeholders[entry.arguments?.getString("key")] ?: ("Coming later" to "This part isn't built yet.")
            PlaceholderScreen(title, msg, onBack = { nav.popBackStack() })
        }
    }
    AnimatedVisibility(
        visible = showBar,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reduced) EnterTransition.None else fadeIn() + slideInVertically { it },
        exit = if (reduced) ExitTransition.None else fadeOut() + slideOutVertically { it },
    ) {
        GlassTabBar(
            items = navItems,
            selectedIndex = navItems.indexOfFirst { it.route == current }.coerceAtLeast(0),
            onSelect = { goTop(navItems[it].route) },
            backdrop = backdrop,
        )
    }
    }
}

@Composable
private fun WithTabBarSpace(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBottomBarSpace provides GlassTabBarHeight, content = content)

private fun NavHostController.back(): () -> Unit = { popBackStack() }

@Composable
private fun Missing(nav: NavHostController) =
    PlaceholderScreen("Not found", "This item isn't available for your board and class.", onBack = { nav.popBackStack() })
