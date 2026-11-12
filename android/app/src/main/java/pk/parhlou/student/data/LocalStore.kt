package pk.parhlou.student.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pk.parhlou.student.domain.ExamEventType
import pk.parhlou.student.domain.ExamLog

private val Context.dataStore by preferencesDataStore(name = "parhlou_local")

/**
 * App-private local storage for onboarding, written drafts, paper answers, the invigilator log and progress.
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
        fun examLog(id: String) = stringPreferencesKey("exam_log_$id")
        fun examMcq(id: String) = stringPreferencesKey("exam_mcq_$id")
        fun examFirst(id: String) = stringPreferencesKey("exam_first_$id")
        val activeExam = stringPreferencesKey("active_exam")
        val progress = stringPreferencesKey("progress_log")
        val readiness = booleanPreferencesKey("readiness_passed")
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

    // ---- Papers: invigilator log, MCQ choices, first Check Master result -------------------------------

    fun examLog(paperId: String): Flow<String> = context.dataStore.data.map { it[K.examLog(paperId)] ?: "" }

    /** Appends an invigilator event if it is valid after the last one (see [ExamLog.append]). Returns the new log. */
    suspend fun appendExamEvent(paperId: String, type: ExamEventType, at: Long): String {
        var result = ""
        context.dataStore.edit { p ->
            result = ExamLog.append(p[K.examLog(paperId)] ?: "", type, at)
            p[K.examLog(paperId)] = result
            when (type) {
                ExamEventType.STARTED -> p[K.activeExam] = paperId
                ExamEventType.SUBMITTED -> if (p[K.activeExam] == paperId) p.remove(K.activeExam)
                else -> Unit
            }
        }
        return result
    }

    /** The paper currently being sat, if any. The tutor stays locked while this is set. */
    val activeExam: Flow<String?> = context.dataStore.data.map { it[K.activeExam] }

    fun mcqChoices(paperId: String): Flow<Map<String, Int>> = context.dataStore.data.map { decodeChoices(it[K.examMcq(paperId)]) }

    suspend fun saveMcqChoice(paperId: String, questionId: String, option: Int) {
        context.dataStore.edit { p ->
            val all = decodeChoices(p[K.examMcq(paperId)]) + (questionId to option)
            p[K.examMcq(paperId)] = all.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    /** Marks from the first automatic check of a paper ("questionId=marks" pairs). Kept when answers are re-checked. */
    fun firstResult(paperId: String): Flow<Map<String, Double>> = context.dataStore.data.map { p ->
        (p[K.examFirst(paperId)] ?: "").split(';').mapNotNull { pair ->
            val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toDoubleOrNull()?.let { k to it }
        }.toMap()
    }

    suspend fun saveFirstResult(paperId: String, marks: Map<String, Double>) {
        context.dataStore.edit { p ->
            if (p[K.examFirst(paperId)] == null) p[K.examFirst(paperId)] = marks.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    /** Clears one paper so it can be sat again. Progress evidence already recorded is kept. */
    suspend fun resetExam(paperId: String, draftKeys: List<String>) {
        context.dataStore.edit { p ->
            p.remove(K.examLog(paperId)); p.remove(K.examMcq(paperId)); p.remove(K.examFirst(paperId))
            if (p[K.activeExam] == paperId) p.remove(K.activeExam)
            draftKeys.forEach { p.remove(K.draft(it)) }
        }
    }

    private fun decodeChoices(text: String?): Map<String, Int> = (text ?: "").split(';').mapNotNull { pair ->
        val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        v.toIntOrNull()?.let { k to it }
    }.toMap()

    // ---- Progress evidence log and readiness -----------------------------------------------------------

    suspend fun progressLog(): String = context.dataStore.data.first()[K.progress] ?: ""

    suspend fun appendProgress(line: String) {
        context.dataStore.edit { p -> p[K.progress] = (p[K.progress]?.let { "$it\n" } ?: "") + line }
    }

    val readinessPassed: Flow<Boolean> = context.dataStore.data.map { it[K.readiness] ?: false }

    suspend fun setReadinessPassed(passed: Boolean) {
        context.dataStore.edit { it[K.readiness] = passed }
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
