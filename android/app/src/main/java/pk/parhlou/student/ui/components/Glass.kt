package pk.parhlou.student.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.components.LocalPrismalBottomTabHighlightedIndex
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.prismalGlassEffects
import com.styropyr0.prismal.shapes.PrismalCapsule

/**
 * Glass chrome (iOS-style frosted/liquid bars) built on the vendored PrismalAGSL library (MIT, see android/prismal).
 * Prismal picks its own tier per device: frosted fallback on Android 7.1–11, blur on 12, liquid glass on 13+.
 */

/** Space reserved at the bottom of scrolling screens so content can scroll under the floating tab bar. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** True when the user has turned animations off (Android "Remove animations"). All motion checks this. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Springy "press in" scale for buttons and cards. Does nothing with reduced motion. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    if (LocalReducedMotion.current) return this
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Frosted glass capsule sampling [backdrop]; used for the floating title bar and glass buttons. */
@Composable
fun Modifier.glassCapsule(backdrop: PrismalBackdrop): Modifier {
    val density = LocalDensity.current
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f) // opaque enough to keep the title readable over text
    return drawPrismalGlass(
        backdrop = backdrop,
        shape = { PrismalCapsule() },
        effects = prismalGlassEffects(density),
        onDrawSurface = { drawRect(tint) },
    )
}

/** iOS-style glass tab bar with a draggable liquid selection droplet. */
@Composable
fun GlassTabBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: PrismalBackdrop,
) {
    val selectedColor = MaterialTheme.colorScheme.secondary
    val idleColor = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrismalGlassBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = onSelect,
            backdrop = backdrop,
            tabsCount = items.size,
            tintDropletContent = true,
            dropletContentTint = selectedColor,
            hapticFeedback = true,
        ) {
            items.forEachIndexed { index, item ->
                val highlighted = LocalPrismalBottomTabHighlightedIndex.current() == index
                val color = if (highlighted) selectedColor else idleColor
                PrismalGlassBottomTab(
                    onClick = { onSelect(index) },
                    modifier = Modifier.semantics { selected = index == selectedIndex },
                ) {
                    Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

/** Height reserved under content for [GlassTabBar] (bar + margins); navigation bar inset is added separately. */
val GlassTabBarHeight: Dp = 96.dp

@Composable
fun ProvideMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(), content = content)
}

/**
 * Staggered entrance: fades and lifts an element into place, [index] × 60 ms after the screen appears.
 * The resting state is fully visible; with reduced motion nothing moves.
 */
@Composable
fun Modifier.appear(index: Int): Modifier {
    if (LocalReducedMotion.current) return this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Direction-aware slide for step-by-step content (onboarding steps, module stages, quiz questions). */
@Composable
fun <S : Comparable<S>> rememberStepTransition(): androidx.compose.animation.AnimatedContentTransitionScope<S>.() -> androidx.compose.animation.ContentTransform {
    val reduced = LocalReducedMotion.current
    return remember(reduced) {
        {
            if (reduced) {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            } else {
                val forward = targetState > initialState
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(300)) { if (forward) it / 4 else -it / 4 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300))) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
            }
        }
    }
}

/** A newer prototype build that can be installed with one tap. Null when the app is up to date. */
data class UpdateOffer(val build: Int, val publishedOn: String, val installing: Boolean, val install: () -> Unit)

val LocalUpdateOffer = compositionLocalOf<UpdateOffer?> { null }
