package pk.parhlou.student.domain

import pk.parhlou.student.data.PaperBlueprint

/** Mirrors public.validate_blueprint in the database: a paper's marks must reconcile (PRD §22.6). */
object BlueprintValidator {
    fun problems(blueprint: PaperBlueprint): List<String> {
        val problems = mutableListOf<String>()
        if (blueprint.sections.isEmpty()) return listOf("sections must not be empty")
        var computed = 0.0
        blueprint.sections.forEachIndexed { i, s ->
            val n = i + 1
            if (s.count <= 0) problems += "section $n: count must be positive"
            if (s.attempt <= 0) problems += "section $n: attempt must be positive"
            else if (s.attempt > s.count) problems += "section $n: attempt exceeds count"
            if (s.marksEach <= 0) problems += "section $n: marks_each must be positive"
            computed += s.attempt * s.marksEach
        }
        if (computed != blueprint.totalMarks) {
            problems += "sections add up to ${fmt(computed)} marks but total is ${fmt(blueprint.totalMarks)}"
        }
        return problems
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
