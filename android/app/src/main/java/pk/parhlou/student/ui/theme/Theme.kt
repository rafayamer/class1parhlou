package pk.parhlou.student.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Parhlou "Calm Green" theme, from the founder's reference image (decision D-010):
 * soft sage background, opaque white study sheets, lime accents and a dark ink action colour.
 *
 * Role mapping used by every screen:
 * - primary            = main action (ink in light, lime in dark): Continue button, selected nav pill, hero card
 * - primaryContainer   = lime tint for highlighted cards and tiles
 * - secondary          = green used for icons, links and "good" states
 * - tertiary           = amber used only for warnings ("Sample — not reviewed", "not official")
 */
object Palette {
    // Light
    val Sage = Color(0xFFEDF2EA)
    val SageDeep = Color(0xFFE2EADC)
    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C211D)
    val InkMuted = Color(0xFF5A6558)
    val Lime = Color(0xFFC6EC8C)
    val LimeInk = Color(0xFF1E2D10)
    val Leaf = Color(0xFF3B7614)
    val Amber = Color(0xFF8A5A00)
    val Red = Color(0xFFB3261E)
    val Outline = Color(0xFFD3DCCD)

    // Dark
    val Night = Color(0xFF101410)
    val NightSurface = Color(0xFF1A201A)
    val NightRaised = Color(0xFF252D24)
    val Mist = Color(0xFFE6ECE2)
    val MistMuted = Color(0xFFB4BFAF)
    val LimeBright = Color(0xFFB9E772)
    val LimeDeep = Color(0xFF2D3D1C)
    val LeafLight = Color(0xFFA6DA66)
    val AmberLight = Color(0xFFF0C072)
    val RedLight = Color(0xFFF2B8B5)
    val OutlineDark = Color(0xFF3A4538)
}

private val Light = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.White,
    primaryContainer = Palette.Lime,
    onPrimaryContainer = Palette.LimeInk,
    secondary = Palette.Leaf,
    onSecondary = Palette.White,
    secondaryContainer = Palette.SageDeep,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Amber,
    background = Palette.Sage,
    onBackground = Palette.Ink,
    surface = Palette.White,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SageDeep,
    onSurfaceVariant = Palette.InkMuted,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Red,
)

private val Dark = darkColorScheme(
    primary = Palette.LimeBright,
    onPrimary = Palette.LimeInk,
    primaryContainer = Palette.LimeDeep,
    onPrimaryContainer = Color(0xFFDDF3BC),
    secondary = Palette.LeafLight,
    onSecondary = Palette.LimeInk,
    secondaryContainer = Palette.NightRaised,
    onSecondaryContainer = Palette.Mist,
    tertiary = Palette.AmberLight,
    background = Palette.Night,
    onBackground = Palette.Mist,
    surface = Palette.NightSurface,
    onSurface = Palette.Mist,
    surfaceVariant = Palette.NightRaised,
    onSurfaceVariant = Palette.MistMuted,
    outline = Palette.OutlineDark,
    outlineVariant = Palette.OutlineDark,
    error = Palette.RedLight,
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun ParhlouTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
