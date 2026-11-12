package pk.parhlou.student.domain

data class Feasibility(val weeklyHours: Double, val weeksNeeded: Int, val weeksAvailable: Int?, val message: String)

/**
 * Honest feasibility message for onboarding step 4 (PRD §4). Effort estimates are placeholders
 * until real module effort data exists.
 */
object StudyPlan {
    fun feasibility(
        modulesRemaining: Int,
        minutesPerModule: Int,
        daysPerWeek: Int,
        minutesPerDay: Int,
        weeksUntilExam: Int?,
        revisionWeeks: Int = 10,
    ): Feasibility {
        val weeklyMinutes = (daysPerWeek.coerceIn(0, 7) * minutesPerDay.coerceAtLeast(0))
        val weeklyHours = weeklyMinutes / 60.0
        if (weeklyMinutes == 0) {
            return Feasibility(0.0, Int.MAX_VALUE, weeksUntilExam, "Add at least one study day to build a plan.")
        }
        val totalMinutes = modulesRemaining * minutesPerModule
        val weeksNeeded = (totalMinutes + weeklyMinutes - 1) / weeklyMinutes
        val message = when {
            weeksUntilExam == null ->
                "About $weeksNeeded weeks to cover the course at this pace. Exam date not confirmed yet."
            weeksNeeded + revisionWeeks <= weeksUntilExam ->
                "On track: about $weeksNeeded weeks of new learning, leaving time for revision and mock papers."
            weeksNeeded <= weeksUntilExam ->
                "Tight: you can finish, but with less than $revisionWeeks weeks for revision. Consider more study time."
            else ->
                "Not enough time to cover everything at this pace. We'll start with a diagnostic and focus on the most important topics."
        }
        return Feasibility(weeklyHours, weeksNeeded, weeksUntilExam, message)
    }
}
