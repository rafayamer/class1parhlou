package pk.parhlou.student.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.domain.CheckMaster
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.PaperResult
import pk.parhlou.student.domain.TOPIC_PREFIX
import pk.parhlou.student.domain.ExamEventType
import pk.parhlou.student.domain.PaperSubmission
import pk.parhlou.student.domain.BoardNumbering
import pk.parhlou.student.domain.ScriptPage
import pk.parhlou.student.domain.ScriptReading
import java.io.File
import pk.parhlou.scanner.PageStore

/**
 * Everything a paper needs on the phone: saved answers, attached page photos, the invigilator log and
 * the hand-over to Check Master. Photos are copied into app-private storage and never leave the phone
 * in this prototype.
 */
class ExamDesk(
    private val context: Context,
    private val repo: ParhlouRepository,
    private val store: LocalStore,
    private val ai: ParhlouAi,
    private val progress: ProgressStore,
    /** Marks the server recorded for a paper (question id → awarded, max); empty when not signed in or offline. */
    private val serverMarks: suspend (paperId: String) -> Map<String, Pair<Double, Double>> = { emptyMap() },
) {
    private companion object {
        const val MAX_PHOTOS = 5
        const val MAX_PHOTO_SIDE = 1600
        /** Handwriting needs a sharper page than a photo answer; still well under the server's size limit. */
        const val SCRIPT_PAGE_SIDE = 2000
    }

    fun draftKey(paperId: String, questionId: String) = "exam_${paperId}_$questionId"

    private fun photoDir(paperId: String, questionId: String) = File(context.filesDir, "exam/$paperId/$questionId")

    private val scans = mutableMapOf<String, PageStore>()

    /** Pages scanned with the camera for one paper; each is matched to a question by the student. */
    fun scanStore(paperId: String): PageStore = synchronized(scans) {
        scans.getOrPut(paperId) { PageStore(File(context.filesDir, "exam/$paperId/_scan")) }
    }

    /** Photos added from the gallery for one answer. */
    fun galleryPhotos(paperId: String, questionId: String): List<File> =
        photoDir(paperId, questionId).listFiles()?.sortedBy { it.name }.orEmpty()

    /** Gallery photos of an answer, then its scanned pages in scan order. */
    fun photos(paperId: String, questionId: String): List<File> =
        galleryPhotos(paperId, questionId) + scanStore(paperId).filesFor(questionId)

    /** Scanned pages not yet matched to a question; they are not sent for marking. */
    fun unassignedScans(paperId: String): Int = scanStore(paperId).unassigned().size

    /** Copies picked images into private storage. Returns how many were added. */
    suspend fun addPhotos(paperId: String, questionId: String, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val dir = photoDir(paperId, questionId).apply { mkdirs() }
        var added = 0
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "page-${System.currentTimeMillis()}-$added.jpg").outputStream().use { input.copyTo(it) }
                    added++
                }
            }
        }
        added
    }

    suspend fun removePhotos(paperId: String, questionId: String) = withContext(Dispatchers.IO) {
        photoDir(paperId, questionId).deleteRecursively()
        Unit
    }

    suspend fun submission(paper: PaperContent): PaperSubmission {
        val id = paper.blueprintId
        return PaperSubmission(
            mcq = store.mcqChoices(id).first(),
            written = paper.written.associate { it.id to store.draft(draftKey(id, it.id)).first() },
            photos = paper.written.associate { it.id to photos(id, it.id).size },
            photoData = if (!ai.readsPhotos) emptyMap() else withContext(Dispatchers.IO) {
                paper.written.associate { q -> q.id to photos(id, q.id).take(MAX_PHOTOS).mapNotNull(::toDataUrl) }
            },
        )
    }

    suspend fun check(blueprint: PaperBlueprint, paper: PaperContent, submission: PaperSubmission): PaperResult =
        CheckMaster.check(blueprint, paper, submission, ai)

    // ── Papers written on paper: printed, answered by hand, scanned in any order and read by the AI examiner ──────
    /** The scanned pages of a script written on paper (all pages together, in any order). */
    fun printedStore(paperId: String): PageStore = synchronized(scans) {
        scans.getOrPut("$paperId/_printed") { PageStore(File(context.filesDir, "exam/$paperId/_printed")) }
    }

    private val readCache = mutableMapOf<String, ScriptPage>()
    private val handedIn = mutableMapOf<String, Pair<PaperSubmission, PaperResult>>()

    private val reports = mutableMapOf<String, pk.parhlou.student.domain.ExaminerReport>()

    /**
     * The examiner's written analysis of a marked paper: by the AI examiner when it is reachable, otherwise by the app from the
     * same numbers. One report per result, kept on the phone.
     */
    suspend fun examinerReport(paperTitle: String, paperId: String, analysis: pk.parhlou.student.domain.PaperAnalysis,
                               profile: String, language: String, name: String): pk.parhlou.student.domain.ExaminerReport {
        val facts = analysis.factsJson(paperTitle)
        val key = "report_${paperId}_${facts.hashCode()}"
        reports[key]?.let { return it }
        val topics = analysis.topics.map { it.title }
        store.draft(key).first().takeIf { it.isNotBlank() }?.let { saved ->
            runCatching { pk.parhlou.student.domain.ExaminerReport.parse(org.json.JSONObject(saved), topics.toSet(), org.json.JSONObject(saved).optBoolean("byAi")) }
                .getOrNull()?.let { reports[key] = it; return it }
        }
        val report = ai.paperReport(facts, topics, profile, language) ?: pk.parhlou.student.domain.ExaminerReport.local(analysis, name)
        reports[key] = report
        if (report.byAi) store.saveDraft(key, report.toJson())
        return report
    }

    /** The submission and result of the paper just handed in, so the result screen shows them without marking again. */
    fun handedIn(paperId: String): Pair<PaperSubmission, PaperResult>? = handedIn[paperId]

    /**
     * Reads every scanned page of a script (a page already read is not sent again). [onPage] reports progress.
     * Pages the AI could not read come back as UNREADABLE, so the student is asked to scan them again.
     */
    suspend fun readScript(blueprint: PaperBlueprint, paper: PaperContent, onPage: (Int, Int) -> Unit = { _, _ -> }): List<ScriptPage> {
        val numbering = BoardNumbering(blueprint)
        val questions = paper.written.sortedWith(compareBy({ it.section }, { it.number })).map { it.id to numbering.label(it) }
        val pages = printedStore(blueprint.id).pages()
        return pages.mapIndexed { i, p ->
            onPage(i + 1, pages.size)
            val key = "${blueprint.id}/${p.id}/${p.rev}/${p.shown}"
            readCache[key]?.copy(page = i + 1) ?: run {
                val image = withContext(Dispatchers.IO) { toDataUrl(File(p.shown), SCRIPT_PAGE_SIDE) }
                val read = image?.let { ai.readScriptPage(it, i + 1, questions, paper.mcqs.size) }
                read?.also { readCache[key] = it } ?: ScriptPage(i + 1, "ANSWERS", "UNREADABLE", emptyList(), emptyMap(), "")
            }
        }
    }

    /** The submission for a script on paper: the answers as read, and for each answer the pages it is written on. */
    suspend fun printedSubmission(paper: PaperContent, reading: ScriptReading): PaperSubmission {
        val files = printedStore(paper.blueprintId).pages().map { File(it.shown) }
        val data = withContext(Dispatchers.IO) {
            reading.pagesOf.mapValues { (_, pages) -> pages.take(MAX_PHOTOS).mapNotNull { files.getOrNull(it - 1)?.let { f -> toDataUrl(f, SCRIPT_PAGE_SIDE) } } }
        }
        return PaperSubmission(
            mcq = reading.mcq, written = reading.answers,
            photos = reading.pagesOf.mapValues { it.value.size }, photoData = data, handwritten = true,
        )
    }

    /** Hands in a paper written on paper. It counts as one of the paper's attempts, like a paper sat on the phone. */
    suspend fun submitPrinted(blueprint: PaperBlueprint, paper: PaperContent, reading: ScriptReading, now: Long = System.currentTimeMillis()): PaperResult {
        store.appendExamEvent(blueprint.id, ExamEventType.STARTED, now)
        reading.mcq.forEach { (q, choice) -> store.saveMcqChoice(blueprint.id, q, choice) }
        reading.answers.forEach { (q, text) -> store.saveDraft(draftKey(blueprint.id, q), text) }
        return submit(blueprint, paper, now, printedSubmission(paper, reading))
    }

    /** Hands the paper in: closes the invigilator log, runs the first check and records demo-checked evidence. */
    suspend fun submit(blueprint: PaperBlueprint, paper: PaperContent, now: Long = System.currentTimeMillis(), given: PaperSubmission? = null): PaperResult {
        store.appendExamEvent(blueprint.id, ExamEventType.SUBMITTED, now)
        val submission = given ?: submission(paper)
        val result = check(blueprint, paper, submission)
        handedIn[blueprint.id] = submission to result
        store.saveFirstResult(
            blueprint.id,
            result.written.associate { it.question.id to it.mark.awarded } + ("mcq" to result.mcqMarks) + ("total" to result.confirmed),
        )
        ai.recordMcqs(blueprint.id, submission.mcq)
        // Only marks the server recorded itself count as verified ("AI-assessed"); everything else stays demo-checked.
        val verified = CheckMaster.verifiedPercent(result, serverMarks(blueprint.id), blueprint.sections.firstOrNull()?.marksEach ?: 1.0)
        val evidence = if (verified != null) Evidence.ExamMarked(verified) else Evidence.DemoCheckedExam(result.percent)
        progress.add(ProgressStore.PAPER_PREFIX + blueprint.id, evidence, now)
        topicEvidence(result).forEach { (note, ev) -> progress.add(TOPIC_PREFIX + note, ev, now) }
        repo.modulesOfChapter(blueprint.chapterId).forEach { progress.add(it.id, evidence, now) }
        return result
    }

    /** Downsizes a page photo and encodes it for the AI examiner. Null if the file is not a readable image. */
    private fun toDataUrl(file: File, side: Int = MAX_PHOTO_SIDE): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        var bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > side) bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * side / longest, bitmap.height * side / longest, true)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** Which tutor topics the paper shows as missed or mastered (only answered questions count). */
    private fun topicEvidence(result: PaperResult): List<Pair<String, Evidence>> {
        val out = mutableListOf<Pair<String, Evidence>>()
        result.mcq.filter { it.chosen != null }.forEach { o ->
            o.question.noteId?.let { out += it to (if (o.correct) Evidence.TopicCorrect else Evidence.TopicMissed) }
        }
        result.written.filter { it.counted }.forEach { o ->
            val note = o.question.noteId ?: return@forEach
            when {
                o.mark.criteria.any { it.status == pk.parhlou.student.ai.CriterionStatus.NOT_FOUND } -> out += note to Evidence.TopicMissed
                o.mark.status == pk.parhlou.student.ai.MarkStatus.CHECKED -> out += note to Evidence.TopicCorrect
            }
        }
        return out
    }

    /** Clears answers, photos and the log of one paper so it can be sat again. */
    /** Draft key that remembers a running paper is being written on paper. */
    fun modeKey(paperId: String) = "mode_$paperId"

    /** Starts the exam to be written on paper: the clock and the Ustaad lock begin now. */
    suspend fun startOnPaper(paperId: String, now: Long = System.currentTimeMillis()) {
        store.saveDraft(modeKey(paperId), "paper")
        store.appendExamEvent(paperId, ExamEventType.STARTED, now)
    }

    suspend fun reset(paper: PaperContent) {
        val id = paper.blueprintId
        store.resetExam(id, paper.written.map { draftKey(id, it.id) } + modeKey(id))
        scanStore(id).clear()
        printedStore(id).clear()
        withContext(Dispatchers.IO) { File(context.filesDir, "exam/$id").deleteRecursively() }
    }
}
