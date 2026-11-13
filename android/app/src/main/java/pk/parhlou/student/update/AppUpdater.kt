package pk.parhlou.student.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pk.parhlou.student.data.HttpGet
import pk.parhlou.student.data.RemoteContent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val apkUrl: String, val publishedOn: String)

/**
 * Prototype self-update. Every push builds a new APK and publishes it as a GitHub release tagged "build-<n>".
 * The app offers it when <n> is higher than its own build number; installing still needs the user's tap.
 * A Play Store version must update through the Play Store and must not include this.
 */
class AppUpdater(
    private val context: Context,
    private val repo: String,
    private val currentBuild: Int,
    private val http: HttpGet = RemoteContent::httpGet,
) {
    suspend fun check(): UpdateInfo? {
        if (repo.isBlank()) return null
        val (status, body) = http("https://api.github.com/repos/$repo/releases/latest", mapOf("Accept" to "application/vnd.github+json")) ?: return null
        return if (status == 200) latest(body, currentBuild) else null
    }

    /** Downloads the APK and opens Android's installer. Returns false if the download failed. */
    suspend fun install(info: UpdateInfo): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "parhlou-${info.build}.apk")
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    c.readTimeout = 120_000
                    if (c.responseCode !in 200..299) return@runCatching null
                    c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                } finally { c.disconnect() }
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
                target
            }.getOrNull()
        } ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        /** Reads GitHub's "latest release" reply. Null unless it is a higher build with an APK attached. */
        fun latest(json: String, currentBuild: Int): UpdateInfo? = runCatching {
            val release = JSONObject(json)
            val build = Regex("^build-(\\d+)$").find(release.getString("tag_name"))?.groupValues?.get(1)?.toInt() ?: return null
            if (build <= currentBuild) return null
            val assets = release.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
            val url = apk.getString("browser_download_url")
            if (!url.startsWith("https://github.com/")) return null
            UpdateInfo(build, url, release.optString("published_at").take(10))
        }.getOrNull()
    }
}
