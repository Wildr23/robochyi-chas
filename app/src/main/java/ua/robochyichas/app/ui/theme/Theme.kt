package ua.robochyichas.app.ui.theme

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.model.ThemeMode

@Immutable
data class AppColors(
    val isDark: Boolean,
    val bgPrimary: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val emerald: Color,
    val emeraldContainer: Color,
    val emeraldOnContainer: Color,
    val amber: Color,
    val amberBright: Color,
    val amberContainer: Color,
    val amberOnContainer: Color,
    val coral: Color,
    val coralContainer: Color,
    val coralOnContainer: Color,
    val sky: Color,
    val skyContainer: Color,
    val skyOnContainer: Color,
    val heroStart: Color,
    val heroEnd: Color
)

private val DarkAppColors = AppColors(
    isDark = true,
    bgPrimary = Color(0xFF090D16),
    surfaceCard = Color(0xFF111827),
    surfaceElevated = Color(0xFF1B2436),
    borderSubtle = Color(0xFF28344A),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    emerald = Color(0xFF10B981),
    emeraldContainer = Color(0xFF064E3B),
    emeraldOnContainer = Color(0xFF6EE7B7),
    amber = Color(0xFFF59E0B),
    amberBright = Color(0xFFFCD34D),
    amberContainer = Color(0xFF451A03),
    amberOnContainer = Color(0xFFFDE68A),
    coral = Color(0xFFF43F5E),
    coralContainer = Color(0xFF4C0519),
    coralOnContainer = Color(0xFFFDA4AF),
    sky = Color(0xFF38BDF8),
    skyContainer = Color(0xFF0C4A6E),
    skyOnContainer = Color(0xFF7DD3FC),
    heroStart = Color(0xFF0F172A),
    heroEnd = Color(0xFF16243D)
)

private val LightAppColors = AppColors(
    isDark = false,
    bgPrimary = Color(0xFFF1F5F9),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    emerald = Color(0xFF059669),
    emeraldContainer = Color(0xFFD1FAE5),
    emeraldOnContainer = Color(0xFF065F46),
    amber = Color(0xFFD97706),
    amberBright = Color(0xFFB45309),
    amberContainer = Color(0xFFFEF3C7),
    amberOnContainer = Color(0xFF78350F),
    coral = Color(0xFFE11D48),
    coralContainer = Color(0xFFFFE4E6),
    coralOnContainer = Color(0xFF9F1239),
    sky = Color(0xFF0284C7),
    skyContainer = Color(0xFFE0F2FE),
    skyOnContainer = Color(0xFF075985),
    heroStart = Color(0xFF0F172A),
    heroEnd = Color(0xFF1E293B)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val DarkM3Scheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF022C22),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF271300),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF090D16),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1B2436),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF28344A),
    error = Color(0xFFF43F5E)
)

private val LightM3Scheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Composable
fun RobochyiChasTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val m3Scheme = if (darkTheme) DarkM3Scheme else LightM3Scheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = AppTypography,
            content = content
        )
    }
}
