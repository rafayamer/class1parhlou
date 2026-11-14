// Adapted from sammyZi/pdf-tools DocCamera.kt (MIT, Copyright (c) 2026 SammyZi) at commit 6a48d60.
// Removed: QR reading and the front lens. Photos are saved to a folder the caller chooses. See scanner/UPSTREAM.md.
package pk.parhlou.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import pk.parhlou.scanner.vision.DocVision
import pk.parhlou.scanner.vision.PageDetector
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A photo from the camera, upright, with the page corners found in it (null when none). */
data class Photo(val file: File, val width: Int, val height: Int, val corners: List<Float>?)

// The live 3:4 camera: preview, page-edge detection on the analysis stream, and full-resolution capture.
// Results arrive on the analysis thread; the screen posts them to its own state.
class DocCamera(context: Context) {
  val preview = PreviewView(context).apply {
    scaleType = PreviewView.ScaleType.FIT_CENTER // the whole 3:4 frame, so the outline lines up with the page
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView: composites correctly under Compose
  }
  private val worker = Executors.newSingleThreadExecutor()
  private var provider: ProcessCameraProvider? = null
  private var capture: ImageCapture? = null
  private var frame = 0

  /** True once the preview is showing camera frames; the screen uses it to spot a camera that never starts. */
  val streaming = kotlinx.coroutines.flow.MutableStateFlow(false)
  /** Last camera error, in the camera's own words, for the "couldn't start" message. */
  @Volatile var lastError: String? = null

  @Volatile var detecting = true
  var onDetect: (List<Float>?) -> Unit = {}

  var torch = false
    set(value) {
      field = value
      capture?.flashMode = if (value) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

  /** Binds preview, capture and analysis to the back camera. */
  suspend fun bind(owner: LifecycleOwner) {
    val p = ProcessCameraProvider.awaitInstance(preview.context)
    val ratio = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    val previewCase = Preview.Builder().setResolutionSelector(ratio).build().also { it.surfaceProvider = preview.surfaceProvider }
    val captureCase = ImageCapture.Builder().setResolutionSelector(ratio).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    val analysis = ImageAnalysis.Builder().setResolutionSelector(ratio).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
      .also { it.setAnalyzer(worker, ::analyze) }
    p.unbindAll()
    if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) throw IllegalStateException("This phone has no back camera.")
    val camera = try {
      p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, UseCaseGroup.Builder().addUseCase(previewCase).addUseCase(captureCase).addUseCase(analysis).build())
    } catch (e: IllegalArgumentException) {
      // Some cheaper phones can't run preview, full-size capture and analysis together: drop live edge detection.
      p.unbindAll()
      p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, UseCaseGroup.Builder().addUseCase(previewCase).addUseCase(captureCase).build())
    }
    preview.previewStreamState.observe(owner) { streaming.value = it == PreviewView.StreamState.STREAMING }
    camera.cameraInfo.cameraState.observe(owner) { state -> state.error?.let { lastError = "camera error ${it.code}" } }
    provider = p
    capture = captureCase
  }

  fun release() {
    provider?.unbindAll()
    provider = null
    capture = null
    worker.shutdown()
  }

  private fun analyze(image: ImageProxy) {
    image.use {
      frame++
      if (!detecting || frame % 2 != 0 || !DocVision.ready()) return
      // Colour, not just luminance: many page/table edges are a colour change more than a brightness change.
      val bmp = it.toBitmap()
      val rgb = Mat()
      Utils.bitmapToMat(bmp, rgb)
      bmp.recycle()
      Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGBA2RGB)
      when (it.imageInfo.rotationDegrees) { // analysis frames arrive sensor-rotated; detect on the upright view
        90 -> Core.rotate(rgb, rgb, Core.ROTATE_90_CLOCKWISE)
        180 -> Core.rotate(rgb, rgb, Core.ROTATE_180)
        270 -> Core.rotate(rgb, rgb, Core.ROTATE_90_COUNTERCLOCKWISE)
      }
      val corners = runCatching { PageDetector.detect(rgb) }.getOrNull()
      rgb.release()
      onDetect(corners?.map { c -> c.toFloat() })
    }
  }

  /** A full-resolution photo, upright, saved as a JPEG in [dir] with the page found in it. */
  suspend fun takePicture(dir: File): Photo = suspendCancellableCoroutine { cont ->
    val c = capture ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("The camera is not ready yet."))
    c.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        try {
          val degrees = image.imageInfo.rotationDegrees.toFloat()
          val bmp = DocVision.rotate(image.toBitmap(), degrees)
          image.close()
          val file = File(dir.apply { mkdirs() }, "shot-${UUID.randomUUID()}.jpg")
          FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
          val corners = runCatching { DocVision.detectBitmap(bmp) }.getOrNull()?.map { it.toFloat() }
          cont.resume(Photo(file, bmp.width, bmp.height, corners))
          bmp.recycle()
        } catch (e: Exception) {
          cont.resumeWithException(e)
        }
      }

      override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
    })
  }
}
