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
import et.windows.server.CategoryDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.MemberDto
import et.windows.server.MonthBudgetResponse
import et.windows.server.MoneyDto
import et.windows.server.SetBudgetRequest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HouseholdDetailScreen(api: ApiClient, householdId: String, refreshSignal: Int) {
    var householdName by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var members by remember { mutableStateOf<List<MemberDto>>(emptyList()) }
    var monthBudget by remember { mutableStateOf<MonthBudgetResponse?>(null) }
    var expenses by remember { mutableStateOf<List<HouseholdExpenseDto>>(emptyList()) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showMonthlyOverride by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val monthLabel = remember(today) { "${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.year}" }

    suspend fun reload() {
        try {
            val response = api.household(householdId)
            householdName = response.household.name
            categories = response.categories
            members = response.members
            monthBudget = api.monthBudget(householdId, today.year, today.monthValue)
            expenses = api.expenses(householdId, today.year, today.monthValue)
            error = null
        } catch (e: Exception) {
            error = e.message ?: e::class.simpleName
        }
    }

    LaunchedEffect(householdId, refreshSignal) { reload() }

    val currency = monthBudget?.effectiveBudget?.currency ?: monthBudget?.defaultBudget?.currency ?: "INR"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(householdName.ifBlank { "Household" }, style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showAddExpense = true }) { Text("➕ Add Expense") }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                error?.let { Text("Couldn't reach the server: $it", color = MaterialTheme.colorScheme.error) }

                val budget = monthBudget
                val monthlyEvaluation = budget?.monthlyEvaluation
                if (budget != null && monthlyEvaluation != null) {
                    MonthlyBudgetChart(
                        monthLabel = monthLabel,
                        monthlyEvaluation = monthlyEvaluation,
                        weeks = budget.weeks,
                        onEditClick = { showMonthlyOverride = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Monthly Budget", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "No budget set for $monthLabel yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { showMonthlyOverride = true }) { Text("Set Monthly Budget") }
                        }
                    }
                }

                if (expenses.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("Spending by Category", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        val totals = expenses.groupBy { it.categoryId }
                            .mapValues { (_, v) -> v.sumOf { it.amount.minorUnits } }
                            .toList()
                            .sortedByDescending { it.second }
                        val entries = totals.mapIndexed { index, (categoryId, total) ->
                            val name = categories.find { it.id == categoryId }?.name ?: "Other"
                            BarEntry(
                                label = name,
                                value = total,
                                color = chartPalette[index % chartPalette.size],
                                valueText = formatMoney(MoneyDto(total, currency)),
                            )
                        }
                        SimpleBarChart(entries, modifier = Modifier.fillMaxWidth().padding(16.dp))
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
                            Text("Paid by $paidByName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    if (showAddExpense) {
        AddExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            onDismiss = { showAddExpense = false },
            onCreateCategory = { name -> api.addCategory(householdId, name) },
            onCreateMember = { name -> api.addMember(householdId, name) },
            onSubmit = { request ->
                scope.launch {
                    api.recordExpense(householdId, request)
                    showAddExpense = false
                    reload()
                }
            },
        )
    }

    if (showMonthlyOverride) {
        MonthlyBudgetOverrideDialog(
            monthLabel = monthLabel,
            currency = currency,
            currentEffectiveAmountMinorUnits = monthBudget?.effectiveBudget?.minorUnits,
            isOverride = monthBudget?.isOverride ?: false,
            onDismiss = { showMonthlyOverride = false },
            onSubmit = { amountMinorUnits ->
                scope.launch {
                    api.setBudget(householdId, SetBudgetRequest(today.year, today.monthValue, amountMinorUnits, currency))
                    showMonthlyOverride = false
                    reload()
                }
            },
        )
    }
}

private val chartPalette = listOf(Indigo, Teal, Amber, Rose, IndigoDark)
