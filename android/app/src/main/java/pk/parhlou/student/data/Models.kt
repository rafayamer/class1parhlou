package pk.parhlou.student.data

/** Review state of a content item. Only PUBLISHED content may be scored for real (PRD §6). */
enum class ReviewStatus { SAMPLE_UNREVIEWED, DRAFT, ACADEMIC_REVIEW, APPROVED, PUBLISHED }

enum class Pathway(val label: String, val description: String) {
    COMPLETE_ACADEMY(
        "Complete Academy",
        "Step-by-step: teacher video, quiz, book reading, written practice, then the chapter test.",
    ),
    TEST_SERIES(
        "Test Series",
        "Go straight to tests. A short readiness quiz unlocks each paper. Videos are optional.",
    ),
}

data class BoardOption(val code: String, val name: String, val available: Boolean)

data class Subject(
    val id: String,
    val name: String,
    val grade: Int,
    val editionLabel: String,
    val editionVerified: Boolean,
    val chapters: List<Chapter>,
)

data class Chapter(
    val id: String,
    val number: Int,
    val title: String,
    val modules: List<Module>,
    val status: ReviewStatus,
)

/** A book reference is only shown when it has been checked against the official edition. */
data class BookReference(val text: String, val verified: Boolean)

/** A teacher video hosted on YouTube. Parhlou links out; it never downloads videos or captions. */
data class VideoLink(val url: String, val teacher: String, val channel: String)

data class Module(
    val id: String,
    val position: Int,
    val title: String,
    val video: VideoLink?,
    val bookReference: BookReference?,
    val readingTasks: List<String>,
    val writtenQuestions: List<WrittenQuestion>,
    val status: ReviewStatus,
    /** Taught like a home tutor: explanations, a worked example step by step, your turn, exam tips. */
    val lesson: Lesson? = null,
)

/** A module's tutor lesson (decision D-022). Written once per section; no AI call while the student studies. */
data class Lesson(
    val explain: List<LessonCard>,
    val example: WorkedExample?,
    val yourTurn: List<PracticeStep>,
    val examTips: List<String>,
    val mistakes: List<String>,
    /** Two quick questions on the previous module, asked first. */
    val recap: List<RecapItem> = emptyList(),
)

data class LessonCard(val title: String, val body: String)

data class WorkedExample(val question: String, val steps: List<ExampleStep>, val answer: String)

/** One line of working, and why it is done. */
data class ExampleStep(val text: String, val why: String)

/** The student's own try, checked by [accept] patterns (TextMatch syntax), with hints before the solution. */
data class PracticeStep(
    val question: String,
    val answer: String,
    val accept: List<List<String>>,
    val hints: List<String>,
    val solution: List<String>,
)

data class RecapItem(val question: String, val answer: String)

data class McqQuestion(
    val id: String,
    val stem: String,
    val options: List<String>,
    /** Only present in offline sample data. Real keys stay on the server (see supabase answer_quiz_item). */
    val sampleCorrectIndex: Int,
    val rationales: List<String>,
    val status: ReviewStatus,
    /** Concept note the tutor uses to explain this question. */
    val noteId: String? = null,
    /** The module whose quiz this question belongs to (module quizzes only). */
    val moduleId: String? = null,
)

/** One marking point of a rubric. [accept] lists alternative pattern groups; see ai/TextMatch.kt for the syntax. */
data class RubricCriterion(
    val id: String,
    val description: String,
    val marks: Double,
    val accept: List<List<String>>,
    val why: String,
    val how: String,
)

/** A written question with its model answer and criterion-level marking scheme. */
data class WrittenQuestion(
    val id: String,
    val text: String,
    val marks: Double,
    val modelAnswer: List<String>,
    val rubric: List<RubricCriterion>,
    val followUp: String,
    val noteId: String? = null,
    /** Index of the blueprint section this question belongs to (papers only). */
    val section: Int = 0,
    val number: Int = 0,
)

/** The questions of one published paper. */
data class PaperContent(
    val blueprintId: String,
    val mcqs: List<McqQuestion>,
    val written: List<WrittenQuestion>,
)

/** A short teaching note the tutor answers from. Sample notes are written by Parhlou, not copied from a textbook. */
data class ConceptNote(
    val id: String,
    val subjectId: String,
    val moduleId: String,
    val title: String,
    val keywords: List<String>,
    val answer: String,
    val explanation: String,
    val simple: String,
    val examples: List<String>,
    val steps: List<String>,
    val checkQuestion: String,
    val checkAnswer: String,
    val checkAccept: List<List<String>>,
)

data class BlueprintSection(
    val type: String,
    val count: Int,
    val attempt: Int,
    val marksEach: Double,
    val minutes: Int? = null,
)

data class PaperBlueprint(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val totalMarks: Double,
    val sections: List<BlueprintSection>,
    val official: Boolean,
    /** Chapter this paper assesses, when it is a chapter test. */
    val chapterId: String? = null,
)

data class OnboardingState(
    val consentAcknowledged: Boolean = false,
    val boardCode: String? = null,
    val grade: Int? = null,
    val medium: String? = null,
    val subjectIds: Set<String> = emptySet(),
    val pathway: Pathway? = null,
    val studyDaysPerWeek: Int = 5,
    val minutesPerDay: Int = 60,
    val completed: Boolean = false,
    /** First name, used in the greeting. Kept on the phone and in the student's own server settings. */
    val name: String = "",
)
