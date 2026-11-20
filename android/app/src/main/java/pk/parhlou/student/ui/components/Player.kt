package pk.parhlou.student.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ui.theme.LocalBrand

/**
 * The one look for everything a student works through step by step: lessons and practice sessions. A close button and a
 * thick progress bar at the top, the step in the middle (Ustaad speaks first, then the content in cards), and one big
 * action pinned at the bottom.
 */
@Composable
fun PlayerScaffold(
    progress: Float,
    onClose: () -> Unit,
    closeLabel: String,
    soundOn: Boolean? = null,
    onToggleSound: () -> Unit = {},
    topEnd: @Composable RowScope.() -> Unit = {},
    /** A new screen: when this changes, the content starts again from the top. */
    screenKey: Any? = null,
    /** Something was added at the end (a new step, a result): when this changes, it is scrolled into view. */
    revealKey: Any? = null,
    /** Pinned under the content: usually one ChunkyButton, or a feedback panel. */
    bottom: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Surface sets the text colour for light and dark themes.
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = closeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                PlayerProgress(progress, Modifier.weight(1f).testTag("player-progress"))
                topEnd()
                if (soundOn != null) IconButton(onClick = onToggleSound) {
                    Icon(if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (soundOn) "Mute sounds" else "Turn sounds on")
                }
            }
            val scroll = rememberScrollState()
            androidx.compose.runtime.LaunchedEffect(screenKey) { scroll.scrollTo(0) }
            androidx.compose.runtime.LaunchedEffect(revealKey) {
                if (revealKey != null) { kotlinx.coroutines.delay(120); scroll.animateScrollTo(scroll.maxValue) }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            bottom()
        }
    }
}

/** The bottom action area of a [PlayerScaffold]: the same margins everywhere. */
@Composable
fun PlayerBottom(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** A thick rounded bar that fills with a springy motion and a light shine on top. */
@Composable
fun PlayerProgress(progress: Float, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "progress")
    Box(modifier.height(16.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(50)).background(brand.success)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 3.dp).height(4.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f)))
        }
    }
}

/** A content card inside a lesson or session: the same rounded, outlined surface as the answer options. */
@Composable
fun StepCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val brand = LocalBrand.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(accent?.copy(alpha = 0.10f) ?: MaterialTheme.colorScheme.surface)
            .border(2.dp, accent ?: brand.line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

/** A small label above a step, e.g. "Step 2" or "Idea 3 of 5". */
@Composable
fun StepLabel(text: String, color: Color = MaterialTheme.colorScheme.primary) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
