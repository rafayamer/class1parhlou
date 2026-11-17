package pk.parhlou.student.ui

import pk.parhlou.student.data.word

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.parhlou.student.domain.PaperPdf
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.domain.PaperPicker
import pk.parhlou.student.ui.screens.MakePaperScreen
import pk.parhlou.student.ui.screens.PracticalScreen
import pk.parhlou.student.ui.screens.practicalNotes
import pk.parhlou.student.ui.screens.PrintedPaperScreen
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
import pk.parhlou.student.data.Account
import pk.parhlou.student.ui.screens.SignInScreen
import pk.parhlou.student.ui.screens.HistoryScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.produceState
import pk.parhlou.student.ui.screens.SubjectScreen
import pk.parhlou.student.ui.screens.SolutionsFinderScreen
import pk.parhlou.student.ui.screens.ExamKitScreen
import pk.parhlou.student.ui.screens.todaysModule
import pk.parhlou.student.ui.screens.ProgressScreen
import pk.parhlou.student.ui.screens.MyUstaadScreen
import pk.parhlou.student.domain.StudentProfile
import pk.parhlou.student.ui.shareFile
import pk.parhlou.student.data.UstaadPrefs
import pk.parhlou.student.ui.session.SessionScreen
import pk.parhlou.student.domain.SessionBuilder
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.Rewards
import pk.parhlou.student.ui.screens.WhatsNew
import pk.parhlou.student.ui.screens.WhatsNewScreen
import pk.parhlou.student.ui.screens.SolutionDetailScreen
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
import pk.parhlou.student.data.PaperContent
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.domain.MockPaper
import pk.parhlou.student.domain.MockScope
import pk.parhlou.student.domain.PaperAssembler
import pk.parhlou.student.domain.PlanKind
import pk.parhlou.student.domain.PlanModule
import pk.parhlou.student.domain.PlanTask
import pk.parhlou.student.domain.StudyPlanner
import pk.parhlou.student.domain.Weakness
import pk.parhlou.student.ui.screens.PlannerScreen
import pk.parhlou.student.ui.screens.ScanChooserScreen
import pk.parhlou.student.ui.screens.WeakTopicsScreen

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
    const val EXAM = "exam/{id}?scan={scan}"
    const val SCAN = "scan"
    const val RESULT = "checked/{id}"
    const val PRINTED = "printed/{id}"
    const val MAKE_PAPER = "make-paper"
    const val JOURNEY = "journey"
    const val PLACEHOLDER = "soon/{key}"
    const val TUTOR = "tutor"
    const val PROFILE = "profile"
    const val SIGN_IN = "signin"
    const val HISTORY = "history"
    const val EDIT_PROFILE = "profile/edit"
    const val PLANNER = "planner"
    const val WEAK_TOPICS = "weak-topics"
    const val SOLUTIONS = "solutions"
    const val KIT = "kit/{id}"
    const val PRACTICAL = "practical/{id}"
    const val WHATS_NEW = "whats-new"
    const val PROGRESS = "progress"
    const val MY_USTAAD = "my-ustaad"
    /** A guided session; module "today" = today's pick. */
    const val SESSION = "session/{module}?min={min}"
    const val SOLUTION = "solution/{module}/{index}"
}

/** Main destinations shown in the floating pill bar. */
private val navItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Filled.Home),
    NavItem(Routes.SUBJECTS, "Books", Icons.AutoMirrored.Filled.MenuBook),
    NavItem(Routes.TESTS, "Papers", Icons.Filled.Quiz),
    NavItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

private val placeholders = mapOf(
    "guardian" to ("Add a parent" to "Linking a parent needs verified accounts on the server. It arrives with guardian reports."),
    "profile" to ("Profile & privacy" to "Account settings, data export and deletion arrive when sign-in is connected."),
    "history" to ("Practice history" to "Your saved quiz and paper history appears here once your account is connected."),
)

