// Adapted from sammyZi/pdf-tools DocVision.kt (MIT, Copyright (c) 2026 SammyZi) at commit 6a48d60.
// Kept: page detection on a photo, loading upright, perspective crop, quarter turns and the Adobe-style filters.
// Removed: strokes, brightness/contrast, PDF auto-crop. See scanner/UPSTREAM.md.
package pk.parhlou.scanner.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Filters offered after a scan, in the order shown. */
val FILTERS = listOf("auto" to "Auto colour", "light" to "Light text", "gray" to "Grayscale", "original" to "Original")

// Corners are always [x0,y0, x1,y1, x2,y2, x3,y3] as fractions of the upright image (top-left, top-right, bottom-right, bottom-left).
object DocVision {
  // Loads libopencv_java4. Anything that builds a Mat outside this object must call ready() first.
  private val loaded: Boolean by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

  fun ready(): Boolean = loaded

  /** Points the page-corner model at the bundled asset. Safe to call more than once. */
  fun init(context: Context) {
    val app = context.applicationContext
    if (DocModel.source == null) DocModel.source = { app.assets.open(DocModel.ASSET).use { it.readBytes() } }
  }

  // Finds the page in a (possibly huge) photo; detection itself runs at ~360 px.
  fun detectBitmap(bmp: Bitmap): DoubleArray? {
    if (!ready()) return null
    val scale = 720.0 / max(bmp.width, bmp.height)
    val small = if (scale < 1) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true) else bmp
    val rgba = Mat()
    Utils.bitmapToMat(small, rgba)
    Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_RGBA2RGB)
    return PageDetector.detect(rgba).also { rgba.release(); if (small !== bmp) small.recycle() }
  }

  // Decodes upright (EXIF applied), downsampled so the long side stays near maxSize.
  fun load(file: File, maxSize: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
      ?: throw IllegalArgumentException("Not an image: ${file.name}")
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
    return rotate(bmp, degrees)
  }

  fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bmp
    val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    if (out !== bmp) bmp.recycle()
    return out
  }

  /** Crops to [corners], turns by [rotation] degrees, applies [filter] and writes a JPEG to [dest]. Returns its size. */
  fun process(src: File, corners: List<Float>?, rotation: Int, filter: String, dest: File, maxSize: Int = 2400): Pair<Int, Int> {
    check(ready()) { "OpenCV is not available on this phone." }
    val bmp = load(src, maxSize)
    val rgba = Mat()
    Utils.bitmapToMat(bmp, rgba)
    bmp.recycle()
    var img = Mat()
    Imgproc.cvtColor(rgba, img, Imgproc.COLOR_RGBA2RGB)
    rgba.release()
    corners?.takeIf { it.size == 8 }?.let { c -> img = warp(img, DoubleArray(8) { c[it].toDouble() }) }
    when (((rotation % 360) + 360) % 360) {
      90 -> Core.rotate(img, img, Core.ROTATE_90_CLOCKWISE)
      180 -> Core.rotate(img, img, Core.ROTATE_180)
      270 -> Core.rotate(img, img, Core.ROTATE_90_COUNTERCLOCKWISE)
    }
    filter(img, filter)
    dest.parentFile?.mkdirs()
    val bgr = Mat()
    Imgproc.cvtColor(img, bgr, Imgproc.COLOR_RGB2BGR)
    Imgcodecs.imwrite(dest.absolutePath, bgr, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 88))
    bgr.release()
    return (img.cols() to img.rows()).also { img.release() }
  }

  private fun warp(src: Mat, c: DoubleArray): Mat {
    val w = src.cols().toDouble()
    val h = src.rows().toDouble()
    // Re-sort in image space so a crop dragged "inside out" still comes out upright.
    val p = PageDetector.order(Array(4) { Point(c[it * 2].coerceIn(0.0, 1.0) * w, c[it * 2 + 1].coerceIn(0.0, 1.0) * h) })
    val dist = { a: Point, b: Point -> hypot(a.x - b.x, a.y - b.y) }
    val outW = max(dist(p[0], p[1]), dist(p[3], p[2])).coerceAtLeast(16.0)
    val outH = max(dist(p[0], p[3]), dist(p[1], p[2])).coerceAtLeast(16.0)
    val m = Imgproc.getPerspectiveTransform(
      MatOfPoint2f(*p),
      MatOfPoint2f(Point(0.0, 0.0), Point(outW, 0.0), Point(outW, outH), Point(0.0, outH)),
    )
    val out = Mat()
    Imgproc.warpPerspective(src, out, m, Size(outW, outH), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
    src.release()
    m.release()
    return out
  }

  // Divides each channel by a blurred estimate of the paper, so shadows and uneven light go flat white.
  private fun flatten(img: Mat) {
    val channels = ArrayList<Mat>()
    Core.split(img, channels)
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    for (ch in channels) {
      val bg = Mat()
      Imgproc.resize(ch, bg, Size(), 0.125, 0.125, Imgproc.INTER_AREA)
      Imgproc.dilate(bg, bg, kernel) // erase text strokes from the estimate
      Imgproc.medianBlur(bg, bg, 9)
      Imgproc.resize(bg, bg, ch.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
      Core.divide(ch, bg, ch, 255.0)
      bg.release()
    }
    Core.merge(channels, img)
    channels.forEach { it.release() }
    kernel.release()
  }

  private fun saturate(img: Mat, factor: Double) {
    Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2HSV)
    val ch = ArrayList<Mat>()
    Core.split(img, ch)
    ch[1].convertTo(ch[1], -1, factor, 0.0)
    Core.merge(ch, img)
    ch.forEach { it.release() }
    Imgproc.cvtColor(img, img, Imgproc.COLOR_HSV2RGB)
  }

  private fun toGray(img: Mat) {
    Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2GRAY)
    Imgproc.cvtColor(img, img, Imgproc.COLOR_GRAY2RGB)
  }

  private fun filter(img: Mat, name: String) {
    when (name) {
      "auto" -> { flatten(img); img.convertTo(img, -1, 1.12, -14.0); saturate(img, 1.2) }
      "light" -> { flatten(img); toGray(img); Core.LUT(img, lightTextCurve, img) }
      "gray" -> { toGray(img); Core.normalize(img, img, 0.0, 255.0, Core.NORM_MINMAX) }
    }
  }

  // Near-white goes pure white; everything darker is pushed down hard, so faint pencil and print become bold.
  private val lightTextCurve: Mat by lazy {
    Mat(1, 256, CvType.CV_8U).apply {
      put(0, 0, ByteArray(256) { i -> (if (i >= 232) 255.0 else 255.0 * Math.pow(i / 232.0, 2.6)).roundToInt().toByte() })
    }
  }
}
