package pk.parhlou.student.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request. Returns the status and body, or null when the server could not be reached. */
typealias HttpCall = suspend (method: String, url: String, headers: Map<String, String>, body: String?) -> Pair<Int, String>?

object Http {
    val default: HttpCall = { method, url, headers, body ->
        withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = method
                    c.connectTimeout = 15_000
                    c.readTimeout = 60_000
                    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                    if (body != null) {
                        c.doOutput = true
                        c.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val status = c.responseCode
                    val stream = if (status in 200..299) c.inputStream else c.errorStream
                    status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }
}
