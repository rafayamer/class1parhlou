package pk.parhlou.student.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pk.parhlou.student.R

/**
 * Parhlou "Bold" brand (decision D-030): deep teal for actions, sunny yellow for rewards and highlights, a clean white
 * page, chunky 3D buttons and cards with a darker "lip", and the rounded Nunito typeface.
 *
 * Role mapping used by every screen:
 * - primary            = teal: the main action, selected tab, current path node
 * - primaryContainer   = pale teal for highlighted cards
 * - secondary          = green for correct / done / good states
 * - tertiary           = amber for warnings ("not official")
 * - error              = coral for wrong answers and errors
 * Extra brand colours (lips, sun, XP, unit colours) are in [Brand], read with [LocalBrand].
 */
object Palette {
    val Teal = Color(0xFF0B8A8A)
    val TealLip = Color(0xFF066565)
    val TealPale = Color(0xFFDDF4F3)
    val Sun = Color(0xFFFFC93C)
    val SunLip = Color(0xFFDDA200)
    val SunInk = Color(0xFF3B2A00)
    val Green = Color(0xFF1C9E5E)
    val GreenLip = Color(0xFF13764A)
    val GreenPale = Color(0xFFDDF6E8)
    val Coral = Color(0xFFE5484D)
    val CoralLip = Color(0xFFB8333A)
    val CoralPale = Color(0xFFFDE4E4)
    val Amber = Color(0xFF9A5B00)
    val Indigo = Color(0xFF5B5FE0)
    val Ink = Color(0xFF1D2B33)
    val InkMuted = Color(0xFF5E6E77)
    val Page = Color(0xFFFFFFFF)
    val Soft = Color(0xFFF3F7F8)
    val Line = Color(0xFFE2E9EC)
    val LineLip = Color(0xFFD2DCE0)

    val NightPage = Color(0xFF0F1A1F)
    val NightCard = Color(0xFF16252C)
    val NightSoft = Color(0xFF1D3039)
    val NightLine = Color(0xFF2A3F49)
    val NightLineLip = Color(0xFF223540)
    val NightInk = Color(0xFFEAF2F4)
    val NightMuted = Color(0xFFA3B4BB)
    val TealBright = Color(0xFF2CC3BF)
    val TealBrightLip = Color(0xFF168E8B)
    val GreenBright = Color(0xFF3DD68C)
    val CoralBright = Color(0xFFFF7176)
}

/** Colours outside Material's roles: the 3D lips, the reward yellow, XP and the colour of each unit on the path. */
@Immutable
data class Brand(
    val lip: Color, val sun: Color, val sunLip: Color, val onSun: Color,
    val success: Color, val successLip: Color, val successPale: Color,
    val danger: Color, val dangerLip: Color, val dangerPale: Color,
    val line: Color, val lineLip: Color, val xp: Color,
    val units: List<Pair<Color, Color>>,
)

private val unitColours = listOf(
    Color(0xFF0B8A8A) to Color(0xFF066565), Color(0xFF5B5FE0) to Color(0xFF4044B5), Color(0xFFE0712B) to Color(0xFFB4561C),
    Color(0xFF1C9E5E) to Color(0xFF13764A), Color(0xFFC2459B) to Color(0xFF983277), Color(0xFF2F7FD8) to Color(0xFF2161AD),
)

private val LightBrand = Brand(Palette.TealLip, Palette.Sun, Palette.SunLip, Palette.SunInk, Palette.Green, Palette.GreenLip, Palette.GreenPale,
    Palette.Coral, Palette.CoralLip, Palette.CoralPale, Palette.Line, Palette.LineLip, Palette.Indigo, unitColours)
private val DarkBrand = Brand(Palette.TealBrightLip, Palette.Sun, Palette.SunLip, Palette.SunInk, Palette.GreenBright, Palette.GreenLip,
    Color(0xFF173B2B), Palette.CoralBright, Palette.CoralLip, Color(0xFF3F1F22), Palette.NightLine, Palette.NightLineLip, Color(0xFF8C8FFF), unitColours)

val LocalBrand = staticCompositionLocalOf { LightBrand }

private val Light = lightColorScheme(
    primary = Palette.Teal, onPrimary = Color.White,
    primaryContainer = Palette.TealPale, onPrimaryContainer = Color(0xFF053F3F),
    secondary = Palette.Green, onSecondary = Color.White,
    secondaryContainer = Palette.GreenPale, onSecondaryContainer = Color(0xFF0B3D24),
    tertiary = Palette.Amber, onTertiary = Color.White,
    background = Palette.Page, onBackground = Palette.Ink,
    surface = Palette.Page, onSurface = Palette.Ink,
    surfaceVariant = Palette.Soft, onSurfaceVariant = Palette.InkMuted,
    outline = Palette.Line, outlineVariant = Palette.Line,
    error = Palette.Coral, onError = Color.White,
    surfaceTint = Color.Transparent, surfaceBright = Palette.Page, surfaceDim = Palette.Soft,
    surfaceContainerLowest = Palette.Page, surfaceContainerLow = Palette.Page, surfaceContainer = Palette.Soft,
    surfaceContainerHigh = Palette.Page, surfaceContainerHighest = Palette.Page,
)

