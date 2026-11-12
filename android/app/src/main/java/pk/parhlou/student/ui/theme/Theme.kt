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
 * PROVISIONAL tokens describing the approved purple/lavender "Easy Guided" look (PRD §5).
 * Replace these values with the exact tokens from the approved UI reference when it arrives;
 * screens only use MaterialTheme, so nothing else needs to change.
 */
object ParhlouColors {
    val Purple = Color(0xFF5B3FD6)
    val PurpleDeep = Color(0xFF3D2A99)
    val Lavender = Color(0xFFEDE8FF)
    val LavenderSoft = Color(0xFFF7F4FF)
    val Ink = Color(0xFF1E1A33)
    val InkMuted = Color(0xFF5C5677)
    val Mint = Color(0xFF1F8A5B)
    val Amber = Color(0xFF9A6200)
    val Rose = Color(0xFFB3261E)
}

private val Light = lightColorScheme(
    primary = ParhlouColors.Purple,
    onPrimary = Color.White,
    primaryContainer = ParhlouColors.Lavender,
    onPrimaryContainer = ParhlouColors.PurpleDeep,
    secondary = ParhlouColors.Mint,
    tertiary = ParhlouColors.Amber,
    background = ParhlouColors.LavenderSoft,
    onBackground = ParhlouColors.Ink,
    surface = Color.White,
    onSurface = ParhlouColors.Ink,
    surfaceVariant = ParhlouColors.Lavender,
    onSurfaceVariant = ParhlouColors.InkMuted,
    error = ParhlouColors.Rose,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC8BBFF),
    onPrimary = Color(0xFF2A1A7A),
    primaryContainer = Color(0xFF3D2A99),
    onPrimaryContainer = Color(0xFFEDE8FF),
    secondary = Color(0xFF7FD8AE),
    tertiary = Color(0xFFF2C27A),
    background = Color(0xFF15121F),
    onBackground = Color(0xFFEAE6F7),
    surface = Color(0xFF1F1B2E),
    onSurface = Color(0xFFEAE6F7),
    surfaceVariant = Color(0xFF2C2740),
    onSurfaceVariant = Color(0xFFC9C3DD),
    error = Color(0xFFF2B8B5),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ParhlouTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
