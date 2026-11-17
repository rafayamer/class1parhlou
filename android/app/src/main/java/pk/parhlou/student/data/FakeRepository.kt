package pk.parhlou.student.data

/**
 * OFFLINE SAMPLE DATA — written by Parhlou for UI development. Not reviewed, not from any textbook.
 * Unit names follow the new Punjab Class 9 Mathematics book as reported by third-party sources
 * (see docs/sources-and-rights.md) and are unverified. No page numbers are given because none are verified.
 */
class FakeRepository : ParhlouRepository {

    override fun boards() = listOf(
        BoardOption("PB", "Punjab Boards", available = true),
        BoardOption("FBISE", "Federal Board", available = false),
        BoardOption("KPK", "KPK Boards", available = false),
    )

    override fun subjectOptions(boardCode: String, grade: Int): List<SubjectOption> {
        if (boardCode != "PB") return emptyList()
        val maths = if (grade == 9) {
            SubjectOption(MATHS_9, "Mathematics", available = true, note = "")
        } else {
            SubjectOption("pb-10-math", "Mathematics", available = false, note = "Coming soon")
        }
        return listOf(
            maths,
            SubjectOption("pb-$grade-phy", "Physics", available = false, note = "Coming soon"),
            SubjectOption("pb-$grade-chem", "Chemistry", available = false, note = "Coming soon"),
            SubjectOption("pb-$grade-bio", "Biology", available = false, note = "Coming soon"),
            SubjectOption("pb-$grade-cs", "Computer Science", available = false, note = "Coming soon"),
        )
    }

    override fun subject(id: String): Subject? = if (id == MATHS_9) maths9 else null

    override fun module(id: String): Module? =
        maths9.chapters.flatMap { it.modules }.firstOrNull { it.id == id }

    override fun chapterOf(moduleId: String): Chapter? =
        maths9.chapters.firstOrNull { c -> c.modules.any { it.id == moduleId } }

    override fun modulesOfChapter(chapterId: String?): List<Module> =
        maths9.chapters.firstOrNull { it.id == chapterId }?.modules.orEmpty()

    override fun moduleQuiz(moduleId: String, count: Int): List<McqQuestion> =
        if (module(moduleId) == null) emptyList() else sampleMcqs.take(count.coerceIn(5, 15))

    override fun readinessQuiz(subjectId: String): List<McqQuestion> =
        if (subjectId == MATHS_9) sampleMcqs.filterIndexed { i, _ -> i % 2 == 0 } else emptyList()

    override fun blueprints(subjectId: String): List<PaperBlueprint> =
        if (subjectId != MATHS_9) emptyList() else listOf(
            PaperBlueprint(
                id = "sample-chapter-test",
                title = "Chapter test",
                durationMinutes = 90,
                totalMarks = 40.0,
                sections = listOf(
                    BlueprintSection("MCQ", count = 10, attempt = 10, marksEach = 1.0),
                    BlueprintSection("Short questions", count = 8, attempt = 6, marksEach = 3.0),
                    BlueprintSection("Long questions", count = 3, attempt = 2, marksEach = 6.0),
                ),
                official = false,
                chapterId = "u-1",
            ),
        )

    override fun paper(blueprintId: String): PaperContent? =
        if (blueprintId == SampleContent.CHAPTER_TEST) SampleContent.chapterTest else null

    override fun notes(subjectId: String): List<ConceptNote> =
        if (subjectId == MATHS_9) SampleContent.notes else emptyList()

