package pk.parhlou.student.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.data.SampleContent
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.ExamEventType
import pk.parhlou.student.domain.ExamLog
import pk.parhlou.student.domain.LearningState
import pk.parhlou.student.ui.theme.ParhlouTheme

/** End-to-end flows of the prototype: AI tutor, sitting a paper, Check Master and the invigilator report. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrototypeFlowTest {
    @get:Rule val rule = createComposeRule()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }
    private val paperId = SampleContent.CHAPTER_TEST

    @Before fun setUp() = runBlocking {
        store.clearAll()
        store.saveOnboarding(
            OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.TEST_SERIES, 5, 60, completed = true),
        )
        store.setReadinessPassed(true)
    }

    private fun launch() {
        rule.setContent { ParhlouTheme { ParhlouApp(FakeRepository(), store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun tutorAnswersRefusesAndChecks() {
        launch()
        rule.onNodeWithText("Ask Ustaad").performClick()

        rule.onNodeWithTag("tutor-input").performTextInput("How do I simplify root 50?")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { exists("largest perfect-square factor") }
        rule.onNodeWithText("Simplify √18.", substring = true).assertExists()

        // Answer the tutor's own check question.
        rule.onNodeWithTag("tutor-input").performTextInput("3 root 2")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { exists("Correct! √18") }

        rule.onNodeWithText("Show each step").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("Split it: √25 × √2 = 5√2.") }

        // Unknown topic: no invented answer, and no follow-up chips for it.
        rule.onNodeWithTag("tutor-input").performTextInput("Explain photosynthesis")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { exists("so I won't guess") }
        // Only my own earlier chat bubble carries that text now; the chip row is gone.
        assertEquals(1, rule.onAllNodes(hasText("Show each step")).fetchSemanticsNodes().size)
    }

    @Test
    fun scannedPagesAreMatchedToQuestionsBeforeHandIn() {
        // Two pages already scanned (the camera can't run here); the scanner opens on its review.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scan = pk.parhlou.scanner.PageStore(java.io.File(ctx.filesDir, "exam/$paperId/_scan")).apply { clear() }
        repeat(2) { n ->
            val f = java.io.File(scan.photoDir(), "p$n.jpg")
            f.outputStream().use { android.graphics.Bitmap.createBitmap(30, 40, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            scan.add(pk.parhlou.scanner.Photo(f, 30, 40, null))
        }
        launch()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        openChapterTest()
        rule.onNodeWithText("Start in the app").performScrollTo().performClick()
        rule.waitUntil(15_000) { exists("Invigilator on") }
        rule.onNodeWithText("2 pages scanned · 2 not matched to a question yet.").performScrollTo().assertExists()

        // Hand-in warns about unmatched pages and offers to match them.
        rule.onNodeWithText("Hand in paper").performScrollTo().performClick()
        rule.onNodeWithText("Some pages aren't matched").assertExists()
        rule.onNodeWithText("Match pages").performClick()
        rule.waitUntil(15_000) { exists("Your pages (2)") }
        rule.onNodeWithText("2 pages aren't matched", substring = true).assertExists()
        // Without OpenCV (as here) the pages can't be processed; they are kept as photographed, not stuck "Preparing".
        rule.waitUntil(10_000) { !exists("Preparing") }

        rule.onAllNodes(hasText("Choose question"))[0].performClick()
        rule.onNodeWithText("Q2 (i)").performClick()
        rule.onNodeWithTag("scan-pages").performScrollToIndex(1)
        rule.onAllNodes(hasText("Choose question"))[0].performClick()
        rule.onNodeWithText("Q2 (i)").performClick()
        assertEquals(2, rule.onAllNodes(hasText("Answer to Q2 (i)")).fetchSemanticsNodes().size)
        assertTrue(!exists("aren't matched"))
        rule.onNodeWithContentDescription("Move page 2 up").performClick()
        rule.onNodeWithText("Done").performClick()

        // Back in the paper, the timer kept running and both pages count as photos of Q2 (i).
        rule.waitUntil(15_000) { exists("2 pages scanned and matched to questions.") }
        assertTrue(exists("2 page photos attached"))
        val log = runBlocking { store.examLog(paperId).first() }
        assertTrue("scanning must not count as leaving the paper", ExamLog.parse(log).none { it.type == ExamEventType.LEFT })
    }

    @Test
    fun sitPaperThenCheckMasterAndInvigilatorReport() {
        launch()
        rule.onNodeWithText("Continue").performScrollTo().performClick() // Test Series: Continue opens the papers
        openChapterTest()
        rule.onNodeWithText("Start in the app").performScrollTo().performClick()
        rule.waitUntil(40_000) { exists("Invigilator on") }

        // Two MCQs (one right, one wrong), one full written answer, one partial, one unrecognised.
        rule.onNodeWithTag("mcq-p1-3").performScrollTo().performClick()
        rule.onNodeWithTag("mcq-p2-0").performScrollTo().performClick()
        rule.onNodeWithTag("answer-s1").performScrollTo().performTextInput("0.45 = 45/100 = 9/20, both integers, so it is rational")
        rule.onNodeWithTag("answer-s7").performScrollTo().performTextInput("root 98 = 7 root 2, final answer 6 root 2")
        rule.onNodeWithTag("answer-s3").performScrollTo().performTextInput("about a third I think")
        rule.waitForIdle()

        rule.onNodeWithText("Hand in paper").performScrollTo().performClick()
        rule.waitUntil(20_000) { exists("Confirmed marks") }

        // 1 MCQ + 3 (s1) + 2 (s7) confirmed; 3 marks of s3 not checked, never counted as wrong.
        rule.onNodeWithTag("confirmed-marks").assertTextContains("6 / 40")
        rule.onNodeWithText("3 more marks not checked yet").assertExists()
        rule.onNodeWithText("Marked by Ustaad", substring = true).assertExists()
        rule.onNodeWithText("Invigilator report").assertExists()
        rule.onNodeWithText("Left the paper").assertExists()
        rule.onNodeWithText("On time").assertExists()
        assertTrue(exists("Couldn't check automatically"))
        assertTrue(exists("What: this step is missing from your answer."))

        // The log is closed and evidence is saved as demo-checked, not verified.
        val log = runBlocking { store.examLog(paperId).first() }
        assertTrue(ExamLog.isSubmitted(log))
        assertEquals(ExamEventType.STARTED, ExamLog.parse(log).first().type)
        val saved = ProgressStore(store).also { runBlocking { it.load() } }
        assertEquals(LearningState.DEMO_CHECKED, saved.state("m-1-1"))
        assertEquals(0, saved.assessedCount(listOf("m-1-1", "m-1-2")))
        assertEquals(1, saved.papersSubmitted)
        assertEquals(null, runBlocking { store.activeExam.first() })
    }

    @Test
    fun recheckAfterRetypingKeepsTheFirstResult() {
        runBlocking {
            store.appendExamEvent(paperId, ExamEventType.STARTED, 1_000)
            store.saveDraft("exam_${paperId}_s3", "about a third I think")
            store.appendExamEvent(paperId, ExamEventType.SUBMITTED, 600_000)
            store.saveFirstResult(paperId, mapOf("s3" to 0.0, "mcq" to 0.0, "total" to 0.0))
        }
        launch()
        rule.onNodeWithText("Continue").performScrollTo().performClick() // Test Series: Continue opens the papers
        openChapterTest()
        rule.onNodeWithText("See your result").performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("Confirmed marks") }
        rule.onNodeWithTag("confirmed-marks").assertTextContains("0 / 40")

        rule.onNodeWithText("Re-check this question").performScrollTo().performClick()
        rule.onNodeWithTag("recheck-s3").performScrollTo()
            .performTextReplacement("x = 0.3636, 100x = 36.3636, 99x = 36, x = 36/99 = 4/11")
        rule.onNodeWithText("Check again").performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("First check: 0 / 3. Re-checked now: 3 / 3.") }
        rule.onNodeWithTag("confirmed-marks").assertTextContains("3 / 40")
        rule.onNodeWithText("At your first hand-in: 0 / 40").assertExists()
    }

    @Test
    fun tutorIsLockedWhileAPaperIsRunning() {
        runBlocking { store.appendExamEvent(paperId, ExamEventType.STARTED, System.currentTimeMillis()) }
        launch()
        rule.onNodeWithText("Ask Ustaad").performClick()
        rule.onNodeWithText("Ask Ustaad is locked").assertExists()
        rule.onNodeWithTag("tutor-input").assertDoesNotExist()
    }

    @Test
    fun progressSurvivesARestart() {
        runBlocking { store.appendProgress("5|m-1-1|Q:90\n6|m-1-1|W") }
        val restored = ProgressStore(store).also { runBlocking { it.load() } }
        assertEquals(listOf(Evidence.QuizCompleted(90), Evidence.WrittenSubmitted), restored.evidence("m-1-1"))
        assertEquals(LearningState.READY_FOR_ASSESSMENT, restored.state("m-1-1"))
        assertEquals(1, restored.quizzesCompleted())
    }

    /** Papers → Chapter → the unit's chapter test. */
    private fun openChapterTest() {
        rule.waitUntil(3_000) { exists("Practice test") }
        rule.onNodeWithTag("scope-chapter").performScrollTo().performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("scope-picker")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("paper-$paperId").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Before you start") }
    }

    /** A student starts a paper on paper, finds Ask Ustaad locked, quits the paper there: no try used, Ustaad opens. */
    @Test
    fun quittingAPaperUnlocksUstaadAndUsesNoTry() {
        launch()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        openChapterTest()
        rule.onNodeWithTag("start-on-paper").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { store.activeExam.first() } == paperId }
        // Back to Home, then Ask Ustaad: locked, with a way back to the paper and a way out.
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("printed-clock")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { exists("Before you start") }
        rule.onNodeWithTag("paper-running").assertExists()
        rule.onNodeWithTag("quit-paper").performScrollTo().performClick()
        rule.onNodeWithTag("quit-confirm").performClick()
        rule.waitUntil(5_000) { runBlocking { store.activeExam.first() } == null }
        rule.waitUntil(5_000) { exists("Assalam-o-Alaikum") }
        rule.onNodeWithText("Ask Ustaad is locked").assertDoesNotExist()
        assertEquals("", runBlocking { store.examLog(paperId).first() })
    }

    /** From the locked Ask Ustaad screen, the paper can be quit too. */
    @Test
    fun lockedUstaadOffersToQuitThePaper() = runBlocking<Unit> {
        store.appendExamEvent(paperId, pk.parhlou.student.domain.ExamEventType.STARTED, System.currentTimeMillis())
        launch()
        rule.onNodeWithText("Ask Ustaad").performClick()
        rule.waitUntil(5_000) { exists("Ask Ustaad is locked") }
        rule.onNodeWithText("Back to my paper").assertExists()
        rule.onNodeWithTag("quit-paper").performScrollTo().performClick()
        rule.onNodeWithTag("quit-confirm").performClick()
        rule.waitUntil(5_000) { exists("Assalam-o-Alaikum") }
        assertEquals(null, store.activeExam.first())
    }
}
