package et.windows.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter

private const val SNAP_EDGE_THRESHOLD_PX = 24

/**
 * Custom title bar for the undecorated `Window` in Main.kt — the default
 * OS-drawn minimize/maximize/close buttons read as flat and generic, so
 * this draws colored, hover-responsive circular buttons matching the
 * app's indigo/teal/amber/rose palette instead.
 *
 * Also implements drag-to-top-to-maximize and drag-to-side-to-half-snap,
 * which `WindowDraggableArea` alone doesn't provide (it only moves the
 * window) — undecorated windows don't get Windows' native Aero Snap for
 * free, so this reimplements the two gestures users actually rely on.
 */
@Composable
fun FrameWindowScope.WindowTitleBar(
    icon: Painter,
    windowState: WindowState,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val dragHandler = remember(window) { SnapDragHandler(window, windowState) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    dragHandler.onDragStarted()
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                if (et.windows.KharchaConfig.isDev) "Kharcha (dev)" else "Kharcha",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TitleBarButton(glyph = "—", color = Amber, onClick = onMinimize)
            TitleBarButton(glyph = if (isMaximized) "❐" else "▢", color = Teal, onClick = onToggleMaximize)
            TitleBarButton(glyph = "✕", color = Rose, onClick = onClose)
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

/**
 * Drags the window by tracking raw AWT mouse events (mirroring Compose
 * Desktop's own `WindowDraggableArea` implementation) rather than Compose
 * pointer deltas, then on release checks the pointer's screen position
 * against the current monitor's edges to snap: top edge → maximize, left/
 * right edge → half-screen.
 */
private class SnapDragHandler(
    private val window: ComposeWindow,
    private val windowState: WindowState,
) {
    private var windowLocationAtDragStart: Point? = null
    private var dragStartPoint: Point? = null
    private var moved = false

    private val dragListener = object : MouseMotionAdapter() {
        override fun mouseDragged(event: MouseEvent) = onDrag()
    }
    private val releaseListener = object : MouseAdapter() {
        override fun mouseReleased(event: MouseEvent) {
            window.removeMouseMotionListener(dragListener)
            window.removeMouseListener(this)
            if (moved) onDragEnded()
        }
    }

    fun onDragStarted() {
        dragStartPoint = currentPointerLocation() ?: return
        windowLocationAtDragStart = window.location
        moved = false
        window.addMouseListener(releaseListener)
        window.addMouseMotionListener(dragListener)
    }

    private fun onDrag() {
        val startLocation = windowLocationAtDragStart ?: return
        val startPoint = dragStartPoint ?: return
        val point = currentPointerLocation() ?: return
        if (windowState.placement == WindowPlacement.Maximized) {
            // Restore to floating first, like native drag-off-maximize.
            windowState.placement = WindowPlacement.Floating
        }
        moved = true
        window.setLocation(startLocation.x + (point.x - startPoint.x), startLocation.y + (point.y - startPoint.y))
    }

    private fun onDragEnded() {
        val point = currentPointerLocation() ?: return
        val usable = usableScreenBounds(screenConfigurationAt(point))
        when {
            point.y <= usable.y + SNAP_EDGE_THRESHOLD_PX -> {
                window.maximizedBounds = usable
                windowState.placement = WindowPlacement.Maximized
            }
            point.x <= usable.x + SNAP_EDGE_THRESHOLD_PX -> {
                windowState.placement = WindowPlacement.Floating
                window.setBounds(usable.x, usable.y, usable.width / 2, usable.height)
            }
            point.x >= usable.x + usable.width - SNAP_EDGE_THRESHOLD_PX -> {
                windowState.placement = WindowPlacement.Floating
                val leftWidth = usable.width / 2
                window.setBounds(usable.x + leftWidth, usable.y, usable.width - leftWidth, usable.height)
            }
        }
    }
}

private fun currentPointerLocation(): Point? = MouseInfo.getPointerInfo()?.location
