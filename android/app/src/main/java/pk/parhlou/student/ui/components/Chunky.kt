package pk.parhlou.student.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ui.theme.LocalBrand

/** The colours of one chunky element: its face, the darker lip under it, an optional border and the text colour. */
data class ChunkyColors(val face: Color, val lip: Color, val content: Color, val border: Color? = null)

object ChunkyDefaults {
    @Composable fun primary() = ChunkyColors(MaterialTheme.colorScheme.primary, LocalBrand.current.lip, MaterialTheme.colorScheme.onPrimary)
    @Composable fun sun() = LocalBrand.current.let { ChunkyColors(it.sun, it.sunLip, it.onSun) }
    @Composable fun success() = LocalBrand.current.let { ChunkyColors(it.success, it.successLip, Color.White) }
    @Composable fun danger() = LocalBrand.current.let { ChunkyColors(it.danger, it.dangerLip, Color.White) }
    @Composable fun plain() = LocalBrand.current.let { ChunkyColors(MaterialTheme.colorScheme.surface, it.lineLip, MaterialTheme.colorScheme.onSurface, it.line) }
    @Composable fun outlinePrimary() = LocalBrand.current.let { ChunkyColors(MaterialTheme.colorScheme.surface, it.lineLip, MaterialTheme.colorScheme.primary, it.line) }
    @Composable fun disabled() = LocalBrand.current.let { ChunkyColors(it.line, it.lineLip, MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * A 3D element: a face sitting on a darker lip. Pressing pushes the face down onto the lip (with a sound and a buzz),
 * like a real button. Without [onClick] it is a static card with the same look.
 */
@Composable
fun Chunky(
    modifier: Modifier = Modifier,
    colors: ChunkyColors = ChunkyDefaults.plain(),
    shape: Shape = RoundedCornerShape(18.dp),
    lip: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    sound: Sfx = Sfx.TAP,
    role: Role = Role.Button,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = if (enabled || onClick == null) colors else ChunkyDefaults.disabled()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val sounds = LocalSounds.current
    val view = LocalView.current
    val down by animateDpAsState(
        if (pressed && onClick != null && enabled) lip else 0.dp,
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "chunkyPress",
    )
    // The whole element (face and lip) is the touch target, and carries the caller's tag and enabled state.
    val touch = if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role) {
        sounds?.play(sound, view) ?: view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    } else Modifier
    Box(modifier.then(touch)) {
        // The lip: the same shape, darker, showing below the face.
        Box(Modifier.matchParentSize().padding(top = lip).clip(shape).background(c.lip))
        Column {
            var face = Modifier.fillMaxWidth().offset(y = down).clip(shape).background(c.face)
            if (c.border != null) face = face.border(BorderStroke(2.dp, c.border), shape)
            Box(face.padding(contentPadding), contentAlignment = contentAlignment) {
                CompositionLocalProvider(LocalContentColor provides c.content) { content() }
            }
            Spacer(Modifier.height(lip))
        }
    }
}

/** A chunky full-width button with a bold label. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ChunkyColors = ChunkyDefaults.primary(),
    enabled: Boolean = true,
    sound: Sfx = Sfx.TAP,
) {
    Chunky(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.9f),
        colors = colors, shape = RoundedCornerShape(16.dp), onClick = onClick, enabled = enabled, sound = sound,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp), contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp))
    }
}

/** A chunky card that can be tapped (lists of papers, solutions, chats, videos); same look as [Sheet]. */
@Composable
fun TapCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val plain = ChunkyDefaults.plain()
    Chunky(
        modifier = modifier.fillMaxWidth(),
        colors = if (containerColor == null) plain else plain.copy(face = containerColor, border = null),
        shape = shape, onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp),
    ) { Column(Modifier.fillMaxWidth(), content = content) }
}
