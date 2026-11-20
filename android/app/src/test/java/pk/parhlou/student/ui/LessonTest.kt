package pk.parhlou.student.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Tutor lessons and per-module quizzes from a content bundle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class LessonTest {
    @get:Rule val rule = createComposeRule()
    private val packed = FakeRepository()
    private val store by lazy { LocalStore(ApplicationProvider.getApplicationContext()) }

    private fun bundle(): JSONObject {
        val b = ContentBundle.export(packed, FakeRepository.MATHS_9, "test edition")
        // Split the quiz between the two modules, and give module 1 a tutor lesson.
        val quiz = b.getJSONArray("quiz")
        for (i in 0 until quiz.length()) quiz.getJSONObject(i).put("moduleId", if (i < 6) "m-1-1" else "m-1-2")
        val lesson = JSONObject()
            .put("recap", JSONArray().put(JSONObject().put("question", "What is 2 + 2?").put("answer", "4")))
            .put("explain", JSONArray()
                .put(JSONObject().put("title", "What a rational number is").put("body", "A number you can write as p/q with q ≠ 0."))
                .put(JSONObject().put("title", "What an irrational number is").put("body", "Its decimal never ends and never repeats.")))
            .put("example", JSONObject().put("question", "Is 0.25 rational?")
                .put("steps", JSONArray()
                    .put(JSONObject().put("text", "0.25 = 25/100").put("why", "Two decimal places means hundredths."))
                    .put(JSONObject().put("text", "25/100 = 1/4").put("why", "Divide top and bottom by 25.")))
                .put("answer", "Yes, 0.25 = 1/4 is rational."))
            .put("yourTurn", JSONArray().put(JSONObject().put("question", "Write 0.75 as p/q in lowest terms.").put("answer", "3/4")
                .put("accept", JSONArray().put(JSONArray().put("3/4")))
                .put("hints", JSONArray().put("Start with 75/100."))
                .put("solution", JSONArray().put("0.75 = 75/100").put("75/100 = 3/4"))))
            .put("examTips", JSONArray().put("Always end with 'so it is rational'."))
            .put("mistakes", JSONArray().put("Forgetting to reduce to lowest terms."))
        b.getJSONObject("subject").getJSONArray("chapters").getJSONObject(0).getJSONArray("modules").getJSONObject(0).put("lesson", lesson)
        return b
    }

    @Before fun setUp() = runBlocking {
        store.clearAll()
        store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
    }

    @Test fun eachModuleGetsItsOwnQuizAndLessonIsRead() {
        val repo = ContentBundle.repository(bundle(), 1, packed)
        val q1 = repo.moduleQuiz("m-1-1").map { it.id }
        val q2 = repo.moduleQuiz("m-1-2").map { it.id }
        assertEquals(6, q1.size)
        assertTrue(q2.isNotEmpty())
        assertTrue(q1.none { it in q2 })
        val lesson = repo.module("m-1-1")!!.lesson!!
        assertEquals(2, lesson.explain.size)
        assertEquals(2, lesson.example!!.steps.size)
        assertEquals(listOf(listOf("3/4")), lesson.yourTurn.single().accept)
        assertEquals(null, repo.module("m-1-2")!!.lesson)
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun aLessonIsTaughtStepByStepLikeATutor() {
        val repo = ContentBundle.repository(bundle(), 1, packed)
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store) } }
        rule.waitUntil(5_000) { exists("Do this now") }
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.waitUntil(3_000) { exists("Quick recap") }

        rule.onNodeWithText("Show answer").performClick()
        rule.onNodeWithTag("player-progress").assertExists()
        rule.onNodeWithText("Start the lesson").performClick()
        rule.onNodeWithText("Idea 1 of 2").assertExists()
        rule.onNodeWithText("Next idea").performClick()
        rule.onNodeWithText("What an irrational number is").assertExists()
        rule.onNodeWithText("Show me an example").performClick()

        // The worked example is revealed one step at a time, each with its reason.
        rule.onNodeWithText("0.25 = 25/100").assertExists()
        assertTrue(!exists("25/100 = 1/4"))
        rule.onNodeWithText("Why?").performScrollTo().performClick()
        rule.onNodeWithText("Two decimal places means hundredths.").assertExists()
        rule.onNodeWithText("Next step").performClick()
        rule.onNodeWithText("25/100 = 1/4").assertExists()
        rule.onNodeWithText("Now your turn").performClick()

        // A wrong answer gets a hint first; the right one is accepted.
        rule.onNodeWithTag("your-turn-answer").performTextInput("75/100")
        rule.onNodeWithTag("lesson-check").performClick()
        rule.onNodeWithText("Not yet. Hint: Start with 75/100.").assertExists()
        rule.onNodeWithTag("your-turn-answer").performTextReplacement("3/4")
        rule.onNodeWithTag("lesson-check").performClick()
        rule.onNodeWithText("Correct!").assertExists()
        rule.onNodeWithText("Exam tips").performClick()
        rule.onNodeWithText("Forgetting to reduce to lowest terms.", substring = true).assertExists()
        rule.onNodeWithTag("lesson-finish").performClick()

        // The lesson ends with XP and goes straight on to practice, in the same player.
        rule.waitUntil(3_000) { exists("Lesson complete") }
        rule.onNodeWithTag("lesson-practice").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("session-check")).fetchSemanticsNodes().isNotEmpty() }
        // Saved to the phone in the background.
        rule.waitUntil(5_000) { runBlocking { store.progressLog() }.contains("|m-1-1|L") }
    }
}
