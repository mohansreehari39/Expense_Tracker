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
import androidx.compose.ui.unit.dp

private val trackWidth = 56.dp
private val trackHeight = 28.dp
private val thumbSize = 22.dp
private val thumbInset = 3.dp

/**
 * Dark (left) / Light (right) slide switch — the thumb position itself
 * shows which theme is active, per the user's request to move this out
 * of the title bar and make it read as a switch rather than a button.
 */
@Composable
fun ThemeToggleSwitch(darkTheme: Boolean, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (darkTheme) thumbInset else trackWidth - thumbSize - thumbInset)
    val trackColor = if (darkTheme) Indigo.copy(alpha = 0.35f) else Amber.copy(alpha = 0.35f)

    Box(
        Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onToggle(!darkTheme) },
    ) {
        Text(
            "☾",
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 7.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "☀",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}
