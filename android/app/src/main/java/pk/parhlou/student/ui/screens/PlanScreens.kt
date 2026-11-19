package pk.parhlou.student.ui.screens

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
fun PlanScreen(plans: PlanClient, signedIn: Boolean, onSignIn: () -> Unit, onPlanChanged: (Plans.Info) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var info by remember { mutableStateOf<Plans.Info?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(signedIn, reload) {
        if (signedIn) { info = plans.info(); info?.let(onPlanChanged) }
        loaded = true
    }
    ParhlouScreen("Parhlou Plus", onBack = onBack) {
        val i = info
        Sheet(Modifier.testTag("plan-compare")) {
            Text("Free", style = MaterialTheme.typography.titleLarge)
            Muted("Every lesson, quiz, solution, past paper and Guess Paper. A small amount of Ask Ustaad and AI marking each month. Short ads after some quizzes, never in lessons or papers.")
            Text("Plus ⭐", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            val times = if (i != null && i.freeUsd > 0) (i.plusUsd / i.freeUsd).toInt() else 6
            Muted("Everything in Free, ${times}× more Ask Ustaad and AI paper marking every month, and no ads.")
            Text("Rs ${i?.pricePkr ?: 499} a month", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("plan-price"))
        }
        if (!signedIn) {
            Sheet { Muted("Sign in first, so Plus is linked to your account."); BigButton("Sign in", onClick = onSignIn) }
            return@ParhlouScreen
        }
        if (!loaded) { Muted("Loading…"); return@ParhlouScreen }
        if (i == null) {
            Sheet { Muted("Can't reach Parhlou right now."); SecondaryButton("Try again", onClick = { reload++ }) }
            return@ParhlouScreen
        }
        if (i.plus) Sheet(Modifier.testTag("plan-plus")) {
            Text("You're on Plus ⭐", style = MaterialTheme.typography.titleLarge)
            Muted("Until ${i.paidUntil}. You can pay for more months below; they're added to the end.")
        }
        i.lastRequest?.let { r ->
            Sheet(Modifier.testTag("plan-request")) {
                when (r.status) {
                    "pending" -> { Text("Checking your payment", style = MaterialTheme.typography.titleMedium)
                        Muted("Rs ${r.amount} · transaction ${r.txn}. Plus switches on once it's confirmed, usually within a day.") }
                    "rejected" -> { Text("We couldn't confirm your last payment", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Muted(r.note.ifBlank { "Check the transaction ID and try again, or contact us." }) }
                    else -> {}
                }
            }
        }
        if (i.waiting) return@ParhlouScreen
        if (i.wallets.isEmpty()) { Sheet { Muted("Payments open soon.") }; return@ParhlouScreen }

        var months by rememberSaveable { mutableIntStateOf(1) }
        var method by rememberSaveable { mutableStateOf(i.wallets.first().method) }
        var txn by rememberSaveable { mutableStateOf("") }
        var payer by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var sending by remember { mutableStateOf(false) }
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
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(if (sending) "Sending…" else "I've paid", enabled = !sending && Plans.cleanTxn(txn).length >= 4,
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
