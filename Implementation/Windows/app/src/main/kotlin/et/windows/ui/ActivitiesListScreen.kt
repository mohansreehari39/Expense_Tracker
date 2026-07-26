package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import et.windows.server.CreateTripRequest
import et.windows.server.TripDto
import kotlinx.coroutines.launch

@Composable
fun ActivitiesListScreen(api: ApiClient, onOpenTrip: (id: String, name: String) -> Unit) {
    var trips by remember { mutableStateOf<List<TripDto>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            trips = api.trips()
            error = null
        } catch (e: Exception) {
            error = e.message ?: e::class.simpleName
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.padding(24.dp).fillMaxSize()) {
        error?.let { Text("Couldn't reach the server: $it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Activities", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showCreate = true }) { Text("+ New Activity") }
        }
        Spacer(Modifier.height(20.dp))

        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No activities yet. Create one for a trip or any time-bound, group expense.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(trips) { trip ->
                    TripCard(trip, onClick = { onOpenTrip(trip.id, trip.name) })
                }
            }
        }
    }

    if (showCreate) {
        CreateTripDialog(
            onDismiss = { showCreate = false },
            onSubmit = { name, budgetMinorUnits, currency, participantNames ->
                scope.launch {
                    api.createTrip(
                        CreateTripRequest(
                            name = name,
                            startDate = System.currentTimeMillis(),
                            budgetAmountMinorUnits = budgetMinorUnits,
                            currency = currency,
                            participantNames = participantNames,
                        ),
                    )
                    showCreate = false
                    reload()
                }
            },
        )
    }
}

@Composable
private fun TripCard(trip: TripDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(trip.name, style = MaterialTheme.typography.titleLarge)
            Text("Budget: ${formatMoney(trip.budget)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            trip.evaluation?.let {
                Spacer(Modifier.height(12.dp))
                BudgetStatusBanner(it, caption = "Overall budget")
            }
        }
    }
}
