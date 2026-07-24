package et.windows.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Section(val label: String, val emoji: String) {
    HOUSEHOLDS("Households", "🏠"),
    ACTIVITIES("Activities", "✈️"),
}

private sealed interface Screen {
    data object List : Screen
    data class Detail(val id: String) : Screen
}

@Composable
fun DashboardApp(api: ApiClient) {
    var darkTheme by remember { mutableStateOf(ThemePreference.load()) }

    KharchaTheme(darkTheme = darkTheme) {
        var section by remember { mutableStateOf(Section.HOUSEHOLDS) }
        var householdScreen by remember { mutableStateOf<Screen>(Screen.List) }
        var activityScreen by remember { mutableStateOf<Screen>(Screen.List) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Section.entries.forEach { s ->
                        NavigationRailItem(
                            selected = section == s,
                            onClick = { section = s },
                            icon = { Text(s.emoji) },
                            label = { Text(s.label) },
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    NavigationRailItem(
                        selected = false,
                        onClick = {
                            darkTheme = !darkTheme
                            ThemePreference.save(darkTheme)
                        },
                        icon = { Text(if (darkTheme) "☀️" else "🌙") },
                        label = { Text(if (darkTheme) "Light" else "Dark") },
                    )
                }

                when (section) {
                    Section.HOUSEHOLDS -> when (val screen = householdScreen) {
                        Screen.List -> HouseholdsListScreen(
                            api = api,
                            onOpenHousehold = { householdScreen = Screen.Detail(it) },
                        )
                        is Screen.Detail -> HouseholdDetailScreen(
                            api = api,
                            householdId = screen.id,
                            onBack = { householdScreen = Screen.List },
                        )
                    }

                    Section.ACTIVITIES -> when (val screen = activityScreen) {
                        Screen.List -> ActivitiesListScreen(
                            api = api,
                            onOpenTrip = { activityScreen = Screen.Detail(it) },
                        )
                        is Screen.Detail -> TripDetailScreen(
                            api = api,
                            tripId = screen.id,
                            onBack = { activityScreen = Screen.List },
                        )
                    }
                }
            }
        }
    }
}
