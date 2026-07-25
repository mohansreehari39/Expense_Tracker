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
import et.android.kharcha.data.DateRange
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun occurredOn(occurredAt: Long): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun inRange(occurredAt: Long, range: DateRange): Boolean {
    val date = occurredOn(occurredAt)
    return !date.isBefore(range.start) && !date.isAfter(range.endInclusive)
}

@Composable
fun HouseholdScreen(repo: LocalRepository, householdId: String, myName: String) {
    var household by remember { mutableStateOf<HouseholdEntity?>(null) }
    val categories by repo.observeCategories(householdId).collectAsState(initial = emptyList())
    val members by repo.observeMembers(householdId).collectAsState(initial = emptyList())
    val expenses by repo.observeHouseholdExpenses(householdId).collectAsState(initial = emptyList())
    var weekIndex by remember { mutableStateOf(0) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    LaunchedEffect(householdId) {
        household = repo.household(householdId)
        repo.ensureMyMembership(householdId, myName)
    }

    val currentHousehold = household
    val currency = currentHousehold?.currency ?: "INR"
    val myMemberId = members.find { it.isMe }?.id

    val monthExpenses = expenses.filter { occurredOn(it.occurredAt).monthValue == today.monthValue && occurredOn(it.occurredAt).year == today.year }
    val monthEvaluation = currentHousehold?.defaultBudgetMinorUnits?.let {
        BudgetMath.evaluateBudget(it, monthExpenses.sumOf { e -> e.amountMinorUnits }, currency)
    }

    val weeks = remember(today) { BudgetMath.weeksInMonth(today.year, today.monthValue) }
    LaunchedEffect(weeks) {
        val currentIndex = weeks.indexOfFirst { !today.isBefore(it.start) && !today.isAfter(it.endInclusive) }
        weekIndex = if (currentIndex >= 0) currentIndex else 0
    }
    val currentWeek = weeks.getOrNull(weekIndex)
    val weekEvaluation = currentHousehold?.defaultBudgetMinorUnits?.let { budget ->
        currentWeek?.let { week ->
            val allocated = BudgetMath.weekAllocation(budget, today.year, today.monthValue, week)
            val spent = monthExpenses.filter { inRange(it.occurredAt, week) }.sumOf { it.amountMinorUnits }
            BudgetMath.evaluateBudget(allocated, spent, currency)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExpense = true }) { Icon(Icons.Filled.Add, contentDescription = "Add Expense") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(currentHousehold?.name ?: "Household", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        BudgetBar("This month", monthEvaluation)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (weekIndex > 0) weekIndex-- }, enabled = weekIndex > 0) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
                            }
                            BudgetBar(
                                currentWeek?.let { "Week of ${it.start}" } ?: "This week",
                                weekEvaluation,
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
                if (monthExpenses.isEmpty()) {
                    Text("No expenses recorded this month yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(monthExpenses.sortedByDescending { it.occurredAt }) { expense ->
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

    if (showAddExpense) {
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            defaultMemberId = myMemberId,
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            onDismiss = { showAddExpense = false },
            onSubmit = { categoryId, amountMinorUnits, paidByMemberId, occurredAt, note ->
                scope.launch {
                    repo.recordHouseholdExpense(householdId, categoryId, amountMinorUnits, currency, paidByMemberId, occurredAt, note)
                    showAddExpense = false
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
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            expenseToEdit = expense,
            onDismiss = { expenseToEdit = null },
            onSubmit = { categoryId, amountMinorUnits, paidByMemberId, occurredAt, note ->
                scope.launch {
                    repo.updateHouseholdExpense(
                        expense.copy(
                            categoryId = categoryId,
                            amountMinorUnits = amountMinorUnits,
                            paidByMemberId = paidByMemberId,
                            occurredAt = occurredAt,
                            note = note,
                        ),
                    )
                    expenseToEdit = null
                }
            },
        )
    }

    expenseToDelete?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "This removes the ${formatMoney(expense.amountMinorUnits, expense.currency)} expense and can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                scope.launch {
                    repo.deleteHouseholdExpense(expense)
                    expenseToDelete = null
                }
            },
        )
    }
}
