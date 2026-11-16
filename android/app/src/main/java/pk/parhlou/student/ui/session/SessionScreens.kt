package pk.parhlou.student.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.domain.Feedback
import pk.parhlou.student.domain.SessionAnswer
import pk.parhlou.student.domain.SessionItem
import pk.parhlou.student.domain.SessionPlayer
import pk.parhlou.student.domain.SessionResult
import pk.parhlou.student.ui.components.Confetti
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.UstadSays
import pk.parhlou.student.ui.components.rememberFeedbackEffects

private val Right = Color(0xFF58A700)
private val Wrong = Color(0xFFD93A3A)

/**
 * A Duolingo-style guided session: one item per screen, a progress bar, a big Check button, then a green or red
 * panel with the reason. Missed items come back at the end. [onAnswered] reports each first attempt (for weak topics).
 */
@Composable
fun SessionScreen(
    title: String,
    items: List<SessionItem>,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onAnswered: (SessionItem, Boolean) -> Unit,
    onAskTutor: (String) -> Unit,
    onFinished: (SessionResult) -> Unit,
    onClose: () -> Unit,
    streakAfter: (SessionResult) -> String = { "" },
) {
    // Surface sets the text colour for light and dark themes.
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        SessionBody(title, items, soundOn, onToggleSound, onAnswered, onAskTutor, onFinished, onClose, streakAfter)
    }
}

@Composable
private fun SessionBody(
    title: String,
    items: List<SessionItem>,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onAnswered: (SessionItem, Boolean) -> Unit,
    onAskTutor: (String) -> Unit,
    onFinished: (SessionResult) -> Unit,
    onClose: () -> Unit,
    streakAfter: (SessionResult) -> String,
) {
    val player = remember(items) { SessionPlayer(items) }
    var tick by remember { mutableIntStateOf(0) }          // recomposes after each player change
    var answer by remember(tick) { mutableStateOf<SessionAnswer?>(null) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var result by remember { mutableStateOf<SessionResult?>(null) }
    val firstTries = remember { mutableSetOf<String>() }
    val fx = rememberFeedbackEffects(soundOn)
    BackHandler { onClose() }

    val finished = result
    if (finished != null) {
        SessionEnd(finished, streakAfter(finished), onDone = { onFinished(finished) })
        return
    }
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UstadSays("This section's practice questions are on their way. Open the lesson or the solutions meanwhile.", UstadMood.THINKING)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back") }
        }
        return
    }
    val item = player.current ?: return
    val progress by animateFloatAsState(player.progress, spring(), label = "progress")

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close session") }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).heightIn(min = 12.dp).testTag("session-progress"),
                color = Right, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (player.combo >= 2) Text("🔥 ${player.combo}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).testTag("combo"))
            IconButton(onClick = onToggleSound) {
                Icon(if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (soundOn) "Mute sounds" else "Turn sounds on")
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            UstadSays(item.instruction, if (feedback?.correct == false) UstadMood.THINKING else UstadMood.HAPPY, size = 48.dp)
            // Each attempt starts fresh, even when a missed item comes straight back.
            key(tick) { ItemBody(item, answer, feedback, onAnswer = { if (feedback == null) answer = it }) }
        }
        val fb = feedback
        if (fb == null) {
            Button(
                onClick = {
                    val a = answer ?: return@Button
                    val f = player.answer(a)
                    if (item.id !in firstTries) { firstTries += item.id; onAnswered(item, f.correct) }
                    if (f.correct) fx.correct() else fx.wrong()
                    feedback = f
                },
                enabled = answer != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Right, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp).testTag("session-check"),
            ) { Text("Check", style = MaterialTheme.typography.titleMedium) }
        } else {
            AnimatedVisibility(visible = true, enter = slideInVertically { it } + fadeIn()) {
                FeedbackPanel(fb, onAsk = { onAskTutor(askText(item)) }, onContinue = {
                    feedback = null
                    val r = player.next()
                    tick++
                    if (r != null) { fx.finish(); result = r }
                })
            }
        }
    }
}

