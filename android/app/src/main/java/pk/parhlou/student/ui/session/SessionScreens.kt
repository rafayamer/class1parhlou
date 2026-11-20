package pk.parhlou.student.ui.session

import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Check
import pk.parhlou.student.ui.components.Chunky
import pk.parhlou.student.ui.components.ChunkyButton
import pk.parhlou.student.ui.components.ChunkyColors
import pk.parhlou.student.ui.components.ChunkyDefaults
import pk.parhlou.student.ui.components.LocalReducedMotion
import pk.parhlou.student.ui.components.Sfx
import pk.parhlou.student.ui.theme.LocalBrand
import pk.parhlou.student.domain.Feedback
import pk.parhlou.student.domain.SessionAnswer
import pk.parhlou.student.domain.SessionItem
import pk.parhlou.student.domain.SessionPlayer
import pk.parhlou.student.domain.SessionResult
import pk.parhlou.student.ui.components.Confetti
import pk.parhlou.student.ui.components.UstadMood
import pk.parhlou.student.ui.components.UstadSays
import pk.parhlou.student.ui.components.rememberFeedbackEffects

private val Right: Color @Composable get() = LocalBrand.current.success
private val Wrong: Color @Composable get() = LocalBrand.current.danger

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
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UstadSays("This section's practice questions are on their way. Open the lesson or the solutions meanwhile.", UstadMood.THINKING)
            pk.parhlou.student.ui.components.BigButton("Back", onClick = onClose)
        }
        return
    }
    val item = player.current ?: return
    val progress = player.progress
    fun check() {
        val a = answer ?: return
        val f = player.answer(a)
        if (item.id !in firstTries) { firstTries += item.id; onAnswered(item, f.correct) }
        when {
            !f.correct -> fx.wrong()
            player.combo == 3 || player.combo == 5 || player.combo % 10 == 0 -> fx.combo()
            else -> fx.correct()
        }
        feedback = f
    }

    val fb = feedback
    pk.parhlou.student.ui.components.PlayerScaffold(
        progress = progress, onClose = onClose, closeLabel = "Close session", soundOn = soundOn, onToggleSound = onToggleSound,
        topEnd = { if (player.combo >= 2) ComboBadge(player.combo) },
        bottom = {
            if (fb == null) {
                pk.parhlou.student.ui.components.PlayerBottom {
                    ChunkyButton("Check", onClick = { check() }, colors = ChunkyDefaults.success(), enabled = answer != null,
                        sound = Sfx.TAP, modifier = Modifier.testTag("session-check"))
                }
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
        },
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UstadSays(item.instruction, if (feedback?.correct == false) UstadMood.THINKING else UstadMood.HAPPY, size = 48.dp)
        // Each attempt starts fresh, even when a missed item comes straight back.
        key(tick) { ItemBody(item, answer, feedback, onAnswer = { if (feedback == null) answer = it }, onSubmit = { if (feedback == null) check() }, fx = fx) }
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
    val brand = LocalBrand.current
    val color = if (fb.correct) brand.success else brand.danger
    Surface(color = if (fb.correct) brand.successPale else brand.dangerPale,
        modifier = Modifier.fillMaxWidth().testTag(if (fb.correct) "feedback-right" else "feedback-wrong")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(if (fb.correct) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(fb.title, style = MaterialTheme.typography.headlineSmall, color = color)
            }
            fb.correctAnswer?.let { pk.parhlou.student.ui.components.MathText("Correct answer: $it", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = color) }
            if (fb.explanation.isNotBlank()) Text(fb.explanation, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAsk, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ask Ustaad", color = color, style = MaterialTheme.typography.labelLarge) }
                ChunkyButton("Continue", onClick = onContinue, colors = if (fb.correct) ChunkyDefaults.success() else ChunkyDefaults.danger(),
                    modifier = Modifier.weight(1f).testTag("session-continue"))
            }
        }
    }
}

