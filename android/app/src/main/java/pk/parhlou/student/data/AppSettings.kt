package pk.parhlou.student.data

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import pk.parhlou.student.domain.Tuning

/**
 * Everything the owner can change about the app from the owner panel (App design), without a new build: brand colour,
 * a home announcement, feature switches with gradual rollout, game tuning, texts, maintenance mode and a minimum version.
 * Missing or broken values always fall back to the built-in defaults, so a bad setting can never break the app.
 */
data class AppSettings(
    /** Brand colour as #RRGGBB; null keeps Parhlou teal. */
    val accent: String? = null,
    /** Replaces Ustaad's line under the greeting on Home. */
    val homeMessage: String? = null,
    /** The daily reminder; {streak} becomes the streak length. */
    val reminderText: String? = null,
    /** One line on the Plus screen. */
    val plusPitch: String? = null,
    val banner: Banner? = null,
    val features: Map<String, Feature> = emptyMap(),
    val tuning: Tuning = Tuning(),
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "Parhlou is getting an upgrade. Please come back in a little while.",
    /** Builds below this must update before they can be used (0 = no minimum). */
    val minBuild: Int = 0,
    val updateMessage: String = "A new version of Parhlou is ready. Please update to keep studying.",
    val adsEnabled: Boolean = true,
) {
    /** A feature switch: on/off, plus the share of students (0–100) who get it, chosen by a stable hash of their install. */
    data class Feature(val on: Boolean = true, val rollout: Int = 100)

    data class Banner(
        val id: String, val emoji: String, val title: String, val message: String,
        val button: String?, val link: String?, val tone: String, val audience: String,
        val startsAt: String?, val endsAt: String?,
    ) {
        /** Shown only between its start and end dates (inclusive), and to its audience. */
        fun showsOn(today: String, plus: Boolean?): Boolean =
            (startsAt.isNullOrBlank() || today >= startsAt) && (endsAt.isNullOrBlank() || today <= endsAt) &&
                when (audience) { "free" -> plus != true; "plus" -> plus == true; else -> true }
    }

    /** Whether [key] is on for this student. Features not listed are on. */
    fun enabled(key: String, installId: String): Boolean {
        val f = features[key] ?: return true
        if (!f.on) return false
        if (f.rollout >= 100) return true
        if (f.rollout <= 0) return false
        return Math.floorMod(("$key:$installId").hashCode(), 100) < f.rollout
    }

    companion object {
        /** Every switch the owner panel offers, with what it controls (the panel shows the same list). */
        val FEATURES = listOf("league", "chest", "celebrations", "askUstaad", "guessPaper", "pastPapers", "makeTest", "videos", "plus", "paywall")

        fun parse(text: String?): AppSettings {
            val o = runCatching { JSONObject(text ?: return AppSettings()) }.getOrNull() ?: return AppSettings()
            fun str(obj: JSONObject?, k: String) = obj?.optString(k)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val accent = str(o, "accent")?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }
            val b = o.optJSONObject("banner")
            val banner = b?.let {
                val title = str(it, "title") ?: return@let null
                if (it.has("on") && !it.optBoolean("on", true)) return@let null
                Banner(
                    id = str(it, "id") ?: title.hashCode().toString(), emoji = str(it, "emoji") ?: "📣", title = title.take(80),
                    message = str(it, "message").orEmpty().take(300), button = str(it, "button")?.take(30),
                    link = str(it, "link")?.takeIf { l -> l.startsWith("https://") },
                    tone = str(it, "tone") ?: "info", audience = str(it, "audience") ?: "all",
                    startsAt = str(it, "startsAt"), endsAt = str(it, "endsAt"),
                )
            }
            val f = o.optJSONObject("features")
            val features = f?.keys()?.asSequence()?.associateWith { k ->
                val x = f.optJSONObject(k)
                Feature(x?.optBoolean("on", true) ?: f.optBoolean(k, true), (x?.optInt("rollout", 100) ?: 100).coerceIn(0, 100))
            }.orEmpty()
            val t = o.optJSONObject("tuning")
            val d = Tuning()
            fun n(k: String, def: Int, lo: Int, hi: Int) = (t?.optInt(k, def) ?: def).coerceIn(lo, hi)
            val chestMin = n("chestMinXp", d.chestMinXp, 0, 200)
            val tuning = Tuning(
                lessonXp = n("lessonXp", d.lessonXp, 0, 200), writtenXp = n("writtenXp", d.writtenXp, 0, 200),
                paperXp = n("paperXp", d.paperXp, 0, 500), chestMinXp = chestMin,
                chestMaxXp = n("chestMaxXp", d.chestMaxXp, chestMin, 300), xpPerMinute = n("xpPerMinute", d.xpPerMinute, 1, 20),
            )
            return AppSettings(
                accent = accent, homeMessage = str(o, "homeMessage")?.take(120), reminderText = str(o, "reminderText")?.take(160),
                plusPitch = str(o, "plusPitch")?.take(160), banner = banner, features = features, tuning = tuning,
                maintenance = o.optBoolean("maintenance", false),
                maintenanceMessage = str(o, "maintenanceMessage")?.take(300) ?: AppSettings().maintenanceMessage,
                minBuild = o.optInt("minBuild", 0).coerceAtLeast(0),
                updateMessage = str(o, "updateMessage")?.take(300) ?: AppSettings().updateMessage,
                adsEnabled = o.optBoolean("adsEnabled", true),
            )
        }
    }
}

/** The app's settings for every screen; tests and previews get the defaults. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Loads the owner's settings: the copy saved on the phone at once, then a fresh one from the server. */
class RemoteSettings(
    private val supabaseUrl: String,
    private val publicKey: String,
    private val store: LocalStore,
    private val http: HttpCall = Http.default,
) {
    suspend fun cached(): AppSettings = AppSettings.parse(store.draft(KEY).first().ifBlank { null })

    /** The latest settings, or null when offline or not configured (keep using the cached copy). */
    suspend fun refresh(): AppSettings? {
        if (supabaseUrl.isBlank()) return null
        val headers = mapOf("apikey" to publicKey, "Authorization" to "Bearer $publicKey", "Content-Type" to "application/json")
        val (status, text) = http("POST", "$supabaseUrl/rest/v1/rpc/public_app_settings", headers, "{}") ?: return null
        if (status != 200) return null
        store.saveDraft(KEY, text)
        return AppSettings.parse(text)
    }

    companion object { const val KEY = "app_settings" }
}
