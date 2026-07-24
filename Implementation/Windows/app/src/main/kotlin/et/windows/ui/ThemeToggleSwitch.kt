package et.windows.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val trackWidth = 56.dp
private val trackHeight = 28.dp
private val thumbSize = 22.dp
private val thumbInset = 3.dp

/**
 * Dark (left) / Light (right) slide switch — the thumb carries the icon
 * for whichever theme is active (bright, on top of the thumb), while the
 * inactive side's icon stays dim in the track background. Previously both
 * icons were drawn at full opacity with a blank thumb on top, which
 * ended up hiding the *active* icon under the thumb and left the
 * *inactive* one fully visible — backwards from what a toggle should show.
 */
@Composable
fun ThemeToggleSwitch(darkTheme: Boolean, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (darkTheme) thumbInset else trackWidth - thumbSize - thumbInset)
    val trackColor = if (darkTheme) Indigo.copy(alpha = 0.35f) else Amber.copy(alpha = 0.35f)
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val activeColor = if (darkTheme) Indigo else Amber

    Box(
        Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onToggle(!darkTheme) },
    ) {
        // Always-present, dim icons — the un-covered one is the inactive theme.
        Text(
            "☾",
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = dimColor,
        )
        Text(
            "☀",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = dimColor,
        )
        // Thumb rides on top, carrying the active theme's icon in full color.
        Box(
            Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (darkTheme) "☾" else "☀",
                style = MaterialTheme.typography.labelSmall,
                color = activeColor,
            )
        }
    }
}
