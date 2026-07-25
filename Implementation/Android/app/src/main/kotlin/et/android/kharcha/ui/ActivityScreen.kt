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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import et.android.kharcha.data.AddTripExpenseRequest
import et.android.kharcha.data.ApiClient
import et.android.kharcha.data.ConnectionStore
import et.android.kharcha.data.MoneyDto
import et.android.kharcha.data.TripDetailResponse
import et.android.kharcha.data.TripExpenseDto
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import kotlinx.coroutines.launch

@Composable
fun ActivityScreen(api: ApiClient, store: ConnectionStore, tripId: String, onChanged: () -> Unit) {
    var detail by remember { mutableStateOf<TripDetailResponse?>(null) }
    var myParticipantId by remember { mutableStateOf<String?>(null) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<TripExpenseDto?>(null) }
    var expenseToDelete by remember { mutableStateOf<TripExpenseDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            detail = api.trip(tripId)
            myParticipantId = store.myParticipantId(tripId)
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: e::class.simpleName ?: "Couldn't reach the server"
        }
    }

    LaunchedEffect(tripId) { reload() }

    if (detail == null && loadError != null) {
        ConnectionErrorScreen(message = loadError!!, onRetry = { scope.launch { reload() } })
        return
    }

    val current = detail
    val currency = current?.trip?.budget?.currency ?: "INR"

    Scaffold(
        floatingActionButton = {
            if (current != null && current.participants.isNotEmpty()) {
                FloatingActionButton(onClick = { showAddExpense = true }) { Icon(Icons.Filled.Add, contentDescription = "Add Expense") }
            }
        },
    ) { padding ->
        if (current == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(current.trip.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { BudgetBar("Overall budget", current.trip.evaluation) }
                    }

                    val myBalance = myParticipantId?.let { current.balances[it]?.minorUnits }
                    if (myBalance != null && myBalance != 0L) {
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (myBalance > 0) "You're owed" else "You owe", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatMoney(MoneyDto(kotlin.math.abs(myBalance), currency)),
                                    color = if (myBalance > 0) Teal else Rose,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Balances", style = MaterialTheme.typography.titleMedium)
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
                                Text("Paid by $payerName · ${formatExpenseDate(expense.occurredAt)}")
                                if (expense.note.isNotBlank()) {
                                    Text(expense.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(expense.amount), style = MaterialTheme.typography.titleMedium)
                                Row {
                                    TextButton(onClick = { expenseToEdit = expense }) { Text("Edit") }
                                    TextButton(onClick = { expenseToDelete = expense }) { Text("Delete") }
                                }
                            }
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
            defaultParticipantId = myParticipantId,
            onDismiss = { showAddExpense = false },
            onSubmit = { amountMinorUnits, paidByParticipantId, occurredAt, note ->
                scope.launch {
                    api.addTripExpense(
                        tripId,
                        AddTripExpenseRequest(amountMinorUnits, currency, paidByParticipantId, occurredAt, note),
                    )
                    showAddExpense = false
                    reload()
                    onChanged()
                }
            },
        )
    }

    if (current != null) {
        expenseToEdit?.let { expense ->
            AddTripExpenseDialog(
                participants = current.participants,
                currency = currency,
                defaultParticipantId = myParticipantId,
                expenseToEdit = expense,
                onDismiss = { expenseToEdit = null },
                onSubmit = { amountMinorUnits, paidByParticipantId, occurredAt, note ->
                    scope.launch {
                        api.updateTripExpense(
                            tripId,
                            expense.id,
                            AddTripExpenseRequest(amountMinorUnits, currency, paidByParticipantId, occurredAt, note),
                        )
                        expenseToEdit = null
                        reload()
                    }
                },
            )
        }
    }

    expenseToDelete?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "This removes the ${formatMoney(expense.amount)} expense and can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                scope.launch {
                    api.deleteTripExpense(tripId, expense.id)
                    expenseToDelete = null
                    reload()
                }
            },
        )
    }
}
