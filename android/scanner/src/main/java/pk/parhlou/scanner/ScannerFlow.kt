package pk.parhlou.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.parhlou.scanner.vision.DocVision
import pk.parhlou.scanner.vision.FILTERS
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** A question a page can be assigned to, e.g. ("q-b3", "B3"). */
data class ScanQuestion(val id: String, val label: String)

/** Turns a page's settings into a finished image. The real one uses OpenCV; tests pass a fake. */
fun interface ScanEngine {
    /** Writes the cropped, turned and filtered page to [dest]; false when it can't. */
    fun render(page: ScanPage, dest: File): Boolean
}

val OpenCvEngine = ScanEngine { page, dest ->
    runCatching { DocVision.process(File(page.src), page.corners, page.rotation, page.filter, dest) }.isSuccess
}

private val FULL = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

/**
 * The answer-page scanner for one paper: camera with live page edges, then a review where pages are cropped, turned,
 * filtered, reordered, retaken, deleted and assigned to questions. Every change is saved as it happens.
 * [status] is shown at the top (e.g. the time left in the paper).
 */
@Composable
fun ScannerFlow(
    store: PageStore,
    questions: List<ScanQuestion>,
    status: String?,
    onClose: () -> Unit,
    engine: ScanEngine = OpenCvEngine,
    startWithCamera: Boolean = store.pages().isEmpty(),
) {
    val context = LocalContext.current
    remember { DocVision.init(context) }
    var version by remember { mutableIntStateOf(0) }
    val pages = remember(version) { store.pages() }
    var camera by remember { mutableStateOf(startWithCamera) }
    var retake by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<String?>(null) }
    val changed = { version++ }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val photos = importFromGallery(context, store, uris)
            val r = retake
            if (r != null && photos.isNotEmpty()) { store.retake(r, photos.first()); retake = null }
            else photos.forEach { store.add(it) }
            importing = false
            camera = false
            changed()
        }
    }
    val pickFromGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Render every page whose settings changed since its last render.
    LaunchedEffect(version) {
        pages.filterNot { it.rendered }.forEach { p ->
            val dest = store.renderFile()
            val ok = withContext(Dispatchers.Default) { engine.render(p, dest) }
            if (ok) { if (store.rendered(p.id, p.rev, dest)) changed() }
            else { dest.delete(); store.renderFailed(p.id, p.rev); changed() }
        }
    }

    BackHandler {
        when {
            cropping != null -> cropping = null
            camera && pages.isNotEmpty() -> { camera = false; retake = null }
            else -> onClose()
        }
    }

    val crop = cropping?.let { store.page(it) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            crop != null -> CropEditor(crop, onCancel = { cropping = null }) { corners ->
                store.edit(crop.id) { it.copy(corners = corners) }; cropping = null; changed()
            }
            camera || pages.isEmpty() -> CaptureScreen(
                store, status, count = pages.size, retaking = retake != null,
                onPhoto = { photo ->
                    val r = retake
                    if (r != null) { store.retake(r, photo); retake = null; camera = false } else store.add(photo)
                    changed()
                },
                onReview = { camera = false; retake = null },
                onClose = onClose,
                onGallery = pickFromGallery,
                importing = importing,
            )
            else -> ReviewScreen(
                pages, questions, status,
                onAssign = { id, q -> store.assign(id, q); changed() },
                onRotate = { id -> store.edit(id) { it.copy(rotation = (it.rotation + 90) % 360) }; changed() },
                onFilter = { id, f -> store.edit(id) { it.copy(filter = f) }; changed() },
                onMove = { id, by -> store.move(id, by); changed() },
                onDelete = { id -> store.delete(id); changed() },
                onCrop = { cropping = it },
                onRetake = { retake = it; camera = true },
                onAddMore = { camera = true },
                onGallery = pickFromGallery,
                onDone = onClose,
            )
        }
    }
}

// ── Capture ─────────────────────────────────────────────────────────────────

private const val STEADY_MS = 1100L // corners must hold still this long before auto-capture fires
private fun moved(a: List<Float>, b: List<Float>) = a.indices.maxOf { abs(a[it] - b[it]) }