private val Dark = darkColorScheme(
    primary = Palette.TealBright, onPrimary = Color(0xFF002E2D),
    primaryContainer = Color(0xFF123F42), onPrimaryContainer = Color(0xFFBFF0EE),
    secondary = Palette.GreenBright, onSecondary = Color(0xFF00321A),
    secondaryContainer = Color(0xFF173B2B), onSecondaryContainer = Color(0xFFC6F3DA),
    tertiary = Color(0xFFF0C072), onTertiary = Color(0xFF3B2A00),
    background = Palette.NightPage, onBackground = Palette.NightInk,
    surface = Palette.NightCard, onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightSoft, onSurfaceVariant = Palette.NightMuted,
    outline = Palette.NightLine, outlineVariant = Palette.NightLine,
    error = Palette.CoralBright, onError = Color(0xFF3F0005),
    surfaceTint = Color.Transparent, surfaceBright = Palette.NightSoft, surfaceDim = Palette.NightPage,
    surfaceContainerLowest = Palette.NightPage, surfaceContainerLow = Palette.NightCard, surfaceContainer = Palette.NightSoft,
    surfaceContainerHigh = Palette.NightCard, surfaceContainerHighest = Palette.NightCard,
)

/** Nunito (SIL Open Font Licence), bundled so it works offline. */
val Nunito = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_extrabold, FontWeight.Black),
)

private fun s(size: Int, weight: FontWeight, line: Int, spacing: Float = 0f) =
    TextStyle(fontFamily = Nunito, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp)

private val Type = Typography(
    displayLarge = s(44, FontWeight.ExtraBold, 50, -0.5f),
    displayMedium = s(38, FontWeight.ExtraBold, 44, -0.5f),
    displaySmall = s(32, FontWeight.ExtraBold, 38, -0.4f),
    headlineLarge = s(30, FontWeight.ExtraBold, 36, -0.3f),
    headlineMedium = s(26, FontWeight.ExtraBold, 32, -0.3f),
    headlineSmall = s(23, FontWeight.ExtraBold, 29),
    titleLarge = s(21, FontWeight.ExtraBold, 27),
    titleMedium = s(17, FontWeight.Bold, 23),
    titleSmall = s(15, FontWeight.Bold, 21),
    bodyLarge = s(17, FontWeight.Medium, 25),
    bodyMedium = s(15, FontWeight.Medium, 22),
    bodySmall = s(13, FontWeight.Medium, 18),
    labelLarge = s(16, FontWeight.ExtraBold, 20, 0.4f),
    labelMedium = s(13, FontWeight.Bold, 16, 0.2f),
    labelSmall = s(11, FontWeight.Bold, 14, 0.3f),
)

@Composable
fun ParhlouTheme(dark: Boolean = isSystemInDarkTheme(), accent: Color? = null, content: @Composable () -> Unit) {
    // The owner can set a brand colour in the owner panel; it replaces teal for buttons, highlights and the 3D lip.
    val base = if (dark) Dark else Light
    val brand = if (dark) DarkBrand else LightBrand
    val scheme = if (accent == null) base else {
        val main = if (dark) androidx.compose.ui.graphics.lerp(accent, Color.White, 0.25f) else accent
        base.copy(
            primary = main,
            primaryContainer = androidx.compose.ui.graphics.lerp(accent, if (dark) Color.Black else Color.White, if (dark) 0.6f else 0.85f),
            onPrimaryContainer = if (dark) Color.White else androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.55f),
        )
    }
    val lip = if (accent == null) brand else brand.copy(lip = androidx.compose.ui.graphics.lerp(scheme.primary, Color.Black, 0.3f))
    CompositionLocalProvider(LocalBrand provides lip) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}

/**
 * The brand gradient behind hero cards (player card, exam summary): deep enough for white text in light and dark, and it
 * follows the owner's brand colour from the owner panel. With the built-in teal it runs teal to indigo.
 */
@Composable
fun heroGradient(): androidx.compose.ui.graphics.Brush {
    val start = androidx.compose.ui.graphics.lerp(LocalBrand.current.lip, MaterialTheme.colorScheme.primary, 0.55f)
    // Dark mode's brand colours are brighter, so they are deepened more to keep white text readable.
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val deep = androidx.compose.ui.graphics.lerp(start, Color.Black, if (night) 0.4f else 0.12f)
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(deep, androidx.compose.ui.graphics.lerp(deep, Color(0xFF4F53D6), 0.75f)))
}

