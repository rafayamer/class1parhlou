package pk.parhlou.student.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.BookLink
import pk.parhlou.student.ai.LessonVideo
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.TutorReply
import pk.parhlou.student.ai.TutorTurn
import pk.parhlou.student.ai.UstaadAnswer
import pk.parhlou.student.ai.UstaadMessage
import pk.parhlou.student.ai.UstaadRequest
import pk.parhlou.student.ai.UstaadStep
import pk.parhlou.student.ai.WrittenMark
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.SolutionKind
import pk.parhlou.student.data.UstaadPrefs
import pk.parhlou.student.data.WrittenQuestion
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.LoggedEvidence
import pk.parhlou.student.domain.StudentProfile
import pk.parhlou.student.domain.TOPIC_PREFIX
import pk.parhlou.student.ui.screens.MyUstaadScreen
import pk.parhlou.student.ui.screens.TutorScreen
import pk.parhlou.student.ui.screens.UstaadChat
import pk.parhlou.student.ui.screens.UstaadChats
import pk.parhlou.student.ui.theme.ParhlouTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class AskUstaadTest {
    @get:Rule val rule = createComposeRule()

    private val module = Module("m-2-3", 3, "2.3 Common Logarithm", null, null, emptyList(), emptyList(), ReviewStatus.APPROVED,
        solutions = listOf(BookSolution(SolutionKind.EXERCISE, "Ex 2.3 Q4(v)", "Find x: log x = 4.3561", listOf("x = antilog 4.3561"), "x = 22700", "",
            emptyList(), true, bookAnswer = "x = 2270")))

    private class FakeAi(val answers: List<UstaadAnswer>) : ParhlouAi {
        val requests = mutableListOf<UstaadRequest>()
        override val label = "Test AI"
        override suspend fun tutor(turn: TutorTurn) = error("not used")
        override suspend fun mcqFeedback(question: McqQuestion, chosen: Int, subjectIds: Set<String>): TutorReply = error("not used")
        override suspend fun markWritten(question: WrittenQuestion, answer: String, photos: Int, images: List<String>, paperId: String?): WrittenMark = error("not used")
        override suspend fun ustaad(req: UstaadRequest): UstaadAnswer { requests += req; return answers[minOf(requests.size - 1, answers.lastIndex)] }
    }

    private val solution = UstaadAnswer("SOLUTION", "Let's solve it together!", questionAsRead = "Find x: log x = 4.3561",
        topic = "Unit 2 · 2.3 Common Logarithm", steps = listOf(UstaadStep("x = antilog 4.3561", "antilog undoes log")),
        finalAnswer = "x = 22700", concept = "Characteristic gives the power of ten.", examTip = "Write antilog clearly.",
        checkQuestion = "What is antilog 2?", suggestions = listOf("Explain more simply", "Give me a similar question"),
        book = BookLink("m-2-3", 0, "Ex 2.3 Q4(v)", "Unit 2 · 2.3 Common Logarithm"),
        videos = listOf(LessonVideo("abc123XYZ_-", "Antilog made easy", "Maths Teacher", "")))

    @Test fun bookQuestionGetsTheCheckedCardVideosAndFollowUps() {
        val ai = FakeAi(listOf(solution, UstaadAnswer("CHAT", "Sure, here is a simpler way.")))
        var opened: Pair<String, Int>? = null
        rule.setContent { ParhlouTheme { TutorScreen(ai, setOf("pb-9-math"), null, examActive = false, onBack = {},
            profile = { "Name: Ayesha." }, moduleOf = { if (it == "m-2-3") module else null }, onOpenSolution = { m, i -> opened = m to i }) } }
        rule.onNodeWithTag("tutor-input").performTextInput("log x = 4.3561, find x")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("ustaad-solution")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("From your textbook · Ex 2.3 Q4(v) · checked").assertExists()
        rule.onNodeWithText("Book answer: x = 2270").assertExists()
        rule.onNodeWithTag("video-abc123XYZ_-").performScrollTo().assertExists()
        rule.onNodeWithText("Open the checked textbook solution").performScrollTo().performClick()
        assertEquals("m-2-3" to 0, opened)
        assertEquals("Name: Ayesha.", ai.requests[0].profile)
        // A follow-up carries the conversation so far.
        rule.onNodeWithText("Explain more simply").performScrollTo().performClick()
        rule.waitUntil(5_000) { ai.requests.size == 2 }
        assertEquals(2, ai.requests[1].history.size)
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Sure, here is a simpler way.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun answersWithoutVideosOfferAYouTubeSearch() {
        val ai = FakeAi(listOf(UstaadAnswer("CHAT", "Sets are collections.", videoQuery = "class 9 maths sets")))
        rule.setContent { ParhlouTheme { TutorScreen(ai, setOf("pb-9-math"), null, examActive = false, onBack = {}) } }
        rule.onNodeWithTag("tutor-input").performTextInput("What is a set?")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("video-search")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun chatsAndAnswersSurviveSaving() {
        val chat = UstaadChat("c1", "log question", 5, listOf(UstaadMessage("student", "find x"), UstaadMessage("ustaad", solution.reply, answer = solution)))
        val back = UstaadChats.parse(UstaadChats.encode(listOf(chat))).single()
        assertEquals(solution, back.messages[1].answer)
        assertEquals("find x", back.messages[0].text)
    }

    @Test fun profileKnowsTheStudentAndCanForget() {
        val now = 1_800_000_000_000L
        val h = listOf(LoggedEvidence(now - 1000, TOPIC_PREFIX + "n-log", Evidence.TopicMissed), LoggedEvidence(now - 900, TOPIC_PREFIX + "n-log", Evidence.TopicMissed))
        val prefs = UstaadPrefs("Urdu", UstaadPrefs.STYLES[1], 70)
        val onboarding = OnboardingState(true, "PB", 9, "English", setOf("pb-9-math"), null, 5, 30, completed = true, name = "Ayesha")
        val p = StudentProfile.build(onboarding, emptyList(), h, prefs, { if (it == "n-log") "Common logarithm" else null }, now)
        val s = p.summary()
        assertTrue(s, s.contains("Ayesha") && s.contains("Common logarithm") && s.contains("Urdu") && s.contains("70 out of 75"))
        assertTrue(s.length <= 1200)
        val fresh = StudentProfile.build(onboarding, emptyList(), h, prefs.copy(forgetBefore = now), { "Common logarithm" }, now)
        assertTrue(fresh.weak.isEmpty())
    }

    @Test fun myUstaadEditsPreferences() {
        var saved: UstaadPrefs? = null
        val profile = StudentProfile("Ayesha", "Class 9", null, null, 2, 3, emptyList(), emptyList(), UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0))
        rule.setContent { ParhlouTheme { MyUstaadScreen(profile, onChange = { saved = it }, onBack = {}) } }
        rule.onNodeWithTag("lang-English with Roman Urdu explanations").performClick()
        assertEquals("English with Roman Urdu explanations", saved?.language)
    }

    @Test fun lockedDuringAPaper() {
        rule.setContent { ParhlouTheme { TutorScreen(FakeAi(listOf(solution)), setOf("pb-9-math"), null, examActive = true, onBack = {}) } }
        rule.onNodeWithText("Ask Ustaad is locked").assertExists()
    }
}
