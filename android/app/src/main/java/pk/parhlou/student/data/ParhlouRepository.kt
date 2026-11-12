package pk.parhlou.student.data

data class SubjectOption(val id: String, val name: String, val available: Boolean, val note: String)

/**
 * Everything the screens need. Milestone 1 uses [FakeRepository] (offline sample data).
 * The Supabase implementation will call the RPCs in supabase/migrations and never receive answer keys.
 */
interface ParhlouRepository {
    fun boards(): List<BoardOption>
    fun subjectOptions(boardCode: String, grade: Int): List<SubjectOption>
    fun subject(id: String): Subject?
    fun module(id: String): Module?
    fun chapterOf(moduleId: String): Chapter?
    fun moduleQuiz(moduleId: String, count: Int = 10): List<McqQuestion>
    fun readinessQuiz(subjectId: String): List<McqQuestion>
    fun blueprints(subjectId: String): List<PaperBlueprint>
}
