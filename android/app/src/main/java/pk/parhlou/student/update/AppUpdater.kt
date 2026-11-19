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

/** [size] and [sha256] come from the release, so a half-downloaded or damaged APK is never handed to the installer. */
data class UpdateInfo(val build: Int, val apkUrl: String, val publishedOn: String, val size: Long = 0, val sha256: String = "")

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

    /** The downloaded APK for [info], if it is already on the phone. */
    fun downloaded(info: UpdateInfo): File? =
        File(File(context.cacheDir, "updates"), "parhlou-${info.build}.apk").takeIf { it.isFile && complete(it, info) }

    /** True when the file is the whole APK: the release's size and SHA-256 (when GitHub gives them) and Android can read it. */
    private fun complete(file: File, info: UpdateInfo): Boolean {
        if (file.length() <= 0 || (info.size > 0 && file.length() != info.size)) return false
        if (info.sha256.isNotBlank() && !sha256(file).equals(info.sha256, ignoreCase = true)) return false
        return runCatching { context.packageManager.getPackageArchiveInfo(file.path, 0) != null }.getOrDefault(true)
    }

    /** Downloads the APK in the background (once) so installing is instant. Returns null if the download failed. */
    suspend fun download(info: UpdateInfo): File? = downloaded(info) ?: withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "parhlou-${info.build}.apk")
                val part = File(dir, "parhlou-${info.build}.part")
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    c.readTimeout = 120_000
                    if (c.responseCode !in 200..299) return@runCatching null
                    c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                } finally { c.disconnect() }
                // A dropped connection can end the stream early without an error: check before keeping it.
                if (!complete(part, info)) { part.delete(); return@runCatching null }
                if (!part.renameTo(target)) return@runCatching null
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
                target
            }.getOrNull()
        }

    /** Downloads the APK if needed and opens Android's installer (Android always asks the user to confirm). */
    suspend fun install(info: UpdateInfo): Boolean {
        // A damaged file left by an older build is deleted and downloaded again.
        File(File(context.cacheDir, "updates"), "parhlou-${info.build}.apk").takeIf { it.isFile && downloaded(info) == null }?.delete()
        val file = download(info) ?: download(info) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        fun sha256(file: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

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
            UpdateInfo(build, url, release.optString("published_at").take(10), apk.optLong("size", 0),
                apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }.orEmpty())
        }.getOrNull()
    }
}
