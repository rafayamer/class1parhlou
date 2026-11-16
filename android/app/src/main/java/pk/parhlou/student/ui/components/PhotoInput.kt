package pk.parhlou.student.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Turns a photographed question into what Ask Ustaad sends: a JPEG data URL, at most [maxSide] px on its longest side. */
object PhotoInput {
    fun dataUrl(context: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 80): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val turned = if (rotation != 0) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else raw
        encode(scale(turned, maxSide), quality)
    }.getOrNull()

    /** A small copy for showing in the chat and saving with it. */
    fun thumbnail(dataUrl: String, maxSide: Int = 360): String? = runCatching {
        val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
        encode(scale(BitmapFactory.decodeByteArray(bytes, 0, bytes.size), maxSide), 70)
    }.getOrNull()

    fun bitmap(dataUrl: String): Bitmap? = runCatching {
        val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** A file the camera app can write the photo to (shared through the app's FileProvider). */
    fun cameraFile(context: Context): File = File(context.cacheDir, "ustaad").apply { mkdirs() }.let { File(it, "question.jpg") }

    private fun scale(b: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(b.width, b.height)
        if (side <= maxSide) return b
        val f = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    private fun encode(b: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
