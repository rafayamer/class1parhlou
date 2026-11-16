package pk.parhlou.student.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val email: String)

sealed interface AuthResult {
    data object Sent : AuthResult
    data class SignedIn(val session: Session) : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Supabase email sign-in through its REST endpoints. The student gets an email with a sign-in link
 * (which opens the app through pk.parhlou.student://auth) or, when a custom email template is set up, a code.
 * The session is kept in app-private storage and refreshed before it expires.
 */
class AuthClient(
    private val supabaseUrl: String,
    private val publicKey: String,
    private val store: LocalStore,
    private val http: HttpCall = Http.default,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val enabled: Boolean get() = supabaseUrl.isNotBlank()
    val session: Flow<Session?> get() = store.session
    private val refreshLock = Mutex()

    private fun headers(token: String? = null) = mapOf(
        "apikey" to publicKey,
        "Content-Type" to "application/json",
        "Authorization" to "Bearer ${token ?: publicKey}",
    )

    suspend fun sendLink(email: String): AuthResult {
        val clean = email.trim().lowercase()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(clean)) return AuthResult.Failed("Enter a valid email address.")
        val body = JSONObject().put("email", clean).put("create_user", true).toString()
        val (status, text) = http("POST", "$supabaseUrl/auth/v1/otp?redirect_to=${Uri.encode(REDIRECT)}", headers(), body)
            ?: return AuthResult.Failed("No internet connection. Try again when you're online.")
        return when (status) {
            200 -> AuthResult.Sent
            429 -> AuthResult.Failed("Too many emails sent. Wait a while and try again, or use the app without an account for now.")
            else -> AuthResult.Failed(message(text) ?: "Couldn't send the email (error $status).")
        }
    }

    suspend fun verifyCode(email: String, code: String): AuthResult {
        val body = JSONObject().put("type", "email").put("email", email.trim().lowercase()).put("token", code.trim()).toString()
        val (status, text) = http("POST", "$supabaseUrl/auth/v1/verify", headers(), body)
            ?: return AuthResult.Failed("No internet connection.")
        if (status != 200) return AuthResult.Failed(if (status in 400..403) "That code is wrong or has expired." else "Sign-in failed (error $status).")
        return parseSession(JSONObject(text))?.let { save(it) } ?: AuthResult.Failed("Sign-in failed.")
    }

    /**
     * "Continue with Google": Supabase's Google sign-in page. After the student picks their account, Supabase sends them
     * back to pk.parhlou.student://auth#access_token=… exactly like the email link, so [handleRedirect] finishes it.
     * A Google account with the same email as an existing Parhlou account signs in to that same account.
     */
    fun googleSignInUrl(): String = "$supabaseUrl/auth/v1/authorize?provider=google&redirect_to=${Uri.encode(REDIRECT)}"

    /** Handles the link from the sign-in email: pk.parhlou.student://auth#access_token=...&refresh_token=... */
    suspend fun handleRedirect(uri: Uri): AuthResult? {
        if (uri.scheme != "pk.parhlou.student" || uri.host != "auth") return null
        val params = (uri.fragment ?: uri.query ?: "").split('&').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        params["error_description"]?.let { return AuthResult.Failed(it.replace('+', ' ')) }
        val access = params["access_token"] ?: return AuthResult.Failed("This sign-in link is not valid.")
        val refresh = params["refresh_token"] ?: return AuthResult.Failed("This sign-in link is not valid.")
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600
        val (status, text) = http("GET", "$supabaseUrl/auth/v1/user", headers(access), null)
            ?: return AuthResult.Failed("No internet connection.")
        if (status != 200) return AuthResult.Failed("This sign-in link has expired. Ask for a new one.")
        val user = JSONObject(text)
        return save(Session(access, refresh, now() + expiresIn * 1000, user.getString("id"), user.optString("email")))
    }

    /** A valid access token, refreshed if it is about to expire. Null when signed out or the session can't be renewed. */
    suspend fun accessToken(): String? = refreshLock.withLock {
        val s = store.session.first() ?: return null
        if (s.expiresAt - now() > 60_000) return s.accessToken
        val body = JSONObject().put("refresh_token", s.refreshToken).toString()
        val (status, text) = http("POST", "$supabaseUrl/auth/v1/token?grant_type=refresh_token", headers(), body) ?: return null
        if (status in 400..403) { store.saveSession(null); return null }      // revoked or expired for good
        if (status != 200) return null
        val renewed = parseSession(JSONObject(text)) ?: return null
        store.saveSession(renewed)
        renewed.accessToken
    }

    suspend fun signOut() {
        val token = store.session.first()?.accessToken
        if (token != null) http("POST", "$supabaseUrl/auth/v1/logout", headers(token), "{}")
        store.saveSession(null)
    }

    private suspend fun save(s: Session): AuthResult { store.saveSession(s); return AuthResult.SignedIn(s) }

    private fun parseSession(o: JSONObject): Session? = runCatching {
        val user = o.getJSONObject("user")
        Session(o.getString("access_token"), o.getString("refresh_token"), now() + o.optLong("expires_in", 3600) * 1000,
            user.getString("id"), user.optString("email"))
    }.getOrNull()

    private fun message(text: String) = runCatching { JSONObject(text).let { it.optString("msg").ifBlank { it.optString("message") } } }
        .getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        const val REDIRECT = "pk.parhlou.student://auth"
    }
}
