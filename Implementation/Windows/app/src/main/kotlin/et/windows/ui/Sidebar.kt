package et.windows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import et.windows.server.CreateTripRequest
import et.windows.server.HouseholdDto
import et.windows.server.MoneyDto
import et.windows.server.TripDto
import et.windows.server.UpdateHouseholdRequest
import et.windows.server.UpdateTripRequest
import kotlinx.coroutines.launch

sealed interface Selection {
    data object None : Selection
    data class HouseholdSel(val id: String) : Selection
    data class TripSel(val id: String) : Selection
}

private sealed interface SettingsTarget {
    data class HouseholdTarget(val id: String, val name: String, val defaultBudget: MoneyDto?) : SettingsTarget
    data class TripTarget(val id: String, val name: String, val budgetMinorUnits: Long, val currency: String) : SettingsTarget
}

/** Slack-style sidebar: two grouped sections, each with a "+" to create and a per-row gear for settings. */
@Composable
fun Sidebar(
    api: ApiClient,
    selection: Selection,
    refreshSignal: Int,
    darkTheme: Boolean,
    onSelect: (Selection) -> Unit,
    onChanged: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var households by remember { mutableStateOf<List<HouseholdDto>>(emptyList()) }
    var trips by remember { mutableStateOf<List<TripDto>>(emptyList()) }
    var showCreateHousehold by remember { mutableStateOf(false) }
    var showCreateTrip by remember { mutableStateOf(false) }
    var settingsTarget by remember { mutableStateOf<SettingsTarget?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        households = api.households()
        trips = api.trips()
    }

    LaunchedEffect(refreshSignal) { reload() }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        SidebarSectionHeader("🏠", "HOUSEHOLD", onAdd = { showCreateHousehold = true })
        households.forEach { household ->
            SidebarRow(
                label = household.name,
                statusColor = household.weekEvaluation?.let { statusColor(it.status) },
                selected = selection == Selection.HouseholdSel(household.id),
                onClick = { onSelect(Selection.HouseholdSel(household.id)) },
                onSettings = {
                    settingsTarget = SettingsTarget.HouseholdTarget(household.id, household.name, household.defaultMonthlyBudget)
                },
            )
        }
        if (households.isEmpty()) SidebarEmptyHint("No households yet")

        Spacer(Modifier.height(24.dp))

        SidebarSectionHeader("✈️", "ACTIVITIES", onAdd = { showCreateTrip = true })
        trips.forEach { trip ->
            SidebarRow(
                label = trip.name,
                statusColor = trip.evaluation?.let { statusColor(it.status) },
                selected = selection == Selection.TripSel(trip.id),
                onClick = { onSelect(Selection.TripSel(trip.id)) },
                onSettings = {
                    settingsTarget = SettingsTarget.TripTarget(trip.id, trip.name, trip.budget.minorUnits, trip.budget.currency)
                },
            )
        }
        if (trips.isEmpty()) SidebarEmptyHint("No activities yet")
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (darkTheme) "Dark Mode" else "Light Mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeToggleSwitch(darkTheme = darkTheme, onToggle = onToggleTheme)
    }
    }

    if (showCreateHousehold) {
        SimpleTextDialog(
            title = "New Household",
            label = "Household name",
            confirmLabel = "Create",
            onDismiss = { showCreateHousehold = false },
            onConfirm = { name ->
                scope.launch {
                    val created = api.createHousehold(name)
                    showCreateHousehold = false
                    onChanged()
                    onSelect(Selection.HouseholdSel(created.id))
                }
            },
        )
    }

    if (showCreateTrip) {
        CreateTripDialog(
            onDismiss = { showCreateTrip = false },
            onSubmit = { name, budgetMinorUnits, currency, participantNames ->
                scope.launch {
                    val created = api.createTrip(
                        CreateTripRequest(
                            name = name,
                            startDate = System.currentTimeMillis(),
                            budgetAmountMinorUnits = budgetMinorUnits,
                            currency = currency,
                            participantNames = participantNames,
                        ),
                    )
                    showCreateTrip = false
                    onChanged()
                    onSelect(Selection.TripSel(created.id))
                }
            },
        )
    }

    when (val target = settingsTarget) {
        is SettingsTarget.HouseholdTarget -> HouseholdSettingsDialog(
            currentName = target.name,
            currentDefaultBudget = target.defaultBudget,
            onDismiss = { settingsTarget = null },
            onSave = { newName, newDefaultBudget ->
                scope.launch {
                    api.updateHousehold(target.id, UpdateHouseholdRequest(newName, newDefaultBudget))
                    settingsTarget = null
                    onChanged()
                }
            },
        )
        is SettingsTarget.TripTarget -> TripSettingsDialog(
            currentName = target.name,
            currentBudgetMinorUnits = target.budgetMinorUnits,
            currency = target.currency,
            onDismiss = { settingsTarget = null },
            onSave = { newName, newBudget ->
                scope.launch {
                    api.updateTrip(target.id, UpdateTripRequest(newName, newBudget, target.currency))
                    settingsTarget = null
                    onChanged()
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun SidebarSectionHeader(icon: String, title: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SidebarIconButton(
            glyph = "+",
            onClick = onAdd,
            size = 26.dp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SidebarRow(label: String, statusColor: Color?, selected: Boolean, onClick: () -> Unit, onSettings: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(statusColor ?: MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (hovered || selected) {
            SidebarIconButton(glyph = "⚙", onClick = onSettings, size = 20.dp)
        }
    }
}

@Composable
private fun SidebarEmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SidebarIconButton(
    glyph: String,
    onClick: () -> Unit,
    size: Dp = 22.dp,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .size(size)
            .hoverable(interactionSource)
            .clip(CircleShape)
            .background(if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
