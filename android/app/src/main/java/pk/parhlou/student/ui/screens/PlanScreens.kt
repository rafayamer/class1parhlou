package pk.parhlou.student.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pk.parhlou.student.data.PlanClient
import pk.parhlou.student.domain.Plans
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet

/**
 * Free vs Plus, and paying for Plus by hand: send the money to the founder's JazzCash/Easypaisa wallet, then type the
 * transaction ID from the SMS. The founder checks it and Plus switches on (usually within a day).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(plans: PlanClient, signedIn: Boolean, onSignIn: () -> Unit, onPlanChanged: (Plans.Info) -> Unit, onBack: () -> Unit,
               /** False in the Play version: Google Play requires its own billing for Plus, so wallet transfers aren't offered. */
               manualPayments: Boolean = pk.parhlou.student.BuildConfig.MANUAL_PAYMENTS) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var info by remember { mutableStateOf<Plans.Info?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(signedIn, reload) {
        if (signedIn) { info = plans.info(); info?.let(onPlanChanged) }
        loaded = true
    }
    ParhlouScreen("Parhlou Premium", onBack = onBack) {
        val i = info
        val price = i?.pricePkr ?: pk.parhlou.student.domain.Premium.PRICE_PKR
        // The offer: what Premium is and what it costs, then free vs Premium side by side.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pk.parhlou.student.ui.theme.heroGradient())) {
            HeroArcs(Modifier.matchParentSize())
            androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("  PREMIUM", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("Everything you need for full marks", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(pk.parhlou.student.data.LocalAppSettings.current.plusPitch
                    ?: "Every chapter of every book, the Guess Paper and all past papers, your own tests, Ask Ustaad and AI marking. No ads.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                Text("${pk.parhlou.student.domain.Premium.rupees(price)} a month", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("plan-price"))
            }
        }
        PlanComparison(Modifier.testTag("plan-compare"))
        if (!signedIn) {
            Sheet { Muted("Sign in first, so Premium is linked to your account."); BigButton("Sign in", onClick = onSignIn) }
            return@ParhlouScreen
        }
        if (!loaded) { Muted("Loading…"); return@ParhlouScreen }
        if (i == null) {
            Sheet { Muted("Can't reach Parhlou right now."); SecondaryButton("Try again", onClick = { reload++ }) }
            return@ParhlouScreen
        }
        if (i.plus) Sheet(Modifier.testTag("plan-plus")) {
            Text("You're on Premium", style = MaterialTheme.typography.titleLarge)
            Muted("Until ${i.paidUntil?.let { prettyDate(it) }}. Premium and its credits stop then. " +
                if (manualPayments) "You can pay for more months below; they're added to the end." else "")
            pk.parhlou.student.ui.components.LocalPremium.current.credits?.takeIf { it.hasCredits }?.let { c ->
                Text("⚡ ${pk.parhlou.student.domain.Credits.format(c.balance)} of ${pk.parhlou.student.domain.Credits.format(c.monthly)} credits left · renew ${prettyDate(c.resetsOn)}",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("plan-credits"))
            }
        }
        // Promo codes give Premium for some days. Only in the direct-download app: Play builds sell Premium through Google Play.
        if (manualPayments) PromoCode(plans, onRedeemed = { info = it; onPlanChanged(it) })
        i.lastRequest?.let { r ->
            Sheet(Modifier.testTag("plan-request")) {
                when (r.status) {
                    "pending" -> { Text("Checking your payment", style = MaterialTheme.typography.titleMedium)
                        Muted("Rs ${r.amount} · transaction ${r.txn}. Premium switches on once it's confirmed, usually within a day.") }
                    "rejected" -> { Text("We couldn't confirm your last payment", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Muted(r.note.ifBlank { "Check the transaction ID and try again, or contact us." }) }
                    else -> {}
                }
            }
        }
        if (i.waiting) return@ParhlouScreen
        if (!manualPayments) { Sheet(Modifier.testTag("plan-play-soon")) { Muted("Premium will be available to buy through Google Play soon.") }; return@ParhlouScreen }
        if (i.wallets.isEmpty()) { Sheet { Muted("Payments open soon.") }; return@ParhlouScreen }

        var months by rememberSaveable { mutableIntStateOf(1) }
        var method by rememberSaveable { mutableStateOf(i.wallets.first().method) }
        var txn by rememberSaveable { mutableStateOf("") }
        var payer by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var sending by remember { mutableStateOf(false) }
        var parentOk by rememberSaveable { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val wallet = i.wallets.firstOrNull { it.method == method } ?: i.wallets.first()
        Sheet(Modifier.testTag("plan-pay")) {
            Text("1. How long?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Plans.MONTHS.forEach { m ->
                    FilterChip(months == m, { months = m }, label = { Text(if (m == 12) "1 year" else "$m month${if (m == 1) "" else "s"}") },
                        modifier = Modifier.testTag("months-$m"))
                }
            }
            Text("2. Pay with", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                i.wallets.forEach { w -> FilterChip(method == w.method, { method = w.method }, label = { Text(Plans.methodName(w.method)) }) }
            }
            Text("3. Send Rs ${i.pricePkr * months} to", style = MaterialTheme.typography.titleMedium)
            Text("${wallet.number}  ·  ${wallet.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("wallet-number"))
            SecondaryButton("Copy number", onClick = { clipboard.setText(AnnotatedString(wallet.number)) })
            Muted("Use the ${Plans.methodName(wallet.method)} app's \"Send money\". Keep the SMS: it has the transaction ID.")
            Text("4. Tell us you've paid", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(txn, { txn = it.take(40) }, singleLine = true, label = { Text("Transaction ID (TID)") },
                modifier = Modifier.fillMaxWidth().testTag("txn-input"))
            OutlinedTextField(payer, { payer = it.take(60) }, singleLine = true, label = { Text("Number you paid from (optional)") },
                modifier = Modifier.fillMaxWidth())
            // Under-18s can't make a binding purchase (Contract Act 1872): a parent or guardian confirms the payment.
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag("parent-ok").then(Modifier.clickable { parentOk = !parentOk })) {
                androidx.compose.material3.Checkbox(parentOk, { parentOk = it })
                Text("I'm the parent or guardian, or my parent or guardian made this payment and agrees to the terms.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            androidx.compose.material3.TextButton(onClick = { pk.parhlou.student.ui.Legal.open(context, pk.parhlou.student.ui.Legal.REFUNDS) }) {
                Text("Refund policy: full refund within 7 days")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(if (sending) "Sending…" else "I've paid", enabled = !sending && parentOk && Plans.cleanTxn(txn).length >= 4,
                modifier = Modifier.testTag("paid"), onClick = {
                    sending = true; error = null
                    scope.launch {
                        val (updated, err) = plans.requestPayment(method, months, txn, payer)
                        sending = false
                        if (updated != null) { info = updated; onPlanChanged(updated); txn = "" } else error = Plans.explain(err)
                    }
                })
        }
    }
}

/** "Have a promo code?": a code from Parhlou gives Premium for some days, on this account. */
@Composable
private fun PromoCode(plans: PlanClient, onRedeemed: (Plans.Info) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    if (!open) {
        androidx.compose.material3.TextButton(onClick = { open = true }, modifier = Modifier.testTag("promo-open")) { Text("Have a promo code?") }
        return
    }
    Sheet(Modifier.testTag("promo")) {
        Text("Promo code", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(code, { code = it.take(40); error = null }, singleLine = true, label = { Text("Code, like PH-1A2B-3C4D-5E6F") },
            modifier = Modifier.fillMaxWidth().testTag("promo-input"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        done?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("promo-done")) }
        BigButton(if (sending) "Checking…" else "Apply code", enabled = !sending && Plans.cleanCode(code).length >= 6,
            modifier = Modifier.testTag("promo-apply"), onClick = {
                sending = true; error = null
                scope.launch {
                    val (updated, err) = plans.redeem(code)
                    sending = false
                    if (updated != null) {
                        code = ""; onRedeemed(updated)
                        done = "Premium is on until ${updated.paidUntil?.let { prettyDate(it) } ?: "the code's end"}. Enjoy!"
                    } else error = Plans.explain(err)
                }
            })
    }
}

/** Free vs Premium, row by row. */
@Composable
private fun PlanComparison(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Row {
            Text("", modifier = Modifier.weight(1.3f))
            Text("Free", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Premium", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        pk.parhlou.student.domain.Premium.COMPARISON.forEach { (what, free, premium) ->
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(what, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
                Text(free, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                androidx.compose.foundation.layout.Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(" $premium", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