private class Tracker {
    var last: List<Float>? = null
    var shown: List<Float>? = null
    var misses = 0
    var since = 0L
    var armed = true
    var busy = false
    var captured: List<Float>? = null
}

@Composable
private fun CaptureScreen(
    store: PageStore,
    status: String?,
    count: Int,
    retaking: Boolean,
    onPhoto: (Photo) -> Unit,
    onReview: () -> Unit,
    onClose: () -> Unit,
    onGallery: () -> Unit,
    importing: Boolean,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var corners by remember { mutableStateOf<List<Float>?>(null) }
    var auto by remember { mutableStateOf(false) }
    var steady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val full = count >= PageStore.MAX_PAGES && !retaking
    val track = remember { Tracker() }
    // "Try again" bumps this to build a fresh camera.
    var attempt by remember { mutableIntStateOf(0) }
    var cameraFailed by remember { mutableStateOf(false) }
    val cam = remember(granted, attempt) { if (granted) DocCamera(context) else null }
    // Page detection needs OpenCV's native library; when a phone can't load it, photos are kept as taken.
    val detection by produceState<Boolean?>(null) { value = withContext(Dispatchers.Default) { DocVision.ready() } }
    val latestAuto = remember { mutableStateOf(auto) }.apply { value = auto }

    val shoot = shoot@{
        val c = cam ?: return@shoot
        if (track.busy || full) return@shoot
        track.busy = true; track.armed = false; busy = true
        scope.launch {
            try {
                val photo = c.takePicture(store.photoDir())
                track.captured = photo.corners
                onPhoto(photo)
                error = null
            } catch (e: Exception) {
                error = "Couldn't take the photo. Try again."
            }
            track.busy = false; busy = false
        }
        Unit
    }

    val onDetect = { found0: List<Float>? ->
        val t = track
        val now = System.currentTimeMillis()
        var found = found0?.takeIf { it.size == 8 }
        // Steady outline: ease small movements, and ride out a couple of missed frames before letting go.
        val shownNow = t.shown
        if (found != null && shownNow != null && moved(found, shownNow) < 0.05f) found = found.mapIndexed { i, v -> shownNow[i] * 0.55f + v * 0.45f }
        if (!(found == null && t.shown != null && ++t.misses <= 3)) {
            t.misses = 0
            t.shown = found
            corners = found
            if (found == null) {
                t.last = null; t.since = 0; t.armed = true; steady = false
            } else {
                if (t.captured?.let { moved(found, it) > 0.12f } == true) t.armed = true // a different page is in view
                if (t.last == null || moved(found, t.last!!) > 0.02f) t.since = now
                t.last = found
                steady = now - t.since > 250 && latestAuto.value && t.armed
                if (latestAuto.value && t.armed && now - t.since > STEADY_MS) shoot()
            }
        }
    }

    // Watchdog: if no camera picture appears within a few seconds, say so instead of showing a black box.
    if (cam != null && !cameraFailed) {
        LaunchedEffect(cam) {
            kotlinx.coroutines.delay(6_000)
            if (!cam.streaming.value) {
                cam.lastError = cam.lastError ?: "the preview didn't start"
                cameraFailed = true
            }
        }
    }
    if (cam != null) {
        DisposableEffect(cam) {
            val main = ContextCompat.getMainExecutor(context)
            cam.onDetect = { c -> main.execute { onDetect(c) } }
            scope.launch {
                runCatching { cam.bind(owner) }.onFailure { cam.lastError = it.message ?: it.javaClass.simpleName; cameraFailed = true }
            }
            onDispose { cam.release() }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White) }
            Text(if (retaking) "Retake page" else "Scan answer pages", color = Color.White,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            status?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onGallery) { Text("Gallery", color = Color.White) }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (!granted || cam == null || cameraFailed) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        cameraFailed -> {
                            Text("The camera couldn't start on this phone.", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("Close other apps that use the camera and try again, or take the photos with your camera app and add them from the gallery.",
                                color = Color.White.copy(alpha = 0.85f))
                            cam?.lastError?.let { Text("Details: $it", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) }
                            Button(onClick = { cameraFailed = false; attempt++ }) { Text("Try again") }
                        }
                        else -> {
                            Text("Parhlou needs the camera to scan your answer pages. Photos stay on this phone until you hand in.",
                                color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            if (asked && !granted) Text("Camera permission was refused. You can allow it in the phone's Settings, or add photos from the gallery.",
                                color = Color.White.copy(alpha = 0.85f))
                            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                        }
                    }
                    OutlinedButton(onClick = onGallery) { Text("Add from gallery", color = Color.White) }
                }
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    AndroidView({ cam.preview }, Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val c = corners ?: return@Canvas
                        val pts = (0 until 4).map { Offset(c[it * 2] * size.width, c[it * 2 + 1] * size.height) }
                        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                        val colour = if (steady) Color(0xFF7BC67B) else Color.White
                        drawPath(path, colour.copy(alpha = 0.22f))
                        drawPath(path, colour, style = Stroke(width = 3.dp.toPx()))
                        pts.forEach { drawCircle(colour, 7.dp.toPx(), it) }
                    }
                }
            }
        }
        val hint = when {
            importing -> "Adding photos…"
            error != null -> error!!
            detection == false -> "Page edges can't be found automatically on this phone. Fill the frame with the page; photos are kept as taken."
            full -> "You've reached ${PageStore.MAX_PAGES} pages for this paper."
            busy -> "Saving…"
            corners == null -> "Fit one page in the frame on a dark surface."
            auto && steady -> "Hold steady…"
            auto -> "Hold still to capture"
            else -> "Page found. Tap the button to capture."
        }
        Text(hint, color = Color.White, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = auto, onCheckedChange = { auto = it })
                Spacer(Modifier.width(6.dp))
                Text(if (auto) "Auto" else "Manual", color = Color.White)
            }
            Surface(
                onClick = { shoot() }, enabled = granted && !cameraFailed && !busy && !full, shape = CircleShape,
                color = if (steady) Color(0xFF7BC67B) else Color.White, modifier = Modifier.size(72.dp).testTag("shutter"),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture page", tint = Color.Black) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (count > 0 && !retaking) TextButton(onClick = onReview) { Text("Review ($count)", color = Color.White) }
            }
        }
    }
}

