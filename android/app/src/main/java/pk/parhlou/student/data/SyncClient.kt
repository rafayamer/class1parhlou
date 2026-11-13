package pk.parhlou.student.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.domain.EvidenceCodec
import java.security.MessageDigest
import java.time.Instant

/**
 * Keeps the signed-in student's settings and progress on the server, so a new phone can continue where
 * the old one stopped. All calls use the student's own session; the database only lets them touch their own rows.
 */
class SyncClient(
    private val supabaseUrl: String,
    private val publicKey: String,
    private val auth: AuthClient,
    private val store: LocalStore,
    private val http: HttpCall = Http.default,
) {
    private suspend fun headers(extra: Map<String, String> = emptyMap()): Map<String, String>? {
        val token = auth.accessToken() ?: return null
        return mapOf("apikey" to publicKey, "Authorization" to "Bearer $token", "Content-Type" to "application/json") + extra
    }

    private suspend fun call(method: String, path: String, body: String?, extra: Map<String, String> = emptyMap()): Pair<Int, String>? {
        val h = headers(extra) ?: return null
        return http(method, "$supabaseUrl$path", h, body)
    }

    /** Creates or renames the student's profile on the server. */
    suspend fun register(name: String): Boolean =
        call("POST", "/rest/v1/rpc/register_learner", JSONObject().put("p_display_name", name).toString())?.first == 200

    suspend fun pushSettings(s: OnboardingState): Boolean {
        val settings = JSONObject()
            .put("consent", s.consentAcknowledged).put("board", s.boardCode ?: JSONObject.NULL).put("grade", s.grade ?: JSONObject.NULL)
            .put("medium", s.medium ?: JSONObject.NULL).put("subjects", JSONArray(s.subjectIds.toList()))
            .put("pathway", s.pathway?.name ?: JSONObject.NULL).put("days", s.studyDaysPerWeek).put("minutes", s.minutesPerDay)
            .put("completed", s.completed).put("name", s.name)
        val body = JSONObject().put("settings", settings).put("updated_at", Instant.now().toString()).toString()
        return call("POST", "/rest/v1/learner_settings?on_conflict=user_id", body,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))?.first?.let { it in 200..299 } == true
    }

    /** The settings saved on the server, or null if there are none (or the server can't be reached). */
    suspend fun pullSettings(): OnboardingState? {
        val (status, text) = call("GET", "/rest/v1/learner_settings?select=settings", null) ?: return null
        if (status != 200) return null
        val o = JSONArray(text).optJSONObject(0)?.optJSONObject("settings") ?: return null
        fun str(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
        return OnboardingState(
            consentAcknowledged = o.optBoolean("consent"), boardCode = str("board"),
            grade = if (o.isNull("grade")) null else o.optInt("grade"), medium = str("medium"),
            subjectIds = o.optJSONArray("subjects")?.let { a -> List(a.length()) { a.getString(it) }.toSet() }.orEmpty(),
            pathway = str("pathway")?.let { runCatching { Pathway.valueOf(it) }.getOrNull() },
            studyDaysPerWeek = o.optInt("days", 5), minutesPerDay = o.optInt("minutes", 60),
            completed = o.optBoolean("completed"), name = o.optString("name"),
        )
    }

    /**
     * Uploads progress lines not yet on the server. Safe to repeat: each line has a stable id and duplicates are ignored.
     * Verified exam marks ("E:") are never uploaded; only the server writes those.
     */
    suspend fun pushEvents(): Boolean {
        val lines = store.progressLog().lines().filter { it.isNotBlank() }
        val from = store.syncedLines().coerceAtMost(lines.size)
        if (from == lines.size) return true
        val rows = JSONArray()
        lines.subList(from, lines.size).forEach { line ->
            val parsed = EvidenceCodec.parse(line).firstOrNull() ?: return@forEach
            val code = line.substringAfterLast('|')
            if (code.startsWith("E:")) return@forEach
            rows.put(JSONObject().put("client_id", clientId(line)).put("at", Instant.ofEpochMilli(parsed.at).toString())
                .put("key", parsed.key).put("code", code))
        }
        if (rows.length() > 0) {
            val r = call("POST", "/rest/v1/learner_events?on_conflict=user_id,client_id", rows.toString(),
                mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")) ?: return false
            if (r.first !in 200..299) return false
        }
        store.setSyncedLines(lines.size)
        return true
    }

    /** On a phone with no progress yet, downloads the student's history from the server. Returns how many lines arrived. */
    suspend fun pullEventsIfEmpty(): Int {
        if (store.progressLog().isNotBlank()) return 0
        val (status, text) = call("GET", "/rest/v1/learner_events?select=at,key,code&order=at.asc&limit=10000", null) ?: return 0
        if (status != 200) return 0
        val rows = JSONArray(text)
        val lines = (0 until rows.length()).mapNotNull { i ->
            val r = rows.getJSONObject(i)
            val at = runCatching { Instant.parse(r.getString("at")).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            EvidenceCodec.parse("$at|${r.getString("key")}|${r.getString("code")}").firstOrNull()?.let { "$at|${it.key}|${r.getString("code")}" }
        }
        if (lines.isEmpty()) return 0
        store.replaceProgress(lines.joinToString("\n"))
        store.setSyncedLines(lines.size)
        return lines.size
    }

    /** Marks the server recorded for one paper (question id → awarded, max). Empty when there are none or offline. */
    suspend fun paperMarks(paperId: String): Map<String, Pair<Double, Double>> {
        val path = "/rest/v1/ai_marks?select=question_id,awarded,max_marks,created_at&paper_id=eq.${Uri.encode(paperId)}&order=created_at.asc"
        val (status, text) = call("GET", path, null) ?: return emptyMap()
        if (status != 200) return emptyMap()
        val rows = JSONArray(text)
        // The latest mark per question wins (re-checks are later rows); history stays on the server.
        return (0 until rows.length()).map { rows.getJSONObject(it) }
            .associate { it.getString("question_id") to (it.getDouble("awarded") to it.getDouble("max_marks")) }
    }

    /** Everything the server holds about the student, as JSON text. */
    suspend fun exportData(): String? {
        val (status, text) = call("POST", "/rest/v1/rpc/export_my_data", "{}") ?: return null
        return if (status == 200) runCatching { JSONObject(text).toString(2) }.getOrNull() else null
    }

    /** Permanently deletes the account and everything on the server. */
    suspend fun deleteAccount(): Boolean = call("POST", "/rest/v1/rpc/delete_my_account", "{}")?.first?.let { it in 200..299 } == true

    companion object {
        fun clientId(line: String): String =
            "ev-" + MessageDigest.getInstance("SHA-256").digest(line.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }
}
