package pk.parhlou.student.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pk.parhlou.student.ai.ParhlouAi
import pk.parhlou.student.ai.ReplyKind
import pk.parhlou.student.ai.TutorReply
import pk.parhlou.student.ai.TutorStyle
import pk.parhlou.student.ai.TutorTurn
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet
import pk.parhlou.student.ui.components.StatusPill

/** Where the tutor was opened from. The app supplies this context; it is never read from the student's text. */
data class TutorSeed(
    val moduleId: String? = null,
    val noteId: String? = null,
    val question: String? = null,
    val mcq: McqQuestion? = null,
    val chosen: Int = 0,
)

private sealed interface ChatItem {
    data class Me(val text: String) : ChatItem
    data class Tutor(val reply: TutorReply) : ChatItem
}

private val starters = listOf("What is a rational number?", "How do I simplify √50?", "Prove that √2 is irrational")

private val followUps = listOf(
    "Explain more simply" to TutorStyle.SIMPLER,
    "Another example" to TutorStyle.ANOTHER_EXAMPLE,
    "Show each step" to TutorStyle.STEPS,
    "Ask me a question" to TutorStyle.QUIZ_ME,
)

@Composable
fun TutorScreen(
    ai: ParhlouAi,
    subjectIds: Set<String>,
    seed: TutorSeed?,
    examActive: Boolean,
    onBack: () -> Unit,
) {
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lastReply = (items.lastOrNull { it is ChatItem.Tutor } as? ChatItem.Tutor)?.reply

    fun ask(question: String, style: TutorStyle = TutorStyle.NORMAL, shown: String = question, noteId: String? = lastReply?.noteId) {
        if (thinking) return
        val awaiting = style == TutorStyle.NORMAL && question.isNotBlank() && lastReply?.checkQuestion != null
        items += ChatItem.Me(shown)
        thinking = true
        scope.launch {
            val reply = ai.tutor(
                TutorTurn(question, subjectIds, seed?.moduleId, style, lastNoteId = noteId, awaitingCheck = awaiting, examActive = examActive),
            )
            items += ChatItem.Tutor(reply)
            thinking = false
        }
    }

    LaunchedEffect(seed) {
        if (items.isNotEmpty() || seed == null) return@LaunchedEffect
        when {
            examActive -> Unit
            seed.mcq != null -> {
                items += ChatItem.Me("Why was my answer wrong?")
                items += ChatItem.Tutor(ai.mcqFeedback(seed.mcq, seed.chosen, subjectIds))
            }
            seed.question != null -> ask(seed.question)
            seed.noteId != null -> ask("", shown = "Explain this to me", noteId = seed.noteId)
        }
    }

    ParhlouScreen(
        title = "AI tutor",
        onBack = onBack,
        scrollToEndKey = if (items.isEmpty()) null else items.size + (if (thinking) 1 else 0),
        bottomBar = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !examActive,
                    placeholder = { Text(if (lastReply?.checkQuestion != null) "Type your answer or a new question…" else "Ask a question…") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).testTag("tutor-input"),
                )
                IconButton(
                    onClick = { val q = input.trim(); input = ""; ask(q) },
                    enabled = input.isNotBlank() && !thinking && !examActive,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        },
    ) {
        StatusPill("${ai.label} · knows the Unit 1 demo notes only")

        if (examActive) {
            Sheet {
                Text("Tutor locked", style = MaterialTheme.typography.titleMedium)
                Text("You have a paper in progress. The tutor can't help until you hand it in.", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(124.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(90.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(46.dp))
                    }
                }
            }
            Text("How can I help you today?", style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Muted("Try one of these:")
            starters.forEach { s ->
                AssistChip(onClick = { ask(s) }, label = { Text(s) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }

        items.forEach { item ->
            when (item) {
                is ChatItem.Me -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(item.text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
                is ChatItem.Tutor -> TutorReplyCard(item.reply)
            }
        }
        if (thinking) Muted("Thinking…")

        if (!thinking && lastReply?.noteId != null && lastReply.kind != ReplyKind.LOCKED) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                followUps.forEach { (label, style) ->
                    AssistChip(onClick = { ask("", style, shown = label) }, label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

@Composable
private fun TutorReplyCard(reply: TutorReply) {
    Sheet {
        if (reply.title != null) {
            Text(reply.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Text(reply.answer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (reply.explanation != null) Text(reply.explanation, style = MaterialTheme.typography.bodyLarge)
        reply.steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        if (reply.checkQuestion != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Check yourself", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(reply.checkQuestion, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (reply.source != null) Muted("Source: ${reply.source}")
    }
}