@Composable
fun ParhlouApp(
    repo: ParhlouRepository,
    store: LocalStore,
    /** This build's number; when above 0, "What's new" opens once after each update. Tests leave it 0. */
    currentBuild: Int = 0,
    ai: ParhlouAi = remember(repo) { DemoBrain(repo) },
    /** Sign-in and sync; null in builds without a Supabase address. */
    account: Account? = null,
    versionLabel: String = "",
    /** The real app needs the internet: while offline everything is blurred behind "Connect to the internet". */
    requireInternet: Boolean = false,
) {
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
    val reminderHour by store.reminderHour.collectAsState(initial = 18)
    // After an update, "What's new" opens once by itself (a fresh install starts with onboarding instead).
    if (currentBuild > 0) {
        val seen by store.seenBuild.collectAsState(initial = -1)
        LaunchedEffect(seen) {
            if (seen < 0 || seen >= currentBuild) return@LaunchedEffect
            store.markSeen(currentBuild)
            if (state.completed) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { nav.navigate(Routes.WHATS_NEW) }
        }
    }
    val session by (account?.auth?.session ?: flowOf(null)).collectAsState(initial = null)
    val guest by store.guest.collectAsState(initial = false)
    val accountError by (account?.error ?: MutableStateFlow(null)).collectAsState()
    var accountMessage by remember { mutableStateOf<String?>(null) }
    // After signing in (by link or code): restore or upload, then continue to the right screen.
    val signedInAs = session?.userId
    LaunchedEffect(signedInAs) {
        if (account == null || signedInAs == null) return@LaunchedEffect
        val settings = account.afterSignIn(progress)
        // Storage and network calls may resume on a background thread; navigation must run on the main thread.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            val onSignIn = nav.currentDestination?.route == Routes.SIGN_IN
            if (onSignIn) nav.navigate(if (settings.completed) Routes.HOME else Routes.ONBOARDING) { popUpTo(0) }
        }
    }
    // Upload new progress whenever the app goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { account?.let { a -> scope.launch { a.syncNow() } } }
    val needsSignIn = account != null && session == null && !guest
    val desk = remember(repo, ai, account) {
        ExamDesk(context.applicationContext, repo, store, ai, progress) { paperId ->
            if (account == null) emptyMap() else account.sync.paperMarks(paperId)
        }
    }
    val readinessPassed by store.readinessPassed.collectAsState(initial = false)
    val activeExam by store.activeExam.collectAsState(initial = null)
    var tutorSeed by remember { mutableStateOf<TutorSeed?>(null) }
    fun openTutor(seed: TutorSeed?) { tutorSeed = seed; nav.navigate(Routes.TUTOR) }
    val subjectIds = state.subjectIds.toList()
    val subjects = subjectIds.mapNotNull { repo.subject(it) }
    val chapterBlueprints = subjectIds.flatMap { repo.blueprints(it) }
    // Each subject has its own papers: chapter tests, mocks in its own board pattern, a board-paper template and a question bank.
    class SubjectPapers(val subject: pk.parhlou.student.data.Subject, val blueprints: List<pk.parhlou.student.data.PaperBlueprint>,
                        val mocks: List<MockPaper>, val template: pk.parhlou.student.data.PaperBlueprint?, val bank: PaperPicker.Bank)
    val perSubject = remember(repo, subjectIds) {
        subjects.associate { s ->
            val own = repo.blueprints(s.id)
            val pool = s.chapters.associate { c ->
                val papers = own.filter { it.chapterId == c.id }.mapNotNull { repo.paper(it.id) }
                c.id to PaperContent("pool-${c.id}", papers.flatMap { it.mcqs }, papers.flatMap { it.written })
            }
            val prefix = if (s.id == FakeRepository.MATHS_9) "" else s.id.substringAfterLast('-') + "-"
            val mocks = MockScope.entries.map { PaperAssembler.assemble(it, s.chapters, pool, PaperAssembler.patternFor(s.id), prefix) }
            s.id to SubjectPapers(s, own, mocks, own.firstOrNull { it.official && it.chapterId == null && it.id.contains("board-") },
                questionBank(repo, listOf(s), own))
        }
    }
    // The subject whose papers the Papers tab shows (and "make me a paper" uses); the first subject by default.
    var paperSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val paperSubject = perSubject[paperSubjectId] ?: perSubject.values.firstOrNull()
    val mocks = paperSubject?.mocks.orEmpty()
    val readyMocks = perSubject.values.flatMap { it.mocks }.filterIsInstance<MockPaper.Ready>().associateBy { it.blueprint.id }
    // "Make my paper": full board-pattern papers chosen for this student, kept on the phone.
    val template = paperSubject?.template
    val bank = paperSubject?.bank ?: questionBank(repo, subjects, chapterBlueprints)
    val myPapersJson by store.draft(MY_PAPERS).collectAsState(initial = "")
    // Each saved paper belongs to the subject whose template and question bank can read it back.
    val myPapers = remember(myPapersJson, perSubject) {
        if (myPapersJson.isBlank()) emptyList()
        else runCatching { org.json.JSONArray(myPapersJson).let { a -> (0 until a.length()).mapNotNull { i ->
            perSubject.values.firstNotNullOfOrNull { sp -> sp.template?.let { t -> PaperPicker.fromJson(a.getString(i), t, sp.bank) } }
        } } }.getOrDefault(emptyList())
    }.associateBy { it.blueprint.id }
    val blueprints = myPapers.values.map { it.blueprint } + chapterBlueprints + readyMocks.values.map { it.blueprint }
    /** The papers the Papers tab lists: only the chosen subject's. */
    val shownBlueprints = paperSubject?.let { sp ->
        val ids = (sp.blueprints.map { it.id } + sp.mocks.filterIsInstance<MockPaper.Ready>().map { it.blueprint.id }).toSet()
        myPapers.values.filter { m -> sp.template != null && PaperPicker.fromJson(PaperPicker.toJson(m), sp.template, sp.bank) != null }.map { it.blueprint } +
            blueprints.filter { it.id in ids }
    } ?: blueprints
    /** The subject a paper belongs to, by name, for its printed header. */
    fun subjectNameOf(blueprintId: String): String = perSubject.values.firstOrNull { sp ->
        sp.blueprints.any { it.id == blueprintId } || sp.mocks.any { (it as? MockPaper.Ready)?.blueprint?.id == blueprintId } ||
            (sp.template != null && myPapers[blueprintId]?.let { m -> PaperPicker.fromJson(PaperPicker.toJson(m), sp.template, sp.bank) } != null)
    }?.subject?.name ?: subjects.firstOrNull()?.name ?: "Mathematics"
    fun paperOf(id: String): PaperContent? = repo.paper(id) ?: readyMocks[id]?.paper ?: myPapers[id]?.paper
    /** Abandons a running paper: everything for it is cleared, no try is used, Do Not Disturb ends and Ask Ustaad unlocks. */
    fun quitPaper(id: String, then: () -> Unit = {}) {
        scope.launch {
            val p = paperOf(id)
            if (p != null) desk.reset(p) else store.resetExam(id, emptyList())
            pk.parhlou.student.ui.FocusMode.off(context)
            withContext(Dispatchers.Main.immediate) { then() }
        }
    }
    // "Make me a paper / notes" from Ask Ustaad: built from the course on the phone, never invented.
    fun makePdf(action: pk.parhlou.student.domain.PdfAction): java.io.File? {
        // The subject the student named, else the one chosen on the Papers tab.
        val sp = perSubject.values.firstOrNull { it.subject.name.equals(action.subject, ignoreCase = true) } ?: paperSubject
        val chapters = sp?.subject?.chapters.orEmpty()
        val dir = java.io.File(context.cacheDir, "papers")
        val name = action.label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (action.isNotes && action.scope != "exercise") {
            val sections = pk.parhlou.student.domain.PdfContent.notes(chapters, pk.parhlou.student.domain.PdfContent.units(chapters, action))
            return if (sections.isEmpty()) null else PaperPdf.notes(java.io.File(dir, "$name.pdf"), action.label, sections, sp?.subject?.name ?: "Mathematics")
        }
        val (b, p) = when (action.scope) {
            "exercise" -> pk.parhlou.student.domain.PdfContent.exercisePaper(chapters, action.exercise)
            "unit" -> chapters.firstOrNull { it.number == action.unit }?.let { c ->
                chapterBlueprints.firstOrNull { it.chapterId == c.id }?.let { b -> repo.paper(b.id)?.let { b to it } } }
            "half" -> sp?.mocks?.filterIsInstance<MockPaper.Ready>()?.firstOrNull { it.scope == MockScope.HALF }?.let { it.blueprint to it.paper }
            else -> sp?.template?.let { t -> repo.paper(t.id)?.let { tp ->
                val slots = PaperPicker.slots(t, tp, sp.bank, emptySet(), System.currentTimeMillis())
                PaperPicker.build("full-${System.currentTimeMillis()}", "Full-book paper (2027 pattern)", t, tp, slots, PaperPicker.localPick(slots), sp.bank, false)
                    ?.let { it.blueprint to it.paper } } }
        } ?: return null
        return PaperPdf.write(java.io.File(dir, "$name.pdf"), b, p, withScheme = action.withAnswers || action.isNotes, subject = sp?.subject?.name ?: "Mathematics")
    }
    // A paper that ended while the app was closed still gives the phone back its own Do Not Disturb setting.
    LaunchedEffect(Unit) { if (store.activeExam.first() == null) pk.parhlou.student.ui.FocusMode.off(context) }
    val noteTitles = remember(repo, subjectIds) { subjectIds.flatMap { repo.notes(it) }.associate { it.id to it.title } }
    // The plan teaches what the exam examines; sections the smart syllabus leaves out stay open as practice.
    val planModules = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.filter { it.inExam }.map { PlanModule(it.id, it.title, c.id, "${c.word} ${c.number} ${c.title}") } } }
    fun plan() = StudyPlanner.plan(
        planModules, progress.history(), { noteTitles[it] },
        blueprints.filter { paperOf(it.id) != null }.map { it.id to it.title },
        testSeries = state.pathway == Pathway.TEST_SERIES, minutesPerDay = state.minutesPerDay, now = System.currentTimeMillis(),
    )
    fun weakTopics() = Weakness.topics(progress.history(), { noteTitles[it] }, System.currentTimeMillis())
    fun openPlanTask(t: PlanTask) = when (t.kind) {
        PlanKind.REPAIR -> openTutor(TutorSeed(noteId = t.target))
        PlanKind.REVIEW, PlanKind.LEARN -> nav.navigate("module/${t.target}")
        PlanKind.PAPER -> nav.navigate("assessment/${t.target}")
        PlanKind.READINESS -> nav.navigate(Routes.READINESS)
    }
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

    val systemOnline = rememberOnline()
    var retried by remember { mutableIntStateOf(0) }
    val offline = requireInternet && !systemOnline && remember(retried, systemOnline) { !Connectivity.isOnline(context) }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (offline) Modifier.blur(androidx.compose.ui.unit.Dp(12f)) else Modifier)) {
    NavHost(
        nav,
        startDestination = if (state.completed) Routes.HOME else Routes.WELCOME,
        modifier = Modifier.fillMaxSize(),
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = exit,
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = { nav.navigate(if (needsSignIn) Routes.SIGN_IN else Routes.ONBOARDING) })
        }
        composable(Routes.SIGN_IN) {
            if (account == null) Missing(nav) else SignInScreen(
                auth = account.auth,
                initialName = state.name,
                error = accountError,
                onName = { n -> scope.launch { store.saveOnboarding(store.onboarding.first().copy(name = n)) } },
                onGuest = {
                    scope.launch { store.setGuest(true) }
                    nav.navigate(if (state.completed) Routes.HOME else Routes.ONBOARDING) { popUpTo(0) }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                repo = repo,
                initial = state,
                onSave = { s ->
                    scope.launch { store.saveOnboarding(s); if (s.completed) account?.syncNow(s) }
                    if (s.completed) nav.navigate(Routes.HOME) { popUpTo(0) }
                },
                onExit = { nav.popBackStack() },
                signInEmail = session?.email,
            )
        }
        composable(Routes.HOME) { WithTabBarSpace {
            HomeScreen(
                repo = repo, onboarding = state, progress = progress,
                onOpenModule = { nav.navigate("module/$it") },
                onSubjects = { goTop(Routes.SUBJECTS) },
                onTests = { goTop(Routes.TESTS) },
                onJourney = { nav.navigate(Routes.JOURNEY) },
                onTutor = { openTutor(null) },
                onPlaceholder = { nav.navigate("soon/$it") },
                plan = plan(),
                onPlanTask = ::openPlanTask,
                onPlanner = { nav.navigate(Routes.PLANNER) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSolutions = { nav.navigate(Routes.SOLUTIONS) },
                onStartSession = { m, min -> nav.navigate("session/${m ?: "today"}?min=$min") },
                onKit = { nav.navigate("kit/$it") },
                onProgress = { nav.navigate(Routes.PROGRESS) },
                // Camera: straight into the scanner of the paper in progress, or choose a paper first.
                onScan = {
                    val active = activeExam
                    if (active != null && blueprints.any { it.id == active }) scope.launch {
                        val onPaper = store.draft(desk.modeKey(active)).first() == "paper"
                        withContext(Dispatchers.Main.immediate) { nav.navigate(if (onPaper) "printed/$active" else "exam/$active?scan=true") }
                    }
                    else nav.navigate(Routes.SCAN)
                },
            )
        } }
        composable(Routes.SCAN) {
            ScanChooserScreen(
                papers = blueprints.filter { paperOf(it.id)?.written?.isNotEmpty() == true },
                readinessPassed = readinessPassed,
                onStart = { nav.navigate("exam/$it?scan=true") { popUpTo(Routes.SCAN) { inclusive = true } } },
                onReadiness = { nav.navigate(Routes.READINESS) },
                onBack = nav.back(),
            )
        }
        composable(Routes.PLANNER) {
            PlannerScreen(
                tasks = plan(), minutesPerDay = state.minutesPerDay, weakTopics = weakTopics(),
                onTask = ::openPlanTask,
                onWeakTopics = { nav.navigate(Routes.WEAK_TOPICS) },
                onBook = { goTop(Routes.SUBJECTS) },
                onBack = nav.back(),
            )
        }
        composable(Routes.WEAK_TOPICS) {
            WeakTopicsScreen(weakTopics(), onFix = { openTutor(TutorSeed(noteId = it.noteId)) }, onBack = nav.back())
        }
        composable(Routes.TUTOR) {
            val prefs by store.ustaadPrefs.collectAsState(initial = UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0))
            androidx.compose.runtime.CompositionLocalProvider(pk.parhlou.student.ui.screens.LocalLessonVideos provides { q: String -> ai.lessonVideos(q) }) {
            TutorScreen(ai, state.subjectIds, tutorSeed, examActive = activeExam != null, onBack = nav.back(),
                onGoToPaper = activeExam?.let { id -> { nav.navigate("assessment/$id") } },
                onQuitPaper = activeExam?.let { id -> { quitPaper(id) } },
                store = store, prefs = prefs, studentName = state.firstName,
                profile = { StudentProfile.build(state, subjects, progress.history(), prefs, { noteTitles[it] }).summary() },
                moduleOf = { repo.module(it) }, noteTitle = { noteTitles[it] },
                onOpenSolution = { m, i -> nav.navigate("solution/$m/$i") },
                onMyUstaad = { nav.navigate(Routes.MY_USTAAD) },
                makePdf = { action -> withContext(Dispatchers.IO) { makePdf(action) } })
            }
        }
        composable(Routes.MY_USTAAD) {
            val prefs by store.ustaadPrefs.collectAsState(initial = UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0))
            MyUstaadScreen(StudentProfile.build(state, subjects, progress.history(), prefs, { noteTitles[it] }),
                onChange = { p -> scope.launch { store.setUstaadPrefs(p) } }, onBack = nav.back())
        }
        composable(Routes.EDIT_PROFILE) {
            pk.parhlou.student.ui.screens.EditProfileScreen(repo, state, session?.email,
                onSave = { s -> scope.launch { store.saveOnboarding(s); account?.syncNow(s) } },
                onBack = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) { WithTabBarSpace {
            val soundOn by store.soundOn.collectAsState(initial = true)
            ProfileScreen(
                repo, state, progress,
                onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                soundOn = soundOn, onToggleSound = { scope.launch { store.setSound(!soundOn) } },
                allowance = { ai.allowance() },
                onSendFeedback = { ai.sendFeedback(it, "profile", "build ${pk.parhlou.student.BuildConfig.VERSION_CODE}") },
                onPlaceholder = { nav.navigate("soon/$it") },
                onClearDevice = { scope.launch { account?.auth?.signOut(); store.clearAll() } },
                email = session?.email,
                accountsAvailable = account != null,
                onSignIn = { scope.launch { store.setGuest(false) }; nav.navigate(Routes.SIGN_IN) },
                onSignOut = { scope.launch { account?.syncNow(); account?.auth?.signOut(); accountMessage = "Signed out. Your progress is still on this phone." } },
                onExport = {
                    scope.launch {
                        val json = account?.sync?.exportData()
                        accountMessage = if (json == null) "Couldn't download your data. Check your internet and try again."
                        else { shareText(context, "parhlou-my-data.json", json); null }
                    }
                },
                onDeleteAccount = {
                    scope.launch {
                        val ok = account?.sync?.deleteAccount() == true
                        if (ok) { account?.auth?.signOut(); store.clearAll() }
                        else accountMessage = "Couldn't delete the account. Check your internet and try again."
                    }
                },
                onHistory = { nav.navigate(Routes.HISTORY) },
                versionLabel = versionLabel,
                onWhatsNew = { nav.navigate(Routes.WHATS_NEW) },
                onMyUstaad = { nav.navigate(Routes.MY_USTAAD) },
                reminderLabel = if (reminderHour < 0) "Off" else "Every day at ${hourLabel(reminderHour)}",
                onReminder = {
                    val next = REMINDER_CHOICES[(REMINDER_CHOICES.indexOf(reminderHour) + 1) % REMINDER_CHOICES.size]
                    scope.launch { store.setReminderHour(next) }
                    runCatching { pk.parhlou.student.update.ReminderWorker.schedule(context.applicationContext, next) }
                },
                accountMessage = accountMessage,
            )
        } }
        composable(Routes.SUBJECTS) { WithTabBarSpace {
            SubjectsScreen(repo, state, onOpen = { nav.navigate("subject/$it") }, onBack = null,
                onSolutions = { nav.navigate(Routes.SOLUTIONS) },
                onAddSubject = { id -> scope.launch { store.saveOnboarding(state.copy(subjectIds = state.subjectIds + id)) } },
                onEditSubjects = { nav.navigate(Routes.EDIT_PROFILE) })
        } }
        composable(
            Routes.SESSION,
            arguments = listOf(androidx.navigation.navArgument("min") { type = androidx.navigation.NavType.IntType; defaultValue = 10 }),
        ) { entry ->
            val requested = entry.arguments?.getString("module").orEmpty()
            val minutes = entry.arguments?.getInt("min") ?: 10
            val module = remember(requested) {
                if (requested == "today") todaysModule(subjects, progress) else repo.module(requested)
            }
            if (module == null) Missing(nav) else {
                val chapter = subjects.flatMap { it.chapters }.firstOrNull { c -> c.modules.any { it.id == module.id } }
                val noteId = remember(module.id) { subjectIds.flatMap { repo.notes(it) }.firstOrNull { it.moduleId == module.id }?.id }
                val items = remember(module.id, minutes) {
                    SessionBuilder.build(module, repo.moduleQuiz(module.id, 40), chapter?.kit, minutes,
                        seed = System.currentTimeMillis() / 3_600_000, noteId = noteId)
                }
                val sound by store.soundOn.collectAsState(initial = true)
                SessionScreen(
                    title = "${chapter?.word ?: "Unit"} ${chapter?.number ?: ""} · ${module.title}",
                    items = items,
                    soundOn = sound,
                    onToggleSound = { scope.launch { store.setSound(!sound) } },
                    onAnswered = { item, right ->
                        item.noteId?.let { progress.add(pk.parhlou.student.domain.TOPIC_PREFIX + it, if (right) Evidence.TopicCorrect else Evidence.TopicMissed) }
                    },
                    onAskTutor = { q -> openTutor(TutorSeed(moduleId = module.id, question = q)) },
                    streakAfter = { r ->
                        val s = Rewards.summary(progress.history(), state.minutesPerDay, System.currentTimeMillis())
                        val days = if (s.studiedToday) s.streakDays else s.streakDays + 1
                        "🔥 $days-day streak · ${s.xpToday + r.xp}/${s.dailyGoalXp} XP today"
                    },
                    onFinished = { r ->
                        progress.add(module.id, Evidence.QuizCompleted(r.accuracy))
                        progress.add("session", Evidence.SessionCompleted(r.xp))
                        nav.popBackStack()
                    },
                    onClose = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.PROGRESS) {
            val s = subjects.firstOrNull()
            ProgressScreen(Rewards.summary(progress.history(), state.minutesPerDay, System.currentTimeMillis()),
                s?.let { pk.parhlou.student.domain.ExamCountdown.daysLeft(it.examDate) }, s?.examName.orEmpty(), onBack = nav.back())
        }
        composable(Routes.WHATS_NEW) {
            val context = LocalContext.current
            WhatsNewScreen(remember { WhatsNew.load(context) }, versionLabel, onDone = { nav.popBackStack() })
        }
        composable(Routes.SOLUTIONS) {
            SolutionsFinderScreen(subjects, onOpen = { m, i -> nav.navigate("solution/$m/$i") }, onBack = nav.back())
        }
        composable(Routes.SOLUTION) { entry ->
            val module = repo.module(entry.arguments?.getString("module").orEmpty())
            val index = entry.arguments?.getString("index")?.toIntOrNull() ?: -1
            if (module == null) Missing(nav) else SolutionDetailScreen(
                module, index,
                onAskTutor = { q -> openTutor(TutorSeed(moduleId = module.id, question = q)) },
                onBack = nav.back(),
                onOpen = { i -> nav.navigate("solution/${module.id}/$i") },
                onHelpful = { yes ->
                    progress.add("sol:${module.id}:${module.solutions.getOrNull(index)?.source.orEmpty()}", Evidence.Helpful(if (yes) 1 else 0))
                },
            )
        }
        composable(Routes.SUBJECT) { entry ->
            val subject = repo.subject(entry.arguments?.getString("id").orEmpty())
            if (subject == null) Missing(nav) else
                SubjectScreen(subject, progress, onOpenModule = { nav.navigate("module/$it") }, onBack = nav.back(),
                    onOpenKit = { nav.navigate("kit/$it") },
                    onStartSession = { nav.navigate("session/$it?min=${state.minutesPerDay}") },
                    onSolutions = { nav.navigate(Routes.SOLUTIONS) },
                    onOpenPractical = { nav.navigate("practical/$it") })
        }
        composable(Routes.PRACTICAL) { entry ->
            val id = entry.arguments?.getString("id")
            val owner = subjects.firstOrNull { s -> s.practicals.any { it.id == id } }
            val p = owner?.practicals?.firstOrNull { it.id == id }
            if (p == null) Missing(nav) else PracticalScreen(p, onBack = nav.back(), onPrint = {
                scope.launch {
                    val f = withContext(Dispatchers.IO) {
                        runCatching { PaperPdf.notes(java.io.File(context.cacheDir, "papers/${p.id}.pdf"), "${p.label}: ${p.title}", practicalNotes(p), owner.name) }.getOrNull()
                    }
                    if (f != null) shareFile(context, f, "application/pdf", p.title) else pdfFailed(context)
                }
            })
        }
        composable(Routes.KIT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val chapter = subjects.flatMap { it.chapters }.firstOrNull { it.id == id }
            if (chapter == null) Missing(nav) else
                ExamKitScreen(chapter, onOpenSolution = { m, i -> nav.navigate("solution/$m/$i") }, onBack = nav.back())
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
                blueprints = shownBlueprints,
                available = shownBlueprints.filter { paperOf(it.id) != null }.map { it.id }.toSet(),
                readinessPassed = readinessPassed,
                onReadiness = { nav.navigate(Routes.READINESS) },
                onOpen = { nav.navigate("assessment/$it") },
                onBack = null,
                pendingMocks = mocks.filterIsInstance<MockPaper.NeedsQuestions>(),
                onMakePaper = if (template != null) ({ nav.navigate(Routes.MAKE_PAPER) }) else null,
                exercises = remember(paperSubject) {
                    val chapters = paperSubject?.subject?.chapters.orEmpty()
                    val dotted = chapters.flatMap { c -> c.modules.flatMap { it.solutions } }
                        .mapNotNull { Regex("""^Ex (\d+\.\d+) """).find(it.source)?.groupValues?.get(1) }.distinct()
                        .sortedWith(compareBy({ it.substringBefore('.').toInt() }, { it.substringAfter('.').toInt() }))
                    // Subjects with one exercise per chapter (Physics): each chapter's exercise.
                    dotted.ifEmpty { chapters.filter { c -> c.modules.any { m -> m.solutions.any { it.kind == pk.parhlou.student.data.SolutionKind.EXERCISE } } }.map { "${it.number}" } }
                },
                exerciseLabel = if (paperSubject?.subject?.id == FakeRepository.MATHS_9 || paperSubject == null) "Exercise" else "Chapter exercise",
                subjects = subjects.map { it.id to it.name },
                selectedSubject = paperSubject?.subject?.id,
                onSubject = { paperSubjectId = it },
                onExercisePdf = { e, answers ->
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { makePdf(pk.parhlou.student.domain.PdfAction("paper_pdf", "exercise", 0, e, answers)) }.getOrNull() }
                        if (f != null) shareFile(context, f, "application/pdf", if ('.' in e) "Exercise $e" else "Chapter $e exercise") else pdfFailed(context)
                    }
                },
                attemptsLeft = { id -> (pk.parhlou.student.domain.PaperAttempts.MAX - pk.parhlou.student.domain.PaperAttempts.used(progress.history(), id)).coerceAtLeast(0) },
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
            val paper = b?.let { paperOf(it.id) }
            if (b == null || paper == null) Missing(nav) else {
                val log by store.examLog(b.id).collectAsState(initial = "")
                AssessmentStartScreen(
                    blueprint = b, examLog = log,
                    onStart = { nav.navigate("exam/${b.id}") },
                    onResult = { nav.navigate("checked/${b.id}") },
                    onReset = { scope.launch { desk.reset(paper) } },
                    onBack = nav.back(),
                    attemptsUsed = pk.parhlou.student.domain.PaperAttempts.used(progress.history(), b.id),
                    bestPercent = pk.parhlou.student.domain.PaperAttempts.best(progress.history(), b.id),
                    onPrint = { withScheme ->
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching { PaperPdf.write(java.io.File(context.cacheDir, "papers/${b.id}${if (withScheme) "-scheme" else ""}.pdf"), b, paper, withScheme, subjectNameOf(b.id)) }.getOrNull()
                            }
                            if (file != null) shareFile(context, file, "application/pdf", "Print or share your paper") else pdfFailed(context)
                        }
                    },
                    onWrittenOnPaper = { nav.navigate("printed/${b.id}") },
                    onPaper = store.draft(desk.modeKey(b.id)).collectAsState(initial = "").value == "paper",
                    onQuit = { quitPaper(b.id) { nav.navigate(Routes.TUTOR) { popUpTo(Routes.HOME) } } },
                    onStartOnPaper = {
                        scope.launch {
                            desk.startOnPaper(b.id)
                            val file = withContext(Dispatchers.IO) {
                                runCatching { PaperPdf.write(java.io.File(context.cacheDir, "papers/${b.id}.pdf"), b, paper, false, subjectNameOf(b.id)) }.getOrNull()
                            }
                            withContext(Dispatchers.Main.immediate) {
                                nav.navigate("printed/${b.id}")
                                if (file != null) shareFile(context, file, "application/pdf", "Print or open your paper") else pdfFailed(context)
                            }
                        }
                    },
                )
            }
        }
        composable(
            Routes.EXAM,
            arguments = listOf(androidx.navigation.navArgument("scan") { type = androidx.navigation.NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { paperOf(it.id) }
            if (b == null || paper == null) Missing(nav) else ExamScreen(
                blueprint = b, paper = paper, store = store, desk = desk, appScope = scope,
                openScanner = entry.arguments?.getBoolean("scan") == true,
                onSubmitted = { nav.navigate("checked/${b.id}") { popUpTo(Routes.HOME) } },
                onBack = nav.back(),
            )
        }
        composable(Routes.PRINTED) { entry ->
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { paperOf(it.id) }
            if (b == null || paper == null) Missing(nav) else PrintedPaperScreen(
                blueprint = b, paper = paper, desk = desk, examStore = store, appScope = scope, readsHandwriting = ai.readsPhotos,
                onMarked = { nav.navigate("checked/${b.id}") { popUpTo(Routes.HOME) } },
                onBack = nav.back(),
            )
        }
        composable(Routes.MAKE_PAPER) {
            val prefs by store.ustaadPrefs.collectAsState(initial = UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0))
            MakePaperScreen(
                make = make@{
                    val t = template ?: return@make null
                    val tPaper = repo.paper(t.id) ?: return@make null
                    val history = progress.history()
                    val seen = history.map { it.key }.filter { it.startsWith(ProgressStore.PAPER_PREFIX) }
                        .mapNotNull { paperOf(it.removePrefix(ProgressStore.PAPER_PREFIX)) }
                        .flatMap { p -> p.mcqs.map { it.id } + p.written.map { it.id } }.toSet()
                    val slots = PaperPicker.slots(t, tPaper, bank, seen, System.currentTimeMillis())
                    val profile = StudentProfile.build(state, subjects, history, prefs, { noteTitles[it] }).summary()
                    val aiPick = if (ai.readsPhotos) ai.pickPaper(slots, profile) else null
                    val n = myPapers.size + 1
                    val made = PaperPicker.build("my-paper-${System.currentTimeMillis()}", "My paper $n (2027 pattern)", t, tPaper, slots,
                        aiPick ?: PaperPicker.localPick(slots), bank, byAi = aiPick != null) ?: return@make null
                    val list = runCatching { org.json.JSONArray(myPapersJson) }.getOrDefault(org.json.JSONArray()).put(PaperPicker.toJson(made))
                    store.saveDraft(MY_PAPERS, list.toString())
                    made
                },
                onOpen = { nav.navigate("assessment/$it") { popUpTo(Routes.TESTS) } },
                onBack = nav.back(),
                saved = myPapers.values.map { it.blueprint.id to it.blueprint.title },
                onDelete = { del ->
                    scope.launch {
                        val a = runCatching { org.json.JSONArray(myPapersJson) }.getOrDefault(org.json.JSONArray())
                        val kept = org.json.JSONArray()
                        for (i in 0 until a.length()) { val e = a.get(i); val id = runCatching { org.json.JSONObject(e.toString()).optString("id") }.getOrNull(); if (id != del) kept.put(e) }
                        store.saveDraft(MY_PAPERS, kept.toString())
                        paperOf(del)?.let { desk.reset(it) }
                    }
                },
            )
        }
        composable(Routes.RESULT) { entry ->
            val prefs by store.ustaadPrefs.collectAsState(initial = UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0))
            val b = blueprints.firstOrNull { it.id == entry.arguments?.getString("id") }
            val paper = b?.let { paperOf(it.id) }
            if (b == null || paper == null) Missing(nav) else CheckMasterScreen(
                blueprint = b, paper = paper, desk = desk, store = store,
                onExplain = { openTutor(it) },
                onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                topicOf = { key -> topicOf(repo, subjects, key) },
                profile = { StudentProfile.build(state, subjects, progress.history(), prefs, { noteTitles[it] }).summary() },
                language = prefs.language, studentName = state.firstName,
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(progress.history(), titleOf = { key ->
                val k = key.removePrefix(ProgressStore.PAPER_PREFIX)
                repo.module(k)?.title ?: blueprints.firstOrNull { it.id == k }?.title ?: k
            }, onBack = nav.back())
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
    if (offline) OfflineCover(onRetry = { retried++ })
    }
}

