package pk.parhlou.student.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.Chapter
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.SolutionKind
import pk.parhlou.student.data.Subject
import pk.parhlou.student.data.ExamKit
import pk.parhlou.student.data.KitDefinition
import pk.parhlou.student.data.KitFormula
import pk.parhlou.student.data.KitImportant
import pk.parhlou.student.data.KitShortTheory
import pk.parhlou.student.ui.screens.ExamKitScreen
import pk.parhlou.student.ui.screens.SolutionDetailScreen
import pk.parhlou.student.ui.screens.SolutionsFinderScreen
import pk.parhlou.student.ui.screens.solutionGroup
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Browse and search every textbook question, then open its step-by-step solution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class SolutionsFinderTest {
    @get:Rule val rule = createComposeRule()

    private fun sol(kind: SolutionKind, source: String, question: String, answer: String, checked: Boolean = true) =
        BookSolution(kind, source, question, listOf("Write the given values.", "Simplify: $answer"), answer, "", listOf(listOf(answer)), checked)

    private val module = Module(
        id = "u2-1", position = 1, title = "Logarithms", video = null, bookReference = null, readingTasks = emptyList(),
        writtenQuestions = emptyList(), status = ReviewStatus.PUBLISHED,
        solutions = listOf(
            sol(SolutionKind.EXAMPLE, "Example 1", "Find log 100.", "2"),
            sol(SolutionKind.EXERCISE, "Ex 2.3 Q4(i)", "Evaluate log 1000.", "3"),
            sol(SolutionKind.EXERCISE, "Ex 2.3 Q5", "Write 2^5 = 32 in logarithmic form.", "log_2 32 = 5"),
            sol(SolutionKind.EXERCISE, "Review Ex 2 Q1", "Choose the correct answer.", "(b)", checked = false),
        ),
    )
    private val subjects = listOf(
        Subject("s", "Mathematics", 9, "2025", true, listOf(Chapter("u2", 2, "Logarithms", listOf(module), ReviewStatus.PUBLISHED))),
    )

    @Test fun groupsFollowTheBook() {
        assertEquals("Exercise 2.3", solutionGroup(module.solutions[1]))
        assertEquals("Review exercise 2", solutionGroup(module.solutions[3]))
        assertEquals("Worked examples", solutionGroup(module.solutions[0]))
    }

    @Test fun browseUnitExerciseQuestion() {
        var opened: Pair<String, Int>? = null
        rule.setContent { ParhlouTheme { SolutionsFinderScreen(subjects, onOpen = { m, i -> opened = m to i }, onBack = {}) } }
        rule.onNodeWithText("Unit 2: Logarithms").performClick()
        rule.onNodeWithText("Exercise 2.3").performClick()
        rule.onNodeWithText("Evaluate log 1000.").performClick()
        assertEquals("u2-1" to 1, opened)
    }

    @Test fun searchByExerciseNumberOrWords() {
        var opened: Pair<String, Int>? = null
        rule.setContent { ParhlouTheme { SolutionsFinderScreen(subjects, onOpen = { m, i -> opened = m to i }, onBack = {}) } }
        rule.onNodeWithTag("solution-search").performTextInput("2.3 Q5")
        rule.onNodeWithText("1 found").assertExists()
        rule.onNodeWithText("Write 2^5 = 32 in logarithmic form.").performClick()
        assertEquals("u2-1" to 2, opened)
    }

    @Test fun searchByQuestionWords() {
        rule.setContent { ParhlouTheme { SolutionsFinderScreen(subjects, onOpen = { _, _ -> }, onBack = {}) } }
        rule.onNodeWithTag("solution-search").performTextInput("evaluate 1000")
        rule.onNodeWithText("Evaluate log 1000.").assertExists()
        rule.onAllNodesWithText("Write 2^5 = 32 in logarithmic form.").assertCountEquals(0)
    }

    @Test fun examKitOpensImportantQuestionsAndShowsExclusions() {
        val kit = ExamKit(
            inExam = true, examLabel = "2027 exam", excluded = listOf("Ex 2.4"),
            definitions = listOf(KitDefinition("Logarithm", "If aˣ = y then x = logₐ y.")),
            formulas = listOf(KitFormula("log(mn) = log m + log n", "product law")),
            important = listOf(KitImportant("Ex 2.3 Q5", "Standard short question")),
            fullMarks = listOf("Write the formula first."), mistakes = listOf("Forgetting the characteristic."),
            shortTheory = listOf(KitShortTheory("Define logarithm.", "If aˣ = y then x = logₐ y.")),
        )
        val chapter = subjects[0].chapters[0].copy(kit = kit)
        var opened: Pair<String, Int>? = null
        rule.setContent { ParhlouTheme { ExamKitScreen(chapter, onOpenSolution = { m, i -> opened = m to i }, onBack = {}) } }
        rule.onNodeWithText("Left out of the 2027 exam").assertExists()
        rule.onNodeWithText("Learn word for word").assertExists()
        rule.onNodeWithText("Write 2^5 = 32 in logarithmic form.").performScrollTo().performClick()
        assertEquals("u2-1" to 2, opened)
        rule.onNodeWithText("Show answer").performScrollTo().performClick()
        rule.onAllNodesWithText("If aˣ = y then x = logₐ y.").assertCountEquals(2)
    }

    @Test fun detailRevealsStepsAndAsksTutor() {
        var asked: String? = null
        rule.setContent { ParhlouTheme { SolutionDetailScreen(module, 1, onAskTutor = { asked = it }, onBack = {}) } }
        rule.onNodeWithText("Show first step").performScrollTo().performClick()
        rule.onAllNodesWithText("Write the given values.", substring = true).onFirst().assertExists()
        rule.onNodeWithText("Ask the tutor about this step").performScrollTo().performClick()
        assertEquals(true, asked?.contains("Write the given values."))
    }
}
