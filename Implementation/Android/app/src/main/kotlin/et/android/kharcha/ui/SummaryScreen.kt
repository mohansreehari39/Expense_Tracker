package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.ApiClient
import et.android.kharcha.data.ConnectionStore
import et.android.kharcha.data.HouseholdDto
import et.android.kharcha.data.MoneyDto
import et.android.kharcha.data.TripDto
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal

/**
 * Landing page — nothing is selected yet, so this shows each household's
 * monthly budget at a glance plus a rollup of what you're owed / you owe
 * across activities, rather than any single household/activity's detail.
 */
@Composable
fun SummaryScreen(
    api: ApiClient,
    store: ConnectionStore,
    households: List<HouseholdDto>,
    trips: List<TripDto>,
    onOpenHousehold: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    var owedToYou by remember { mutableStateOf(0L) }
    var youOwe by remember { mutableStateOf(0L) }
    var owedCurrency by remember { mutableStateOf("INR") }
    var perTripBalance by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(trips) {
        var owed = 0L
        var owe = 0L
        val breakdown = mutableMapOf<String, Long>()
        for (trip in trips) {
            val myParticipantId = store.myParticipantId(trip.id) ?: continue
            val detail = runCatching { api.trip(trip.id) }.getOrNull() ?: continue
            val balance = detail.balances[myParticipantId]?.minorUnits ?: continue
            breakdown[trip.id] = balance
            owedCurrency = detail.trip.budget.currency
            if (balance > 0) owed += balance else owe += -balance
        }
        owedToYou = owed
        youOwe = owe
        perTripBalance = breakdown
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Households", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (households.isEmpty()) {
            item {
                Text(
                    "No households yet — add one from the drawer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(households) { household ->
            Card(onClick = { onOpenHousehold(household.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(household.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    BudgetBar("This month", household.monthEvaluation)
                }
            }
        }

        if (owedToYou > 0 || youOwe > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Activities", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("You're owed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(MoneyDto(owedToYou, owedCurrency)), color = Teal, style = MaterialTheme.typography.titleMedium)
                            }
                            Column {
                                Text("You owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(MoneyDto(youOwe, owedCurrency)), color = Rose, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        items(trips) { trip ->
            Card(onClick = { onOpenActivity(trip.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(trip.name, style = MaterialTheme.typography.titleSmall)
                    val balance = perTripBalance[trip.id]
                    val caption = when {
                        balance == null -> "Tap to set up who you are in this activity"
                        balance == 0L -> "You're settled up"
                        balance > 0 -> "You're owed ${formatMoney(MoneyDto(balance, owedCurrency))}"
                        else -> "You owe ${formatMoney(MoneyDto(-balance, owedCurrency))}"
                    }
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
