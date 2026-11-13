package pk.parhlou.student.domain

import org.junit.Assume.assumeTrue
import org.junit.Test
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.SampleContent
import pk.parhlou.student.data.WrittenQuestion
import java.io.File

/**
 * Writes the sample content as JSON for the AI gateway (supabase/functions/ai/content/sample.json), so the
 * server holds the questions, keys and rubrics and the phone only sends ids.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*ContentExportTest' -PcontentExport=<file>
 */
class ContentExportTest {
    private fun s(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun arr(items: List<String>) = items.joinToString(",", "[", "]")
    private fun num(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    private fun written(q: WrittenQuestion) = "{" +
        "\"id\":${s(q.id)},\"text\":${s(q.text)},\"marks\":${num(q.marks)}," +
        "\"modelAnswer\":${arr(q.modelAnswer.map(::s))},\"followUp\":${s(q.followUp)},\"matching\":\"method\"," +
        "\"rubric\":" + arr(q.rubric.map { c ->
            "{\"id\":${s(c.id)},\"description\":${s(c.description)},\"marks\":${num(c.marks)},\"why\":${s(c.why)},\"how\":${s(c.how)}}"
        }) + "}"

    @Test fun export() {
        val path = System.getProperty("parhlou.contentExport")
        assumeTrue(path != null)
        val allWritten = SampleContent.chapterTest.written + SampleContent.moduleWritten.values.flatten()
        val mcqs = SampleContent.chapterTest.mcqs + FakeRepository.sampleMcqs
        val json = "{" +
            "\"course\":{\"board\":\"Punjab\",\"grade\":9,\"subject\":\"Mathematics\",\"edition\":\"Parhlou demo content, Unit 1 (not a textbook)\"}," +
            "\"subjectId\":${s(SampleContent.MATHS_9)}," +
            "\"notes\":" + arr(SampleContent.notes.map { n ->
                "{\"id\":${s(n.id)},\"title\":${s(n.title)},\"answer\":${s(n.answer)},\"explanation\":${s(n.explanation)}," +
                    "\"examples\":${arr(n.examples.map(::s))},\"steps\":${arr(n.steps.map(::s))},\"checkQuestion\":${s(n.checkQuestion)}}"
            }) + "," +
            "\"written\":" + arr(allWritten.map(::written)) + "," +
            "\"mcqs\":" + arr(mcqs.map { q ->
                "{\"id\":${s(q.id)},\"stem\":${s(q.stem)},\"options\":${arr(q.options.map(::s))}," +
                    "\"correctIndex\":${q.sampleCorrectIndex},\"rationales\":${arr(q.rationales.map(::s))}}"
            }) + "}"
        File(path!!).apply { parentFile?.mkdirs() }.writeText(json)
    }
}
