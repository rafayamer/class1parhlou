package pk.parhlou.student.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The privacy policy, terms and refund policy, published with the owner panel on GitHub Pages. */
object Legal {
    private const val BASE = "https://rafayamer.github.io/class1parhlou/"
    const val PRIVACY = BASE + "privacy.html"
    const val TERMS = BASE + "terms.html"
    const val REFUNDS = BASE + "refunds.html"

    fun open(context: Context, url: String) = Links.open(context, url)
}

/**
 * The one way the app opens web links. Only https web pages are opened: links that come from course content or a server
 * reply can never start another app through an intent:, file: or content: link. A phone with no browser doesn't crash.
 */
object Links {
    fun safe(url: String): Uri? = runCatching { Uri.parse(url.trim()) }.getOrNull()?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }

    fun open(context: Context, url: String): Boolean {
        val uri = safe(url) ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /** A YouTube video id as YouTube issues them; anything else is refused. */
    fun youtubeId(id: String): String? = id.takeIf { Regex("^[A-Za-z0-9_-]{6,20}$").matches(it) }
}
