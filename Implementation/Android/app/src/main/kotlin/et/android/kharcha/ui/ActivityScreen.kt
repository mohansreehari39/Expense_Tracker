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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.BudgetMath
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import kotlinx.coroutines.launch

@Composable
fun ActivityScreen(repo: LocalRepository, activityId: String, myName: String) {
    var activity by remember { mutableStateOf<ActivityEntity?>(null) }
    val participants by repo.observeParticipants(activityId).collectAsState(initial = emptyList())
    val expenses by repo.observeActivityExpenses(activityId).collectAsState(initial = emptyList())
    var balances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ActivityExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ActivityExpenseEntity?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(activityId) {
        activity = repo.activity(activityId)
        repo.ensureMyParticipation(activityId, myName)
    }

    LaunchedEffect(activityId, participants, expenses) {
        balances = repo.activityBalances(activityId)
    }

    val current = activity
    val currency = current?.currency ?: "INR"
    val myParticipantId = participants.find { it.isMe }?.id
    val spent = expenses.sumOf { it.amountMinorUnits }
    val evaluation = current?.let { BudgetMath.evaluateBudget(it.budgetMinorUnits, spent, currency) }

    Scaffold(
        floatingActionButton = {
            if (participants.isNotEmpty()) {
                FloatingActionButton(onClick = { showAddExpense = true }) { Icon(Icons.Filled.Add, contentDescription = "Add Expense") }
            }
        },
    ) { padding ->
        if (current == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(current.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { BudgetBar("Overall budget", evaluation) }
                    }

                    val myBalance = myParticipantId?.let { balances[it] }
                    if (myBalance != null && myBalance != 0L) {
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (myBalance > 0) "You're owed" else "You owe", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatMoney(kotlin.math.abs(myBalance), currency),
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
                            participants.forEach { participant ->
                                val balance = balances[participant.id]
                                val label = when {
                                    balance == null || balance == 0L -> "settled up"
                                    balance > 0 -> "is owed ${formatMoney(balance, currency)}"
                                    else -> "owes ${formatMoney(-balance, currency)}"
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
                    if (expenses.isEmpty()) {
                        Text("No expenses recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(expenses.sortedByDescending { it.occurredAt }) { expense ->
                    val payerName = participants.find { it.id == expense.paidByParticipantId }?.displayName ?: "?"
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Paid by $payerName · ${formatExpenseDate(expense.occurredAt)}")
                                if (expense.note.isNotBlank()) {
                                    Text(expense.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(expense.amountMinorUnits, expense.currency), style = MaterialTheme.typography.titleMedium)
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
            participants = participants,
            currency = currency,
            defaultParticipantId = myParticipantId,
            onDismiss = { showAddExpense = false },
            onSubmit = { amountMinorUnits, paidByParticipantId, occurredAt, note ->
                scope.launch {
                    repo.addActivityExpense(activityId, amountMinorUnits, currency, paidByParticipantId, occurredAt, note)
                    showAddExpense = false
                }
            },
        )
    }

    if (current != null) {
        expenseToEdit?.let { expense ->
            AddTripExpenseDialog(
                participants = participants,
                currency = currency,
                defaultParticipantId = myParticipantId,
                expenseToEdit = expense,
                onDismiss = { expenseToEdit = null },
                onSubmit = { amountMinorUnits, paidByParticipantId, occurredAt, note ->
                    scope.launch {
                        repo.updateActivityExpense(
                            expense.copy(
                                amountMinorUnits = amountMinorUnits,
                                paidByParticipantId = paidByParticipantId,
                                occurredAt = occurredAt,
                                note = note,
                            ),
                        )
                        expenseToEdit = null
                    }
                },
            )
        }
    }

    expenseToDelete?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "This removes the ${formatMoney(expense.amountMinorUnits, expense.currency)} expense and can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                scope.launch {
                    repo.deleteActivityExpense(expense)
                    expenseToDelete = null
                }
            },
        )
    }
}
