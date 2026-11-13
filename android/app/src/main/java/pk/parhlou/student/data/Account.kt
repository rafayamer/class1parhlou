package pk.parhlou.student.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Sign-in plus server sync. Absent in builds without a Supabase address (tests, offline demo). */
class Account(val auth: AuthClient, val sync: SyncClient, private val store: LocalStore) {
    /** The last sign-in problem to show the student (for example an expired link). */
    val error = MutableStateFlow<String?>(null)

    /**
     * Runs once after signing in: creates the profile, restores settings and history from the server when this phone
     * has none, then uploads whatever this phone has. Returns the settings to use.
     */
    suspend fun afterSignIn(progress: ProgressStore): OnboardingState {
        val local = store.onboarding.first()
        sync.register(local.name.ifBlank { "Learner" })
        val remote = sync.pullSettings()
        val settings = if (!local.completed && remote?.completed == true) remote.copy(name = remote.name.ifBlank { local.name }) else local
        if (settings != local) store.saveOnboarding(settings)
        if (sync.pullEventsIfEmpty() > 0) progress.load()
        syncNow(settings)
        return settings
    }

    /** Uploads settings and new progress. Quietly does nothing when signed out or offline; it retries next time. */
    suspend fun syncNow(settings: OnboardingState? = null) {
        if (store.session.first() == null) return
        settings?.let { sync.pushSettings(it) } ?: sync.pushSettings(store.onboarding.first())
        sync.pushEvents()
    }
}
