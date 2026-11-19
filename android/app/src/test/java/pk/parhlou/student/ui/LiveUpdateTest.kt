package pk.parhlou.student.ui

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.HttpGet
import pk.parhlou.student.data.RemoteContent
import pk.parhlou.student.data.SampleContent
import pk.parhlou.student.update.AppUpdater

/** Live content from the server and the prototype update check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveUpdateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val packed = FakeRepository()
    private val subject = FakeRepository.MATHS_9
    private fun bundle() = ContentBundle.export(packed, subject, "test edition")
    private fun row(version: Int, bundle: JSONObject) = JSONArray().put(JSONObject().put("version", version).put("bundle", bundle)).toString()
    private fun remote(reply: () -> Pair<Int, String>?, seen: MutableList<Map<String, String>> = mutableListOf()): RemoteContent {
        val http: HttpGet = { _, headers -> seen += headers; reply() }
        return RemoteContent(tmp.root, "https://example.test", "public-key", packed, http)
    }

    @Test fun bundleRoundTripKeepsEverything() {
        val repo = ContentBundle.repository(bundle(), 1, packed)
        assertEquals(packed.subject(subject), repo.subject(subject))
        assertEquals(packed.notes(subject), repo.notes(subject))
        assertEquals(packed.moduleQuiz("m-1-1"), repo.moduleQuiz("m-1-1"))
        assertEquals(packed.readinessQuiz(subject), repo.readinessQuiz(subject))
        assertEquals(packed.blueprints(subject), repo.blueprints(subject))
        assertEquals(packed.paper(SampleContent.CHAPTER_TEST), repo.paper(SampleContent.CHAPTER_TEST))
        assertNull(repo.paper("sample-full-paper"))
        assertEquals(packed.module("m-1-2"), repo.module("m-1-2"))
        assertEquals(packed.modulesOfChapter("u-1"), repo.modulesOfChapter("u-1"))
        assertEquals(packed.boards(), repo.boards())
    }

    @Test fun contentEditedOnTheServerReachesTheAppWithoutANewBuild() = runBlocking {
        var served = row(1, bundle())
        val content = remote({ 200 to served })
        val first = content.refresh(subject, null)!!
        assertEquals(1, first.version)
        assertNull("nothing newer yet", content.refresh(subject, first.version))

        // Someone edits a tutor note and a module title on the server and publishes version 2.
        val edited = bundle()
        edited.getJSONArray("notes").getJSONObject(0).put("answer", "EDITED ON THE SERVER")
        edited.getJSONObject("subject").getJSONArray("chapters").getJSONObject(0).getJSONArray("modules").getJSONObject(0).put("title", "New module title")
        served = row(2, edited)
        val second = content.refresh(subject, first.version)!!
        assertEquals(2, second.version)
        assertEquals("EDITED ON THE SERVER", second.notes(subject).first().answer)
        assertEquals("New module title", second.module("m-1-1")!!.title)
    }

    @Test fun offlineUsesTheSavedCopyAndABrokenBundleIsNeverSaved() = runBlocking {
        assertNull(remote({ null }).cached(subject))
        assertNotNull(remote({ 200 to row(3, bundle()) }).refresh(subject, null))

        val offline = remote({ null })
        assertNull(offline.refresh(subject, null))
        assertEquals(3, offline.cached(subject)!!.version)

        // A damaged publish and a server error leave the saved copy untouched.
        val broken = bundle().apply { remove("notes") }
        assertNull(remote({ 200 to row(4, broken) }).refresh(subject, 3))
        assertNull(remote({ 500 to "oops" }).refresh(subject, 3))
        assertNull(remote({ 200 to "[]" }).refresh(subject, 3))
        assertEquals(3, offline.cached(subject)!!.version)
    }

    @Test fun onlyThePublicKeyIsSent() = runBlocking {
        val seen = mutableListOf<Map<String, String>>()
        remote({ 200 to row(1, bundle()) }, seen).refresh(subject, null)
        assertEquals("public-key", seen.single()["apikey"])
    }

    private fun release(tag: String, asset: String = "parhlou.apk", url: String = "https://github.com/rafayamer/class1parhlou/releases/download/$tag/parhlou.apk") =
        """{"tag_name":"$tag","published_at":"2026-11-13T10:00:00Z","assets":[{"name":"$asset","browser_download_url":"$url"}]}"""

    @Test fun aHalfDownloadedUpdateIsNeverOffered() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val whole = ByteArray(1000) { it.toByte() }
        val file = java.io.File(java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }, "parhlou-12.apk")
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(whole).joinToString("") { "%02x".format(it) }
        val info = pk.parhlou.student.update.UpdateInfo(12, "https://github.com/x/y.apk", "", size = 1000, sha256 = sha)
        val updater = AppUpdater(ctx, "x/y", 7)
        file.writeBytes(whole.copyOf(600))
        assertNull("cut-off download", updater.downloaded(info))
        whole.copyOf().also { it[5] = 99 }.let(file::writeBytes)
        assertNull("damaged download", updater.downloaded(info))
        file.delete()
    }

    @Test fun updateIsOfferedOnlyForAHigherBuildWithAnApkFromGitHub() {
        val offer = AppUpdater.latest(release("build-12"), currentBuild = 7)!!
        assertEquals(12, offer.build)
        assertEquals("2026-11-13", offer.publishedOn)
        assertTrue(offer.apkUrl.endsWith("/build-12/parhlou.apk"))
        assertNull(AppUpdater.latest(release("build-7"), 7))
        assertNull(AppUpdater.latest(release("build-3"), 7))
        assertNull(AppUpdater.latest(release("v2.0"), 7))
        assertNull(AppUpdater.latest(release("build-12", asset = "notes.txt"), 7))
        assertNull(AppUpdater.latest(release("build-12", url = "https://evil.example/parhlou.apk"), 7))
        assertNull(AppUpdater.latest("not json", 7))
    }
}
