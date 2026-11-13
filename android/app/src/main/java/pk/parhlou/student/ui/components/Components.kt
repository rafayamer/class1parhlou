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
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
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

/**
 * Standard screen. Content scrolls underneath a floating glass title bar (iOS style) and, on main
 * screens, underneath the glass tab bar ([LocalBottomBarSpace] keeps the last item reachable).
 * `title = null` hides the bar (Home draws its own greeting header).
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
    val backdrop = rememberPrismalGlassLayer()
    val bottomSpace = LocalBottomBarSpace.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(scrollToEndKey, scroll.maxValue) {
        if (scrollToEndKey != null) scroll.scrollTo(scroll.maxValue)
    }
    // Surface sets the default text/icon colour for the theme (light or dark).
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .prismalGlassLayer(backdrop)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = if (title != null) 76.dp else 8.dp, bottom = 16.dp + bottomSpace + barHeight),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(52.dp).pressScale(interaction).glassCapsule(backdrop)
                            .clip(CircleShape)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onBack)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Box(
                    Modifier.heightIn(min = 52.dp).glassCapsule(backdrop).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() })
                }
            }
        }
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

/** The one big primary action per screen (PRD §2). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier.pressScale(interaction).fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text) }
}

/** Opaque study sheet: readable text on a solid surface, no translucent layers (PRD §5). */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Large icon + text tile. The label is always visible next to the pictogram. */
@Composable
fun Tile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleIcon(icon, background = MaterialTheme.colorScheme.surface)
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Visible label on anything that has not passed academic review. */
@Composable
fun StatusPill(text: String, warning: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (warning) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SampleBadge() = StatusPill("Sample — demo content")

/** A large selectable choice (onboarding). */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else null,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Task row: icon, title, subtitle and an arrow; the whole row is the touch target. */
@Composable
fun TaskRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(icon, background = MaterialTheme.colorScheme.surfaceVariant)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Update available", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Build ${offer.build}" + (if (offer.publishedOn.isNotBlank()) " · ${offer.publishedOn}" else "") +
                    ". Your answers and progress stay on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            BigButton(if (offer.installing) "Downloading…" else "Update now", onClick = offer.install, enabled = !offer.installing)
        }
    }
}