    companion object {
        const val MATHS_9 = "pb-9-math"
        const val PHYSICS_9 = "pb-9-phy"
        const val CHEMISTRY_9 = "pb-9-chem"
        const val COMPUTER_9 = "pb-9-cs"
        const val BIOLOGY_9 = "pb-9-bio"
        /** Subjects whose published courses the app downloads. */
        val LIVE_SUBJECTS = listOf(MATHS_9, PHYSICS_9, CHEMISTRY_9, BIOLOGY_9, COMPUTER_9)

        private val unitTitles = listOf(
            "Real Numbers", "Logarithms", "Sets and Functions", "Factorization and Algebraic Manipulation",
            "Linear Equations and Inequalities", "Trigonometry", "Coordinate Geometry", "Logic",
            "Similar Figures", "Graphs of Functions", "Loci and Construction", "Information Handling", "Probability",
        )

        private val unit1Modules = listOf(
            Module(
                id = "m-1-1", position = 1, title = "Rational and irrational numbers",
                video = null,
                bookReference = BookReference("Unit 1 — page reference pending official edition", verified = false),
                readingTasks = listOf(
                    "Read the unit's introduction on the number system.",
                    "Without looking, write the definition of a rational number.",
                    "Give two examples of irrational numbers and explain why.",
                ),
                writtenQuestions = SampleContent.moduleWritten.getValue("m-1-1"),
                status = ReviewStatus.SAMPLE_UNREVIEWED,
            ),
            Module(
                id = "m-1-2", position = 2, title = "Properties of real numbers",
                video = null,
                bookReference = BookReference("Unit 1 — page reference pending official edition", verified = false),
                readingTasks = listOf(
                    "Read the properties of addition and multiplication.",
                    "Write the distributive property from memory with an example.",
                ),
                writtenQuestions = SampleContent.moduleWritten.getValue("m-1-2"),
                status = ReviewStatus.SAMPLE_UNREVIEWED,
            ),
        )

        private val maths9 = Subject(
            id = MATHS_9,
            name = "Mathematics",
            grade = 9,
            editionLabel = "National Curriculum 2023",
            editionVerified = false,
            chapters = unitTitles.mapIndexed { i, title ->
                Chapter(
                    id = "u-${i + 1}", number = i + 1, title = title,
                    modules = if (i == 0) unit1Modules else emptyList(),
                    status = if (i == 0) ReviewStatus.SAMPLE_UNREVIEWED else ReviewStatus.DRAFT,
                )
            },
        )

        private val quizNotes = mapOf(
            "q1" to "n-irrational", "q2" to "n-recurring-fraction", "q3" to "n-identity-inverse",
            "q4" to "n-identity-inverse", "q5" to "n-decimals", "q6" to "n-surds", "q7" to "n-commutative",
            "q8" to "n-surds", "q9" to "n-rational", "q10" to "n-distributive",
        )

        private fun q(id: String, stem: String, options: List<String>, correct: Int, rationales: List<String>) =
            McqQuestion(id, stem, options, correct, rationales, ReviewStatus.SAMPLE_UNREVIEWED, quizNotes[id])

        val sampleMcqs = listOf(
            q("q1", "Which of these numbers is irrational?", listOf("√9", "0.25", "√2", "3/4"), 2,
                listOf("√9 = 3, which is rational.", "0.25 = 1/4, which is rational.",
                    "Correct. √2 cannot be written as p/q; its decimal never ends or repeats.", "3/4 is a ratio of integers, so rational.")),
            q("q2", "Which fraction equals 0.333… (3 repeating)?", listOf("3/10", "1/3", "33/100", "1/30"), 1,
                listOf("3/10 = 0.3 exactly; it does not repeat.", "Correct. 1 ÷ 3 = 0.333… repeating.",
                    "33/100 = 0.33 exactly.", "1/30 = 0.0333…, which is ten times smaller.")),
            q("q3", "What is the additive inverse of −7?", listOf("7", "−7", "1/7", "0"), 0,
                listOf("Correct. −7 + 7 = 0.", "−7 + (−7) = −14, not 0.", "1/7 is a reciprocal of 7, not an additive inverse.", "−7 + 0 = −7, not 0.")),
            q("q4", "Which number is the multiplicative identity?", listOf("0", "1", "−1", "10"), 1,
                listOf("a × 0 = 0, so 0 does not keep a unchanged.", "Correct. a × 1 = a for every real a.",
                    "a × (−1) = −a, which changes the sign.", "a × 10 changes the value.")),
            q("q5", "Which fraction gives a terminating decimal?", listOf("1/3", "2/7", "3/8", "5/6"), 2,
                listOf("1/3 = 0.333… repeats.", "2/7 repeats with period 6.",
                    "Correct. 3/8 = 0.375. Its denominator has only the prime factor 2.", "5/6 = 0.8333… repeats.")),
            q("q6", "Simplify √50.", listOf("5√2", "25√2", "10√5", "2√5"), 0,
                listOf("Correct. √50 = √(25 × 2) = 5√2.", "25√2 squared is 1250, not 50.",
                    "10√5 squared is 500.", "2√5 squared is 20.")),
            q("q7", "a + b = b + a is called the…", listOf("associative property", "commutative property", "distributive property", "closure property"), 1,
                listOf("Associative is about grouping: (a + b) + c = a + (b + c).", "Correct. Changing the order does not change the sum.",
                    "Distributive links multiplication and addition.", "Closure means the result stays in the set.")),
            q("q8", "What is (√3)²?", listOf("3", "9", "√3", "6"), 0,
                listOf("Correct. Squaring a square root returns the number.", "9 would be 3², not (√3)².",
                    "√3 is the starting value, not its square.", "6 would be 2 × 3, not a square.")),
            q("q9", "Which number is both rational and an integer?", listOf("−4", "1/2", "√5", "π"), 0,
                listOf("Correct. −4 = −4/1 and is a whole number.", "1/2 is rational but not an integer.",
                    "√5 is irrational.", "π is irrational.")),
            q("q10", "a(b + c) = ab + ac is called the…", listOf("commutative property", "associative property", "distributive property", "identity property"), 2,
                listOf("Commutative is about order.", "Associative is about grouping.",
                    "Correct. Multiplication distributes over addition.", "Identity is about 0 and 1.")),
        )
    }
}
