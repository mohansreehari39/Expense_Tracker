package et.windows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Rectangle
import java.awt.Toolkit

/** Slack-style shell: a persistent sidebar (Household/Activities) and a main content pane. */
@Composable
fun FrameWindowScope.DashboardApp(
    api: ApiClient,
    icon: Painter,
    windowState: WindowState,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    // Explicit toggle choice wins; otherwise follow the OS theme; otherwise light.
    var darkTheme by remember { mutableStateOf(ThemePreference.load() ?: SystemTheme.isDark() ?: false) }
    var selection by remember { mutableStateOf<Selection>(Selection.None) }
    var refreshSignal by remember { mutableStateOf(0) }

    // Undecorated windows don't automatically respect the taskbar's work
    // area when maximized on Windows — that's normally handled by the
    // native title bar we opted out of. Telling AWT explicitly what
    // "maximized" means fixes it; otherwise the window covers the
    // taskbar. Single-primary-monitor assumption: if the window is moved
    // to another display before maximizing, these bounds won't match it.
    LaunchedEffect(Unit) {
        val screenBounds = window.graphicsConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
        window.maximizedBounds = Rectangle(
            screenBounds.x + insets.left,
            screenBounds.y + insets.top,
            screenBounds.width - insets.left - insets.right,
            screenBounds.height - insets.top - insets.bottom,
        )
    }

    KharchaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                WindowTitleBar(
                    icon = icon,
                    isMaximized = windowState.placement == WindowPlacement.Maximized,
                    onMinimize = onMinimize,
                    onToggleMaximize = onToggleMaximize,
                    onClose = onClose,
                )

                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        api = api,
                        selection = selection,
                        refreshSignal = refreshSignal,
                        darkTheme = darkTheme,
                        onSelect = { selection = it },
                        onChanged = { refreshSignal++ },
                        onToggleTheme = {
                            darkTheme = it
                            ThemePreference.save(it)
                        },
                        modifier = Modifier.width(260.dp).fillMaxHeight(),
                    )

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (val sel = selection) {
                            Selection.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Select a household or activity from the sidebar",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is Selection.HouseholdSel -> HouseholdDetailScreen(
                                api = api,
                                householdId = sel.id,
                                refreshSignal = refreshSignal,
                            )
                            is Selection.TripSel -> TripDetailScreen(
                                api = api,
                                tripId = sel.id,
                                refreshSignal = refreshSignal,
                            )
                        }
                    }
                }
            }
        }
    }
}
