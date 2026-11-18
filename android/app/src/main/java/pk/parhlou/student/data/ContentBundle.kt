package pk.parhlou.student.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One subject's content as a single JSON document. The same document is published to the server
 * (table app_content), downloaded by the app, and read by the AI function for questions and marking keys.
 * Changing it on the server updates every phone the next time the app is opened; no new app build is needed.
 */
object ContentBundle {
    const val FORMAT = 1

    // ---- Writing ---------------------------------------------------------------------------------------

    fun export(repo: ParhlouRepository, subjectId: String, courseEdition: String): JSONObject {
        val subject = requireNotNull(repo.subject(subjectId)) { "unknown subject $subjectId" }
        val firstModule = subject.chapters.flatMap { it.modules }.firstOrNull()
        val quiz = firstModule?.let { repo.moduleQuiz(it.id, 15) }.orEmpty()
        val blueprints = repo.blueprints(subjectId)
        val papers = blueprints.mapNotNull { repo.paper(it.id) }
        val allWritten = papers.flatMap { it.written } + subject.chapters.flatMap { c -> c.modules.flatMap { it.writtenQuestions } }
        val allMcqs = (papers.flatMap { it.mcqs } + quiz + repo.readinessQuiz(subjectId)).distinctBy { it.id }
        return JSONObject()
            .put("format", FORMAT)
            .put("subjectId", subjectId)
            .put("course", JSONObject().put("board", "Punjab").put("grade", subject.grade).put("subject", subject.name).put("edition", courseEdition))
            .put("subject", subject(subject))
            .put("notes", array(repo.notes(subjectId), ::note))
            .put("quiz", array(quiz, ::mcq))
            .put("readiness", array(repo.readinessQuiz(subjectId), ::mcq))
            .put("blueprints", array(blueprints, ::blueprint))
            .put("papers", array(papers) { p ->
                JSONObject().put("blueprintId", p.blueprintId).put("mcqs", array(p.mcqs, ::mcq)).put("written", array(p.written, ::written))
            })
            // Flat lists read by the AI function (supabase/functions/ai/handler.ts).
            .put("written", array(allWritten.distinctBy { it.id }, ::written))
            .put("mcqs", array(allMcqs, ::mcq))
    }

    private fun <T> array(items: List<T>, f: (T) -> Any) = JSONArray().apply { items.forEach { put(f(it)) } }
    private fun strings(items: List<String>) = JSONArray(items)

    private fun subject(s: Subject) = JSONObject()
        .put("id", s.id).put("name", s.name).put("grade", s.grade)
        .put("editionLabel", s.editionLabel).put("editionVerified", s.editionVerified)
        .put("chapters", array(s.chapters) { c ->
            JSONObject().put("id", c.id).put("number", c.number).put("title", c.title).put("status", c.status.name)
                .put("modules", array(c.modules, ::module))
        })

    private fun module(m: Module) = JSONObject()
        .put("id", m.id).put("position", m.position).put("title", m.title).put("status", m.status.name)
        .put("video", m.video?.let { JSONObject().put("url", it.url).put("teacher", it.teacher).put("channel", it.channel) } ?: JSONObject.NULL)
        .put("bookReference", m.bookReference?.let { JSONObject().put("text", it.text).put("verified", it.verified) } ?: JSONObject.NULL)
        .put("readingTasks", strings(m.readingTasks))
        .put("writtenQuestions", array(m.writtenQuestions, ::written))

    private fun written(q: WrittenQuestion) = JSONObject()
        .put("id", q.id).put("text", q.text).put("marks", q.marks).put("modelAnswer", strings(q.modelAnswer))
        .put("followUp", q.followUp).put("noteId", q.noteId ?: JSONObject.NULL)
        .put("section", q.section).put("number", q.number).put("matching", "method")
        .put("rubric", array(q.rubric) { c ->
            JSONObject().put("id", c.id).put("description", c.description).put("marks", c.marks)
                .put("why", c.why).put("how", c.how).put("accept", array(c.accept, ) { JSONArray(it) })
        })

    private fun mcq(q: McqQuestion) = JSONObject()
        .put("id", q.id).put("stem", q.stem).put("options", strings(q.options)).put("correctIndex", q.sampleCorrectIndex)
        .put("rationales", strings(q.rationales)).put("status", q.status.name).put("noteId", q.noteId ?: JSONObject.NULL)

