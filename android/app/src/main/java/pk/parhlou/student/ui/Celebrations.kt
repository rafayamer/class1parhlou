package pk.parhlou.student.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.domain.Achievement
import pk.parhlou.student.domain.Game
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.ui.components.CelebrationDialog

private const val SEEN_KEY = "celebrated"

/**
 * Watches the student's rewards and pops up one celebration at a time: a new level, a new badge, a streak milestone, all
 * quests done. What's been celebrated is remembered on the phone, so each moment shows once.
 */
@Composable
fun CelebrationHost(store: LocalStore, rewards: RewardSummary, achievements: List<Achievement>, questsDone: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showing by remember { mutableStateOf<Game.Celebration?>(null) }
    var after by remember { mutableStateOf<Game.Seen?>(null) }
    var checks by remember { mutableIntStateOf(0) }
    val earned = achievements.count { it.earned }
    LaunchedEffect(rewards.level, rewards.streakDays, earned, questsDone, checks) {
        if (showing != null) return@LaunchedEffect
        val seen = Game.Seen.decode(store.draft(SEEN_KEY).first())
        val (c, next) = Game.next(seen, rewards, achievements, questsDone, java.time.LocalDate.now().toString())
        if (c == null) store.saveDraft(SEEN_KEY, next.encode()) else { after = next; showing = c }
    }
    showing?.let { c ->
        CelebrationDialog(c,
            onShare = {
                val text = "${c.emoji} ${c.title} I'm studying Class 9 on Parhlou: lessons, past papers and an AI tutor. Join me!"
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Share"))
                }
            },
            onDismiss = {
                val seen = after
                showing = null
                scope.launch { seen?.let { store.saveDraft(SEEN_KEY, it.encode()) }; checks++ }
            })
    }
}
