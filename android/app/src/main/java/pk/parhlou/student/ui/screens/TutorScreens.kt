package pk.parhlou.student.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.UstaadAnswer
import pk.parhlou.student.ai.LessonVideo
import pk.parhlou.student.ai.UstaadMessage
import pk.parhlou.student.ai.UstaadRequest
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.UstaadPrefs
import pk.parhlou.student.ui.components.FigureSpec
import pk.parhlou.student.ui.components.FigureView
import pk.parhlou.student.ui.components.BookFigure
import pk.parhlou.student.ui.components.MathText
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.PhotoInput
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill
import pk.parhlou.student.ui.components.Ustad
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.UstadSays

/** What another screen wants Ask Ustaad to start with: a question, a topic, or a wrong MCQ to explain. */
data class TutorSeed(
    val moduleId: String? = null,
    val noteId: String? = null,
    val question: String? = null,
    val mcq: McqQuestion? = null,
    val chosen: Int = 0,
)

/** One saved Ask Ustaad chat. */
data class UstaadChat(val id: String, val title: String, val updated: Long, val messages: List<UstaadMessage>)

/** Chats are saved on the phone as JSON; photos are kept only as small thumbnails. */
object UstaadChats {
    const val MAX_CHATS = 30

    fun parse(json: String): List<UstaadChat> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { i ->
            val c = a.getJSONObject(i)
            val m = c.getJSONArray("messages")
            UstaadChat(c.getString("id"), c.optString("title"), c.optLong("updated"), List(m.length()) { k ->
                val o = m.getJSONObject(k)
                UstaadMessage(o.getString("role"), o.optString("text"), o.optString("image").ifBlank { null },
                    o.optJSONObject("answer")?.let(UstaadAnswer::parse), o.optLong("at"))
            })
        }
    }.getOrDefault(emptyList())

    fun encode(chats: List<UstaadChat>): String = JSONArray(chats.take(MAX_CHATS).map { c ->
        JSONObject().put("id", c.id).put("title", c.title).put("updated", c.updated).put("messages", JSONArray(c.messages.map { m ->
            JSONObject().put("role", m.role).put("text", m.text).put("image", m.image ?: "").put("at", m.at)
                .put("answer", m.answer?.toJson() ?: JSONObject.NULL)
        }))
    }).toString()

    fun title(first: UstaadMessage?): String = when {
        first == null -> "New chat"
        first.text.isNotBlank() -> first.text.take(48)
        first.image != null -> "Photo question"
        else -> "New chat"
    }
}

private val starters = listOf("Explain logarithms simply", "How do I factorise x² + 5x + 6?", "Give me a practice question on sets")

/**
 * Ask Ustaad: a ChatGPT-style personal tutor. Type a question or send a photo of one; Ustaad answers like a tutor, with
 * Chegg-style steps (each with its "why"), the idea behind the question, an exam tip, a quick check, lesson videos and
 * follow-up suggestions. A textbook question is answered from its checked solution. Ustaad knows the student
 * ([profile]) and answers in their preferred language and style.
 */
