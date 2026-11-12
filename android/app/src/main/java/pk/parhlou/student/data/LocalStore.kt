package pk.parhlou.student.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "parhlou_local")

/**
 * App-private local storage for onboarding progress, written drafts and the exam start time.
 * Survives process death. It is a cache: the server stays the authority for enrollment and marks.
 */
class LocalStore(private val context: Context) {
    private object K {
        val consent = booleanPreferencesKey("consent")
        val board = stringPreferencesKey("board")
        val grade = intPreferencesKey("grade")
        val medium = stringPreferencesKey("medium")
        val subjects = stringSetPreferencesKey("subjects")
        val pathway = stringPreferencesKey("pathway")
        val days = intPreferencesKey("days")
        val minutes = intPreferencesKey("minutes")
        val completed = booleanPreferencesKey("completed")
        fun draft(id: String) = stringPreferencesKey("draft_$id")
        fun examStart(id: String) = longPreferencesKey("exam_start_$id")
    }

    val onboarding: Flow<OnboardingState> = context.dataStore.data.map { it.toOnboarding() }

    suspend fun saveOnboarding(state: OnboardingState) {
        context.dataStore.edit { p ->
            p[K.consent] = state.consentAcknowledged
            state.boardCode?.let { p[K.board] = it } ?: p.remove(K.board)
            state.grade?.let { p[K.grade] = it } ?: p.remove(K.grade)
            state.medium?.let { p[K.medium] = it } ?: p.remove(K.medium)
            p[K.subjects] = state.subjectIds
            state.pathway?.let { p[K.pathway] = it.name } ?: p.remove(K.pathway)
            p[K.days] = state.studyDaysPerWeek
            p[K.minutes] = state.minutesPerDay
            p[K.completed] = state.completed
        }
    }

    fun draft(id: String): Flow<String> = context.dataStore.data.map { it[K.draft(id)] ?: "" }

    suspend fun saveDraft(id: String, text: String) {
        context.dataStore.edit { it[K.draft(id)] = text }
    }

    /** Returns the stored start time, creating it on first call so a restart cannot reset the clock. */
    suspend fun examStartedAt(attemptId: String, now: Long): Long {
        val existing = context.dataStore.data.first()[K.examStart(attemptId)]
        if (existing != null) return existing
        context.dataStore.edit { it[K.examStart(attemptId)] = now }
        return now
    }

    suspend fun clearExam(attemptId: String) {
        context.dataStore.edit { it.remove(K.examStart(attemptId)) }
    }

    /** Removes everything stored on this device (sign-out on a shared phone, and tests). */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toOnboarding() = OnboardingState(
        consentAcknowledged = this[K.consent] ?: false,
        boardCode = this[K.board],
        grade = this[K.grade],
        medium = this[K.medium],
        subjectIds = this[K.subjects] ?: emptySet(),
        pathway = this[K.pathway]?.let { runCatching { Pathway.valueOf(it) }.getOrNull() },
        studyDaysPerWeek = this[K.days] ?: 5,
        minutesPerDay = this[K.minutes] ?: 60,
        completed = this[K.completed] ?: false,
    )
}
