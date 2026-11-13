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

    /** Hands the paper in: closes the invigilator log, runs the first check and records demo-checked evidence. */
    suspend fun submit(blueprint: PaperBlueprint, paper: PaperContent, now: Long = System.currentTimeMillis()): PaperResult {
        store.appendExamEvent(blueprint.id, ExamEventType.SUBMITTED, now)
        val submission = submission(paper)
        val result = check(blueprint, paper, submission)
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
    private fun toDataUrl(file: File): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
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
    suspend fun reset(paper: PaperContent) {
        val id = paper.blueprintId
        store.resetExam(id, paper.written.map { draftKey(id, it.id) })
        scanStore(id).clear()
        withContext(Dispatchers.IO) { File(context.filesDir, "exam/$id").deleteRecursively() }
    }
}
