package pk.parhlou.student.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pk.parhlou.scanner.Photo
import pk.parhlou.student.ai.CriterionMark
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.ExamDesk
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.PaperAttempts
import pk.parhlou.student.domain.ScriptPage
import pk.parhlou.student.domain.ScriptSegment
import pk.parhlou.student.ui.screens.PrintedPaperScreen
import pk.parhlou.student.ui.theme.ParhlouTheme
import java.io.File

/** The whole "written on paper" path: scanned pages in any order → read → checked by the student → double-marked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class PrintedPaperFlowTest {
    @get:Rule val rule = createComposeRule()

    private val repo = FakeRepository()
    private val blueprint: pk.parhlou.student.data.PaperBlueprint = repo.blueprints(FakeRepository.MATHS_9).first { b -> repo.paper(b.id) != null }
    private val paper: pk.parhlou.student.data.PaperContent = repo.paper(blueprint.id)!!
    private val ordered: List<WrittenQuestion> = paper.written.sortedWith(compareBy<WrittenQuestion>({ it.section }, { it.number }))
    private val first: WrittenQuestion = ordered[0]
    private val second: WrittenQuestion = ordered[1]
    private val handwritten = mutableListOf<Pair<String, String>>()
    private val readPages = mutableListOf<Int>()

    /** Reads page 1 as the second answer plus a piece with no number, and page 2 as the first answer and the bubble sheet. */
    private val ai = object : ParhlouAi by DemoBrain(repo) {
        override val readsPhotos = true
        override suspend fun readScriptPage(image: String, page: Int, questions: List<Pair<String, String>>, mcqCount: Int): ScriptPage {
            readPages += page
            return if (page == 1) ScriptPage(1, "ANSWERS", "CLEAR", listOf(
                ScriptSegment("2 ii)", second.id, false, false, false, "CLEAR", "second answer"),
                ScriptSegment("", null, false, false, false, "CLEAR", "loose working")), emptyMap(), "")
            else ScriptPage(2, "ANSWERS", "CLEAR", listOf(ScriptSegment("Q2 (i)", first.id, false, false, false, "CLEAR", "first answer")),
                mapOf(1 to "A", 2 to "MULTIPLE"), "")
        }
        override suspend fun markHandwritten(question: WrittenQuestion, answer: String, images: List<String>, paperId: String?): WrittenMark {
            handwritten += question.id to answer
            assertTrue("the pages go to the examiners", images.isNotEmpty())
            return WrittenMark(question.id, MarkStatus.CHECKED, question.rubric.map {
                CriterionMark(it.id, it.description, it.marks, it.marks, CriterionStatus.MET, it.why, it.how, "saw it")
            }, question.followUp, examiners = 2)
        }
    }

    @Test fun aScriptWrittenOnPaperIsReadCheckedAndMarked() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(context)
        val progress = ProgressStore()
        val desk = ExamDesk(context, repo, store, ai, progress)
        kotlinx.coroutines.runBlocking { desk.reset(paper) }
        repeat(2) { i ->
            val f = File(desk.printedStore(blueprint.id).photoDir(), "p$i.jpg")
            f.outputStream().use { Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
            desk.printedStore(blueprint.id).add(Photo(f, 300, 400, null))
        }
        var marked = false
        rule.setContent {
            ParhlouTheme {
                PrintedPaperScreen(blueprint, paper, desk, store, androidx.compose.runtime.rememberCoroutineScope(), readsHandwriting = true,
                    onMarked = { marked = true }, onBack = {})
            }
        }
        rule.onNodeWithText("2 pages scanned").assertExists()
        rule.onNodeWithText("Read my pages").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("What Ustaad read")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(1, 2), readPages)
        // Answers are under the right questions, whatever order they were written in.
        rule.onNodeWithTag("read-${first.id}").performScrollTo()
        rule.onNodeWithText("Q2 (i) first answer").assertExists()
        rule.onNodeWithText("MCQ 2: more than one circle", substring = true).assertExists()
        rule.onNodeWithText("Bubble sheet: 1 of ${paper.mcqs.size} MCQs read").assertExists()
        // The student places the piece with no question number.
        rule.onNodeWithTag("loose-1-1").performScrollTo()
        rule.onNodeWithText("Rough work, ignore").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("loose-1-1")).fetchSemanticsNodes().isEmpty() }

        rule.onNodeWithText("Mark my paper").performScrollTo().performClick()
        // The hand-back runs on the main looper, which Robolectric only runs when asked.
        rule.waitUntil(20_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); marked }
        assertEquals(setOf(first.id to "Q2 (i) first answer", second.id to "2 ii) second answer"), handwritten.toSet())
        // It is one of the paper's three attempts, and the result is kept so the result screen doesn't mark it again.
        assertEquals(1, PaperAttempts.used(progress.history(), blueprint.id))
        val (_, result) = desk.handedIn(blueprint.id)!!
        assertEquals(1, result.mcq.count { it.correct } + result.mcq.count { it.chosen != null && !it.correct })
        assertEquals(2, result.written.count { it.mark.examiners == 2 })
    }

    /** "Start on paper": the clock and lock start, the start screen leads to scanning and handing in, and time up keeps scanning open briefly. */
    @Test fun startingOnPaperRunsTheClockAndKeepsHandInReachable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(context)
        val desk = ExamDesk(context, repo, store, ai, ProgressStore())
        kotlinx.coroutines.runBlocking { desk.reset(paper); desk.startOnPaper(blueprint.id) }
        assertEquals(blueprint.id, kotlinx.coroutines.runBlocking { store.activeExam.first() })
        var scan = false
        rule.setContent {
            ParhlouTheme {
                val log = store.examLog(blueprint.id).collectAsStateValue()
                pk.parhlou.student.ui.screens.AssessmentStartScreen(blueprint, log, onStart = {}, onResult = {}, onReset = {}, onBack = {},
                    onPrint = {}, onWrittenOnPaper = { scan = true }, onStartOnPaper = {}, onPaper = true)
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("paper-running")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("left", substring = true).assertExists()
        rule.onNodeWithTag("continue-on-paper").performScrollTo().performClick()
        assertTrue(scan)
    }
}

@androidx.compose.runtime.Composable
private fun kotlinx.coroutines.flow.Flow<String>.collectAsStateValue(): String =
    collectAsState(initial = "").value
