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

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
