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
    val writtenPrompts: List<String>,
    val status: ReviewStatus,
)

data class McqQuestion(
    val id: String,
    val stem: String,
    val options: List<String>,
    /** Only present in offline sample data. Real keys stay on the server (see supabase answer_quiz_item). */
    val sampleCorrectIndex: Int,
    val rationales: List<String>,
    val status: ReviewStatus,
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
)
