package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Browser-tab-style navigation: two pinned section tabs, plus one closable tab per opened household/activity. */
private sealed interface AppTab {
    val key: String
    val title: String

    data object HouseholdsPinned : AppTab {
        override val key = "pinned:households"
        override val title = "🏠 Household"
    }

    data object ActivitiesPinned : AppTab {
        override val key = "pinned:activities"
        override val title = "✈️ Activities"
    }

    data class HouseholdOpen(val id: String, val name: String) : AppTab {
        override val key get() = "household:$id"
        override val title get() = name
    }

    data class TripOpen(val id: String, val name: String) : AppTab {
        override val key get() = "trip:$id"
        override val title get() = name
    }
}

@Composable
fun DashboardApp(api: ApiClient) {
    var darkTheme by remember { mutableStateOf(ThemePreference.load()) }
    var openTabs by remember { mutableStateOf(listOf<AppTab>()) }
    var selectedKey by remember { mutableStateOf(AppTab.HouseholdsPinned.key) }

    val allTabs = listOf(AppTab.HouseholdsPinned, AppTab.ActivitiesPinned) + openTabs
    val selected = allTabs.find { it.key == selectedKey } ?: AppTab.HouseholdsPinned

    fun openHousehold(id: String, name: String) {
        val key = "household:$id"
        if (openTabs.none { it.key == key }) openTabs = openTabs + AppTab.HouseholdOpen(id, name)
        selectedKey = key
    }

    fun openTrip(id: String, name: String) {
        val key = "trip:$id"
        if (openTabs.none { it.key == key }) openTabs = openTabs + AppTab.TripOpen(id, name)
        selectedKey = key
    }

    fun closeTab(key: String) {
        openTabs = openTabs.filterNot { it.key == key }
        if (selectedKey == key) selectedKey = AppTab.HouseholdsPinned.key
    }

    fun renameOpenTab(key: String, newName: String) {
        openTabs = openTabs.map {
            when {
                it.key == key && it is AppTab.HouseholdOpen -> it.copy(name = newName)
                it.key == key && it is AppTab.TripOpen -> it.copy(name = newName)
                else -> it
            }
        }
    }

    KharchaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Kharcha", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = {
                        darkTheme = !darkTheme
                        ThemePreference.save(darkTheme)
                    }) {
                        Text(if (darkTheme) "☀️" else "🌙")
                    }
                }
                HorizontalDivider()

                ScrollableTabRow(selectedTabIndex = allTabs.indexOf(selected).coerceAtLeast(0), edgePadding = 12.dp) {
                    allTabs.forEach { tab ->
                        Tab(
                            selected = tab.key == selected.key,
                            onClick = { selectedKey = tab.key },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tab.title)
                                    if (tab !is AppTab.HouseholdsPinned && tab !is AppTab.ActivitiesPinned) {
                                        Box(
                                            Modifier.padding(start = 2.dp),
                                        ) {
                                            IconButton(onClick = { closeTab(tab.key) }, modifier = Modifier.size(18.dp)) {
                                                Text("✕", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                HorizontalDivider()

                Box(Modifier.fillMaxSize()) {
                    when (val tab = selected) {
                        AppTab.HouseholdsPinned -> HouseholdsListScreen(api = api, onOpenHousehold = ::openHousehold)
                        AppTab.ActivitiesPinned -> ActivitiesListScreen(api = api, onOpenTrip = ::openTrip)
                        is AppTab.HouseholdOpen -> HouseholdDetailScreen(
                            api = api,
                            householdId = tab.id,
                            onRenamed = { newName -> renameOpenTab(tab.key, newName) },
                        )
                        is AppTab.TripOpen -> TripDetailScreen(
                            api = api,
                            tripId = tab.id,
                            onRenamed = { newName -> renameOpenTab(tab.key, newName) },
                        )
                    }
                }
            }
        }
    }
}
