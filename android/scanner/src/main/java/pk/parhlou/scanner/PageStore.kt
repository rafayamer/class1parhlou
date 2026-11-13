package pk.parhlou.scanner

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One scanned answer page. The original photo is never changed; crop, turn and filter are settings that are
 * rendered to [out], so they can be redone any time. [questionId] says which answer the page belongs to.
 */
data class ScanPage(
    val id: String,
    val src: String,
    val width: Int,
    val height: Int,
    /** What page detection found, for "Auto-detect" in the crop screen. */
    val detected: List<Float>? = null,
    /** Null = no crop. */
    val corners: List<Float>? = null,
    val rotation: Int = 0,
    val filter: String = "auto",
    /** Bumped on every edit; a render only counts if it was made from the current settings. */
    val rev: Int = 1,
    val out: String? = null,
    val outRev: Int? = null,
    val questionId: String? = null,
) {
    val rendered: Boolean get() = out != null && outRev == rev
    /** The file to show and to send: the render when it is current, else the original photo. */
    val shown: String get() = if (rendered) out!! else src
}

/**
 * The scanned pages of one paper, kept in app-private storage and saved after every change, so a scan survives the
 * app closing. Not thread-safe across processes; one store per folder.
 */
class PageStore(val dir: File) {
    private val index = File(dir, "pages.json")
    private var pages: List<ScanPage> = load()

    @Synchronized fun pages(): List<ScanPage> = pages

    @Synchronized fun page(id: String): ScanPage? = pages.firstOrNull { it.id == id }

    /** Folder new camera photos are written to before they are added. */
    fun photoDir(): File = File(dir, "photos").apply { mkdirs() }

    /** Adds a captured photo at the end, cropped to the detected page. Returns null when the paper's page limit is reached. */
    @Synchronized fun add(photo: Photo): ScanPage? {
        if (pages.size >= MAX_PAGES) return null
        val page = ScanPage(newId(), adopt(photo.file).path, photo.width, photo.height, photo.corners, photo.corners)
        save(pages + page)
        return page
    }

    /** Replaces a page's photo (retake), keeping its place, question and filter. */
    @Synchronized fun retake(id: String, photo: Photo): ScanPage? {
        val old = page(id) ?: return null
        val next = old.copy(src = adopt(photo.file).path, width = photo.width, height = photo.height,
            detected = photo.corners, corners = photo.corners, rotation = 0, rev = old.rev + 1)
        save(pages.map { if (it.id == id) next else it })
        clean()
        return next
    }

    /** Changes a page's crop, turn or filter. */
    @Synchronized fun edit(id: String, change: (ScanPage) -> ScanPage) {
        save(pages.map { if (it.id == id) change(it).copy(rev = it.rev + 1) else it })
    }

    /** Records a finished render; ignored when the page was edited again meanwhile. */
    @Synchronized fun rendered(id: String, rev: Int, out: File): Boolean {
        val p = page(id)
        if (p == null || p.rev != rev) { out.delete(); return false }
        save(pages.map { if (it.id == id) it.copy(out = out.path, outRev = rev) else it })
        clean()
        return true
    }

    /** The page couldn't be processed (e.g. an unreadable photo): it is shown and sent as photographed. */
    @Synchronized fun renderFailed(id: String, rev: Int) {
        if (page(id)?.rev != rev) return
        save(pages.map { if (it.id == id) it.copy(out = it.src, outRev = rev) else it })
    }

    @Synchronized fun assign(id: String, questionId: String?) {
        save(pages.map { if (it.id == id) it.copy(questionId = questionId) else it })
    }

    /** Moves a page [by] places (negative = earlier). */
    @Synchronized fun move(id: String, by: Int) {
        val from = pages.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + by).coerceIn(0, pages.lastIndex)
        if (to == from) return
        val list = pages.toMutableList()
        list.add(to, list.removeAt(from))
        save(list)
    }

    @Synchronized fun delete(id: String) {
        save(pages.filterNot { it.id == id })
        clean()
    }

    /** Pages of one answer, in scan order. */
    @Synchronized fun filesFor(questionId: String): List<File> =
        pages.filter { it.questionId == questionId }.map { File(it.shown) }

    @Synchronized fun unassigned(): List<ScanPage> = pages.filter { it.questionId == null }

    /** Removes every page and photo. */
    @Synchronized fun clear() {
        dir.deleteRecursively()
        pages = emptyList()
    }

    // Photos and renders stay on disk while a page points at them; this sweeps the rest.
    private fun clean() {
        val keep = pages.flatMap { listOfNotNull(it.src, it.out) }.map { File(it).name }.toSet()
        listOf(File(dir, "pages"), File(dir, "renders")).forEach { d ->
            d.listFiles()?.forEach { if (it.isFile && it.name !in keep) it.delete() }
        }
    }

    /** Moves a camera photo into the store's own folder. */
    private fun adopt(file: File): File {
        val dest = File(File(dir, "pages").apply { mkdirs() }, "src-${newId()}.jpg")
        if (!file.renameTo(dest)) { file.copyTo(dest, overwrite = true); file.delete() }
        return dest
    }

    /** A new file for a render of a page (a new name each time, so an image never shows a stale copy). */
    fun renderFile(): File = File(File(dir, "renders").apply { mkdirs() }, "out-${newId()}.jpg")

    private fun save(next: List<ScanPage>) {
        pages = next
        dir.mkdirs()
        val tmp = File(dir, "pages.json.tmp")
        tmp.writeText(JSONArray(next.map(::toJson)).toString())
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
    }

    private fun load(): List<ScanPage> = runCatching {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.filter { File(it.src).isFile }
    }.getOrDefault(emptyList())

    companion object {
        /** Enough for a long paper: a full-book mock answered on loose sheets. */
        const val MAX_PAGES = 40

        private fun newId() = UUID.randomUUID().toString().take(12)

        private fun floats(list: List<Float>?) = list?.let { JSONArray(it.map(Float::toDouble)) }
        private fun floats(json: JSONObject, key: String): List<Float>? =
            json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }

        private fun toJson(p: ScanPage) = JSONObject().apply {
            put("id", p.id); put("src", p.src); put("width", p.width); put("height", p.height)
            floats(p.detected)?.let { put("detected", it) }
            floats(p.corners)?.let { put("corners", it) }
            put("rotation", p.rotation); put("filter", p.filter); put("rev", p.rev)
            p.out?.let { put("out", it) }
            p.outRev?.let { put("outRev", it) }
            p.questionId?.let { put("questionId", it) }
        }

        private fun fromJson(j: JSONObject) = ScanPage(
            id = j.getString("id"), src = j.getString("src"), width = j.getInt("width"), height = j.getInt("height"),
            detected = floats(j, "detected"), corners = floats(j, "corners"),
            rotation = j.optInt("rotation"), filter = j.optString("filter", "auto"), rev = j.optInt("rev", 1),
            out = j.optString("out").ifEmpty { null }, outRev = if (j.has("outRev")) j.getInt("outRev") else null,
            questionId = j.optString("questionId").ifEmpty { null },
        )
    }
}
