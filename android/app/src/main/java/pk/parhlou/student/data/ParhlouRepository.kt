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
    fun modulesOfChapter(chapterId: String?): List<Module>
    fun moduleQuiz(moduleId: String, count: Int = 10): List<McqQuestion>
    fun readinessQuiz(subjectId: String): List<McqQuestion>
    fun blueprints(subjectId: String): List<PaperBlueprint>
    /** Questions of a paper, or null when none are published for it yet. */
    fun paper(blueprintId: String): PaperContent?
    /** Notes the tutor may answer from for this subject. Never notes of another board or subject. */
    fun notes(subjectId: String): List<ConceptNote>
}
