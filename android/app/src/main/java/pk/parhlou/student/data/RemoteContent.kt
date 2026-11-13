package pk.parhlou.student.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Fetches a URL with headers. Returns the HTTP status and body, or null when the server cannot be reached. */
typealias HttpGet = suspend (url: String, headers: Map<String, String>) -> Pair<Int, String>?

/**
 * Live content: downloads the published content bundle from the server, keeps a copy on the phone and
 * falls back to that copy, then to the content packed in the app, when offline or when a bundle is unreadable.
 */
class RemoteContent(
    private val cacheDir: File,
    private val supabaseUrl: String,
    private val publicKey: String,
    private val base: ParhlouRepository,
    private val http: HttpGet = ::httpGet,
) {
    constructor(context: Context, supabaseUrl: String, publicKey: String, base: ParhlouRepository) :
        this(File(context.filesDir, "content"), supabaseUrl, publicKey, base)

    private fun cacheFile(subjectId: String) = File(cacheDir, "$subjectId.json")

    /** The copy saved on the phone, if there is a readable one. Fast; use it to start the app. */
    suspend fun cached(subjectId: String): BundleRepository? = withContext(Dispatchers.IO) {
        runCatching { parse(JSONObject(cacheFile(subjectId).readText())) }.getOrNull()
    }

    /**
     * Asks the server for the published bundle. Returns it when it is newer than [haveVersion] and readable;
     * null when there is nothing newer, the server is unreachable, or the bundle is invalid (the old one stays in use).
     */
    suspend fun refresh(subjectId: String, haveVersion: Int?): BundleRepository? = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank()) return@withContext null
        runCatching {
            val url = "$supabaseUrl/rest/v1/app_content?subject_id=eq.$subjectId&status=eq.PUBLISHED&select=version,bundle&limit=1"
            val (status, body) = http(url, mapOf("apikey" to publicKey, "Authorization" to "Bearer $publicKey")) ?: return@runCatching null
            if (status != 200) return@runCatching null
            val row = JSONArray(body).optJSONObject(0) ?: return@runCatching null
            if (haveVersion != null && row.getInt("version") <= haveVersion) return@runCatching null
            val repo = parse(row)                    // throws on an unreadable bundle, so a bad publish is never cached
            cacheDir.mkdirs()
            cacheFile(subjectId).writeText(row.toString())
            repo
        }.getOrNull()
    }

    private fun parse(row: JSONObject): BundleRepository =
        ContentBundle.repository(row.getJSONObject("bundle"), row.getInt("version"), base)

    companion object {
        suspend fun httpGet(url: String, headers: Map<String, String>): Pair<Int, String>? = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 10_000
                    c.readTimeout = 30_000
                    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                    val status = c.responseCode
                    val stream = if (status in 200..299) c.inputStream else c.errorStream
                    status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }
}
