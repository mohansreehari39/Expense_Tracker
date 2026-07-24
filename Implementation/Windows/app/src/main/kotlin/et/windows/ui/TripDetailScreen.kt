package et.windows.ui

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
import androidx.compose.material3.IconButton
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
import et.windows.server.AddTripExpenseRequest
import et.windows.server.TripDetailResponse
import et.windows.server.UpdateTripRequest
import kotlinx.coroutines.launch

@Composable
fun TripDetailScreen(api: ApiClient, tripId: String, onRenamed: (String) -> Unit) {
    var detail by remember { mutableStateOf<TripDetailResponse?>(null) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            detail = api.trip(tripId)
            error = null
        } catch (e: Exception) {
            error = e.message ?: e::class.simpleName
        }
    }

    LaunchedEffect(tripId) { reload() }

    val current = detail
    val currency = current?.trip?.budget?.currency ?: "INR"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current?.trip?.name ?: "Activity", style = MaterialTheme.typography.headlineSmall)
            Row {
                IconButton(onClick = { showAddExpense = true }, enabled = current != null && current.participants.isNotEmpty()) {
                    Text("➕")
                }
                IconButton(onClick = { showSettings = true }, enabled = current != null) { Text("⚙️") }
            }
        }

        if (current == null) {
            Text("Loading…", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    error?.let { Text("Couldn't reach the server: $it", color = MaterialTheme.colorScheme.error) }

                    current.trip.evaluation?.let { BudgetStatusBanner(it, caption = "overall") }
                    Spacer(Modifier.height(24.dp))

                    if (current.balances.values.any { it.minorUnits != 0L }) {
                        Text("Balances", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            val entries = current.participants.mapIndexed { index, participant ->
                                val balance = current.balances[participant.id]?.minorUnits ?: 0L
                                BarEntry(
                                    label = participant.displayName,
                                    value = balance,
                                    color = if (balance >= 0) Teal else Rose,
                                    valueText = if (balance == 0L) {
                                        "settled"
                                    } else {
                                        formatMoney(et.windows.server.MoneyDto(kotlin.math.abs(balance), currency))
                                    },
                                )
                            }
                            SimpleBarChart(entries, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text("Balances Detail", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            current.participants.forEach { participant ->
                                val balance = current.balances[participant.id]
                                val label = when {
                                    balance == null || balance.minorUnits == 0L -> "settled up"
                                    balance.minorUnits > 0 -> "is owed ${formatMoney(balance)}"
                                    else -> "owes ${formatMoney(balance.copy(minorUnits = -balance.minorUnits))}"
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(participant.displayName)
                                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (current.suggestedSettlements.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Suggested Settlements", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                current.suggestedSettlements.forEach { s ->
                                    val fromName = current.participants.find { it.id == s.fromParticipantId }?.displayName ?: s.fromParticipantId
                                    val toName = current.participants.find { it.id == s.toParticipantId }?.displayName ?: s.toParticipantId
                                    Text("$fromName → $toName: ${formatMoney(s.amount)}")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (current.expenses.isEmpty()) {
                        Text("No expenses recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(current.expenses) { expense ->
                    val payerName = current.participants.find { it.id == expense.paidByParticipantId }?.displayName ?: "?"
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Paid by $payerName")
                                if (expense.note.isNotBlank()) {
                                    Text(expense.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(formatMoney(expense.amount), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    if (showAddExpense && current != null) {
        AddTripExpenseDialog(
            participants = current.participants,
            currency = currency,
            onDismiss = { showAddExpense = false },
            onSubmit = { amountMinorUnits, paidByParticipantId, note ->
                scope.launch {
                    api.addTripExpense(
                        tripId,
                        AddTripExpenseRequest(
                            amountMinorUnits = amountMinorUnits,
                            currency = currency,
                            paidByParticipantId = paidByParticipantId,
                            occurredAt = System.currentTimeMillis(),
                            note = note,
                        ),
                    )
                    showAddExpense = false
                    reload()
                }
            },
        )
    }

    if (showSettings && current != null) {
        TripSettingsDialog(
            currentName = current.trip.name,
            currentBudgetMinorUnits = current.trip.budget.minorUnits,
            currency = currency,
            onDismiss = { showSettings = false },
            onSave = { name, budgetMinorUnits ->
                scope.launch {
                    api.updateTrip(tripId, UpdateTripRequest(name, budgetMinorUnits, currency))
                    showSettings = false
                    onRenamed(name)
                    reload()
                }
            },
        )
    }
}
