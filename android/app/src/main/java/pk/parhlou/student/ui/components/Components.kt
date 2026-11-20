package pk.parhlou.student.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.graphicsLayer
import pk.parhlou.student.ui.theme.LocalBrand

/**
 * Standard screen: a solid title bar (back button, centred title, mirrored spacer so the title sits exactly in the
 * middle), then content that scrolls under it with the same side margins everywhere. On main screens the content also
 * scrolls under the tab bar ([LocalBottomBarSpace] keeps the last item reachable). `title = null` hides the bar (Home
 * draws its own header). The content rises gently into place when the screen opens.
 */
@Composable
fun ParhlouScreen(
    title: String?,
    onBack: (() -> Unit)? = null,
    /** Pinned above the keyboard at the bottom of the screen (the tutor's question box). */
    bottomBar: (@Composable () -> Unit)? = null,
    /** When this value changes, the content scrolls to its end (new chat message). */
    scrollToEndKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottomSpace = LocalBottomBarSpace.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(scrollToEndKey, scroll.maxValue) {
        if (scrollToEndKey != null) scroll.scrollTo(scroll.maxValue)
    }
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize()) {
            if (title != null) TitleBar(title, onBack, scrolled = scroll.value > 0)
            // The page ends above the tab bar (it no longer scrolls underneath), so nothing is ever hidden behind it.
            val navBar = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val navInset = if (bottomSpace > 0.dp) navBar else 0.dp
            // Pages without the tab bar scroll their last item clear of Android's navigation bar (gesture or 3-button).
            val endInset = if (bottomSpace == 0.dp && bottomBar == null) navBar else 0.dp
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomSpace + navInset)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .then(if (title == null) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 20.dp)
                        .padding(top = if (title != null) 8.dp else 12.dp, bottom = 20.dp + barHeight + endInset)
                        .appear(0),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                if (bottomBar != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    ) {
                        Box(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) { bottomBar() }
                    }
                }
            }
        }
    }
}

/** Title bar: back button on the left, the title centred, and a hairline that appears once the page scrolls. */
@Composable
fun TitleBar(title: String, onBack: (() -> Unit)?, scrolled: Boolean = false, actions: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, textAlign = TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() })
            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) { actions?.invoke() }
        }
        val line by animateFloatAsState(if (scrolled) 1f else 0f, label = "titleLine")
        Box(Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = line }.background(LocalBrand.current.line))
    }
}

/** A round chunky icon button (back, close, sound). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, colors: ChunkyColors = ChunkyDefaults.plain()) {
    Chunky(
        modifier = Modifier.width(46.dp).semantics(mergeDescendants = true) { contentDescription = description },
        colors = colors, shape = CircleShape, lip = 3.dp, onClick = onClick,
        contentPadding = PaddingValues(0.dp), contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
    }
}

/** The one big primary action per screen (PRD §2): a chunky teal button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    ChunkyButton(text, onClick, modifier, ChunkyDefaults.primary(), enabled)

/** A secondary action: white chunky button with a teal label. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    ChunkyButton(text, onClick, modifier, ChunkyDefaults.outlinePrimary(), enabled)

/** A reward or highlight action: sunny yellow. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    ChunkyButton(text, onClick, modifier, ChunkyDefaults.sun(), enabled)

/** Opaque study sheet: a white card with a soft border and lip, so every card in the app looks the same (PRD §5). */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Chunky(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), lip = 3.dp, contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Large icon + text tile. The label is always visible next to the pictogram. */
@Composable
fun Tile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Chunky(modifier = modifier.heightIn(min = 104.dp), shape = RoundedCornerShape(20.dp), onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(14.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIcon(icon)
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Visible label on anything that has not passed academic review, and small status tags. */
@Composable
fun StatusPill(text: String, warning: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (warning) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun SampleBadge() = Unit // Content is reviewed; no "sample" label is shown to students.

/** A large selectable choice (onboarding, settings): chunky, teal when chosen. */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val brand = LocalBrand.current
    val colors = if (selected) ChunkyColors(MaterialTheme.colorScheme.primaryContainer, brand.lip, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary)
    else ChunkyDefaults.plain()
    Chunky(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { role = Role.RadioButton; this.selected = selected },
        colors = colors, onClick = onClick, enabled = enabled, sound = Sfx.SELECT, role = Role.RadioButton,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Muted(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Checkbox whose whole row (label included) is the touch target, at least 48dp tall. */
@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Round icon badge used on tiles and list rows. */
@Composable
fun CircleIcon(icon: ImageVector, background: Color = MaterialTheme.colorScheme.primaryContainer, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Task row: icon, title, subtitle and an arrow; the whole row is the touch target. */
@Composable
fun TaskRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Chunky(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(20.dp), onClick = onClick,
        contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Circular progress ring with a centred label (no animation, so reduced-motion is respected). */
@Composable
fun ProgressRing(
    fraction: Float,
    label: String,
    description: String,
    track: Color,
    progress: Color,
    textColor: Color,
    size: Dp = 84.dp,
) {
    Box(
        Modifier.size(size).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val reduced = LocalReducedMotion.current
        val shown by animateFloatAsState(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = if (reduced) snap() else tween(durationMillis = 900, easing = FastOutSlowInEasing),
            label = "ring",
        )
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(progress, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false, style = stroke)
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/** Shown on Home and Profile when a newer prototype build is available. Draws nothing when up to date. */
@Composable
fun UpdateBanner() {
    val offer = LocalUpdateOffer.current ?: return
    Sheet {
        Text(if (offer.ready) "Update ready — tap to install" else "Update available", style = MaterialTheme.typography.titleMedium)
        Muted("Build ${offer.build}" + (if (offer.publishedOn.isNotBlank()) " · ${offer.publishedOn}" else "") +
            ". Your answers and progress stay on this phone.")
        AccentButton(if (offer.installing) "Opening installer…" else if (offer.ready) "Install build ${offer.build}" else "Update now",
            onClick = offer.install, enabled = !offer.installing)
    }
}

/** "Came in Lahore 2023 (Group I) · +2 more": where a question appeared in board exams. Nothing for book-only questions. */
@Composable
fun PastPaperTag(papers: List<String>, modifier: Modifier = Modifier) {
    if (papers.isEmpty()) return
    val text = "📋 Came in " + papers.first() + if (papers.size > 1) " · +${papers.size - 1} more" else ""
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
