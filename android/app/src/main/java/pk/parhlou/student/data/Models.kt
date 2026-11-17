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
    /** Approximate start of the board exam this course prepares for (ISO date), and its name, e.g. "2027 board exam". */
    val examDate: String = "",
    val examName: String = "",
    /** Practical work for science subjects, from the book's activities. */
    val practicals: List<Practical> = emptyList(),
)

/** One practical: the book's activity written up as students write it in their practical notebook. */
data class Practical(
    val id: String,
    val unit: Int,
    val label: String,
    val title: String,
    val aim: String,
    val apparatus: List<String>,
    val diagram: String,
    val procedure: List<String>,
    val columns: List<String>,
    val rows: List<List<String>>,
    val calculation: List<String>,
    val result: String,
    val precautions: List<String>,
    val viva: List<Pair<String, String>>,
    /** The activity exactly as the book prints it. */
    val bookText: String,
    /** False when the write-up could not be checked: then only the book's text is shown. */
    val checked: Boolean,
    val inExam: Boolean,
    /** The book's figures for this activity (asset ids). */
    val figures: List<String> = emptyList(),
)

data class Chapter(
    val id: String,
    val number: Int,
    val title: String,
    val modules: List<Module>,
    val status: ReviewStatus,
    /** The unit's exam kit (definitions, formulas, important questions, full-marks writing, mistakes). */
    val kit: ExamKit? = null,
)

/** How the book names its parts: Mathematics has units; the sciences (ids "phy-…", "chem-…") have chapters. */
val Chapter.word: String get() = if (id.startsWith("phy-") || id.startsWith("chem-")) "Chapter" else "Unit"

/** Everything a student needs to revise one unit for the board exam. */
data class ExamKit(
    /** False when the current exam's smart syllabus leaves the whole unit out. */
    val inExam: Boolean,
    val examLabel: String,
    /** What the smart syllabus leaves out of this unit, e.g. "Ex 1.3", "Example 10-14". */
    val excluded: List<String>,
    val definitions: List<KitDefinition>,
    val formulas: List<KitFormula>,
    val important: List<KitImportant>,
    val fullMarks: List<String>,
    val mistakes: List<String>,
    val shortTheory: List<KitShortTheory>,
)

data class KitDefinition(val term: String, val text: String)
data class KitFormula(val formula: String, val use: String)
data class KitImportant(val ref: String, val why: String)
data class KitShortTheory(val question: String, val answer: String)

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
    /** Every worked example and exercise question of this section, with step-by-step solutions. */
    val solutions: List<BookSolution> = emptyList(),
    /** False when the current exam's smart syllabus leaves this section out (still available as practice). */
    val inExam: Boolean = true,
)

enum class SolutionKind { EXAMPLE, EXERCISE }

/**
 * One textbook example or exercise question with its solution in the book's method. [checked] is false when no
 * solution passed the checks; then only the textbook's own answer is shown, never an unchecked solution.
 */
data class BookSolution(
    val kind: SolutionKind,
    val source: String,
    val question: String,
    val steps: List<String>,
    val answer: String,
    val figure: String,
    val accept: List<List<String>>,
    val checked: Boolean,
    /** Shown under the answer, e.g. when the textbook's answer key differs from the checked working. */
    val note: String = "",
    /** The answer printed in the textbook's answer key for this question, when it has one. */
    val bookAnswer: String = "",
    /** False when the current exam's smart syllabus leaves this question out. */
    val inExam: Boolean = true,
    /** One short reason per step ("why this step"), same length as [steps] when present. */
    val why: List<String> = emptyList(),
    /** Book figures the question needs (ids of images shipped in assets/figures). */
    val figures: List<String> = emptyList(),
    /** The book's own answer figure (a number line, graph or construction), when it prints one. */
    val answerFigures: List<String> = emptyList(),
    /** A figure the answer draws (vectors, forces, circuits, graphs), as checked data the app draws itself; "" if none. */
    val drawn: String = "",
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

/** One idea of a lesson, with the book diagrams that explain it (asset ids). */
data class LessonCard(val title: String, val body: String, val figures: List<String> = emptyList())

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
    /** Board papers this question came in, e.g. "Lahore 2023 (Group I)". Empty for book-only questions. */
    val pastPapers: List<String> = emptyList(),
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
    /** Board papers this question came in, e.g. "Lahore 2023 (Group I)". Empty for book-only questions. */
    val pastPapers: List<String> = emptyList(),
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
