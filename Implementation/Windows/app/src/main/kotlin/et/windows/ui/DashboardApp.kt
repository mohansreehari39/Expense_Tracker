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
import kotlinx.coroutines.delay

/** How often the sidebar + whichever detail screen is open re-fetch from the server. */
private const val AUTO_REFRESH_INTERVAL_MS = 4000L

/** Slack-style shell: a persistent sidebar (Household/Activities) and a main content pane. */
@Composable
fun FrameWindowScope.DashboardApp(
    api: ApiClient,
    icon: Painter,
    windowState: WindowState,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    // Explicit toggle choice wins; otherwise follow the OS theme; otherwise light.
    var darkTheme by remember { mutableStateOf(ThemePreference.load() ?: SystemTheme.isDark() ?: false) }
    var selection by remember { mutableStateOf<Selection>(Selection.None) }
    var refreshSignal by remember { mutableStateOf(0) }

    // The app has no push mechanism yet (Design/Windows/03-rest-api.md's
    // /ws/changes is aspirational, not implemented) — data written by
    // anything other than this window's own actions (e.g. the seed-data
    // scripts, or a future second device) wouldn't otherwise show up until
    // the next manual action forced a reload. Cheap enough locally that
    // polling is fine until real push exists.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MS)
            refreshSignal++
        }
    }

    // Undecorated windows don't automatically respect the taskbar's work
    // area when maximized on Windows — that's normally handled by the
    // native title bar we opted out of. Telling AWT explicitly what
    // "maximized" means fixes it; otherwise the window covers the
    // taskbar. Recomputed on every click (not once at startup) against
    // window.graphicsConfiguration — which reflects whichever monitor the
    // window is *currently* on — so dragging to a different monitor
    // (different taskbar position/size) before maximizing still works.
    fun handleToggleMaximize() {
        if (windowState.placement == WindowPlacement.Maximized) {
            windowState.placement = WindowPlacement.Floating
        } else {
            window.maximizedBounds = usableScreenBounds(window.graphicsConfiguration)
            windowState.placement = WindowPlacement.Maximized
        }
    }

    KharchaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                WindowTitleBar(
                    icon = icon,
                    windowState = windowState,
                    onMinimize = onMinimize,
                    onToggleMaximize = ::handleToggleMaximize,
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
