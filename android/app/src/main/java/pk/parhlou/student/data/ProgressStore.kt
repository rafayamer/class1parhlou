package pk.parhlou.student.data

import androidx.compose.runtime.mutableStateMapOf
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.LearningState
import pk.parhlou.student.domain.ProgressCalculator

/**
 * In-session evidence log for the offline sample. In production, evidence is written by the server
 * (learning_events) and this becomes a read-through cache.
 */
class ProgressStore {
    private val events = mutableStateMapOf<String, List<Evidence>>()

    fun add(moduleId: String, evidence: Evidence) {
        events[moduleId] = events[moduleId].orEmpty() + evidence
    }

    fun evidence(moduleId: String): List<Evidence> = events[moduleId].orEmpty()

    fun state(moduleId: String): LearningState = ProgressCalculator.stateOf(evidence(moduleId))

    fun assessedCount(moduleIds: List<String>): Int =
        moduleIds.count { ProgressCalculator.countsTowardMilestone(state(it)) }
}
