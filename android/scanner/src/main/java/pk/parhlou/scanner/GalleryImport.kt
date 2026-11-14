package pk.parhlou.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.parhlou.scanner.vision.DocVision
import java.io.File
import java.util.UUID

/**
 * Copies photos chosen from the gallery into the store's folder, upright size read from EXIF, with the page found
 * in each when page detection is available on this phone (otherwise the photo is kept as taken).
 */
suspend fun importFromGallery(context: Context, store: PageStore, uris: List<Uri>): List<Photo> = withContext(Dispatchers.IO) {
    val detect = DocVision.ready()
    uris.mapNotNull { uri ->
        runCatching {
            val file = File(store.photoDir(), "gallery-${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) { file.delete(); return@runCatching null }
            val turned = ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in
                setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270)
            val (w, h) = if (turned) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
            val corners = if (!detect) null else runCatching {
                val bmp = DocVision.load(file, 1600)
                DocVision.detectBitmap(bmp).also { bmp.recycle() }?.map { it.toFloat() }
            }.getOrNull()
            Photo(file, w, h, corners)
        }.getOrNull()
    }
}
