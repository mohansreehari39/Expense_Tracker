package et.android.kharcha.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.android.kharcha.R

/*
 * Kharcha's "calm green" look (redesign concept A): tonal green-grey
 * surfaces, one deep green accent, Plus Jakarta Sans throughout, generous
 * rounding. Status colors (on track / nearing / over budget) are separate
 * from the accent and have their own dark-mode values.
 */

/** Colors Material's scheme has no slot for: budget status, tonal fills, hairlines. */
@Immutable
data class KharchaColors(
    val ok: Color,
    val warn: Color,
    val over: Color,
    val tonal: Color,
    val line: Color,
    val muted: Color,
)

private val LightKharcha = KharchaColors(
    ok = Color(0xFF13795F),
    warn = Color(0xFFC77A12),
    over = Color(0xFFC2412D),
    tonal = Color(0xFFE6EFEC),
    line = Color(0xFFE1E7E4),
    muted = Color(0xFF63706B),
)

private val DarkKharcha = KharchaColors(
    ok = Color(0xFF57C9A6),
    warn = Color(0xFFE8A64A),
    over = Color(0xFFEF7A64),
    tonal = Color(0xFF1F2A26),
    line = Color(0xFF26302C),
    muted = Color(0xFF93A19B),
)

val LocalKharchaColors = staticCompositionLocalOf { LightKharcha }

/** Shorthand for the extra palette inside composables. */
val kharcha: KharchaColors @Composable get() = LocalKharchaColors.current

// Budget status colors, kept under their historical names so existing call
// sites read the same; they now follow the light/dark theme.
val Teal: Color @Composable get() = kharcha.ok
val Amber: Color @Composable get() = kharcha.warn
val Rose: Color @Composable get() = kharcha.over

private val LightColors = lightColorScheme(
    primary = Color(0xFF13795F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8DF),
    onPrimaryContainer = Color(0xFF0A3D30),
    secondary = Color(0xFF13795F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EFEC),
    onSecondaryContainer = Color(0xFF18211E),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF18211E),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF18211E),
    surfaceVariant = Color(0xFFE6EFEC),
    onSurfaceVariant = Color(0xFF63706B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEFF3F1),
    outline = Color(0xFFC9D2CE),
    outlineVariant = Color(0xFFE1E7E4),
    // Snackbars and other "inverse" surfaces: dark green-grey, not Material's default lavender.
    inverseSurface = Color(0xFF18211E),
    inverseOnSurface = Color(0xFFE4EBE8),
    inversePrimary = Color(0xFF57C9A6),
    error = Color(0xFFC2412D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF57C9A6),
    onPrimary = Color(0xFF08261D),
    primaryContainer = Color(0xFF1C4A3D),
    onPrimaryContainer = Color(0xFFCFE8DF),
    secondary = Color(0xFF57C9A6),
    onSecondary = Color(0xFF08261D),
    secondaryContainer = Color(0xFF1F2A26),
    onSecondaryContainer = Color(0xFFE4EBE8),
    background = Color(0xFF0F1412),
    onBackground = Color(0xFFE4EBE8),
    surface = Color(0xFF0F1412),
    onSurface = Color(0xFFE4EBE8),
    surfaceVariant = Color(0xFF1F2A26),
    onSurfaceVariant = Color(0xFF93A19B),
    surfaceContainerLowest = Color(0xFF171D1B),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF171D1B),
    surfaceContainerHigh = Color(0xFF1B2220),
    surfaceContainerHighest = Color(0xFF222B28),
    outline = Color(0xFF3A4541),
    outlineVariant = Color(0xFF26302C),
    inverseSurface = Color(0xFFE4EBE8),
    inverseOnSurface = Color(0xFF18211E),
    inversePrimary = Color(0xFF13795F),
    error = Color(0xFFEF7A64),
)

@OptIn(ExperimentalTextApi::class)
private val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private fun TextStyle.jakarta(weight: FontWeight, tracking: Double = 0.0) =
    copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = tracking.sp)

private val Base = Typography()

private val KharchaTypography = Typography(
    displayLarge = Base.displayLarge.jakarta(FontWeight.ExtraBold, -1.0),
    displayMedium = Base.displayMedium.jakarta(FontWeight.ExtraBold, -0.8),
    displaySmall = Base.displaySmall.jakarta(FontWeight.ExtraBold, -0.6),
    headlineLarge = Base.headlineLarge.jakarta(FontWeight.ExtraBold, -0.4),
    headlineMedium = Base.headlineMedium.jakarta(FontWeight.ExtraBold, -0.3),
    headlineSmall = Base.headlineSmall.jakarta(FontWeight.Bold, -0.2),
    titleLarge = Base.titleLarge.jakarta(FontWeight.Bold),
    titleMedium = Base.titleMedium.jakarta(FontWeight.Bold),
    titleSmall = Base.titleSmall.jakarta(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.jakarta(FontWeight.Medium),
    bodyMedium = Base.bodyMedium.jakarta(FontWeight.Medium),
    bodySmall = Base.bodySmall.jakarta(FontWeight.Medium),
    labelLarge = Base.labelLarge.jakarta(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.jakarta(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.jakarta(FontWeight.SemiBold, 0.4),
)

private val KharchaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Follows the OS theme unless the user picked one in Me → Dark mode. */
@Composable
fun KharchaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // System bar icons and scrims follow the app's own light/dark choice, not just the OS
    // setting — otherwise light mode on a dark-mode phone gets white status-bar icons on white.
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(darkTheme, activity) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (darkTheme) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    CompositionLocalProvider(LocalKharchaColors provides if (darkTheme) DarkKharcha else LightKharcha) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KharchaTypography,
            shapes = KharchaShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