@Composable
fun TutorScreen(
    ai: ParhlouAi,
    subjectIds: Set<String>,
    seed: TutorSeed?,
    examActive: Boolean,
    onBack: () -> Unit,
    store: LocalStore? = null,
    profile: () -> String = { "" },
    prefs: UstaadPrefs = UstaadPrefs(UstaadPrefs.LANGUAGES[0], UstaadPrefs.STYLES[0], 0),
    studentName: String = "",
    moduleOf: (String) -> Module? = { null },
    noteTitle: (String) -> String? = { null },
    onOpenSolution: (String, Int) -> Unit = { _, _ -> },
    onMyUstaad: () -> Unit = {},
    /** Builds the paper or notes PDF a student asked for; null where PDFs can't be made. */
    makePdf: (suspend (pk.parhlou.student.domain.PdfAction) -> java.io.File?)? = null,
    /** While a paper runs: go back to it, or quit it (cleared, no try used) to use Ustaad now. */
    onGoToPaper: (() -> Unit)? = null,
    onQuitPaper: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<UstaadMessage>() }
    var chatId by rememberSaveable { mutableStateOf("c${System.currentTimeMillis()}") }
    var chats by remember { mutableStateOf<List<UstaadChat>>(emptyList()) }
    var showChats by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val input = field.text
    // Starter buttons fill the box with the cursor at the end, ready for the student to finish the sentence.
    fun setInput(t: String) { field = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)) }
    var photo by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) { store?.ustaadChats?.collect { chats = UstaadChats.parse(it) } }

    fun save() {
        val s = store ?: return
        if (messages.isEmpty()) return
        val chat = UstaadChat(chatId, UstaadChats.title(messages.firstOrNull { it.role == "student" }), System.currentTimeMillis(), messages.toList())
        val all = listOf(chat) + chats.filter { it.id != chatId }
        chats = all
        scope.launch { s.saveUstaadChats(UstaadChats.encode(all)) }
    }

    fun send(text: String, image: String? = null, shown: String = text) {
        if (thinking || examActive || (text.isBlank() && image == null)) return
        val history = messages.toList()
        messages += UstaadMessage("student", shown, image?.let { PhotoInput.thumbnail(it) ?: it })
        thinking = true
        scope.launch {
            val answer = ai.ustaad(UstaadRequest(text, image, history, profile(), prefs.language, prefs.style, subjectIds,
                seed?.moduleId, examActive))
            messages += UstaadMessage("ustaad", answer.reply, answer = answer)
            thinking = false
            save()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) PhotoInput.dataUrl(context, uri).let { if (it == null) photoError = "That picture couldn't be opened. Try another." else photo = it }
    }
    val cameraUri = remember { runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", PhotoInput.cameraFile(context)) }.getOrNull() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && cameraUri != null) PhotoInput.dataUrl(context, cameraUri).let { if (it == null) photoError = "The photo couldn't be read. Please try again." else photo = it }
    }

    LaunchedEffect(seed) {
        if (messages.isNotEmpty() || seed == null || examActive) return@LaunchedEffect
        when {
            seed.mcq != null -> {
                messages += UstaadMessage("student", "Why was my answer wrong?")
                thinking = true
                val r = ai.mcqFeedback(seed.mcq, seed.chosen, subjectIds)
                messages += UstaadMessage("ustaad", r.answer, answer = UstaadAnswer("CHAT", listOfNotNull(r.answer, r.explanation).joinToString("\n\n"),
                    checkQuestion = r.checkQuestion.orEmpty(), suggestions = listOf("Explain more simply", "Give me a similar question")))
                thinking = false
                save()
            }
            seed.question != null -> send(seed.question)
            seed.noteId != null -> send("Explain this topic to me: ${noteTitle(seed.noteId) ?: "this topic"}")
        }
    }

    ParhlouScreen(
        title = "Ask Ustaad",
        onBack = { if (showChats) showChats = false else onBack() },
        scrollToEndKey = if (messages.isEmpty() || showChats) null else messages.size + (if (thinking) 1 else 0),
        bottomBar = if (showChats || examActive) null else ({
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                photo?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoInput.bitmap(p)?.let { Image(it.asImageBitmap(), "Photo to send", Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                        Text("Photo ready. Add a note or just send.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { photo = null }) { Icon(Icons.Filled.Close, contentDescription = "Remove photo") }
                    }
                }
                photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        IconButton(onClick = { photoMenu = true }, modifier = Modifier.size(48.dp).testTag("ustaad-photo")) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Send a photo of a question")
                        }
                        DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                            DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = {
                                photoMenu = false; photoError = null
                                cameraUri?.let { u -> try { camera.launch(u) } catch (_: ActivityNotFoundException) { photoError = "No camera app found. Choose from your gallery instead." } }
                            })
                            DropdownMenuItem(text = { Text("Choose from gallery") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) }, onClick = {
                                photoMenu = false; photoError = null
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }
                    OutlinedTextField(
                        value = field, onValueChange = { field = it },
                        placeholder = { Text(if (photo != null) "Add a note (optional)…" else "Ask Ustaad anything…") },
                        shape = RoundedCornerShape(24.dp), maxLines = 4,
                        modifier = Modifier.weight(1f).testTag("tutor-input"),
                    )
                    IconButton(
                        onClick = { val t = input.trim(); val p = photo; setInput(""); photo = null; send(t, p, t.ifBlank { "" }) },
                        enabled = (input.isNotBlank() || photo != null) && !thinking,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            IconButton(onClick = { showChats = !showChats }, modifier = Modifier.size(48.dp).testTag("ustaad-chats")) {
                Icon(Icons.Filled.History, contentDescription = "Your chats")
            }
            IconButton(onClick = { messages.clear(); chatId = "c${System.currentTimeMillis()}"; showChats = false }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        }

        if (showChats) {
            Text("Your chats", style = MaterialTheme.typography.titleLarge)
            if (chats.isEmpty()) Muted("No saved chats yet.")
            chats.forEach { c ->
                pk.parhlou.student.ui.components.TapCard(onClick = { messages.clear(); messages += c.messages; chatId = c.id; showChats = false },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Muted("${c.messages.size} messages")
                        }
                        IconButton(onClick = {
                            chats = chats.filter { it.id != c.id }
                            store?.let { s -> scope.launch { s.saveUstaadChats(UstaadChats.encode(chats)) } }
                        }) { Icon(Icons.Filled.Close, contentDescription = "Delete chat ${c.title}") }
                    }
                }
            }
            return@ParhlouScreen
        }

        if (examActive) {
            Sheet {
                Text("Ask Ustaad is locked", style = MaterialTheme.typography.titleMedium)
                Text("You have a paper in progress. Ustaad will help as soon as you hand it in.", style = MaterialTheme.typography.bodyLarge)
                onGoToPaper?.let { pk.parhlou.student.ui.components.BigButton("Back to my paper", onClick = it) }
                onQuitPaper?.let { QuitPaper(it) }
            }
            return@ParhlouScreen
        }

        if (messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Ustad(120.dp, UstadMood.HAPPY) }
            Text("Assalam-o-Alaikum${if (studentName.isNotBlank()) ", $studentName" else ""}!", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // Four big starts; everything else is just typing.
            val starts = listOf(
                Triple("📷", "Snap a question", "ustaad-photo-start") to { photoMenu = true },
                Triple("💡", "Explain a topic", "start-explain") to { setInput("Explain ") },
                Triple("📄", "Make me a paper", "start-paper") to { setInput("Make me a PDF paper on Unit ") },
                Triple("❓", "Quiz me", "start-quiz") to { send("Quiz me on what I studied last") },
            )
            starts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (t, go) ->
                        pk.parhlou.student.ui.components.TapCard(onClick = go, modifier = Modifier.weight(1f).testTag(t.third), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t.first, style = MaterialTheme.typography.headlineSmall)
                                Text(t.second, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }

        }

        messages.forEachIndexed { i, m ->
            if (m.role == "student") StudentBubble(m)
            else m.answer?.let { a -> UstaadBubble(a, moduleOf, onOpenSolution, latest = i == messages.lastIndex && !thinking, onSuggestion = { send(it) }, makePdf = makePdf) }
        }
        if (thinking) Thinking()
    }
}

