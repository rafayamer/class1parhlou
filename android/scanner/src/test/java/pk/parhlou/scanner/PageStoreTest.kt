package pk.parhlou.scanner

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageStoreTest {
    private lateinit var dir: File
    private lateinit var store: PageStore

    @Before fun setUp() {
        dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "scan-test").apply { deleteRecursively() }
        store = PageStore(dir)
    }

    private fun photo(name: String, corners: List<Float>? = listOf(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)) =
        Photo(File(store.photoDir(), "$name.jpg").apply { writeText(name) }, 300, 400, corners)

    @Test fun pagesKeepOrderMoveAndSurviveARestart() {
        val a = store.add(photo("a"))!!
        val b = store.add(photo("b"))!!
        val c = store.add(photo("c"))!!
        store.move(c.id, -2)
        store.move(a.id, 5) // clamps to the end
        assertEquals(listOf(c.id, b.id, a.id), store.pages().map { it.id })
        // The detected page is the starting crop, and the photo moved into the store's own folder.
        assertEquals(a.detected, a.corners)
        assertFalse(File(store.photoDir(), "a.jpg").exists())

        val reopened = PageStore(dir)
        assertEquals(listOf(c.id, b.id, a.id), reopened.pages().map { it.id })
        assertEquals("a", File(reopened.page(a.id)!!.src).readText())
    }

    @Test fun pagesAreMatchedToQuestionsAndUnmatchedOnesAreListed() {
        val a = store.add(photo("a"))!!
        val b = store.add(photo("b"))!!
        val c = store.add(photo("c"))!!
        store.assign(a.id, "q1"); store.assign(c.id, "q1"); store.assign(b.id, "q2")
        assertEquals(listOf("a", "c"), store.filesFor("q1").map { it.readText() })
        assertTrue(store.unassigned().isEmpty())
        store.assign(b.id, null)
        assertEquals(listOf(b.id), store.unassigned().map { it.id })
    }

    @Test fun retakeKeepsPlaceAndQuestionAndDropsTheOldPhoto() {
        val a = store.add(photo("a"))!!
        val b = store.add(photo("b"))!!
        store.assign(a.id, "q1")
        val oldSrc = File(store.page(a.id)!!.src)
        val next = store.retake(a.id, photo("a2", corners = null))!!
        assertEquals(listOf(a.id, b.id), store.pages().map { it.id })
        assertEquals("q1", next.questionId)
        assertNull(next.corners)
        assertEquals("a2", File(next.src).readText())
        assertFalse(oldSrc.exists())
    }

    @Test fun aRenderOnlyCountsForTheSettingsItWasMadeFrom() {
        val a = store.add(photo("a"))!!
        val stale = store.renderFile().apply { writeText("old") }
        store.edit(a.id) { it.copy(rotation = 90) }
        assertFalse(store.rendered(a.id, a.rev, stale))
        assertFalse(stale.exists())
        val fresh = store.renderFile().apply { writeText("new") }
        assertTrue(store.rendered(a.id, store.page(a.id)!!.rev, fresh))
        store.assign(a.id, "q1")
        assertEquals("new", store.filesFor("q1").single().readText())
        // A further edit makes the render stale again: the original photo is used until it is redone.
        store.edit(a.id) { it.copy(filter = "gray") }
        assertEquals("a", store.filesFor("q1").single().readText())
    }

    @Test fun aPageThatCantBeProcessedIsSentAsPhotographed() {
        val a = store.add(photo("a"))!!
        store.assign(a.id, "q1")
        store.renderFailed(a.id, a.rev)
        val p = store.page(a.id)!!
        assertTrue(p.rendered)
        assertEquals("a", store.filesFor("q1").single().readText())
        assertTrue(File(p.src).exists())
    }

    @Test fun deleteRemovesFilesAndThePageLimitHolds() {
        val a = store.add(photo("a"))!!
        val src = File(a.src)
        store.delete(a.id)
        assertFalse(src.exists())
        repeat(PageStore.MAX_PAGES) { store.add(photo("p$it")) }
        assertNull(store.add(photo("extra")))
        store.clear()
        assertTrue(PageStore(dir).pages().isEmpty())
    }
}
