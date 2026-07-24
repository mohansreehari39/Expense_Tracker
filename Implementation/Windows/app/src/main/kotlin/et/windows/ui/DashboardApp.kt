package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import et.windows.server.CategoryDto
import et.windows.server.HouseholdDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.WeekEvaluationDto
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardApp(api: ApiClient) {
    MaterialTheme {
        var household by remember { mutableStateOf<HouseholdDto?>(null) }
        var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
        var weeks by remember { mutableStateOf<List<WeekEvaluationDto>>(emptyList()) }
        var expenses by remember { mutableStateOf<List<HouseholdExpenseDto>>(emptyList()) }
        var showAddExpense by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val today = remember { LocalDate.now() }

        suspend fun reload() {
            try {
                val (h, cats) = api.household()
                household = h
                categories = cats
                weeks = api.monthBudget(today.year, today.monthValue).weeks
                expenses = api.expenses(today.year, today.monthValue)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: e::class.simpleName
            }
        }

        LaunchedEffect(Unit) { reload() }

        Scaffold(
            topBar = { TopAppBar(title = { Text(household?.name ?: "Expense Tracker") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddExpense = true }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                errorMessage?.let { Text("Couldn't reach the server: $it", color = Color.Red) }

                Text("This Week", style = MaterialTheme.typography.titleMedium)
                val currentWeek = weeks.find {
                    val start = LocalDate.parse(it.weekStart)
                    val end = LocalDate.parse(it.weekEnd)
                    !today.isBefore(start) && !today.isAfter(end)
                }
                if (currentWeek != null) {
                    BudgetStatusBanner(currentWeek.evaluation)
                } else {
                    Text("No budget set for this month yet — set one from the API for now.")
                }

                Spacer(Modifier.height(24.dp))
                Text("Recent Expenses (this month)", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(expenses) { expense ->
                        val categoryName = categories.find { it.id == expense.categoryId }?.name ?: expense.categoryId
                        Text("${formatMoney(expense.amount)} · $categoryName · ${expense.note}".trimEnd(' ', '·'))
                    }
                }
            }
        }

        if (showAddExpense && categories.isNotEmpty() && household != null) {
            val currency = weeks.firstOrNull()?.evaluation?.allocated?.currency ?: "INR"
            AddExpenseDialog(
                categories = categories,
                currency = currency,
                onDismiss = { showAddExpense = false },
                onSubmit = { request ->
                    scope.launch {
                        api.recordExpense(request)
                        showAddExpense = false
                        reload()
                    }
                },
            )
        }
    }
}
