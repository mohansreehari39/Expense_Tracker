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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.CategoryDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.SetBudgetRequest
import et.windows.server.WeekEvaluationDto
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdDetailScreen(api: ApiClient, householdId: String, onBack: () -> Unit) {
    var householdName by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var weeks by remember { mutableStateOf<List<WeekEvaluationDto>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<HouseholdExpenseDto>>(emptyList()) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showSetBudget by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    suspend fun reload() {
        try {
            val response = api.household(householdId)
            householdName = response.household.name
            categories = response.categories
            weeks = api.monthBudget(householdId, today.year, today.monthValue).weeks
            expenses = api.expenses(householdId, today.year, today.monthValue)
            error = null
        } catch (e: Exception) {
            error = e.message ?: e::class.simpleName
        }
    }

    LaunchedEffect(householdId) { reload() }

    val currentWeek = weeks.find {
        val start = LocalDate.parse(it.weekStart)
        val end = LocalDate.parse(it.weekEnd)
        !today.isBefore(start) && !today.isAfter(end)
    }
    val currency = currentWeek?.evaluation?.allocated?.currency ?: "INR"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(householdName.ifBlank { "Household" }) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
        floatingActionButton = {
            if (categories.isNotEmpty()) {
                FloatingActionButton(onClick = { showAddExpense = true }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize()) {
            error?.let { Text("Couldn't reach the server: $it", color = MaterialTheme.colorScheme.error) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("This Week", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showSetBudget = true }) { Text(if (currentWeek == null) "Set Budget" else "Update Budget") }
            }
            Spacer(Modifier.height(8.dp))
            if (currentWeek != null) {
                BudgetStatusBanner(currentWeek.evaluation)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No budget set for this month yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (expenses.isEmpty()) {
                Text("No expenses recorded this month yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(expenses) { expense ->
                        val categoryName = categories.find { it.id == expense.categoryId }?.name ?: expense.categoryId
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(categoryName, style = MaterialTheme.typography.bodyLarge)
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
    }

    if (showAddExpense) {
        AddExpenseDialog(
            categories = categories,
            currency = currency,
            onDismiss = { showAddExpense = false },
            onSubmit = { request ->
                scope.launch {
                    api.recordExpense(householdId, request)
                    showAddExpense = false
                    reload()
                }
            },
        )
    }

    if (showSetBudget) {
        SetBudgetDialog(
            currency = currency,
            onDismiss = { showSetBudget = false },
            onSubmit = { amountMinorUnits, curr ->
                scope.launch {
                    api.setBudget(householdId, SetBudgetRequest(today.year, today.monthValue, amountMinorUnits, curr))
                    showSetBudget = false
                    reload()
                }
            },
        )
    }
}
