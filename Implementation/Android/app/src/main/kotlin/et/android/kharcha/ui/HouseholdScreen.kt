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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.ApiClient
import et.android.kharcha.data.CategoryDto
import et.android.kharcha.data.ConnectionStore
import et.android.kharcha.data.HouseholdExpenseDto
import et.android.kharcha.data.MemberDto
import et.android.kharcha.data.MonthBudgetResponse
import et.android.kharcha.data.RecordExpenseRequest
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HouseholdScreen(api: ApiClient, store: ConnectionStore, householdId: String, onChanged: () -> Unit) {
    var householdName by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var members by remember { mutableStateOf<List<MemberDto>>(emptyList()) }
    var monthBudget by remember { mutableStateOf<MonthBudgetResponse?>(null) }
    var expenses by remember { mutableStateOf<List<HouseholdExpenseDto>>(emptyList()) }
    var weekIndex by remember { mutableStateOf(0) }
    var myMemberId by remember { mutableStateOf<String?>(null) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<HouseholdExpenseDto?>(null) }
    var expenseToDelete by remember { mutableStateOf<HouseholdExpenseDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var everLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    suspend fun reload() {
        try {
            val response = api.household(householdId)
            householdName = response.household.name
            categories = response.categories
            members = response.members
            val budget = api.monthBudget(householdId, today.year, today.monthValue)
            monthBudget = budget
            expenses = api.expenses(householdId, today.year, today.monthValue)
            val currentWeekIndex = budget.weeks.indexOfFirst {
                val start = LocalDate.parse(it.weekStart)
                val end = LocalDate.parse(it.weekEnd)
                !today.isBefore(start) && !today.isAfter(end)
            }
            weekIndex = if (currentWeekIndex >= 0) currentWeekIndex else 0
            myMemberId = store.myMemberId(householdId)
            loadError = null
            everLoaded = true
        } catch (e: Exception) {
            loadError = e.message ?: e::class.simpleName ?: "Couldn't reach the server"
        }
    }

    LaunchedEffect(householdId) { reload() }

    if (!everLoaded && loadError != null) {
        ConnectionErrorScreen(message = loadError!!, onRetry = { scope.launch { reload() } })
        return
    }

    val currency = monthBudget?.effectiveBudget?.currency ?: monthBudget?.defaultBudget?.currency ?: "INR"
    val weeks = monthBudget?.weeks.orEmpty()
    val currentWeek = weeks.getOrNull(weekIndex)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExpense = true }) { Icon(Icons.Filled.Add, contentDescription = "Add Expense") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(householdName.ifBlank { "Household" }, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        BudgetBar("This month", monthBudget?.monthlyEvaluation)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (weekIndex > 0) weekIndex-- }, enabled = weekIndex > 0) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
                            }
                            BudgetBar(
                                currentWeek?.let { "Week of ${it.weekStart}" } ?: "This week",
                                currentWeek?.evaluation,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { if (weekIndex < weeks.size - 1) weekIndex++ }, enabled = weekIndex < weeks.size - 1) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (expenses.isEmpty()) {
                    Text("No expenses recorded this month yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(expenses) { expense ->
                val categoryName = categories.find { it.id == expense.categoryId }?.name ?: expense.categoryId
                val paidByName = members.find { it.id == expense.paidByMemberId }?.displayName ?: expense.paidByMemberId
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(categoryName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Paid by $paidByName · ${formatExpenseDate(expense.occurredAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

    if (showAddExpense) {
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            defaultMemberId = myMemberId,
            onDismiss = { showAddExpense = false },
            onSubmit = { request ->
                scope.launch {
                    api.recordExpense(householdId, request)
                    showAddExpense = false
                    reload()
                    onChanged()
                }
            },
        )
    }

    expenseToEdit?.let { expense ->
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            defaultMemberId = myMemberId,
            expenseToEdit = expense,
            onDismiss = { expenseToEdit = null },
            onSubmit = { request ->
                scope.launch {
                    api.updateExpense(householdId, expense.id, request)
                    expenseToEdit = null
                    reload()
                }
            },
        )
    }

    expenseToDelete?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "This removes the ${formatMoney(expense.amount)} expense and can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                scope.launch {
                    api.deleteExpense(householdId, expense.id)
                    expenseToDelete = null
                    reload()
                }
            },
        )
    }
}
