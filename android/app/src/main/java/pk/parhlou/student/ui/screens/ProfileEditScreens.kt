package pk.parhlou.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.ParhlouRepository
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.Sheet

/** Checks for the details a student gives about themself. Phone is optional. */
object ProfileRules {
    private val emailRe = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun nameOk(s: String) = s.trim().length >= 2
    fun emailOk(s: String) = emailRe.matches(s.trim())
    /** Pakistani mobile, written 03001234567 or +923001234567 (spaces and dashes allowed); blank is fine. */
    fun phoneOk(s: String): Boolean {
        val d = s.filter { it.isDigit() || it == '+' }
        return d.isEmpty() || Regex("^(\\+92|0092|0)3\\d{9}$").matches(d)
    }
    fun aboutOk(s: OnboardingState) = nameOk(s.name) && emailOk(s.email) && phoneOk(s.phone)
}

/** Full name, email and phone (optional). Used at sign-up and in Edit profile. */
@Composable
fun AboutYouFields(state: OnboardingState, onChange: (OnboardingState) -> Unit, emailLocked: Boolean = false) {
    Sheet {
        OutlinedTextField(
            value = state.name, onValueChange = { onChange(state.copy(name = it.take(60))) }, singleLine = true,
            label = { Text("Full name") }, isError = state.name.isNotEmpty() && !ProfileRules.nameOk(state.name),
            modifier = Modifier.fillMaxWidth().testTag("profile-name"),
        )
        OutlinedTextField(
            value = state.email, onValueChange = { onChange(state.copy(email = it.trim().take(80))) }, singleLine = true,
            enabled = !emailLocked, label = { Text("Email") },
            isError = state.email.isNotEmpty() && !ProfileRules.emailOk(state.email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("profile-email"),
        )
        if (emailLocked) Muted("This is the email you sign in with.")
        OutlinedTextField(
            value = state.phone, onValueChange = { onChange(state.copy(phone = it.filter { c -> c.isDigit() || c in "+ -" }.take(16))) },
            singleLine = true, label = { Text("Mobile number (optional)") }, placeholder = { Text("03XX XXXXXXX") },
            isError = !ProfileRules.phoneOk(state.phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().testTag("profile-phone"),
        )
        if (!ProfileRules.phoneOk(state.phone)) Text("Write it like 03001234567.", color = MaterialTheme.colorScheme.error)
    }
}

/** The favourite subject, chosen from the student's subjects; tapping it again clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoriteSubjectPicker(repo: ParhlouRepository, state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    val board = state.boardCode ?: return
    val grade = state.grade ?: return
    val mine = repo.subjectOptions(board, grade).filter { it.id in state.subjectIds }
    if (mine.isEmpty()) return
    Text("Favourite subject (optional)", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        mine.forEach { o ->
            val on = state.favoriteSubject == o.id
            FilterChip(selected = on, onClick = { onChange(state.copy(favoriteSubject = if (on) null else o.id)) },
                label = { Text(o.name) }, modifier = Modifier.testTag("favorite-${o.id}"))
        }
    }
}

/** Profile → Edit profile: name, email, phone, board and class, subjects (add or remove) and favourite subject. */
@Composable
fun EditProfileScreen(
    repo: ParhlouRepository,
    initial: OnboardingState,
    signInEmail: String?,
    onSave: (OnboardingState) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(initial.copy(email = signInEmail ?: initial.email)) }
    // A favourite that is no longer one of the subjects is dropped.
    val fixed = if (state.favoriteSubject != null && state.favoriteSubject !in state.subjectIds) state.copy(favoriteSubject = null) else state
    val ok = ProfileRules.aboutOk(fixed) && fixed.boardCode != null && fixed.grade != null && fixed.medium != null && fixed.subjectIds.isNotEmpty()
    ParhlouScreen("Edit profile", onBack = onBack) {
        Text("About you", style = MaterialTheme.typography.titleLarge)
        AboutYouFields(fixed, { state = it }, emailLocked = signInEmail != null)
        AcademicStep(repo, fixed) { state = it }
        if (fixed.subjectIds.isEmpty()) Text("Choose at least one subject.", color = MaterialTheme.colorScheme.error)
        FavoriteSubjectPicker(repo, fixed) { state = it }
        BigButton("Save", enabled = ok && fixed != initial, modifier = Modifier.testTag("profile-save"), onClick = { onSave(fixed); onBack() })
    }
}