    private fun note(n: ConceptNote) = JSONObject()
        .put("id", n.id).put("subjectId", n.subjectId).put("moduleId", n.moduleId).put("title", n.title)
        .put("keywords", strings(n.keywords)).put("answer", n.answer).put("explanation", n.explanation).put("simple", n.simple)
        .put("examples", strings(n.examples)).put("steps", strings(n.steps))
        .put("checkQuestion", n.checkQuestion).put("checkAnswer", n.checkAnswer).put("checkAccept", array(n.checkAccept, ) { JSONArray(it) })

    private fun blueprint(b: PaperBlueprint) = JSONObject()
        .put("id", b.id).put("title", b.title).put("durationMinutes", b.durationMinutes).put("totalMarks", b.totalMarks)
        .put("official", b.official).put("chapterId", b.chapterId ?: JSONObject.NULL)
        .put("sections", array(b.sections) { s ->
            JSONObject().put("type", s.type).put("count", s.count).put("attempt", s.attempt).put("marksEach", s.marksEach)
                .put("minutes", s.minutes ?: JSONObject.NULL)
        })

    // ---- Reading ---------------------------------------------------------------------------------------

    private fun <T> JSONArray.list(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun status(name: String) = runCatching { ReviewStatus.valueOf(name) }.getOrDefault(ReviewStatus.DRAFT)

    fun readSubject(o: JSONObject) = Subject(
        o.getString("id"), o.getString("name"), o.getInt("grade"), o.getString("editionLabel"), o.getBoolean("editionVerified"),
        o.getJSONArray("chapters").list { c ->
            Chapter(c.getString("id"), c.getInt("number"), c.getString("title"), c.getJSONArray("modules").list(::readModule), status(c.getString("status")),
                kit = c.optJSONObject("kit")?.let(::readKit))
        },
        examDate = o.optString("examDate"), examName = o.optString("examName"),
        practicals = o.optJSONArray("practicals")?.list(::readPractical).orEmpty(),
    )

    private fun readPractical(p: JSONObject) = Practical(
        id = p.getString("id"), unit = p.optInt("unit"), label = p.optString("label"), title = p.optString("title"), aim = p.optString("aim"),
        apparatus = p.optJSONArray("apparatus")?.strings().orEmpty(), diagram = p.optString("diagram"),
        procedure = p.optJSONArray("procedure")?.strings().orEmpty(),
        columns = p.optJSONObject("table")?.optJSONArray("columns")?.strings().orEmpty(),
        rows = p.optJSONObject("table")?.optJSONArray("rows")?.let { a -> List(a.length()) { a.getJSONArray(it).strings() } }.orEmpty(),
        calculation = p.optJSONArray("calculation")?.strings().orEmpty(), result = p.optString("result"),
        precautions = p.optJSONArray("precautions")?.strings().orEmpty(),
        viva = p.optJSONArray("viva")?.list { v -> v.optString("question") to v.optString("answer") }.orEmpty(),
        bookText = p.optString("bookText"), checked = p.optBoolean("checked"), inExam = p.optBoolean("inExam", true),
        figures = p.optJSONArray("figures")?.strings().orEmpty(),
    )

    private fun readModule(m: JSONObject) = Module(
        id = m.getString("id"), position = m.getInt("position"), title = m.getString("title"),
        video = m.optJSONObject("video")?.let { VideoLink(it.getString("url"), it.getString("teacher"), it.getString("channel")) },
        bookReference = m.optJSONObject("bookReference")?.let { BookReference(it.getString("text"), it.getBoolean("verified")) },
        readingTasks = m.getJSONArray("readingTasks").strings(),
        writtenQuestions = m.getJSONArray("writtenQuestions").list(::readWritten),
        status = status(m.getString("status")),
        lesson = m.optJSONObject("lesson")?.let(::readLesson),
        solutions = m.optJSONArray("solutions")?.list { s ->
            BookSolution(
                kind = if (s.optString("kind") == "EXAMPLE") SolutionKind.EXAMPLE else SolutionKind.EXERCISE,
                source = s.optString("source"), question = s.getString("question"),
                steps = s.optJSONArray("steps")?.strings().orEmpty(), answer = s.optString("answer"), figure = s.optString("figure"),
                accept = s.optJSONArray("accept")?.let { a -> List(a.length()) { a.getJSONArray(it).strings() } }.orEmpty(),
                checked = s.optBoolean("checked", true),
                note = s.optString("note"),
                bookAnswer = s.optString("bookAnswer"),
                inExam = s.optBoolean("inExam", true),
                why = s.optJSONArray("why")?.strings().orEmpty(),
                figures = s.optJSONArray("figures")?.strings().orEmpty(),
                answerFigures = s.optJSONArray("answerFigures")?.strings().orEmpty(),
                drawn = s.optJSONObject("drawn")?.toString().orEmpty(),
            )
        }.orEmpty(),
        inExam = m.optBoolean("inExam", true),
    )

    private fun readKit(k: JSONObject) = ExamKit(
        inExam = k.optBoolean("inExam", true), examLabel = k.optString("examLabel"),
        excluded = k.optJSONArray("excluded")?.strings().orEmpty(),
        definitions = k.optJSONArray("definitions")?.list { KitDefinition(it.optString("term"), it.optString("text")) }.orEmpty(),
        formulas = k.optJSONArray("formulas")?.list { KitFormula(it.optString("formula"), it.optString("use")) }.orEmpty(),
        important = k.optJSONArray("important")?.list { KitImportant(it.optString("ref"), it.optString("why")) }.orEmpty(),
        fullMarks = k.optJSONArray("fullMarks")?.strings().orEmpty(),
        mistakes = k.optJSONArray("mistakes")?.strings().orEmpty(),
        shortTheory = k.optJSONArray("shortTheory")?.list { KitShortTheory(it.optString("question"), it.optString("answer")) }.orEmpty(),
    )

    private fun readLesson(l: JSONObject) = Lesson(
        explain = l.optJSONArray("explain")?.list { LessonCard(it.getString("title"), it.getString("body"), it.optJSONArray("figures")?.strings().orEmpty()) }.orEmpty(),
        example = l.optJSONObject("example")?.let { e ->
            WorkedExample(e.getString("question"), e.getJSONArray("steps").list { ExampleStep(it.getString("text"), it.optString("why")) }, e.optString("answer"))
        },
        yourTurn = l.optJSONArray("yourTurn")?.list { p ->
            PracticeStep(
                p.getString("question"), p.getString("answer"),
                p.optJSONArray("accept")?.let { a -> List(a.length()) { a.getJSONArray(it).strings() } }.orEmpty(),
                p.optJSONArray("hints")?.strings().orEmpty(), p.optJSONArray("solution")?.strings().orEmpty(),
            )
        }.orEmpty(),
        examTips = l.optJSONArray("examTips")?.strings().orEmpty(),
        mistakes = l.optJSONArray("mistakes")?.strings().orEmpty(),
        recap = l.optJSONArray("recap")?.list { RecapItem(it.getString("question"), it.getString("answer")) }.orEmpty(),
    )

    fun readWritten(q: JSONObject) = WrittenQuestion(
        id = q.getString("id"), text = q.getString("text"), marks = q.getDouble("marks"),
        modelAnswer = q.getJSONArray("modelAnswer").strings(),
        rubric = q.getJSONArray("rubric").list { c ->
            RubricCriterion(
                c.getString("id"), c.getString("description"), c.getDouble("marks"),
                c.optJSONArray("accept")?.let { a -> List(a.length()) { a.getJSONArray(it).strings() } }.orEmpty(),
                c.getString("why"), c.getString("how"),
            )
        },
        followUp = q.getString("followUp"), noteId = q.textOrNull("noteId"),
        section = q.optInt("section", 0), number = q.optInt("number", 0),
        pastPapers = q.optJSONArray("tags")?.strings().orEmpty(), pastRefs = refs(q),
    )

    private fun refs(q: JSONObject): Map<String, String> =
        q.optJSONObject("refs")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()

    fun readMcq(q: JSONObject) = McqQuestion(
        q.getString("id"), q.getString("stem"), q.getJSONArray("options").strings(), q.getInt("correctIndex"),
        q.getJSONArray("rationales").strings(), status(q.optString("status")), q.textOrNull("noteId"),
        moduleId = if (q.has("moduleId")) q.textOrNull("moduleId") else null,
        pastPapers = q.optJSONArray("tags")?.strings().orEmpty(), pastRefs = refs(q),
    )

    fun readNote(n: JSONObject) = ConceptNote(
        n.getString("id"), n.getString("subjectId"), n.getString("moduleId"), n.getString("title"),
        n.getJSONArray("keywords").strings(), n.getString("answer"), n.getString("explanation"), n.getString("simple"),
        n.getJSONArray("examples").strings(), n.getJSONArray("steps").strings(),
        n.getString("checkQuestion"), n.getString("checkAnswer"),
        n.getJSONArray("checkAccept").let { a -> List(a.length()) { a.getJSONArray(it).strings() } },
    )

    fun readBlueprint(b: JSONObject) = PaperBlueprint(
        id = b.getString("id"), title = b.getString("title"), durationMinutes = b.getInt("durationMinutes"),
        totalMarks = b.getDouble("totalMarks"),
        sections = b.getJSONArray("sections").list { s ->
            BlueprintSection(s.getString("type"), s.getInt("count"), s.getInt("attempt"), s.getDouble("marksEach"),
                if (s.isNull("minutes")) null else s.getInt("minutes"))
        },
        official = b.getBoolean("official"), chapterId = b.textOrNull("chapterId"),
    )

    fun readPaper(p: JSONObject) = PaperContent(
        p.getString("blueprintId"), p.getJSONArray("mcqs").list(::readMcq), p.getJSONArray("written").list(::readWritten),
    )

    /** Parses a downloaded bundle. Throws if it is not a complete, readable bundle of a known format. */
    fun repository(bundle: JSONObject, version: Int, base: ParhlouRepository): BundleRepository {
        require(bundle.optInt("format") == FORMAT) { "unsupported bundle format" }
        return BundleRepository(
            version = version,
            subject = readSubject(bundle.getJSONObject("subject")),
            notes = bundle.getJSONArray("notes").list(::readNote),
            quiz = bundle.getJSONArray("quiz").list(::readMcq),
            readiness = bundle.getJSONArray("readiness").list(::readMcq),
            blueprints = bundle.getJSONArray("blueprints").list(::readBlueprint),
            papers = bundle.getJSONArray("papers").list(::readPaper),
            base = base,
            // Every written question of the course, past-paper questions included (they feed the paper engine).
            written = bundle.optJSONArray("written")?.list(::readWritten).orEmpty(),
        )
    }
}

/**
 * Content downloaded from the server for one subject. Anything it doesn't cover (the list of boards,
 * other subjects) comes from [base], the content packed inside the app.
 */
class BundleRepository(
    val version: Int,
    private val subject: Subject,
    private val notes: List<ConceptNote>,
    private val quiz: List<McqQuestion>,
    private val readiness: List<McqQuestion>,
    private val blueprints: List<PaperBlueprint>,
    private val papers: List<PaperContent>,
    private val base: ParhlouRepository,
    private val written: List<WrittenQuestion> = emptyList(),
) : ParhlouRepository {
    private val modules = subject.chapters.flatMap { it.modules }
    val subjectId: String get() = subject.id

    /** The same course on top of another repository, so several subjects' bundles stack into one repository. */
    fun over(newBase: ParhlouRepository) = BundleRepository(version, subject, notes, quiz, readiness, blueprints, papers, newBase, written)

    override fun writtenBank(subjectId: String): List<WrittenQuestion> = if (subjectId == subject.id) written else base.writtenBank(subjectId)
    override fun mcqBank(subjectId: String): List<McqQuestion> = if (subjectId == subject.id) quiz else base.mcqBank(subjectId)

    companion object {
        /** Every subject's bundle in one repository; the packed content is the bottom layer. */
        fun stack(bundles: Collection<BundleRepository>, base: ParhlouRepository): ParhlouRepository =
            bundles.fold(base) { below, b -> b.over(below) }
    }

    override fun boards() = base.boards()
    // A subject becomes selectable as soon as its course is on the phone.
    override fun subjectOptions(boardCode: String, grade: Int) =
        base.subjectOptions(boardCode, grade).map { if (it.id == subject.id) it.copy(available = true, note = "") else it }
    override fun subject(id: String) = if (id == subject.id) subject else base.subject(id)
    override fun module(id: String) = modules.firstOrNull { it.id == id } ?: base.module(id)
    override fun chapterOf(moduleId: String) = subject.chapters.firstOrNull { c -> c.modules.any { it.id == moduleId } } ?: base.chapterOf(moduleId)
    override fun modulesOfChapter(chapterId: String?) =
        subject.chapters.firstOrNull { it.id == chapterId }?.modules ?: base.modulesOfChapter(chapterId)
    override fun moduleQuiz(moduleId: String, count: Int): List<McqQuestion> {
        if (modules.none { it.id == moduleId }) return base.moduleQuiz(moduleId, count)
        // The module's own questions; older bundles without module links share one quiz, as before.
        if (quiz.none { it.moduleId != null }) return quiz.take(count.coerceIn(5, 15))
        return quiz.filter { it.moduleId == moduleId }.take(count.coerceIn(5, 15))
    }
    override fun readinessQuiz(subjectId: String) = if (subjectId == subject.id) readiness else base.readinessQuiz(subjectId)
    override fun blueprints(subjectId: String) = if (subjectId == subject.id) blueprints else base.blueprints(subjectId)
    override fun paper(blueprintId: String) =
        if (blueprints.any { it.id == blueprintId }) papers.firstOrNull { it.blueprintId == blueprintId } else base.paper(blueprintId)
    override fun notes(subjectId: String) = if (subjectId == subject.id) notes else base.notes(subjectId)
}
