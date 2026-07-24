package et.windows.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same indigo -> teal family as the app icon.
val Indigo = Color(0xFF4F46E5)
val IndigoDark = Color(0xFF4338CA)
val Teal = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)
val Rose = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = IndigoDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    background = Color(0xFFF7F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F8),
    error = Rose,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B85F5),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF121218),
    surface = Color(0xFF1B1B24),
    surfaceVariant = Color(0xFF25252F),
    error = Color(0xFFEF4444),
)

@Composable
fun KharchaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
