package pk.parhlou.student.ui

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.GatewayTransport
import pk.parhlou.student.ai.LunaGateway
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.ReplyKind
import pk.parhlou.student.ai.TutorTurn
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.SampleContent

/** The app side of the AI gateway, against a fake server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LunaGatewayTest {
    private val repo = FakeRepository()
    private val q = SampleContent.chapterTest.written.first { it.id == "s7" }
    private val sent = mutableListOf<Pair<Map<String, String>, JSONObject>>()

    private fun gateway(status: Int, reply: String?): LunaGateway {
        val transport: GatewayTransport = { _, headers, body ->
            sent += headers to JSONObject(body)
            reply?.let { status to it }
        }
        return LunaGateway("https://example.test/ai", "anon-key", { "install-1234567890abcdef" }, DemoBrain(repo), transport = transport)
    }

    private fun markReply(vararg statuses: String, ids: List<String> = q.rubric.map { it.id }) =
        """{"result":{"questionId":"s7","status":"CHECKED","correction":"","followUp":"x","criteria":[""" +
            ids.mapIndexed { i, id -> """{"criterionId":"$id","status":"${statuses[i]}","awarded":99,"max":99}""" }.joinToString(",") + "]}}"

    @Test fun sendsOnlyIdsAndTheStudentsText() = runBlocking {
        val mark = gateway(200, markReply("MET", "NOT_FOUND", "MET")).markWritten(q, "my working", 0)
        val (headers, body) = sent.single()
        assertEquals(setOf("task", "questionId", "answer", "images"), body.keys().asSequence().toSet())
        assertFalse(body.toString().contains("7√2"))                 // no model answer or rubric leaves the phone
        assertEquals("install-1234567890abcdef", headers["x-parhlou-install"])
        assertFalse(headers.values.any { it.startsWith("sk-") })
        // Marks come from the app's rubric, not the reply's inflated numbers.
        assertEquals(2.0, mark.awarded, 0.0)
        assertEquals(MarkStatus.CHECKED, mark.status)
    }

    @Test fun invalidOrMissingReplyFallsBackToTheDemoChecker() = runBlocking {
        val model = q.modelAnswer.joinToString("\n")
        val wrongIds = gateway(200, markReply("MET", "MET", "MET", ids = listOf("a", "zz", "c"))).markWritten(q, model, 0)
        assertEquals(3.0, wrongIds.awarded, 0.0)                       // demo checker marked the model answer itself
        assertEquals(3.0, gateway(500, "{}").markWritten(q, model, 0).awarded, 0.0)
        assertEquals(3.0, gateway(200, null).markWritten(q, model, 0).awarded, 0.0)
        assertEquals(3.0, gateway(200, "not json").markWritten(q, model, 0).awarded, 0.0)
    }

    @Test fun tutorReplyIsParsedAndOfflineIsLabelled() = runBlocking {
        val turn = TutorTurn("How do I simplify root 50?", setOf(FakeRepository.MATHS_9))
        val live = gateway(200, """{"result":{"kind":"ANSWER","answer":"√50 = 5√2.","explanation":"Take out 25.","steps":["a","b"],"noteId":"n-surds","checkQuestion":"Simplify √18."}}""").tutor(turn)
        assertEquals(ReplyKind.ANSWER, live.kind)
        assertEquals(listOf("a", "b"), live.steps)
        assertEquals("n-surds", live.noteId)
        assertEquals(pk.parhlou.student.ai.LunaGateway.SOURCE, live.source)

        val offline = gateway(200, null).tutor(turn)
        assertEquals("n-surds", offline.noteId)
        assertEquals("Answered offline from your course notes", offline.source)
    }

    @Test fun photosAreSentOnlyToTheRealAi() = runBlocking {
        val g = gateway(200, markReply("MET", "MET", "MET"))
        assertTrue(g.readsPhotos)
        assertFalse(DemoBrain(repo).readsPhotos)
        g.markWritten(q, "", 1, listOf("data:image/jpeg;base64,AAAA"))
        assertEquals(1, sent.single().second.getJSONArray("images").length())
    }

    // ── Papers written on paper ──
    @Test fun handwrittenAnswersAreSentWithTheirPagesAndDoubleMarkingIsShown() = runBlocking {
        val reply = """{"result":{"questionId":"s7","status":"CHECKED","correction":"Divide by 9.","examiners":3,"followUp":"x","criteria":[""" +
            q.rubric.mapIndexed { i, c -> """{"criterionId":"${c.id}","status":"${if (i == 0) "NOT_FOUND" else "MET"}","evidence":"saw line $i"}""" }.joinToString(",") + "]}}"
        val mark = gateway(200, reply).markHandwritten(q, "read from the page", listOf("data:image/jpeg;base64,AAAA"), "board-2027-a")
        val body = sent.single().second
        assertEquals(true, body.getBoolean("handwritten"))
        assertEquals(1, body.getJSONArray("images").length())
        assertEquals(3, mark.examiners)
        assertEquals("Divide by 9.", mark.correction)
        assertEquals("saw line 1", mark.criteria[1].evidence)
    }

    @Test fun aScriptPageIsReadWithThePapersLabelsOnly() = runBlocking {
        val reply = """{"result":{"page":1,"kind":"ANSWERS","legibility":"CLEAR","note":"","mcq":[],
            "segments":[{"label":"2 iii)","questionId":"s7","continues":false,"crossedOut":false,"roughWork":false,"legible":"CLEAR","text":"7√2"}]}}"""
        val page = gateway(200, reply).readScriptPage("data:image/jpeg;base64,AAAA", 1, listOf("s7" to "Q2 (iii)", "s8" to "Q2 (iv)"), 15)!!
        val body = sent.single().second
        assertEquals("read_script", body.getString("task"))
        assertEquals("s7", body.getString("questionId"))
        assertEquals("Q2 (iii)", body.getJSONArray("questions").getJSONObject(0).getString("label"))
        assertFalse(body.toString().contains("modelAnswer"))
        assertEquals("s7", page.segments.single().questionId)
        // No server: nothing is guessed.
        sent.clear()
        assertEquals(null, gateway(500, "{}").readScriptPage("data:image/jpeg;base64,AAAA", 1, listOf("s7" to "Q2 (iii)"), 15))
    }
}
