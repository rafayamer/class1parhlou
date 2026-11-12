package pk.parhlou.student.data

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.EvidenceCodec
import pk.parhlou.student.domain.LearningState
import pk.parhlou.student.domain.LoggedEvidence
import pk.parhlou.student.domain.ProgressCalculator

/**
 * Append-only evidence log, saved on the phone so a restart keeps it. In production the server writes
 * evidence (learning_events) and this becomes a read-through cache.
 */
class ProgressStore(private val store: LocalStore? = null, private val scope: CoroutineScope? = null) {
    private val log = mutableStateListOf<LoggedEvidence>()

    /** Loads the saved log. Call once before showing progress. */
    suspend fun load() {
        val saved = store?.progressLog() ?: return
        log.clear()
        log.addAll(EvidenceCodec.parse(saved))
    }

    fun add(key: String, evidence: Evidence, at: Long = System.currentTimeMillis()) {
        log += LoggedEvidence(at, key, evidence)
        val line = EvidenceCodec.line(at, key, evidence)
        if (store != null && scope != null) scope.launch { store.appendProgress(line) }
    }

    fun evidence(moduleId: String): List<Evidence> = log.filter { it.key == moduleId }.map { it.evidence }

    fun state(moduleId: String): LearningState = ProgressCalculator.stateOf(evidence(moduleId))

    /** Modules proven by verified assessment. Demo-checked work is not included. */
    fun assessedCount(moduleIds: List<String>): Int =
        moduleIds.count { ProgressCalculator.countsTowardMilestone(state(it)) }

    fun demoCheckedCount(moduleIds: List<String>): Int =
        moduleIds.count { state(it) == LearningState.DEMO_CHECKED }

    fun quizzesCompleted(): Int = log.count { it.evidence is Evidence.QuizCompleted }

    /** Papers handed in, counted from the log entries keyed "paper:<id>". */
    val papersSubmitted: Int get() = log.count { it.key.startsWith(PAPER_PREFIX) }

    companion object {
        const val PAPER_PREFIX = "paper:"
    }
}