private fun askText(item: SessionItem): String = when (item) {
    is SessionItem.Mcq -> "Explain this question: ${item.stem} Options: ${item.options.joinToString("; ")}"
    is SessionItem.TrueFalse -> "Is this true or false, and why? ${item.statement}"
    is SessionItem.TypeAnswer -> "Help me with: ${item.question}"
    is SessionItem.OrderSteps -> "Explain the order of the steps for: ${item.question}"
    is SessionItem.NextStep -> "What is the next step, and why? ${item.question}"
    is SessionItem.Flashcard -> "Help me remember the definition of ${item.term}: ${item.text}"
}

@Composable
private fun FeedbackPanel(fb: Feedback, onAsk: () -> Unit, onContinue: () -> Unit) {
    val color = if (fb.correct) Right else Wrong
    Surface(color = color.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth().testTag(if (fb.correct) "feedback-right" else "feedback-wrong")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fb.title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            fb.correctAnswer?.let { pk.parhlou.student.ui.components.MathText("Correct answer: $it", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) }
            if (fb.explanation.isNotBlank()) Text(fb.explanation, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAsk, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ask Ustad") }
                Box(Modifier.weight(1f))
                Button(onClick = onContinue, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("session-continue")) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun ItemBody(item: SessionItem, answer: SessionAnswer?, feedback: Feedback?, onAnswer: (SessionAnswer) -> Unit) {
    val checked = feedback != null
    when (item) {
        is SessionItem.Mcq -> {
            pk.parhlou.student.ui.components.MathText(item.stem, style = MaterialTheme.typography.titleLarge)
            item.options.forEachIndexed { i, o ->
                val chosen = (answer as? SessionAnswer.Choice)?.index == i
                OptionCard(o, chosen, state = when { !checked -> null; i == item.correct -> true; chosen -> false; else -> null },
                    tag = "option-$i") { onAnswer(SessionAnswer.Choice(i)) }
            }
        }
        is SessionItem.TrueFalse -> {
            Text(item.statement, style = MaterialTheme.typography.titleLarge)
            listOf("True", "False").forEachIndexed { i, o ->
                val chosen = (answer as? SessionAnswer.Choice)?.index == i
                val isRight = (i == 0) == item.isTrue
                OptionCard(o, chosen, state = when { !checked -> null; isRight -> true; chosen -> false; else -> null }, tag = "option-$i") {
                    onAnswer(SessionAnswer.Choice(i))
                }
            }
        }
        is SessionItem.TypeAnswer -> {
            pk.parhlou.student.ui.components.MathText(item.question, style = MaterialTheme.typography.titleLarge)
            item.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
            var text by remember(item.id) { mutableStateOf("") }
            var hint by remember(item.id) { mutableStateOf(false) }
            OutlinedTextField(value = text, onValueChange = { text = it; if (it.isNotBlank()) onAnswer(SessionAnswer.Typed(it)) },
                enabled = !checked, placeholder = { Text("Your final answer") }, singleLine = false,
                modifier = Modifier.fillMaxWidth().testTag("session-input"))
            if (item.hint != null && !checked) {
                if (hint) Text(item.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                else TextButton(onClick = { hint = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show a hint") }
            }
        }
        is SessionItem.OrderSteps -> {
            pk.parhlou.student.ui.components.MathText(item.question, style = MaterialTheme.typography.titleMedium)
            item.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
            var picked by remember(item.id) { mutableStateOf(listOf<Int>()) }
            Text("Your solution", style = MaterialTheme.typography.labelLarge)
            if (picked.isEmpty()) Text("Tap the steps below in the right order.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            picked.forEachIndexed { pos, s ->
                OptionCard("${pos + 1}. ${item.steps[s]}", selected = true, state = if (checked) (s == pos) else null, tag = "step-placed-$pos") {
                    if (!checked) picked = picked - s
                }
            }
            val bank = item.shuffled.filter { it !in picked }
            if (bank.isNotEmpty()) Text("Steps", style = MaterialTheme.typography.labelLarge)
            bank.forEach { s ->
                OptionCard(item.steps[s], selected = false, state = null, tag = "step-bank-$s") {
                    if (!checked) {
                        picked = picked + s
                        if (picked.size == item.steps.size) onAnswer(SessionAnswer.Order(picked))
                    }
                }
            }
        }
        is SessionItem.NextStep -> {
            pk.parhlou.student.ui.components.MathText(item.question, style = MaterialTheme.typography.titleMedium)
            item.figures.forEach { pk.parhlou.student.ui.components.BookFigure(it) }
            item.shown.forEachIndexed { i, s -> pk.parhlou.student.ui.components.MathText("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
            Text("${item.shown.size + 1}. ?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            item.options.forEachIndexed { i, o ->
                val chosen = (answer as? SessionAnswer.Choice)?.index == i
                OptionCard(o, chosen, state = when { !checked -> null; i == item.correct -> true; chosen -> false; else -> null }, tag = "option-$i") {
                    onAnswer(SessionAnswer.Choice(i))
                }
            }
        }
        is SessionItem.Flashcard -> {
            var flipped by remember(item.id) { mutableStateOf(false) }
            val turn by animateFloatAsState(if (flipped) 180f else 0f, label = "flip")
            Card(onClick = { flipped = !flipped }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("flashcard"), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(if (turn > 90f) item.text else item.term, textAlign = TextAlign.Center,
                        style = if (turn > 90f) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall)
                }
            }
            if (!flipped) Text("Say the definition aloud, then tap the card.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val knew = (answer as? SessionAnswer.SelfRated)?.knewIt
                OptionCard("I knew it", knew == true, null, "knew", Modifier.weight(1f)) { onAnswer(SessionAnswer.SelfRated(true)) }
                OptionCard("Not yet", knew == false, null, "not-yet", Modifier.weight(1f)) { onAnswer(SessionAnswer.SelfRated(false)) }
            }
        }
    }
}

/** A big tappable answer card. [state]: null = not marked, true = right, false = wrong. */
@Composable
private fun OptionCard(text: String, selected: Boolean, state: Boolean?, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val border = when (state) {
        true -> Right
        false -> Wrong
        null -> if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    }
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected || state != null) 2.5.dp else 1.5.dp, border),
        color = when (state) {
            true -> Right.copy(alpha = 0.12f)
            false -> Wrong.copy(alpha = 0.12f)
            null -> if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
            pk.parhlou.student.ui.components.MathText(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}


@Composable
private fun SessionEnd(r: SessionResult, streak: String, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (r.accuracy >= 80) "Session complete!" else "Good effort!", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("session-done"))
            UstadSays(
                when {
                    r.accuracy == 100 -> "Shabash! A perfect session. That is board-topper work."
                    r.accuracy >= 80 -> "Very good! You're getting exam-ready. Keep the streak going tomorrow."
                    r.missed.isNotEmpty() -> "Well tried. The ones you missed will come back in your next sessions until they stick."
                    else -> "Every session makes you stronger. See you tomorrow!"
                },
                if (r.accuracy >= 80) UstadMood.CHEER else UstadMood.HAPPY, size = 72.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Stat("XP", "+${r.xp}", Color(0xFFF4B400), Modifier.weight(1f))
                Stat("Accuracy", "${r.accuracy}%", Right, Modifier.weight(1f))
                Stat("Best combo", "${r.bestCombo}", Color(0xFFEA4335), Modifier.weight(1f))
            }
            if (streak.isNotBlank()) Text(streak, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("session-streak"))
            if (r.missed.isNotEmpty()) {
                Text("To practise again", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                r.missed.forEach { Text("•  ${label(it)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
            }
            Button(onClick = onDone, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Right, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("session-finish")) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
        }
        Confetti()
    }
}

private fun label(i: SessionItem): String = when (i) {
    is SessionItem.Mcq -> i.stem
    is SessionItem.TrueFalse -> i.statement
    is SessionItem.TypeAnswer -> i.question
    is SessionItem.OrderSteps -> i.question
    is SessionItem.NextStep -> i.question
    is SessionItem.Flashcard -> i.term
}.take(90)

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(2.dp, color), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
