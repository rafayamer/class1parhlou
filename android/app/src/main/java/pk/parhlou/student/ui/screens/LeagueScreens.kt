package pk.parhlou.student.ui.screens

import pk.parhlou.student.ui.components.appear
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pk.parhlou.student.data.SocialClient
import pk.parhlou.student.domain.Quests
import pk.parhlou.student.domain.RewardSummary
import pk.parhlou.student.domain.Social
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Muted
import pk.parhlou.student.ui.components.ParhlouScreen
import pk.parhlou.student.ui.components.SecondaryButton
import pk.parhlou.student.ui.components.Sheet

/**
 * The League tab: today's quests and streak (for everyone), then, when signed in, this week's league, friends with
 * 👏 nudges, and class groups. Other students are shown only by first name and an emoji; there is no chat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeagueScreen(
    rewards: RewardSummary,
    quests: List<Quests.Quest>,
    weekXp: Int,
    signedIn: Boolean,
    social: SocialClient,
    onSignIn: () -> Unit,
    /** Every achievement, earned or not (Rewards.achievements). */
    achievements: List<pk.parhlou.student.domain.Achievement> = emptyList(),
    playerName: String = "You",
    chestReady: Boolean = false,
    chestOpened: Boolean = false,
    /** Records today's chest and returns the XP it gave. */
    onOpenChest: () -> Int = { 0 },
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var me by remember { mutableStateOf<Social.Me?>(null) }
    var board by remember { mutableStateOf<Social.Board?>(null) }
    var friends by remember { mutableStateOf<List<Social.Friend>>(emptyList()) }
    var nudges by remember { mutableStateOf<List<Social.Nudge>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Social.Group>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(signedIn, reload) {
        if (!signedIn) return@LaunchedEffect
        me = social.me()
        board = social.postWeek(weekXp, rewards.streakDays)
        friends = social.friends()
        nudges = social.nudges()
        groups = social.groups()
        loaded = true
    }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_SHORT).show()

    fun invite() {
        val code = me?.friendCode
        val text = if (code != null) "Study Class 9 with me on Parhlou! Add my friend code $code in the League tab and let's race to the top."
            else "Study Class 9 with me on Parhlou: lessons, past papers and an AI tutor, with streaks and leagues."
        runCatching {
            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Invite a friend"))
        }
    }

    ParhlouScreen("League", largeTitle = true) {
        pk.parhlou.student.ui.components.PlayerCard(
            name = me?.firstName?.takeIf { it.isNotBlank() && it != "Learner" } ?: playerName,
            avatar = me?.avatar ?: "🦉",
            rewards = rewards,
            league = board?.let { Social.LEAGUES[it.league.coerceIn(0, 6)] },
            badgeCount = achievements.count { it.earned },
            onInvite = ::invite,
        )
        // Streak and quests: everyone, signed in or not.
        Sheet(Modifier.testTag("streak-card").then(Modifier.appear(1))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥 ${rewards.streakDays}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(if (rewards.studiedToday) "Streak safe for today" else "Study today to keep your streak", style = MaterialTheme.typography.titleMedium)
                    Muted(if (rewards.freezes > 0) "🧊 ${rewards.freezes} streak freeze${if (rewards.freezes == 1) "" else "s"} ready: a missed day won't break it."
                        else "Every 7-day streak earns a streak freeze.")
                }
            }
        }
        Sheet(Modifier.testTag("quests").then(Modifier.appear(2))) {
            Text("Today's quests", style = MaterialTheme.typography.titleLarge)
            quests.forEach { q ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (q.done) "✅" else "🎯", style = MaterialTheme.typography.titleMedium)
                        Text(q.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("${q.progress}/${q.target}", style = MaterialTheme.typography.labelLarge)
                    }
                    LinearProgressIndicator(progress = { q.progress / q.target.toFloat() }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
                }
            }
            if (quests.all { it.done }) Text("🏆 All done today. Brilliant!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            pk.parhlou.student.ui.components.ChestCard(ready = chestReady, openedToday = chestOpened, onOpen = onOpenChest)
        }
        if (achievements.isNotEmpty()) Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Achievements", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${achievements.count { it.earned }}/${achievements.size}", style = MaterialTheme.typography.labelLarge)
            }
            // Earned first, then the ones closest to unlocking: six at a time keeps the league in view.
            var all by rememberSaveable { mutableStateOf(false) }
            val ordered = remember(achievements) { achievements.sortedWith(compareByDescending<pk.parhlou.student.domain.Achievement> { it.earned }
                .thenByDescending { it.shownProgress.toFloat() / it.target }) }
            pk.parhlou.student.ui.components.AchievementGrid(if (all) ordered else ordered.take(6))
            if (achievements.size > 6) TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 44.dp).testTag("achievements-all")) {
                Text(if (all) "Show fewer" else "Show all ${achievements.size}")
            }
        }

        if (!signedIn) {
            Sheet(Modifier.testTag("league-sign-in")) {
                Text("Leagues and friends", style = MaterialTheme.typography.titleLarge)
                Muted("Sign in to join a weekly league, add friends and make a class group.")
                BigButton("Sign in", onClick = onSignIn)
            }
            return@ParhlouScreen
        }
        if (!loaded) { Muted("Loading your league…"); return@ParhlouScreen }
        if (me == null) {
            Sheet {
                Text("Can't reach the league right now", style = MaterialTheme.typography.titleMedium)
                SecondaryButton("Try again", onClick = { reload++ })
            }
            return@ParhlouScreen
        }

        if (nudges.isNotEmpty()) Sheet(Modifier.testTag("nudges")) {
            nudges.take(3).forEach { n -> Text("${n.avatar} ${n.name} cheered you on 👏", style = MaterialTheme.typography.bodyLarge) }
        }

        // This week's league.
        board?.let { b ->
            Sheet(Modifier.testTag("league-board").then(Modifier.appear(3))) {
                Text("${Social.LEAGUE_ICONS[b.league.coerceIn(0, 6)]} ${Social.LEAGUES[b.league.coerceIn(0, 6)]} League", style = MaterialTheme.typography.titleLarge)
                Muted("${b.daysLeft} day${if (b.daysLeft == 1) "" else "s"} left · top 7 move up, bottom 5 move down")
                b.rows.forEach { r ->
                    val zone = b.zone(r.rank)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (r.me) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${r.rank}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp),
                            color = when (zone) { 1 -> MaterialTheme.colorScheme.primary; -1 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface })
                        Text("${r.avatar}  ${if (r.me) "You" else r.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${r.xp} XP", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Friends.
        Sheet(Modifier.testTag("friends")) {
            Text("Friends", style = MaterialTheme.typography.titleLarge)
            Text("Your code: ${me!!.friendCode}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("friend-code"))
            Muted("Share it with a friend; when they add it, you're friends.")
            SecondaryButton("Share my code", onClick = {
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,
                            "Add me on Parhlou! My friend code is ${me!!.friendCode}"), "Share your code"))
                }
            })
            var code by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, singleLine = true,
                label = { Text("Friend's code") }, modifier = Modifier.fillMaxWidth().testTag("friend-code-input"))
            SecondaryButton("Add friend", enabled = code.length == 6, modifier = Modifier.testTag("add-friend"), onClick = {
                scope.launch {
                    val (list, err) = social.addFriend(code)
                    if (list != null) { friends = list; code = ""; toast("Friend added") } else message = Social.explain(err)
                }
            })
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (friends.isEmpty()) Muted("No friends yet.")
            friends.forEach { f ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.avatar}  ${f.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("🔥${f.streak}  ·  ${f.xp} XP", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { scope.launch { if (social.nudge(f.code)) toast("You cheered on ${f.name} 👏") } },
                        modifier = Modifier.heightIn(min = 44.dp).testTag("nudge-${f.code}")) { Text("👏") }
                }
            }
        }

        // Class groups.
        Sheet(Modifier.testTag("groups")) {
            Text("Class groups", style = MaterialTheme.typography.titleLarge)
            Muted("A shared code for your class or study group, with its own weekly board.")
            var groupCode by rememberSaveable { mutableStateOf("") }
            var groupName by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(groupCode, { groupCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, singleLine = true,
                label = { Text("Group code") }, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Join group", enabled = groupCode.length == 6, onClick = {
                scope.launch { val err = social.joinGroup(groupCode); if (err == null) { groupCode = ""; reload++ } else message = Social.explain(err) }
            })
            OutlinedTextField(groupName, { groupName = it.take(40) }, singleLine = true, label = { Text("New group's name, e.g. 9th Blue") },
                modifier = Modifier.fillMaxWidth())
            SecondaryButton("Make a group", enabled = groupName.isNotBlank(), onClick = {
                scope.launch { val err = social.createGroup(groupName); if (err == null) { groupName = ""; reload++ } else message = Social.explain(err) }
            })
            groups.forEach { g ->
                Text("${g.name} · code ${g.code}", style = MaterialTheme.typography.titleMedium)
                g.rows.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", modifier = Modifier.width(30.dp))
                        Text("${r.avatar}  ${if (r.me) "You" else r.name}", modifier = Modifier.weight(1f))
                        Text("${r.xp} XP", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Avatar.
        Sheet {
            Text("Your avatar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Social.AVATARS.forEach { a ->
                    TextButton(onClick = { scope.launch { if (social.setAvatar(a)) reload++ } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(a, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