/** "🔥 3": pops in each time the run of right answers grows. */
@Composable
private fun ComboBadge(combo: Int) {
    val pop = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(combo) { if (!reduced) { pop.snapTo(1.5f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) } }
    Text("🔥 $combo", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF7A00),
        modifier = Modifier.padding(start = 8.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.testTag("combo"))
}

@Composable
private fun ItemBody(item: SessionItem, answer: SessionAnswer?, feedback: Feedback?, onAnswer: (SessionAnswer) -> Unit,
                     onSubmit: () -> Unit = {}, fx: pk.parhlou.student.ui.components.FeedbackEffects? = null) {
    val checked = feedback != null
    when (item) {
        is SessionItem.Mcq -> {
            pk.parhlou.student.ui.components.PastPaperTag(item.pastPapers)
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
            // The answer box takes focus (and opens the keyboard) as soon as the question appears; "Done" checks it.
            val focus = remember(item.id) { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(item.id) { runCatching { focus.requestFocus() } }
            OutlinedTextField(value = text, onValueChange = { text = it; if (it.isNotBlank()) onAnswer(SessionAnswer.Typed(it)) },
                enabled = !checked, placeholder = { Text("Your final answer") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("session-input"))
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
            pk.parhlou.student.ui.components.TapCard(onClick = { flipped = !flipped }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("flashcard"), shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer) {
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

/** A big tappable 3D answer card. [state]: null = not marked, true = right, false = wrong (it shakes). */
@Composable
private fun OptionCard(text: String, selected: Boolean, state: Boolean?, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val brand = LocalBrand.current
    val colors = when {
        state == true -> ChunkyColors(brand.successPale, brand.success, MaterialTheme.colorScheme.onSurface, brand.success)
        state == false -> ChunkyColors(brand.dangerPale, brand.danger, MaterialTheme.colorScheme.onSurface, brand.danger)
        selected -> ChunkyColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary)
        else -> ChunkyDefaults.plain()
    }
    val shake = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == false && !reduced) for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
        // A right answer swells and springs back, like a satisfying click.
        if (state == true && !reduced) { pop.snapTo(1.06f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
    }
    Chunky(
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp)
            .graphicsLayer { translationX = shake.value * density; scaleX = pop.value; scaleY = pop.value }.testTag(tag),
        colors = colors, shape = RoundedCornerShape(16.dp), onClick = onClick, sound = Sfx.SELECT,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart,
    ) {
        pk.parhlou.student.ui.components.MathText(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
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
            // XP counts up with little coin ticks; then the streak line lands with its own sound.
            val fx = rememberFeedbackEffects(true)
            val reduced = LocalReducedMotion.current
            var xpShown by remember { mutableIntStateOf(if (reduced) r.xp else 0) }
            LaunchedEffect(r) {
                if (!reduced) {
                    kotlinx.coroutines.delay(500)
                    val steps = minOf(r.xp, 12).coerceAtLeast(1)
                    for (i in 1..steps) { xpShown = r.xp * i / steps; if (i % 2 == 0) fx.xp(); kotlinx.coroutines.delay(55) }
                }
                xpShown = r.xp
                if (streak.isNotBlank()) { kotlinx.coroutines.delay(250); fx.streak() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Stat("XP", "+$xpShown", LocalBrand.current.sunLip, Modifier.weight(1f))
                Stat("Accuracy", "${r.accuracy}%", Right, Modifier.weight(1f))
                Stat("Best combo", "${r.bestCombo}", Color(0xFFFF7A00), Modifier.weight(1f))
            }
            if (streak.isNotBlank()) Text(streak, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("session-streak"))
            if (r.missed.isNotEmpty()) {
                Text("To practise again", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                r.missed.forEach { Text("•  ${label(it)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
            }
            ChunkyButton("Continue", onClick = onDone, colors = ChunkyDefaults.success(), modifier = Modifier.testTag("session-finish"))
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
    Chunky(modifier = modifier, colors = ChunkyColors(MaterialTheme.colorScheme.surface, color, MaterialTheme.colorScheme.onSurface, color),
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