@Composable
private fun WithTabBarSpace(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBottomBarSpace provides GlassTabBarHeight, content = content)

/** Writes text to a private cache file and opens the share sheet for it. */
private fun shareText(context: android.content.Context, fileName: String, text: String) {
    val file = java.io.File(exportsDir(context), fileName).apply { writeText(text) }
    shareFile(context, file, "application/json", "Save your data")
}

private fun NavHostController.back(): () -> Unit = { popBackStack() }

@Composable
private fun Missing(nav: NavHostController) =
    PlaceholderScreen("Not found", "This item isn't available for your board and class.", onBack = { nav.popBackStack() })

/** Daily reminder choices, cycled by tapping the Profile row: 4, 6, 8 and 9 pm, or off. */
private val REMINDER_CHOICES = listOf(16, 18, 20, 21, -1)
private fun hourLabel(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"

private const val MY_PAPERS = "my_papers"

/** Every published question of the student's subjects (papers and section quizzes), for "Make my paper". */
private fun questionBank(repo: pk.parhlou.student.data.ParhlouRepository, subjects: List<pk.parhlou.student.data.Subject>,
                         blueprints: List<pk.parhlou.student.data.PaperBlueprint>): PaperPicker.Bank {
    val papers = blueprints.mapNotNull { repo.paper(it.id) }
    val modules = subjects.flatMap { s -> s.chapters.flatMap { c -> c.modules.map { it to c } } }
    val quiz = modules.flatMap { (m, _) -> repo.moduleQuiz(m.id, 1000) }
    val noteModule = subjects.flatMap { repo.notes(it.id) }.associate { it.id to it.moduleId }
    val chapterOfModule = modules.associate { (m, c) -> m.id to c }
    val titleOfModule = modules.associate { (m, c) -> m.id to "${c.word} ${c.number} · ${m.title}" }
    return PaperPicker.Bank(
        mcqs = (papers.flatMap { it.mcqs } + quiz).distinctBy { it.id },
        written = papers.flatMap { it.written }.distinctBy { it.id },
        unitOfNote = { key -> key?.let { chapterOfModule[noteModule[it] ?: it]?.number } },
        topicOfNote = { key -> key?.let { titleOfModule[noteModule[it] ?: it] }.orEmpty() },
    )
}

/** The topic (lesson section) and unit behind a question's note or module id, for the examiner's analysis. */
private fun topicOf(repo: pk.parhlou.student.data.ParhlouRepository, subjects: List<pk.parhlou.student.data.Subject>, key: String?): pk.parhlou.student.domain.TopicInfo? {
    if (key == null) return null
    val notes = subjects.flatMap { repo.notes(it.id) }
    val note = notes.firstOrNull { it.id == key }
    val moduleId = note?.moduleId ?: key
    val module = repo.module(moduleId) ?: return null
    val chapter = repo.chapterOf(moduleId)
    // The topic is the lesson section; its first note is where Ustaad starts when the student works on it.
    val noteId = note?.id ?: notes.firstOrNull { it.moduleId == moduleId }?.id ?: moduleId
    return pk.parhlou.student.domain.TopicInfo(moduleId, (chapter?.let { "${it.word} ${it.number} · " } ?: "") + module.title, chapter?.number, chapter?.title ?: "",
        noteId.takeIf { id -> notes.any { it.id == id } })
}