@Composable
private fun StudentBubble(m: UstaadMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.widthIn(max = 290.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.image?.let { PhotoInput.bitmap(it) }?.let {
                    Image(it.asImageBitmap(), "Your photo of the question", Modifier.widthIn(max = 260.dp).heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)))
                }
                if (m.text.isNotBlank()) Text(m.text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun Thinking() {
    val t = rememberInfiniteTransition(label = "dots").animateFloat(0f, 3f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "d")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("ustaad-thinking")) {
        Ustad(36.dp, UstadMood.THINKING)
        Text("Ustaad is thinking" + ".".repeat(1 + t.value.toInt() % 3), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ustaad's message: chat text, or the full Chegg-style answer card. */
@Composable
private fun UstaadBubble(a: UstaadAnswer, moduleOf: (String) -> Module?, onOpenSolution: (String, Int) -> Unit, latest: Boolean, onSuggestion: (String) -> Unit,
                         makePdf: (suspend (pk.parhlou.student.domain.PdfAction) -> java.io.File?)? = null) {
    val unsafe = a.safety.startsWith("UNSAFE")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("ustaad-reply")) {
        Ustad(36.dp, when { unsafe -> UstadMood.CROSS; a.safety == "SELF_HARM" -> UstadMood.SAD; a.kind == "SOLUTION" -> UstadMood.CHEER; else -> UstadMood.HAPPY })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (a.reply.isNotBlank()) RichText(a.reply)
            if (a.action != null && makePdf != null) PdfCard(a.action, makePdf)
            if (a.kind == "SOLUTION" || a.questionAsRead.isNotBlank() && a.steps.isNotEmpty()) SolutionCardView(a, moduleOf, onOpenSolution)
            if (a.kind != "SOLUTION" && a.concept.isNotBlank()) Section("Understand the topic", a.concept)
            FigureSpec.parse(a.figure.takeIf { it.isNotBlank() }?.let { org.json.JSONObject(it) })?.let { FigureView(it) }
            VideoCards(a)
            // Suggested replies only under the latest answer, as in a chat app.
            if (latest && a.suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.suggestions.forEach { s -> AssistChip(onClick = { onSuggestion(s) }, label = { Text(s) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
    }
}

@Composable
private fun SolutionCardView(a: UstaadAnswer, moduleOf: (String) -> Module?, onOpenSolution: (String, Int) -> Unit) {
    val bookSolution = a.book?.let { b -> moduleOf(b.moduleId)?.solutions?.getOrNull(b.index) }
    Sheet(Modifier.testTag("ustaad-solution")) {
        if (a.book != null) Text("📘 From your book", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (a.topic.isNotBlank()) Text(a.topic, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        if (a.questionAsRead.isNotBlank()) {
            Text("Question", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MathText(a.questionAsRead, style = MaterialTheme.typography.titleMedium)
        }
        bookSolution?.figures?.forEach { BookFigure(it) }
        // The answer first; the working folds open, so the card stays short.
        var stepsOpen by remember(a) { mutableStateOf(a.finalAnswer.isBlank()) }
        if (a.finalAnswer.isNotBlank()) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Final answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                MathText(a.finalAnswer, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (a.steps.isNotEmpty()) {
            if (stepsOpen) a.steps.forEachIndexed { i, s -> AnswerStep(i + 1, s.text, s.why) }
            TextButton(onClick = { stepsOpen = !stepsOpen }, modifier = Modifier.heightIn(min = 48.dp).testTag("ustaad-steps")) {
                Text(if (stepsOpen) "Hide steps" else "Show ${a.steps.size} steps")
            }
        }
        if (stepsOpen) {
            if (bookSolution != null && bookSolution.bookAnswer.isNotBlank()) Muted("Book answer: ${bookSolution.bookAnswer}")
            if (a.concept.isNotBlank()) Section("💡 Idea", a.concept)
            if (a.examTip.isNotBlank()) Section("🎯 Exam tip", a.examTip)
            if (a.checkQuestion.isNotBlank()) Section("✍️ Try this", a.checkQuestion)
        }
        if (a.book != null) TextButton(onClick = { onOpenSolution(a.book.moduleId, a.book.index) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Open in your book")
        }
    }
}

@Composable
private fun AnswerStep(n: Int, text: String, why: String) {
    var open by remember(text) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Column(Modifier.weight(1f)) {
            MathText(text, style = MaterialTheme.typography.bodyLarge)
            if (why.isNotBlank()) {
                if (open) Text("Why: $why", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                else TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Why?") }
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        RichText(body)
    }
}

/** Finds lesson videos when the student asks for them; null where there's no server (then YouTube search opens). */
val LocalLessonVideos = androidx.compose.runtime.staticCompositionLocalOf<(suspend (String) -> List<LessonVideo>?)?> { null }

/**
 * Lesson videos. They come by themselves only when the student asked to understand a concept; after a solved question
 * there is a small "Watch a lesson on this topic" button instead, so videos are searched only when wanted.
 */
@Composable
private fun VideoCards(a: UstaadAnswer) {
    val context = LocalContext.current
    val find = LocalLessonVideos.current
    val scope = rememberCoroutineScope()
    var asked by remember(a) { mutableStateOf<List<LessonVideo>?>(null) }
    var looking by remember(a) { mutableStateOf(false) }
    fun open(url: String) = pk.parhlou.student.ui.Links.open(context, url)
    val shown = a.videos.ifEmpty { asked.orEmpty() }
    val topic = a.topic.substringAfter("· ").ifBlank { a.questionAsRead.take(80) }
    if (shown.isEmpty() && a.videoQuery.isBlank() && a.kind == "SOLUTION" && topic.isNotBlank() && asked == null) {
        TextButton(onClick = {
            looking = true
            scope.launch {
                val found = find?.invoke(topic)
                looking = false
                if (found.isNullOrEmpty()) open("https://www.youtube.com/results?search_query=" + Uri.encode("class 9 $topic")) else asked = found
            }
        }, enabled = !looking, modifier = Modifier.heightIn(min = 48.dp).testTag("video-ask")) {
            Icon(Icons.Filled.PlayCircle, null, modifier = Modifier.size(20.dp))
            Text(if (looking) "  Finding lessons…" else "  Watch a lesson on this topic")
        }
    }
    if (shown.isNotEmpty()) {
        Text("Watch a lesson on this", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        shown.forEach { v ->
            pk.parhlou.student.ui.components.TapCard(onClick = { pk.parhlou.student.ui.Links.youtubeId(v.id)?.let { open("https://www.youtube.com/watch?v=$it") } }, modifier = Modifier.fillMaxWidth().testTag("video-${v.id}"), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.PlayCircle, null, tint = Color(0xFFE53935), modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        Muted(v.channel)
                    }
                }
            }
        }
    } else if (a.videoQuery.isNotBlank()) {
        Surface(onClick = { open("https://www.youtube.com/results?search_query=" + Uri.encode(a.videoQuery)) }, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("video-search")) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.PlayCircle, null, tint = Color(0xFFE53935), modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("Find a video lesson on this", style = MaterialTheme.typography.bodyLarge)
                    Muted("Opens YouTube: \"${a.videoQuery}\"")
                }
            }
        }
    }
}

/** Light Markdown: paragraphs, "- " bullets, "### " headings and **bold**, with maths layout for plain lines. */
@Composable
fun RichText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        for (raw in text.trim().split("\n")) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Box(Modifier.size(2.dp))
                line.startsWith("#") -> Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Regex("^\\s*([-•*]|\\d+[.)])\\s+").containsMatchIn(line) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val marker = Regex("^\\s*([-•*]|\\d+[.)])\\s+").find(line)!!
                    Text(if (marker.groupValues[1].first().isDigit()) marker.groupValues[1] else "•", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(18.dp))
                    Line(line.substring(marker.range.last + 1), Modifier.weight(1f))
                }
                else -> Line(line, Modifier)
            }
        }
    }
}

@Composable
private fun Line(text: String, modifier: Modifier) {
    if (!text.contains("**") && !text.contains("_(")) { MathText(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier); return }
    val parts = Regex("\\*\\*(.+?)\\*\\*|_\\((.+?)\\)_").findAll(text).toList()
    Text(buildAnnotatedString {
        var i = 0
        for (m in parts) {
            append(text.substring(i, m.range.first))
            if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            else withStyle(SpanStyle(color = Color.Gray)) { append("(${m.groupValues[2]})") }
            i = m.range.last + 1
        }
        append(text.substring(i))
    }, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/** "Here's your paper": builds the PDF the student asked for and opens the share sheet to print, save or send it. */
@Composable
private fun PdfCard(action: pk.parhlou.student.domain.PdfAction, make: suspend (pk.parhlou.student.domain.PdfAction) -> java.io.File?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(action) { mutableStateOf(0) } // 0 ready, 1 making, 2 failed
    Sheet(Modifier.testTag("ustaad-pdf")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📄", style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.titleMedium)
                Muted(if (state == 2) "Couldn't make this one. Try another unit or exercise." else "PDF · print or share")
            }
        }
        pk.parhlou.student.ui.components.BigButton(if (state == 1) "Making…" else "Open PDF", enabled = state != 1, modifier = Modifier.testTag("ustaad-pdf-open"), onClick = {
            state = 1
            scope.launch {
                val f = runCatching { make(action) }.getOrNull()
                state = if (f == null) 2 else 0
                if (f != null) runCatching { pk.parhlou.student.ui.shareFile(context, f, "application/pdf", action.label) }
            }
        })
    }
}
