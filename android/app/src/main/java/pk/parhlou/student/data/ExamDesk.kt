package pk.parhlou.student.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.domain.CheckMaster
import pk.parhlou.student.domain.Evidence
import pk.parhlou.student.domain.ExamEventType
import pk.parhlou.student.domain.PaperResult
import pk.parhlou.student.domain.PaperSubmission
import java.io.File

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
) {
    fun draftKey(paperId: String, questionId: String) = "exam_${paperId}_$questionId"

    private fun photoDir(paperId: String, questionId: String) = File(context.filesDir, "exam/$paperId/$questionId")

    fun photos(paperId: String, questionId: String): List<File> =
        photoDir(paperId, questionId).listFiles()?.sortedBy { it.name }.orEmpty()

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
        )
    }

    suspend fun check(blueprint: PaperBlueprint, paper: PaperContent, submission: PaperSubmission): PaperResult =
        CheckMaster.check(blueprint, paper, submission, ai)

    /** Hands the paper in: closes the invigilator log, runs the first check and records demo-checked evidence. */
    suspend fun submit(blueprint: PaperBlueprint, paper: PaperContent, now: Long = System.currentTimeMillis()): PaperResult {
        store.appendExamEvent(blueprint.id, ExamEventType.SUBMITTED, now)
        val result = check(blueprint, paper, submission(paper))
        store.saveFirstResult(
            blueprint.id,
            result.written.associate { it.question.id to it.mark.awarded } + ("mcq" to result.mcqMarks) + ("total" to result.confirmed),
        )
        val evidence = Evidence.DemoCheckedExam(result.percent)
        progress.add(ProgressStore.PAPER_PREFIX + blueprint.id, evidence, now)
        repo.modulesOfChapter(blueprint.chapterId).forEach { progress.add(it.id, evidence, now) }
        return result
    }

    /** Clears answers, photos and the log of one paper so it can be sat again. */
    suspend fun reset(paper: PaperContent) {
        val id = paper.blueprintId
        store.resetExam(id, paper.written.map { draftKey(id, it.id) })
        withContext(Dispatchers.IO) { File(context.filesDir, "exam/$id").deleteRecursively() }
    }
}
