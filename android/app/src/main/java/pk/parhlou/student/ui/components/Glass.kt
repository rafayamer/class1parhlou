package pk.parhlou.student.ui.components

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.PrismalBackdrop
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

/**
 * Frosted glass capsule sampling [backdrop]; used for the floating title bar and glass buttons.
 * Only put it on a background element: anything tappable inside a wide glass capsule stops receiving taps
 * (the layer clips hit-testing to an outline that rejects them). See [GlassTabBar].
 */
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

/**
 * Floating glass tab bar. Plain tap targets (one per section) over a frosted capsule, with a pill that slides to the
 * selected tab. Built here rather than with Prismal's droplet tabs, whose drag overlay swallowed taps.
 */
@Composable
fun GlassTabBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: PrismalBackdrop,
) {
    val reduced = LocalReducedMotion.current
    val view = LocalView.current
    val selectedColor = MaterialTheme.colorScheme.onPrimaryContainer
    val idleColor = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The glass is drawn on its own layer behind the tabs, never around them: the glass clips touches to its
        // outline, and for a wide capsule that outline rejected every tap (the bar didn't respond on phones).
        Box(Modifier.fillMaxWidth().height(64.dp).shadow(10.dp, CircleShape, clip = false)) {
        Box(Modifier.matchParentSize().glassCapsule(backdrop))
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val tabWidth = maxWidth / items.size
            val pillX by animateDpAsState(
                tabWidth * selectedIndex,
                if (reduced) snap() else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                label = "tabPill",
            )
            Box(
                Modifier.offset(x = pillX).width(tabWidth).fillMaxHeight()
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .selectable(selected = selected, role = Role.Tab) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelect(index)
                            }
                            .testTag("tab-${item.route}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val color = if (selected) selectedColor else idleColor
                        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
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
/** [ready] is true once the new build has been downloaded in the background, so installing is one tap. */
data class UpdateOffer(val build: Int, val publishedOn: String, val installing: Boolean, val install: () -> Unit, val ready: Boolean = false)

val LocalUpdateOffer = compositionLocalOf<UpdateOffer?> { null }
