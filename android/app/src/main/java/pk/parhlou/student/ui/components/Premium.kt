package pk.parhlou.student.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.domain.Premium

/** The student's Premium status for every screen, with the actions that go with it. */
data class PremiumAccess(
    val access: Premium.Access = Premium.Access.OPEN,
    val pricePkr: Int = Premium.PRICE_PKR,
    /** This month's credits, when the server has said (null offline, signed out, or before it answers). */
    val credits: pk.parhlou.student.ai.Allowance? = null,
    /** Asks the server for the credits again, after some AI work. */
    val refreshCredits: () -> Unit = {},
    /** Opens the Premium screen. */
    val upgrade: () -> Unit = {},
) {
    /** The credits one piece of AI work is likely to cost: the server's estimate, or the typical one. */
    fun estimate(key: String): Double = credits?.estimate(key) ?: pk.parhlou.student.domain.Credits.TYPICAL[key] ?: 1.0
}

/** Open by default, so tests and previews see everything; the app provides the real status. */
val LocalPremium = compositionLocalOf { PremiumAccess() }

/**
 * Premium content for a free student: the screen behind is blurred (and dimmed, so it stays hidden on phones older than
 * Android 12, where blur isn't available) and can't be touched, with a card that says what Premium unlocks. When
 * [locked] is false the content shows as normal. [preview] replaces the real content behind the glass for screens that
 * must not start anything (a paper's clock, a question to Ustaad).
 */
@Composable
fun PremiumGate(
    locked: Boolean,
    title: String,
    message: String,
    onBack: (() -> Unit)? = null,
    preview: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!locked) { content(); return }
    val premium = LocalPremium.current
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().testTag("premium-lock")) {
        Box(Modifier.fillMaxSize().blur(16.dp).clearAndSetSemantics {}) { (preview ?: content)() }
        Box(
            Modifier.fillMaxSize()
                // Phones before Android 12 can't blur, so the cover is thicker there and the content can't be read.
                .background(Brush.verticalGradient(
                    if (android.os.Build.VERSION.SDK_INT >= 31) listOf(bg.copy(alpha = 0.62f), bg.copy(alpha = 0.9f), bg.copy(alpha = 0.97f))
                    else listOf(bg.copy(alpha = 0.9f), bg.copy(alpha = 0.96f), bg.copy(alpha = 0.99f))))
                .pointerInput(Unit) { detectTapGestures { } },
        )
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            PremiumCard(title, message, premium.pricePkr, onUnlock = premium.upgrade, onBack = onBack)
        }
    }
}

/** The card on a locked screen: lock, what this is, what Premium gives, the price and the button. */
@Composable
fun PremiumCard(title: String, message: String, pricePkr: Int, onUnlock: () -> Unit, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(pk.parhlou.student.ui.theme.heroGradient()), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text("PREMIUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (perk in listOf("Every chapter of every book", "Every paper, with no limits", "1,000 credits a month for Ask Ustaad and AI marking", "No ads")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(perk, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("${Premium.rupees(pricePkr)} a month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        BigButton("Unlock Premium", onClick = onUnlock, modifier = Modifier.testTag("premium-upgrade"))
        if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) { Text("Not now") }
    }
}

/** A small "Premium" tag with a lock, for cards and list rows that lead to Premium content. */
@Composable
fun PremiumTag(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("premium-tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("Premium", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

/**
 * "About N credits": what a paper will use, item by item, and what the student has left. Shown before making or sitting a
 * paper; [onConfirm] goes ahead.
 */
@Composable
fun CreditConfirm(title: String, cost: pk.parhlou.student.domain.Credits.PaperCost, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String = "Go ahead") {
    val credits = LocalPremium.current.credits
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("credit-confirm"),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CostLines(cost)
                Text(credits?.let { "You have ${pk.parhlou.student.domain.Credits.format(it.balance)} credits left this month." }
                    ?: "Credits are counted when the paper is checked.", style = MaterialTheme.typography.bodyMedium)
                if (credits != null && credits.balance < cost.total) Text(
                    "That's more than you have left. MCQs are still checked, and anything not checked waits until your credits renew on " +
                        "${pk.parhlou.student.ui.screens.prettyDate(credits.resetsOn)}.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text("Estimates: each AI step uses what it really costs, often less.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("credit-confirm-ok")) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Making, checking and the report, each with its credits, then the total. */
@Composable
fun CostLines(cost: pk.parhlou.student.domain.Credits.PaperCost, modifier: Modifier = Modifier) {
    Column(modifier.testTag("paper-cost"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (cost.make > 0) CostLine("Ustaad chooses the questions", cost.make)
        CostLine("Checking your answers", cost.check)
        CostLine("Examiner's report", cost.analyse)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("⚡ ${cost.total} credits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("paper-cost-total"))
        }
    }
}

@Composable
private fun CostLine(what: String, credits: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(what, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$credits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A neutral placeholder to blur behind a locked screen that must not run (a paper, a chat). */
@Composable
fun LockedPreview() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) { i ->
            Box(Modifier.fillMaxWidth(if (i % 2 == 0) 1f else 0.7f).heightIn(min = 72.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}
