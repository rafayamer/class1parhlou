package pk.parhlou.student.domain

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import java.io.File

/**
 * Checks a built course with the app's own offline marker before it is published:
 * every model answer earns full marks, a blank answer earns nothing, every lesson "your turn" accepts its answer,
 * every module has its own quiz, and every unit test reconciles.
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*ContentQualityTest' -PcourseBundle=/path/bundle.json
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentQualityTest {
    private val path = System.getProperty("parhlou.courseBundle")

    @Test fun builtCourseIsMarkedCorrectlyByTheApp() = runBlocking {
        assumeTrue("no course bundle given", path != null)
        val json = JSONObject(File(path!!).readText())
        val repo = ContentBundle.repository(json, 1, FakeRepository())
        val brain = DemoBrain(repo)
        val subject = repo.subject(json.getString("subjectId"))!!
        val problems = mutableListOf<String>()

        val written = json.getJSONArray("written").let { a -> List(a.length()) { ContentBundle.readWritten(a.getJSONObject(it)) } }
        for (q in written) {
            val full = brain.markWritten(q, q.modelAnswer.joinToString("\n"), 0)
            if (full.awarded != q.marks) problems += "${q.id}: model answer earns ${full.awarded}/${q.marks}"
            val blank = brain.markWritten(q, "", 0)
            if (blank.awarded != 0.0) problems += "${q.id}: blank answer earns ${blank.awarded}"
        }
        for (c in subject.chapters) for (m in c.modules) {
            if (repo.moduleQuiz(m.id).isEmpty()) problems += "${m.id}: no quiz"
            m.lesson?.yourTurn?.forEach { p ->
                if (!TextMatch.matchesAny(p.answer, p.accept)) problems += "${m.id}: your-turn answer not accepted: ${p.question.take(60)}"
            }
        }
        for (b in repo.blueprints(subject.id)) {
            BlueprintValidator.problems(b).forEach { problems += "${b.id}: $it" }
            val paper = repo.paper(b.id)
            if (paper == null) problems += "${b.id}: no questions" else if (paper.mcqs.size != b.sections[0].count) problems += "${b.id}: MCQ count"
        }
        println("Checked ${written.size} written questions, ${subject.chapters.sumOf { it.modules.size }} modules, ${repo.blueprints(subject.id).size} unit tests")
        problems.take(40).forEach(::println)
        assertTrue("${problems.size} problems", problems.isEmpty())
        assertEquals(json.getJSONObject("subject").getJSONArray("chapters").length(), subject.chapters.size)
    }
}