// ── Review ──────────────────────────────────────────────────────────────────

@Composable
private fun ReviewScreen(
    pages: List<ScanPage>,
    questions: List<ScanQuestion>,
    status: String?,
    onAssign: (String, String?) -> Unit,
    onRotate: (String) -> Unit,
    onFilter: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onCrop: (String) -> Unit,
    onRetake: (String) -> Unit,
    onAddMore: () -> Unit,
    onGallery: () -> Unit,
    onDone: () -> Unit,
) {
    var deleting by remember { mutableStateOf<String?>(null) }
    val unassigned = pages.count { it.questionId == null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close scanner") }
            Text("Your pages (${pages.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            status?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
        if (unassigned > 0) {
            Text(
                "$unassigned ${if (unassigned == 1) "page isn't" else "pages aren't"} matched to a question yet. " +
                    "Choose the question each page answers, so it's marked with the right answer.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("scan-pages"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                PageCard(i, pages.size, p, questions,
                    onAssign = { onAssign(p.id, it) }, onRotate = { onRotate(p.id) }, onFilter = { onFilter(p.id, it) },
                    onMove = { onMove(p.id, it) }, onDelete = { deleting = p.id }, onCrop = { onCrop(p.id) }, onRetake = { onRetake(p.id) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddMore, enabled = pages.size < PageStore.MAX_PAGES,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Camera") }
            OutlinedButton(onClick = onGallery, enabled = pages.size < PageStore.MAX_PAGES,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Gallery") }
            Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Done") }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this page?") },
            text = { Text("The photo is removed from this phone.") },
            confirmButton = { TextButton(onClick = { onDelete(id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun PageCard(
    index: Int,
    total: Int,
    page: ScanPage,
    questions: List<ScanQuestion>,
    onAssign: (String?) -> Unit,
    onRotate: () -> Unit,
    onFilter: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onCrop: () -> Unit,
    onRetake: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("scan-page-$index"), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(File(page.shown), Modifier.size(width = 90.dp, height = 120.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Page ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    if (!page.rendered) Text("Preparing…", style = MaterialTheme.typography.bodySmall)
                    Box {
                        val label = questions.firstOrNull { it.id == page.questionId }?.label
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (label == null) "Choose question" else "Answer to $label")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            questions.forEach { q ->
                                DropdownMenuItem(text = { Text(q.label) }, onClick = { onAssign(q.id); menu = false })
                            }
                            if (page.questionId != null) DropdownMenuItem(text = { Text("Not matched yet") }, onClick = { onAssign(null); menu = false })
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FILTERS.forEach { (id, name) ->
                    FilterChip(selected = page.filter == id, onClick = { onFilter(id) }, label = { Text(name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCrop) { Icon(Icons.Filled.Crop, contentDescription = "Crop page ${index + 1}") }
                IconButton(onClick = onRotate) { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Turn page ${index + 1}") }
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move page ${index + 1} up") }
                IconButton(onClick = { onMove(1) }, enabled = index < total - 1) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move page ${index + 1} down") }
                IconButton(onClick = onRetake) { Icon(Icons.Filled.PhotoCamera, contentDescription = "Retake page ${index + 1}") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete page ${index + 1}") }
            }
        }
    }
}

@Composable
private fun Thumb(file: File, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file.path) { value = withContext(Dispatchers.IO) { decode(file, 400) } }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

private fun decode(file: File, max: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

// ── Crop ────────────────────────────────────────────────────────────────────

/** Drag the four corner handles; "Auto-detect" goes back to the found page, "No crop" keeps the whole photo. */
@Composable
private fun CropEditor(page: ScanPage, onCancel: () -> Unit, onDone: (List<Float>?) -> Unit) {
    var corners by remember(page.id) { mutableStateOf(page.corners ?: FULL) }
    val image by produceState<ImageBitmap?>(null, page.src) { value = withContext(Dispatchers.IO) { decode(File(page.src), 1600) } }
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
            Text("Crop", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDone(if (corners == FULL) null else corners) }) { Text("Done", color = Color.White) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
            val img = image ?: return@BoxWithConstraints
            val bw = with(density) { maxWidth.toPx() }
            val bh = with(density) { maxHeight.toPx() }
            val scale = min(bw / img.width, bh / img.height)
            val dw = img.width * scale
            val dh = img.height * scale
            val ox = (bw - dw) / 2
            val oy = (bh - dh) / 2
            var dragging by remember { mutableIntStateOf(-1) }
            Box(Modifier.fillMaxSize().pointerInput(page.id, dw, dh) {
                detectDragGestures(
                    onDragStart = { at ->
                        dragging = (0 until 4).minByOrNull { i -> hypot(ox + corners[i * 2] * dw - at.x, oy + corners[i * 2 + 1] * dh - at.y) } ?: -1
                    },
                    onDragEnd = { dragging = -1 },
                    onDrag = { change, delta ->
                        change.consume()
                        val i = dragging
                        if (i >= 0) corners = corners.toMutableList().also {
                            it[i * 2] = (it[i * 2] + delta.x / dw).coerceIn(0f, 1f)
                            it[i * 2 + 1] = (it[i * 2 + 1] + delta.y / dh).coerceIn(0f, 1f)
                        }
                    },
                )
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()))
                    val pts = (0 until 4).map { Offset(ox + corners[it * 2] * dw, oy + corners[it * 2 + 1] * dh) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, Color(0xFF7BC67B), style = Stroke(width = 3.dp.toPx()))
                    pts.forEach { drawCircle(Color.White, 12.dp.toPx(), it); drawCircle(Color(0xFF3B7614), 8.dp.toPx(), it) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { corners = page.detected ?: FULL }, enabled = page.detected != null, modifier = Modifier.weight(1f)) {
                Text("Auto-detect", color = Color.White)
            }
            OutlinedButton(onClick = { corners = FULL }, modifier = Modifier.weight(1f)) { Text("No crop", color = Color.White) }
        }
    }
}
