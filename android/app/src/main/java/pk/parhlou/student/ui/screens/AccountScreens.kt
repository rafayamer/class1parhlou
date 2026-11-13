package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import pk.parhlou.student.data.AuthClient
import pk.parhlou.student.data.AuthResult
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet

/**
 * Email sign-in. The email holds a sign-in link that opens the app; a code typed here also works when the
 * email shows one. "Use without an account" keeps everything on this phone only.
 */
@Composable
fun SignInScreen(
    auth: AuthClient,
    initialName: String,
    error: String?,
    onName: (String) -> Unit,
    onGuest: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    ParhlouScreen("Sign in", onBack = onBack) {
        Text("Save your progress", style = MaterialTheme.typography.titleLarge)
        Muted("Sign in with your email so your work is saved and you can continue on another phone. No password needed.")
        val shown = message ?: error
        if (shown != null) Text(shown, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)

        if (sentTo == null) {
            Sheet {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("Your first name") }, modifier = Modifier.fillMaxWidth().testTag("signin-name"),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() }, singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("signin-email"),
                )
            }
            BigButton(if (busy) "Sending…" else "Email me a sign-in link", enabled = !busy && email.isNotBlank(), onClick = {
                busy = true; message = null
                onName(name.trim())
                scope.launch {
                    when (val r = auth.sendLink(email)) {
                        AuthResult.Sent -> sentTo = email
                        is AuthResult.Failed -> message = r.message
                        is AuthResult.SignedIn -> Unit
                    }
                    busy = false
                }
            })
        } else {
            Sheet {
                Text("Check your email", style = MaterialTheme.typography.titleMedium)
                Text("We sent a sign-in link to $sentTo. Open the email on this phone and tap the link. Parhlou will open and sign you in.",
                    style = MaterialTheme.typography.bodyLarge)
                Muted("Can't find it? Look in Spam or Promotions. The link works for 15 minutes.")
            }
            Sheet {
                Text("Got a code instead?", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) }, singleLine = true,
                    label = { Text("Code from the email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("signin-code"),
                )
                SecondaryButton(if (busy) "Checking…" else "Sign in with code", enabled = !busy && code.length >= 6, onClick = {
                    busy = true; message = null
                    scope.launch {
                        (auth.verifyCode(sentTo!!, code) as? AuthResult.Failed)?.let { message = it.message }
                        busy = false
                    }
                })
            }
            TextButton(onClick = { sentTo = null; code = "" }) { Text("Use a different email") }
        }
        SecondaryButton("Use without an account", onClick = onGuest)
        Muted("Without an account, your progress stays only on this phone and is lost if the app is removed.")
    }
}

/** Dated list of quizzes and papers from the progress log. */
@Composable
fun HistoryScreen(history: List<pk.parhlou.student.domain.LoggedEvidence>, titleOf: (String) -> String, onBack: () -> Unit) {
    val fmt = remember { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()) }
    val rows = history.mapNotNull { e ->
        when (val ev = e.evidence) {
            is pk.parhlou.student.domain.Evidence.QuizCompleted -> e to "Quiz · ${titleOf(e.key)} · first try ${ev.firstTryPercent}%"
            is pk.parhlou.student.domain.Evidence.DemoCheckedExam ->
                if (e.key.startsWith(pk.parhlou.student.data.ProgressStore.PAPER_PREFIX)) e to "Paper · ${titleOf(e.key)} · ${ev.percent}% (demo-checked)" else null
            is pk.parhlou.student.domain.Evidence.ExamMarked ->
                if (e.key.startsWith(pk.parhlou.student.data.ProgressStore.PAPER_PREFIX)) e to "Paper · ${titleOf(e.key)} · ${ev.percent}% (AI-marked)" else null
            pk.parhlou.student.domain.Evidence.WrittenSubmitted -> e to "Written practice · ${titleOf(e.key)}"
            else -> null
        }
    }.reversed()
    ParhlouScreen("Practice history", onBack = onBack) {
        if (rows.isEmpty()) Muted("Nothing yet. Your quizzes, written practice and papers will appear here.")
        rows.forEach { (e, text) ->
            Sheet {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Muted(fmt.format(java.util.Date(e.at)))
            }
        }
    }
}
