package et.windows.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope

/**
 * Custom title bar for the undecorated `Window` in Main.kt — the default
 * OS-drawn minimize/maximize/close buttons read as flat and generic, so
 * this draws colored, hover-responsive circular buttons matching the
 * app's indigo/teal/amber/rose palette instead.
 */
@Composable
fun FrameWindowScope.WindowTitleBar(
    icon: Painter,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    WindowDraggableArea {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Kharcha", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleBarButton(glyph = if (darkTheme) "☀" else "☾", color = Indigo, onClick = onToggleTheme)
                TitleBarButton(glyph = "—", color = Amber, onClick = onMinimize)
                TitleBarButton(glyph = if (isMaximized) "❐" else "▢", color = Teal, onClick = onToggleMaximize)
                TitleBarButton(glyph = "✕", color = Rose, onClick = onClose)
            }
        }
    }
}

@Composable
private fun TitleBarButton(glyph: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .hoverable(interactionSource)
            .clip(CircleShape)
            .background(if (hovered) color else color.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (hovered) Color.White else color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
